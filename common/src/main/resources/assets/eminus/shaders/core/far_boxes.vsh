#version 330
#extension GL_ARB_separate_shader_objects : require

#include <eminus:far_lightmap.glsl>
#include <eminus:far_boxes.glsl>

uniform usamplerBuffer BoxRecords;
uniform sampler2D Lightmap;

layout(location = 0) out vec4 vertexColor;
layout(location = 1) out vec3 eyePoint;

const int FACE_CORNERS = 4;
const int FACE_VERTICES = 6;
const int FULL_SKY_LIGHT = 240;
const vec4 CULLED_POSITION = vec4(2.0, 2.0, 2.0, 1.0);

const ivec3 CORNERS[24] = ivec3[](
    ivec3(0, 0, 0), ivec3(1, 0, 0), ivec3(1, 0, 1), ivec3(0, 0, 1),
    ivec3(0, 1, 0), ivec3(0, 1, 1), ivec3(1, 1, 1), ivec3(1, 1, 0),
    ivec3(0, 0, 0), ivec3(0, 1, 0), ivec3(1, 1, 0), ivec3(1, 0, 0),
    ivec3(0, 0, 1), ivec3(1, 0, 1), ivec3(1, 1, 1), ivec3(0, 1, 1),
    ivec3(0, 0, 0), ivec3(0, 0, 1), ivec3(0, 1, 1), ivec3(0, 1, 0),
    ivec3(1, 0, 0), ivec3(1, 1, 0), ivec3(1, 1, 1), ivec3(1, 0, 1));

const vec3 NORMALS[6] = vec3[](
    vec3(0.0, -1.0, 0.0), vec3(0.0, 1.0, 0.0), vec3(0.0, 0.0, -1.0),
    vec3(0.0, 0.0, 1.0), vec3(-1.0, 0.0, 0.0), vec3(1.0, 0.0, 0.0));

const int TRIANGLES[6] = int[](0, 1, 2, 0, 2, 3);

float box_shade(int face) {
    return face == 0 ? BoxShadeDown
        : face == 1 ? BoxShadeUp
        : face == 2 ? BoxShadeNorth
        : face == 3 ? BoxShadeSouth
        : face == 4 ? BoxShadeWest
        : BoxShadeEast;
}

vec4 box_colour(uint argb) {
    return vec4(float((argb >> 16) & 255u), float((argb >> 8) & 255u), float(argb & 255u), float(argb >> 24))
        / 255.0;
}

void main() {
    int box = gl_VertexIndex / BOX_VERTICES;
    int vertex = gl_VertexIndex % BOX_VERTICES;
    int face = vertex / FACE_VERTICES;
    ivec3 corner = CORNERS[face * FACE_CORNERS + TRIANGLES[vertex % FACE_VERTICES]];

    int first = box * BOX_TEXELS;
    uvec4 low = texelFetch(BoxRecords, first);
    uvec4 high = texelFetch(BoxRecords, first + 1);
    vec3 lowFraction = uintBitsToFloat(texelFetch(BoxRecords, first + 2).xyz);
    vec3 highFraction = uintBitsToFloat(texelFetch(BoxRecords, first + 3).xyz);

    ivec3 lowBlock = ivec3(low.xyz);
    ivec3 block = lowBlock + corner * (ivec3(high.xyz) - lowBlock);
    vec3 position = vec3(block - BoxCameraBlockPos) + mix(lowFraction, highFraction, vec3(corner))
        + BoxCameraOffset;

    eyePoint = position;
    gl_Position = dot(NORMALS[face], position) < 0.0 ? BoxProjView * vec4(position, 1.0) : CULLED_POSITION;

    vec4 colour = box_colour(low.w);
    if ((high.w & uint(BOX_EMISSIVE)) == 0u) {
        colour.rgb *= far_lightmap(Lightmap, ivec2(0, FULL_SKY_LIGHT)).rgb * box_shade(face);
    }
    vertexColor = colour;
}
