#ifndef EMINUS_FAR_CORNERS_GLSL
#define EMINUS_FAR_CORNERS_GLSL

const float EMINUS_LIT_SHADE = 1.0;
const float EMINUS_DARK_SHADE = 0.2;
const float EMINUS_SHARP_FOOTPRINT = 0.5;
const float EMINUS_FILTERED_FOOTPRINT = 1.5;
const float EMINUS_POINT_WINDOW = 1.0e-4;

struct EminusCorners {
    float front;
    vec4 edges;
    vec4 twists;
};

float eminus_shade(uint word, int bit) {
    return (word & uint(bit)) != 0u ? EMINUS_DARK_SHADE : EMINUS_LIT_SHADE;
}

EminusCorners eminus_corners(uint word) {
    float front = eminus_shade(word, OCCLUSION_FRONT);
    vec4 sides = vec4(eminus_shade(word, OCCLUSION_LOW_U), eminus_shade(word, OCCLUSION_HIGH_U),
                      eminus_shade(word, OCCLUSION_LOW_V), eminus_shade(word, OCCLUSION_HIGH_V));
    vec4 corners = vec4(eminus_shade(word, OCCLUSION_LOW_U_LOW_V), eminus_shade(word, OCCLUSION_HIGH_U_LOW_V),
                        eminus_shade(word, OCCLUSION_LOW_U_HIGH_V), eminus_shade(word, OCCLUSION_HIGH_U_HIGH_V));
    vec4 edges = (sides + front) * 0.5;
    vec4 vertices = (sides.xyxy + sides.zzww + corners + front) * 0.25;
    return EminusCorners(front, edges, vertices - edges.xyxy - edges.zzww + front);
}

float eminus_ramp(float distance) {
    return clamp(1.0 - distance, 0.0, 1.0);
}

float eminus_ramp_integral(float distance) {
    return distance <= 0.0 ? distance : (distance >= 1.0 ? 0.5 : distance - 0.5 * distance * distance);
}

float eminus_ramp_mean(float from, float to) {
    return to - from < EMINUS_POINT_WINDOW ? eminus_ramp(0.5 * (from + to))
            : (eminus_ramp_integral(to) - eminus_ramp_integral(from)) / (to - from);
}

float eminus_field(EminusCorners corners, vec4 ramps) {
    return corners.front + dot(corners.edges - corners.front, ramps)
            + dot(corners.twists, ramps.xyxy * ramps.zzww);
}

vec4 eminus_ramps(vec2 point, vec2 size) {
    return vec4(eminus_ramp(point.x), eminus_ramp(size.x - point.x), eminus_ramp(point.y), eminus_ramp(size.y - point.y));
}

float eminus_triangles(EminusCorners corners, vec2 point, vec2 size, bool mainDiagonal) {
    vec2 cell = clamp(floor(point), vec2(0.0), size - 1.0);
    vec2 local = clamp(point - cell, 0.0, 1.0);
    float low = eminus_field(corners, eminus_ramps(cell, size));
    float across = eminus_field(corners, eminus_ramps(cell + vec2(1.0, 0.0), size));
    float high = eminus_field(corners, eminus_ramps(cell + vec2(1.0), size));
    float up = eminus_field(corners, eminus_ramps(cell + vec2(0.0, 1.0), size));
    float twist = low + high - across - up;
    vec2 folded = mainDiagonal ? local : vec2(1.0 - local.x, local.y);
    float fold = min(folded.x, folded.y) * (1.0 - max(folded.x, folded.y));
    return eminus_field(corners, eminus_ramps(point, size)) + (mainDiagonal ? twist : -twist) * fold;
}

float eminus_filtered(EminusCorners corners, vec2 point, vec2 size, vec2 footprint) {
    vec2 from = max(point - 0.5 * footprint, vec2(0.0));
    vec2 to = min(point + 0.5 * footprint, size);
    vec4 ramps = vec4(eminus_ramp_mean(from.x, to.x), eminus_ramp_mean(size.x - to.x, size.x - from.x),
                      eminus_ramp_mean(from.y, to.y), eminus_ramp_mean(size.y - to.y, size.y - from.y));
    return eminus_field(corners, ramps);
}

float eminus_corner_occlusion(uint word, vec2 point, vec2 size, bool mainDiagonal, vec2 footprint) {
    if (EminusSmoothLighting == 0 || word == 0u) {
        return EMINUS_LIT_SHADE;
    }

    EminusCorners corners = eminus_corners(word);
    float spread = max(footprint.x, footprint.y);
    float filtered = smoothstep(EMINUS_SHARP_FOOTPRINT, EMINUS_FILTERED_FOOTPRINT, spread);
    if (filtered >= 1.0) {
        return eminus_filtered(corners, point, size, footprint);
    }

    float exact = eminus_triangles(corners, point, size, mainDiagonal);
    return filtered <= 0.0 ? exact : mix(exact, eminus_filtered(corners, point, size, footprint), filtered);
}

#endif
