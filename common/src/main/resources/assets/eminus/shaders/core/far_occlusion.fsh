#version 330

layout(std140) uniform Occlusion {
    mat4 FarViewProjection;
    mat4 FarInverse;
    mat4 GameInverse;
    mat4 GameToFar;
    float FocalPixels;
    float DepthBias;
};

#moj_import <eminus:far_depth.glsl>

uniform sampler2D FarDepth;
uniform sampler2D GameDepth;

out vec4 fragColor;

const float TAU = 6.28318531;
const float GOLDEN_ANGLE = 2.39996323;
const float MIN_DISTANCE_DIFFERENCE = 1.0e-4;

float interleaved_noise(vec2 pixel) {
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

bool far_surface(vec2 uv, float far, float game) {
    return NEARER(far, FARTHEST)
            && (!NEARER(game, FARTHEST) || NEARER(far, far_mask_depth(GameToFar, uv, game, DepthBias)));
}

bool scene_position(ivec2 texel, ivec2 size, out vec3 position) {
    position = vec3(0.0);
    if (any(lessThan(texel, ivec2(0))) || any(greaterThanEqual(texel, size))) {
        return false;
    }

    vec2 uv = (vec2(texel) + 0.5) / vec2(size);
    float far = texelFetch(FarDepth, texel, 0).r;
    float game = texelFetch(GameDepth, texel, 0).r;
    if (far_surface(uv, far, game)) {
        position = far_unproject(FarInverse, uv, far);
        return true;
    }

    if (NEARER(game, FARTHEST)) {
        position = far_unproject(GameInverse, uv, game);
        return true;
    }

    return false;
}

bool surface_step(vec3 centre, ivec2 texel, ivec2 size, ivec2 offset, out vec3 difference) {
    vec3 ahead;
    vec3 behind;
    bool hasAhead = scene_position(texel + offset, size, ahead);
    bool hasBehind = scene_position(texel - offset, size, behind);
    vec3 toAhead = ahead - centre;
    vec3 fromBehind = centre - behind;

    if (hasAhead && (!hasBehind || length(toAhead) < length(fromBehind))) {
        difference = toAhead;
        return true;
    }

    difference = fromBehind;
    return hasBehind;
}

void main() {
    ivec2 size = textureSize(FarDepth, 0);
    ivec2 texel = ivec2(gl_FragCoord.xy);
    vec2 uv = (vec2(texel) + 0.5) / vec2(size);
    float far = texelFetch(FarDepth, texel, 0).r;
    if (!far_surface(uv, far, texelFetch(GameDepth, texel, 0).r)) {
        discard;
    }

    vec3 centre = far_unproject(FarInverse, uv, far);

    vec3 normal = -normalize(centre);
    vec3 alongX;
    vec3 alongY;
    if (surface_step(centre, texel, size, ivec2(1, 0), alongX) && surface_step(centre, texel, size, ivec2(0, 1), alongY)) {
        vec3 crossed = cross(alongX, alongY);
        if (dot(crossed, crossed) > 0.0) {
            normal = normalize(crossed);
            if (dot(normal, centre) > 0.0) {
                normal = -normal;
            }
        }
    }

    vec3 tangent = normalize(abs(normal.y) < 0.99 ? cross(normal, vec3(0.0, 1.0, 0.0)) : cross(normal, vec3(1.0, 0.0, 0.0)));
    vec3 bitangent = cross(normal, tangent);

    float distance = length(centre);
    float radius = max(RADIUS, PIXEL_RADIUS * distance / FocalPixels);
    float bias = MIN_BIAS + distance * distance * BIAS_PER_SQUARED_BLOCK;
    float noise = interleaved_noise(vec2(texel));
    float occlusion = 0.0;

    for (int i = 0; i < SAMPLES; i++) {
        float height = (float(i) + 0.5) / float(SAMPLES);
        float angle = float(i) * GOLDEN_ANGLE + noise * TAU;
        float spread = sqrt(height);
        vec3 direction = (tangent * cos(angle) + bitangent * sin(angle)) * spread + normal * sqrt(1.0 - height);
        vec3 probe = centre + direction * radius;

        vec4 clip = FarViewProjection * vec4(probe, 1.0);
        if (clip.w <= 0.0) {
            continue;
        }

        vec3 hit;
        if (!scene_position(ivec2((clip.xy / clip.w * 0.5 + 0.5) * vec2(size)), size, hit)) {
            continue;
        }

        float hitDistance = length(hit);
        if (hitDistance < length(probe) - bias) {
            occlusion += clamp(radius / max(abs(distance - hitDistance), MIN_DISTANCE_DIFFERENCE), 0.0, 1.0);
        }
    }

    fragColor = vec4(vec3(1.0 - STRENGTH * occlusion / float(SAMPLES)), 1.0);
}
