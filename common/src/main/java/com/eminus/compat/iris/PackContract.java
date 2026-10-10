package com.eminus.compat.iris;

import java.util.ArrayList;
import java.util.List;

public final class PackContract {
    public static final int VERSION = 6;
    public static final String OPAQUE_FILE = "eminus_opaque.glsl";
    public static final String TRANSLUCENT_FILE = "eminus_translucent.glsl";
    public static final String SHADOW_FILE = "eminus_shadow.glsl";
    public static final String SHADOW_TRANSLUCENT_FILE = "eminus_shadow_translucent.glsl";
    public static final String SHADOW_VERTEX_FILE = "eminus_shadow_vertex.glsl";
    public static final String VERTEX_FILE = "eminus_vertex.glsl";
    public static final List<String> FILES = List.of(OPAQUE_FILE, TRANSLUCENT_FILE, SHADOW_FILE,
            SHADOW_TRANSLUCENT_FILE, SHADOW_VERTEX_FILE, VERTEX_FILE);
    public static final String MACRO = "EMINUS";
    public static final String VERSION_MACRO = "EMINUS_CONTRACT_VERSION";
    public static final String FUNCTION = "eminus_emitFragment";
    public static final String DEPTH_SAMPLER = "eminusDepthTex0";
    public static final String OPAQUE_DEPTH_SAMPLER = "eminusDepthTex1";
    public static final String PROJECTION = "eminusProjection";
    public static final String PROJECTION_INVERSE = "eminusProjectionInverse";
    public static final String PREVIOUS_PROJECTION = "eminusPreviousProjection";
    public static final String RENDER_DISTANCE = "eminusRenderDistance";
    public static final List<String> UNIFORMS = List.of(PROJECTION, PROJECTION_INVERSE, PREVIOUS_PROJECTION,
            RENDER_DISTANCE);
    public static final String VIEW_POSITION = "eminus_viewPosition";
    public static final String VIEW_POINT = "eminus_viewPoint";
    public static final String VIEW_NEAR_DEPTH = "eminus_viewNearDepth0";
    public static final String VIEW_NEAR_OPAQUE_DEPTH = "eminus_viewNearDepth1";
    public static final String VIEW_FAR_DEPTH = "eminus_viewFarDepth0";
    public static final String VIEW_FAR_OPAQUE_DEPTH = "eminus_viewFarDepth1";
    public static final String VIEW_NEAR_PROJECTION_INVERSE = "eminus_viewNearProjectionInverse";
    public static final String VIEW_FAR_PROJECTION_INVERSE = "eminus_viewFarProjectionInverse";
    public static final String VIEW_FAR_DISTANCE = "eminus_viewFarDistance";
    public static final String NEAR_DEPTH = "depthtex0";
    public static final String NEAR_OPAQUE_DEPTH = "depthtex1";
    public static final List<String> SAMPLERS = List.of(DEPTH_SAMPLER, OPAQUE_DEPTH_SAMPLER, VIEW_NEAR_DEPTH,
            VIEW_NEAR_OPAQUE_DEPTH, VIEW_FAR_DEPTH, VIEW_FAR_OPAQUE_DEPTH);

    public static final String ROOT = "/";

    private PackContract() {
    }

    public static List<String> paths(List<String> folders) {
        List<String> paths = new ArrayList<>();
        for (String file : FILES) {
            paths.add(ROOT + file);
            for (String folder : folders) {
                paths.add(ROOT + folder + ROOT + file);
            }
        }
        return paths;
    }
}
