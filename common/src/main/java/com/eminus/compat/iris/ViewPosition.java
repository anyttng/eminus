package com.eminus.compat.iris;

public final class ViewPosition {
    static final String SOURCE = """
            uniform sampler2D %3$s;
            uniform sampler2D %4$s;
            uniform sampler2D %5$s;
            uniform sampler2D %6$s;
            uniform mat4 %7$s;
            uniform mat4 %8$s;
            uniform int %9$s;

            vec3 %2$s(mat4 projectionInverse, vec2 texcoord, float depth) {
                vec4 view = projectionInverse * vec4(vec3(texcoord, depth) * 2.0 - 1.0, 1.0);
                return view.xyz / view.w;
            }

            vec4 %1$s(vec2 texcoord, bool opaqueOnly) {
                float nearDepth = opaqueOnly ? texture(%4$s, texcoord).r : texture(%3$s, texcoord).r;
                if (nearDepth < 1.0) {
                    return vec4(%2$s(%7$s, texcoord, nearDepth), 1.0);
                }
                if (%9$s > 0) {
                    float farDepth = opaqueOnly ? texture(%6$s, texcoord).r : texture(%5$s, texcoord).r;
                    if (farDepth < 1.0) {
                        return vec4(%2$s(%8$s, texcoord, farDepth), 1.0);
                    }
                }
                return vec4(%2$s(%7$s, texcoord, 1.0), 0.0);
            }
            """.formatted(PackContract.VIEW_POSITION, PackContract.VIEW_POINT, PackContract.VIEW_NEAR_DEPTH,
            PackContract.VIEW_NEAR_OPAQUE_DEPTH, PackContract.VIEW_FAR_DEPTH, PackContract.VIEW_FAR_OPAQUE_DEPTH,
            PackContract.VIEW_NEAR_PROJECTION_INVERSE, PackContract.VIEW_FAR_PROJECTION_INVERSE,
            PackContract.VIEW_FAR_DISTANCE);

    private static final String NO_MAIN = "";

    private ViewPosition() {
    }

    public static String splice(String source) {
        if (!source.contains(PackContract.VIEW_POSITION)) {
            return source;
        }
        try {
            return PackSources.insert(source, SOURCE, NO_MAIN);
        } catch (IllegalArgumentException noVersion) {
            return source;
        }
    }
}
