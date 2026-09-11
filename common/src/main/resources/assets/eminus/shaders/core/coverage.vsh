#version 330

#moj_import <minecraft:globals.glsl>

layout(std140) uniform FarFrame {
    mat4 FarProjView;
    int MinBlockY;
    int AtlasCells;
};

uniform isamplerBuffer Sections;

const int VERTICES_PER_SECTION = 36;
const int CORNER_OF[36] = int[36](
    0, 2, 6, 0, 6, 4,
    1, 3, 7, 1, 7, 5,
    0, 1, 5, 0, 5, 4,
    2, 3, 7, 2, 7, 6,
    0, 1, 3, 0, 3, 2,
    4, 5, 7, 4, 7, 6);

void main() {
    int section = gl_VertexID / VERTICES_PER_SECTION;
    int corner = CORNER_OF[gl_VertexID % VERTICES_PER_SECTION];
    vec3 unit = vec3(corner & 1, (corner >> 1) & 1, (corner >> 2) & 1);

    ivec3 origin = texelFetch(Sections, section).xyz;
    vec3 local = unit * (float(SECTION_SIZE) + 2.0 * COVERAGE_MARGIN) - COVERAGE_MARGIN;
    vec3 position = vec3(origin - CameraBlockPos) + CameraOffset + local;
    gl_Position = FarProjView * vec4(position, 1.0);
}
