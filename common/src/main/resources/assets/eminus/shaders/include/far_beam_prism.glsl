#ifndef EMINUS_FAR_BEAM_PRISM_GLSL
#define EMINUS_FAR_BEAM_PRISM_GLSL

const int EMINUS_BEAM_FACE_VERTICES = 6;
const int EMINUS_BEAM_FIRST_SIDE_FACE = 2;

const vec2 EMINUS_BEAM_CORNERS[8] = vec2[](
    vec2(-1.0, -1.0), vec2(1.0, -1.0),
    vec2(1.0, 1.0), vec2(-1.0, 1.0),
    vec2(-1.0, 1.0), vec2(-1.0, -1.0),
    vec2(1.0, -1.0), vec2(1.0, 1.0));

const vec2 EMINUS_BEAM_NORMALS[4] = vec2[](
    vec2(0.0, -1.0), vec2(0.0, 1.0), vec2(-1.0, 0.0), vec2(1.0, 0.0));

const int EMINUS_BEAM_SIDES[6] = int[](0, 1, 1, 0, 1, 0);
const int EMINUS_BEAM_ENDS[6] = int[](0, 0, 1, 0, 1, 1);

struct EminusBeamVertex {
    vec3 position;
    vec3 normal;
    int face;
    float height;
    vec4 color;
    bool culled;
};

vec4 eminus_beam_color(uint argb, bool glow) {
    vec3 rgb = vec3(float((argb >> 16) & 255u), float((argb >> 8) & 255u), float(argb & 255u)) / 255.0;
    return vec4(rgb, glow ? float(BEAM_GLOW_ALPHA) / 255.0 : 1.0);
}

EminusBeamVertex eminus_beam_vertex(int section, int vertex, bool glow, ivec3 cameraBlockPos, vec3 cameraOffset) {
    int face = vertex / EMINUS_BEAM_FACE_VERTICES;
    int corner = vertex % EMINUS_BEAM_FACE_VERTICES;

    int first = section * BEAM_TEXELS;
    uvec4 low = texelFetch(EminusBeamRecords, first);
    int top = int(texelFetch(EminusBeamRecords, first + 1).x);
    ivec3 base = ivec3(low.xyz);

    vec3 center = vec3(base - cameraBlockPos) + vec3(0.5, 0.0, 0.5) + cameraOffset;
    float scale = max(1.0, length(center.xz) / BEAM_SCALE_DISTANCE);
    float radius = (glow ? BEAM_GLOW_RADIUS : BEAM_SOLID_RADIUS) * scale;

    vec2 across = EMINUS_BEAM_CORNERS[face * 2 + EMINUS_BEAM_SIDES[corner]] * radius;
    int end = EMINUS_BEAM_ENDS[corner] == 0 ? base.y : top;
    float y = float(end - cameraBlockPos.y) + cameraOffset.y;
    vec2 normal = EMINUS_BEAM_NORMALS[face];
    vec2 toFace = center.xz + normal * radius;

    EminusBeamVertex beam;
    beam.position = vec3(center.x + across.x, y, center.z + across.y);
    beam.normal = vec3(normal.x, 0.0, normal.y);
    beam.face = EMINUS_BEAM_FIRST_SIDE_FACE + face;
    beam.height = float(end - base.y);
    beam.color = eminus_beam_color(low.w, glow);
    beam.culled = dot(normal, toFace) >= 0.0;
    return beam;
}

#endif
