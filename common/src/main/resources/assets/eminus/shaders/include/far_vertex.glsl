struct FarVertex {
    vec3 position;
    vec3 facePoint;
    int face;
    int blockLight;
    int skyLight;
    ivec2 atlasCell;
    vec2 faceUV;
    vec3 tint;
};

const int FAR_CORNERS_PER_QUAD = 6;
const int FAR_CORNER_OF[6] = int[6](0, 1, 2, 0, 2, 3);
const int FAR_MODEL_TEXELS = 4;
const int FAR_NO_TINT = -1;
const int FAR_NIBBLE = 15;
const float FAR_HALF_VOXEL = 0.5;

FarVertex far_vertex(int vertexId) {
    FarVertex vertex;

    int quadIndex = vertexId / FAR_CORNERS_PER_QUAD;
    int corner = FAR_CORNER_OF[vertexId % FAR_CORNERS_PER_QUAD];
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
    int colourIndex = int((quad.y >> 20u) & 0xFFFu);

    vec4 first = texelFetch(ModelRecords, modelId * FAR_MODEL_TEXELS);
    vec4 second = texelFetch(ModelRecords, modelId * FAR_MODEL_TEXELS + 1);
    vec4 third = texelFetch(ModelRecords, modelId * FAR_MODEL_TEXELS + 2);
    vec4 fourth = texelFetch(ModelRecords, modelId * FAR_MODEL_TEXELS + 3);
    float insets[6] = float[6](first.x, first.y, first.z, first.w, second.x, second.y);
    vec3 boundsMin = vec3(second.z, second.w, third.x);
    vec3 boundsMax = vec3(third.y, third.z, third.w);
    int tintRow = floatBitsToInt(fourth.y);

    vec3 local = vec3(voxel);
    vec2 extent;

    if (face >= FIRST_BLADE_FACE) {
        float slide = mix(boundsMin.x, boundsMax.x, unit.x);
        float across = face == FIRST_BLADE_FACE ? slide : boundsMin.x + boundsMax.x - slide;
        float up = unit.y * float(height - 1) + mix(boundsMin.y, boundsMax.y, unit.y);

        local.x += across;
        local.y += up;
        local.z += mix(boundsMin.z, boundsMax.z, unit.x);
        extent = vec2(across, up);
    } else {
        int normalAxis = face < 2 ? 1 : (face < 4 ? 2 : 0);
        int widthAxis = face < 4 ? 0 : 2;
        int heightAxis = face < 2 ? 2 : 1;

        local[normalAxis] += (face & 1) == 1 ? 1.0 - insets[face] : insets[face];
        extent = vec2(unit.x * float(width - 1) + mix(boundsMin[widthAxis], boundsMax[widthAxis], unit.x),
                      unit.y * float(height - 1) + mix(boundsMin[heightAxis], boundsMax[heightAxis], unit.y));
        local[widthAxis] += extent.x;
        local[heightAxis] += extent.y;
    }

    int cellBlocks = VOXELS_PER_SIDE << level;
    ivec3 origin = ivec3(cellX * cellBlocks, cellY * cellBlocks + MinBlockY, cellZ * cellBlocks);
    vertex.position = vec3(origin - CameraBlockPos) + CameraOffset + local * float(1 << level);

    vec3 faceLocal = local;
    if (face < FIRST_BLADE_FACE) {
        int faceAxis = face < 2 ? 1 : (face < 4 ? 2 : 0);
        faceLocal[faceAxis] = float(voxel[faceAxis]) + FAR_HALF_VOXEL;
    }
    vertex.facePoint = vec3(origin - CameraBlockPos) + faceLocal * float(1 << level);

    vertex.faceUV = vec2(face == 2 || face == 5 ? float(width) - extent.x : extent.x,
                         face == 1 ? float(height) - extent.y : extent.y);

    int slot = modelId * MODEL_FACES + (face >= FIRST_BLADE_FACE ? face - FIRST_BLADE_FACE : face);
    vertex.atlasCell = ivec2(slot % AtlasCells, slot / AtlasCells);

    vertex.face = face;
    vertex.blockLight = light & FAR_NIBBLE;
    vertex.skyLight = (light >> 4) & FAR_NIBBLE;

    vertex.tint = vec3(1.0);
    if (tintRow != FAR_NO_TINT) {
        uint tint = texelFetch(Quads, int(mesh.z + mesh.w) + colourIndex).r;
        vertex.tint = vec3((tint >> 16u) & 255u, (tint >> 8u) & 255u, tint & 255u) / 255.0;
    }

    return vertex;
}
