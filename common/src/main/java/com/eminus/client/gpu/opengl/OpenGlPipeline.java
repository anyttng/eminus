package com.eminus.client.gpu.opengl;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.eminus.Eminus;
import com.eminus.gpu.pipeline.Binding;
import com.eminus.gpu.pipeline.Pipeline;
import com.eminus.gpu.pipeline.PipelineSpec;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;

import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL31C;

final class OpenGlPipeline implements Pipeline {
    private static final String SHADER_FOLDER = "shaders/";
    private static final String INCLUDE_FOLDER = "shaders/include/";
    private static final String VERTEX_EXTENSION = ".vsh";
    private static final String FRAGMENT_EXTENSION = ".fsh";
    private static final int NO_PROGRAM = 0;
    private static final int NO_SHADER = 0;
    private static final int NOT_ACTIVE = -1;
    private static final int LOG_LENGTH = 32768;
    private static final String INACTIVE = "inactive";

    record Slot(Binding.Kind kind, int index) {
    }

    private final PipelineSpec spec;
    private final int program;
    private final Map<String, @Nullable Slot> slots;

    private OpenGlPipeline(PipelineSpec spec, int program, Map<String, @Nullable Slot> slots) {
        this.spec = spec;
        this.program = program;
        this.slots = slots;
    }

    static OpenGlPipeline of(OpenGlObjects objects, ResourceProvider resources, PipelineSpec spec) {
        String name = spec.location().toString();
        int vertex = compile(resources, spec, spec.vertexShader(), VERTEX_EXTENSION, GL20C.GL_VERTEX_SHADER);
        int fragment = compile(resources, spec, spec.fragmentShader(), FRAGMENT_EXTENSION, GL20C.GL_FRAGMENT_SHADER);
        if (vertex == NO_SHADER || fragment == NO_SHADER) {
            GL20C.glDeleteShader(vertex);
            GL20C.glDeleteShader(fragment);
            return new OpenGlPipeline(spec, NO_PROGRAM, Map.of());
        }

        int program = GL20C.glCreateProgram();
        GL20C.glAttachShader(program, vertex);
        GL20C.glAttachShader(program, fragment);
        GL20C.glLinkProgram(program);
        GL20C.glDetachShader(program, vertex);
        GL20C.glDetachShader(program, fragment);
        GL20C.glDeleteShader(vertex);
        GL20C.glDeleteShader(fragment);
        if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
            Eminus.LOGGER.error("Program {} did not link: {}", name, GL20C.glGetProgramInfoLog(program, LOG_LENGTH).strip());
            GL20C.glDeleteProgram(program);
            return new OpenGlPipeline(spec, NO_PROGRAM, Map.of());
        }

        objects.created(OpenGlObjects.Kind.PROGRAM, program, name);
        Map<String, @Nullable Slot> slots = new HashMap<>();
        resolve(program, spec, slots);
        return new OpenGlPipeline(spec, program, slots);
    }

    private static int compile(ResourceProvider resources, PipelineSpec spec, Identifier shader, String extension,
            int type) {
        String name = spec.location() + " " + shader + extension;
        String source;
        try {
            source = GlslSource.compose(read(resources, shader.withPath(SHADER_FOLDER + shader.getPath() + extension)),
                    spec.defines(), include -> includeSource(resources, include));
        } catch (IOException | RuntimeException refused) {
            Eminus.LOGGER.error("Shader {} could not be read: {}", name, refused.toString());
            return NO_SHADER;
        }

        int id = GL20C.glCreateShader(type);
        GL20C.glShaderSource(id, source);
        GL20C.glCompileShader(id);
        if (GL20C.glGetShaderi(id, GL20C.GL_COMPILE_STATUS) == GL11C.GL_FALSE) {
            Eminus.LOGGER.error("Shader {} did not compile: {}", name, GL20C.glGetShaderInfoLog(id, LOG_LENGTH).strip());
            GL20C.glDeleteShader(id);
            return NO_SHADER;
        }

        return id;
    }

    private static Optional<String> includeSource(ResourceProvider resources, Identifier include) {
        try {
            return Optional.of(read(resources, include.withPrefix(INCLUDE_FOLDER)));
        } catch (IOException missing) {
            return Optional.empty();
        }
    }

    private static String read(ResourceProvider resources, Identifier file) throws IOException {
        Resource resource = resources.getResource(file)
                .orElseThrow(() -> new IOException("No resource " + file));
        try (Reader reader = resource.openAsReader()) {
            return reader.readAllAsString();
        }
    }

    private static void resolve(int program, PipelineSpec spec, Map<String, @Nullable Slot> slots) {
        int nextBlock = 0;
        int nextUnit = 0;
        GL20C.glUseProgram(program);
        for (Binding binding : spec.bindings()) {
            switch (binding.kind()) {
                case UNIFORM -> {
                    int index = GL31C.glGetUniformBlockIndex(program, binding.name());
                    if (index == GL31C.GL_INVALID_INDEX) {
                        slots.put(binding.name(), null);
                    } else {
                        GL31C.glUniformBlockBinding(program, index, nextBlock);
                        slots.put(binding.name(), new Slot(binding.kind(), nextBlock++));
                    }
                }
                case TEXEL, SAMPLED -> {
                    int location = GL20C.glGetUniformLocation(program, binding.name());
                    if (location == NOT_ACTIVE) {
                        slots.put(binding.name(), null);
                    } else {
                        GL20C.glUniform1i(location, nextUnit);
                        slots.put(binding.name(), new Slot(binding.kind(), nextUnit++));
                    }
                }
                case STORAGE -> throw new IllegalArgumentException("Own OpenGL binds no storage buffers");
            }
        }
        GL20C.glUseProgram(NO_PROGRAM);

        StringBuilder resolved = new StringBuilder();
        for (Map.Entry<String, @Nullable Slot> slot : slots.entrySet()) {
            resolved.append(' ').append(slot.getKey()).append(':')
                    .append(slot.getValue() == null ? INACTIVE : slot.getValue().kind() + "" + slot.getValue().index());
        }
        Eminus.LOGGER.info("[eminus-gl] program name={} blocks={} units={} text={}", spec.location(),
                nextBlock, nextUnit, resolved.toString().strip());
    }

    PipelineSpec spec() {
        return spec;
    }

    Map<String, @Nullable Slot> slots() {
        return slots;
    }

    int program() {
        if (program == NO_PROGRAM) {
            throw new IllegalStateException("Pipeline " + spec.location() + " did not compile");
        }
        return program;
    }

    @Nullable Slot slot(String name) {
        if (!slots.containsKey(name)) {
            throw new IllegalArgumentException("Pipeline " + spec.location() + " declares no binding " + name);
        }
        return slots.get(name);
    }

    @Override
    public Identifier location() {
        return spec.location();
    }

    @Override
    public boolean compiles() {
        return program != NO_PROGRAM;
    }

    void delete(OpenGlObjects objects) {
        if (program != NO_PROGRAM) {
            GL20C.glDeleteProgram(program);
            objects.deleted(OpenGlObjects.Kind.PROGRAM);
        }
    }
}
