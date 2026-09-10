#version 330

#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

layout(std140) uniform FarFrame {
    mat4 FarProjView;
    int MinBlockY;
    int AtlasCells;
};

uniform usamplerBuffer Quads;
uniform usamplerBuffer MeshRecords;
uniform samplerBuffer ModelRecords;
uniform usamplerBuffer TintColours;
uniform sampler2D Lightmap;

out vec2 faceUV;
out vec4 vertexColor;
flat out ivec2 atlasCell;

const int CORNERS_PER_QUAD = 6;
const int CORNER_OF[6] = int[6](0, 1, 2, 0, 2, 3);
const float FACE_SHADE[6] = float[6](
    SHADE_DOWN, SHADE_UP, SHADE_NORTH_SOUTH, SHADE_NORTH_SOUTH, SHADE_WEST_EAST, SHADE_WEST_EAST);
const int MODEL_TEXELS = 4;
const int NO_TINT = -1;
const int LIGHT_STEP = 16;
const int NIBBLE = 15;

void main() {
    int quadIndex = gl_VertexID / CORNERS_PER_QUAD;
    int corner = CORNER_OF[gl_VertexID % CORNERS_PER_QUAD];
    vec2 unit = vec2(corner == 1 || corner == 2 ? 1.0 : 0.0, corner >= 2 ? 1.0 : 0.0);

    uvec4 mesh = texelFetch(MeshRecords, quadIndex / QUADS_PER_BLOCK);
    int level = int((mesh.y >> 28u) & 7u);
    int cellX = int((mesh.y >> 4u) & 0xFFFFFFu) + MIN_HORIZONTAL;
    int cellZ = int(((mesh.y & 0xFu) << 20u) | (mesh.x >> 12u)) + MIN_HORIZONTAL;
    int cellY = int(mesh.x & 0xFFFu) + MIN_VERTICAL;

    uvec2 quad = texelFetch(Quads, quadIndex).xy;
    int face = int(quad.x & 7u);
    ivec3 voxel = ivec3(int((quad.x >> 3u) & 31u), int((quad.x >> 8u) & 31u), int((quad.x >> 13u) & 31u));
    int width = int((quad.x >> 18u) & 15u) + 1;
    int height = int((quad.x >> 22u) & 15u) + 1;
    int light = int(((quad.x >> 26u) | ((quad.y & 3u) << 6u)) & 255u);
    int modelId = int((quad.y >> 2u) & 0x3FFFFu);
    int biomeId = int((quad.y >> 20u) & 0xFFFu);

    vec4 first = texelFetch(ModelRecords, modelId * MODEL_TEXELS);
    vec4 second = texelFetch(ModelRecords, modelId * MODEL_TEXELS + 1);
    vec4 third = texelFetch(ModelRecords, modelId * MODEL_TEXELS + 2);
    vec4 fourth = texelFetch(ModelRecords, modelId * MODEL_TEXELS + 3);
    float insets[6] = float[6](first.x, first.y, first.z, first.w, second.x, second.y);
    vec3 boundsMin = vec3(second.z, second.w, third.x);
    vec3 boundsMax = vec3(third.y, third.z, third.w);
    int tintRow = floatBitsToInt(fourth.y);

    int normalAxis = face < 2 ? 1 : (face < 4 ? 2 : 0);
    int widthAxis = face < 4 ? 0 : 2;
    int heightAxis = face < 2 ? 2 : 1;

    vec3 local = vec3(voxel);
    local[normalAxis] += (face & 1) == 1 ? 1.0 - insets[face] : insets[face];
    local[widthAxis] += unit.x * float(width - 1) + mix(boundsMin[widthAxis], boundsMax[widthAxis], unit.x);
    local[heightAxis] += unit.y * float(height - 1) + mix(boundsMin[heightAxis], boundsMax[heightAxis], unit.y);

    int cellBlocks = VOXELS_PER_SIDE << level;
    ivec3 origin = ivec3(cellX * cellBlocks, cellY * cellBlocks + MinBlockY, cellZ * cellBlocks);
    vec3 position = vec3(origin - CameraBlockPos) + CameraOffset + local * float(1 << level);
    gl_Position = FarProjView * vec4(position, 1.0);

    vec2 span = unit * vec2(float(width), float(height));
    faceUV = vec2(face == 2 || face == 5 ? float(width) - span.x : span.x,
                  face == 1 ? float(height) - span.y : span.y);

    int slot = modelId * MODEL_FACES + face;
    atlasCell = ivec2(slot % AtlasCells, slot / AtlasCells);

    vec4 colour = sample_lightmap(Lightmap, ivec2((light & NIBBLE) * LIGHT_STEP, ((light >> 4) & NIBBLE) * LIGHT_STEP));
    if (tintRow != NO_TINT) {
        uint tint = texelFetch(TintColours, tintRow * BIOME_STRIDE + biomeId).r;
        colour.rgb *= vec3((tint >> 16u) & 255u, (tint >> 8u) & 255u, tint & 255u) / 255.0;
    }

    colour.rgb *= FACE_SHADE[face];
    vertexColor = colour;
}
