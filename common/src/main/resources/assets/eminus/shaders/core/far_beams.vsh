#version 330
#extension GL_ARB_separate_shader_objects : require

#include <eminus:far_beams.glsl>

uniform usamplerBuffer BeamRecords;

layout(location = 0) out vec4 vertexColor;

const int FACE_VERTICES = 6;
const vec4 CULLED_POSITION = vec4(2.0, 2.0, 2.0, 1.0);

const vec2 CORNERS[8] = vec2[](
    vec2(-1.0, -1.0), vec2(1.0, -1.0),
    vec2(1.0, 1.0), vec2(-1.0, 1.0),
    vec2(-1.0, 1.0), vec2(-1.0, -1.0),
    vec2(1.0, -1.0), vec2(1.0, 1.0));

const vec2 NORMALS[4] = vec2[](
    vec2(0.0, -1.0), vec2(0.0, 1.0), vec2(-1.0, 0.0), vec2(1.0, 0.0));

const int SIDES[6] = int[](0, 1, 1, 0, 1, 0);
const int ENDS[6] = int[](0, 0, 1, 0, 1, 1);

vec4 beam_color(uint argb, bool glow) {
    vec3 rgb = vec3(float((argb >> 16) & 255u), float((argb >> 8) & 255u), float(argb & 255u)) / 255.0;
    return vec4(rgb, glow ? float(BEAM_GLOW_ALPHA) / 255.0 : 1.0);
}

void main() {
    int prismVertices = BeamSections * BEAM_PRISM_VERTICES;
    bool glow = gl_VertexIndex >= prismVertices;
    int local = gl_VertexIndex % prismVertices;
    int section = local / BEAM_PRISM_VERTICES;
    int vertex = local % BEAM_PRISM_VERTICES;
    int face = vertex / FACE_VERTICES;
    int corner = vertex % FACE_VERTICES;

    int first = section * BEAM_TEXELS;
    uvec4 low = texelFetch(BeamRecords, first);
    int top = int(texelFetch(BeamRecords, first + 1).x);
    ivec3 base = ivec3(low.xyz);

    vec3 center = vec3(base - BeamCameraBlockPos) + vec3(0.5, 0.0, 0.5) + BeamCameraOffset;
    float scale = max(1.0, length(center.xz) / BEAM_SCALE_DISTANCE);
    float radius = (glow ? BEAM_GLOW_RADIUS : BEAM_SOLID_RADIUS) * scale;

    vec2 across = CORNERS[face * 2 + SIDES[corner]] * radius;
    float y = float((ENDS[corner] == 0 ? base.y : top) - BeamCameraBlockPos.y) + BeamCameraOffset.y;
    vec3 position = vec3(center.x + across.x, y, center.z + across.y);

    vec2 toFace = center.xz + NORMALS[face] * radius;
    gl_Position = dot(NORMALS[face], toFace) < 0.0 ? BeamProjView * vec4(position, 1.0) : CULLED_POSITION;
    vertexColor = beam_color(low.w, glow);
}
