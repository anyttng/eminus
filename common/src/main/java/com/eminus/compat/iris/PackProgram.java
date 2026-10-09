package com.eminus.compat.iris;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.eminus.Eminus;
import com.eminus.client.render.far.FarDraw;
import com.eminus.client.render.far.FarShadow;
import com.eminus.client.render.far.FarTarget;
import com.eminus.gpu.Foreign;
import com.eminus.gpu.Location;
import com.eminus.gpu.ShaderSources;
import com.eminus.gpu.pass.Pass;
import com.eminus.gpu.pipeline.Pipeline;
import com.eminus.gpu.texture.Texture;

import com.google.common.primitives.Ints;

import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.blending.BufferBlendOverride;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.program.ProgramImages;
import net.irisshaders.iris.gl.program.ProgramSamplers;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.ShaderPrinter;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import net.irisshaders.iris.samplers.IrisSamplers;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.builtin.BuiltinReplacementUniforms;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryStack;

public final class PackProgram {
    private static final int FIRST_UNIT = IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS.stream()
            .mapToInt(Integer::intValue).max().orElseThrow() + 1;
    private static final String LOCATION_PREFIX = "pack";
    private static final String MODEL_VIEW = "iris_ModelViewMatrix";
    private static final String MODEL_VIEW_INVERSE = "iris_ModelViewMatrixInverse";
    private static final String PROJECTION = "iris_ProjectionMatrix";
    private static final String PROJECTION_INVERSE = "iris_ProjectionMatrixInverse";
    private static final String NORMAL_MATRIX = "iris_NormalMatrix";
    private static final String DH_EXTRA = "irisExtra";
    private static final String DH_POSITION = "vPosition";
    private static final int NO_PROGRAM = 0;
    private static final int ZERO = 0;
    private static final int MAT4_FLOATS = 16;
    private static final int MAT3_FLOATS = 9;
    private static final boolean TRANSPOSE = false;
    private static final Pattern CORE_VERSION = Pattern.compile("#version\\s+(\\d+)\\s+compatibility");
    private static final String CORE_PROFILE = "#version $1 core";

    private final Foreign foreign;
    private final Pipeline pipeline;
    private final ProgramSource source;
    private final Object pass;
    private final CustomUniforms customUniforms;
    private final ProgramUniforms uniforms;
    private final ProgramSamplers samplers;
    private final ProgramImages images;
    private final List<BufferBlendOverride> bufferBlends;
    private final int modelView;
    private final int modelViewInverse;
    private final int projection;
    private final int projectionInverse;
    private final int normalMatrix;
    private final int dhExtra;
    private final int dhPosition;
    private final Matrix4f inverse = new Matrix4f();
    private final Matrix3f normal = new Matrix3f();

    public enum Kind {
        OPAQUE,
        TRANSLUCENT,
        SHADOW
    }

    private PackProgram(Foreign foreign, Pipeline pipeline, ProgramSource source, Object pass,
            CustomUniforms customUniforms, ProgramUniforms uniforms, ProgramSamplers samplers, ProgramImages images) {
        this.foreign = foreign;
        this.pipeline = pipeline;
        this.source = source;
        this.pass = pass;
        this.customUniforms = customUniforms;
        this.uniforms = uniforms;
        this.samplers = samplers;
        this.images = images;
        this.bufferBlends = bufferBlends(source);
        int program = foreign.program(pipeline);
        modelView = GL20C.glGetUniformLocation(program, MODEL_VIEW);
        modelViewInverse = GL20C.glGetUniformLocation(program, MODEL_VIEW_INVERSE);
        projection = GL20C.glGetUniformLocation(program, PROJECTION);
        projectionInverse = GL20C.glGetUniformLocation(program, PROJECTION_INVERSE);
        normalMatrix = GL20C.glGetUniformLocation(program, NORMAL_MATRIX);
        dhExtra = GL20C.glGetAttribLocation(program, DH_EXTRA);
        dhPosition = GL20C.glGetAttribLocation(program, DH_POSITION);
    }

    static @Nullable PackProgram build(FarDraw draw, Foreign foreign, IrisRenderingPipeline irisPipeline,
            ProgramSet programSet, ShaderProperties properties, PackPath.Source source, Kind kind) {
        String name = source.programName();
        Location location = new Location(Eminus.MODID, LOCATION_PREFIX + source.file());
        ProgramSource[] built = new ProgramSource[1];
        Pipeline pipeline = foreign.pipeline(
                kind == Kind.SHADOW ? draw.shadowPipeline(location, source.vertexStage())
                        : draw.packPipeline(location, kind == Kind.TRANSLUCENT, source.vertexStage()),
                FIRST_UNIT,
                ours -> {
                    String spliced = source.fragment().apply(ours.fragment());
                    String splicedVertex = source.vertex() == null ? null : source.vertex().apply(ours.vertex());
                    built[0] = new ProgramSource(name, null, null, null, null, spliced, programSet, properties, null);
                    // A vertex stage Iris patches carries its smooth outputs to our fragment divided by w.
                    boolean hook = source.vertexStage().hook();
                    Map<PatchShaderType, String> patched = TransformPatcher.patchDHTerrain(name,
                            hook ? null : splicedVertex, null, null, null, spliced, irisPipeline.getTextureMap(),
                            irisPipeline.getTextureOverrides(TextureStage.GBUFFERS_AND_SHADOW));
                    ShaderPrinter.printProgram(name).addSources(patched).print();
                    String vertex = splicedVertex == null ? ours.vertex()
                            : hook ? CORE_VERSION.matcher(splicedVertex).replaceFirst(CORE_PROFILE)
                                    : patched.get(PatchShaderType.VERTEX);
                    return new ShaderSources(vertex, patched.get(PatchShaderType.FRAGMENT));
                });
        if (!pipeline.compiles()) {
            foreign.release(pipeline);
            return null;
        }

        int program = foreign.program(pipeline);
        Set<Integer> reserved = IntStream.range(0, foreign.textureUnits(pipeline)).boxed().collect(Collectors.toSet());
        GL20C.glUseProgram(program);
        try {
            ProgramUniforms.Builder uniforms = ProgramUniforms.builder(name, program);
            ProgramSamplers.Builder samplers = ProgramSamplers.builder(program, reserved);
            ProgramImages.Builder images = ProgramImages.builder(program);
            CustomUniforms customUniforms = irisPipeline.getCustomUniforms();
            CommonUniforms.addDynamicUniforms(uniforms, FogMode.PER_VERTEX);
            customUniforms.assignTo(uniforms);
            BuiltinReplacementUniforms.addBuiltinReplacementUniforms(uniforms);
            irisPipeline.addGbufferOrShadowSamplers(samplers, images, switch (kind) {
                case OPAQUE -> irisPipeline::getFlippedAfterPrepare;
                case TRANSLUCENT -> irisPipeline::getFlippedAfterTranslucent;
                case SHADOW -> irisPipeline::getFlippedBeforeShadow;
            }, kind == Kind.SHADOW, false, true, false);
            Object pass = new Object();
            customUniforms.mapholderToPass(uniforms, pass);
            return new PackProgram(foreign, pipeline, built[0], pass, customUniforms, uniforms.buildUniforms(),
                    samplers.build(), images.build());
        } catch (RuntimeException refused) {
            Eminus.LOGGER.error("Shader pack file {} did not bind to the pack: {}", source.file(), refused.toString());
            foreign.release(pipeline);
            return null;
        } finally {
            GL20C.glUseProgram(NO_PROGRAM);
        }
    }

    ProgramSource source() {
        return source;
    }

    void release() {
        foreign.release(pipeline);
    }

    void draw(FarDraw draw, GlFramebuffer framebuffer, boolean translucent) {
        FarTarget target = draw.target();
        try (Pass farPass = open(framebuffer, target.width(), target.height())) {
            use(farPass, draw.view(), draw.projection(), draw.lightmap());
            draw.bind(farPass);
            if (translucent) {
                draw.drawTranslucent(farPass);
            } else {
                draw.drawOpaque(farPass);
            }
        } finally {
            restore();
        }
    }

    void drawShadow(FarDraw draw, FarShadow shadow, GlFramebuffer framebuffer, int resolution) {
        try (Pass farPass = open(framebuffer, resolution, resolution)) {
            use(farPass, shadow.view(), shadow.projection(), draw.lightmap());
            draw.bindShadow(farPass, shadow);
            shadow.draw(farPass);
        } finally {
            restore();
        }
    }

    private Pass open(GlFramebuffer framebuffer, int width, int height) {
        return foreign.pass(framebuffer.getId(), width, height, source.getDirectives().getDrawBuffers().length);
    }

    private void use(Pass farPass, Matrix4fc view, Matrix4fc projection, Texture lightmap) {
        farPass.pipeline(pipeline);
        samplers.update();
        uniforms.update();
        customUniforms.push(pass);
        images.update();
        matrices(view, projection);
        zeroAttribute(dhExtra);
        zeroAttribute(dhPosition);
        IrisRenderSystem.bindTextureToUnit(GL11C.GL_TEXTURE_2D, IrisSamplers.LIGHTMAP_TEXTURE_UNIT,
                foreign.texture(lightmap));
        source.getDirectives().getBlendModeOverride().ifPresent(BlendModeOverride::apply);
        bufferBlends.forEach(BufferBlendOverride::apply);
    }

    private static void restore() {
        ProgramUniforms.clearActiveUniforms();
        ProgramSamplers.clearActiveSamplers();
        BlendModeOverride.restore();
    }

    private void matrices(Matrix4fc view, Matrix4fc farProjection) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer matrix = stack.mallocFloat(MAT4_FLOATS);
            matrix4(modelView, view, matrix);
            matrix4(modelViewInverse, view.invert(inverse), matrix);
            matrix4(projection, farProjection, matrix);
            matrix4(projectionInverse, farProjection.invert(inverse), matrix);
            if (normalMatrix >= 0) {
                GL20C.glUniformMatrix3fv(normalMatrix, TRANSPOSE,
                        view.invert(inverse).transpose3x3(normal).get(stack.mallocFloat(MAT3_FLOATS)));
            }
        }
    }

    // Iris's _vert_init reads iris_TexId from these; with no array bound, zero keeps dh_hasTexture() false.
    private static void zeroAttribute(int location) {
        if (location >= 0) {
            GL30C.glVertexAttribI4ui(location, ZERO, ZERO, ZERO, ZERO);
        }
    }

    private static void matrix4(int location, Matrix4fc value, FloatBuffer scratch) {
        if (location >= 0) {
            GL20C.glUniformMatrix4fv(location, TRANSPOSE, value.get(scratch));
        }
    }

    private static List<BufferBlendOverride> bufferBlends(ProgramSource source) {
        List<BufferBlendOverride> overrides = new ArrayList<>();
        int[] drawBuffers = source.getDirectives().getDrawBuffers();
        source.getDirectives().getBufferBlendOverrides().forEach(information -> {
            int index = Ints.indexOf(drawBuffers, information.index());
            if (index >= 0) {
                overrides.add(new BufferBlendOverride(index, information.blendMode()));
            }
        });
        return overrides;
    }
}
