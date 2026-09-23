struct FarVertex {
    vec3 position;
    vec3 facePoint;
    int face;
    int blockLight;
    int skyLight;
    ivec2 atlasCell;
    vec2 faceUV;
    vec3 tint;
    ivec3 cellOrigin;
    int level;
    vec3 voxelPoint;
    int faceSlot;
    int variantStart;
    int variantCount;
};

const int FAR_CORNERS_PER_QUAD = 4;
const int FAR_MODEL_TEXELS = 7;
const uint FAR_OFFSET_MASK = 1023u;
const int FAR_OFFSET_SIGN = 512;
const float FAR_OFFSET_STEPS = 256.0;
const uint FAR_OFFSET_X_SHIFT = 0u;
const uint FAR_OFFSET_Y_SHIFT = 10u;
const uint FAR_OFFSET_Z_SHIFT = 20u;
const int FAR_NIBBLE = 15;
const float FAR_HALF_VOXEL = 0.5;

float far_offset_axis(uint bits, uint shift) {
    int steps = int((bits >> shift) & FAR_OFFSET_MASK);
    return float(steps >= FAR_OFFSET_SIGN ? steps - 2 * FAR_OFFSET_SIGN : steps) / FAR_OFFSET_STEPS;
}

// The corner arithmetic below reads and writes every axis by comparison, never by a varying index: a translated shader
// must not depend on how the translator lowers a dynamic index into a vector or a local array.
float far_axis(vec3 value, int axis) {
    return axis == 0 ? value.x : (axis == 1 ? value.y : value.z);
}

int far_axis_int(ivec3 value, int axis) {
    return axis == 0 ? value.x : (axis == 1 ? value.y : value.z);
}

vec3 far_axis_add(vec3 value, int axis, float amount) {
    return value + vec3(axis == 0 ? amount : 0.0, axis == 1 ? amount : 0.0, axis == 2 ? amount : 0.0);
}

vec3 far_axis_set(vec3 value, int axis, float amount) {
    return vec3(axis == 0 ? amount : value.x, axis == 1 ? amount : value.y, axis == 2 ? amount : value.z);
}

float far_inset(vec4 first, vec4 second, int face) {
    if (face == 0) {
        return first.x;
    }
    if (face == 1) {
        return first.y;
    }
    if (face == 2) {
        return first.z;
    }
    if (face == 3) {
        return first.w;
    }
    if (face == 4) {
        return second.x;
    }

    return second.y;
}

vec4 far_fluid_corners(uint corners, float flatHeight) {
    float steps = float(CORNER_STEPS);
    if (corners == 0u) {
        return vec4(round(flatHeight * steps) / steps);
    }

    return vec4(float(corners & 255u), float((corners >> 8u) & 255u), float((corners >> 16u) & 255u),
                float(corners >> 24u)) / steps;
}

float far_fluid_corner(vec4 surface, int face, vec2 unit) {
    bool widthEnd = unit.x > FAR_HALF_VOXEL;
    if (face == 1) {
        return unit.y > FAR_HALF_VOXEL ? (widthEnd ? surface.w : surface.z) : (widthEnd ? surface.y : surface.x);
    }
    if (face == 2) {
        return widthEnd ? surface.y : surface.x;
    }
    if (face == 3) {
        return widthEnd ? surface.w : surface.z;
    }
    if (face == 4) {
        return widthEnd ? surface.z : surface.x;
    }

    return widthEnd ? surface.w : surface.y;
}

vec2 far_slope(vec4 fifth, vec4 sixth, vec4 seventh, int face) {
    if (face == 0) {
        return fifth.xy;
    }
    if (face == 1) {
        return fifth.zw;
    }
    if (face == 2) {
        return sixth.xy;
    }
    if (face == 3) {
        return sixth.zw;
    }
    if (face == 4) {
        return seventh.xy;
    }

    return seventh.zw;
}

FarVertex far_vertex(int vertexId) {
    FarVertex vertex;

    int quadIndex = vertexId / FAR_CORNERS_PER_QUAD;
    int corner = vertexId % FAR_CORNERS_PER_QUAD;
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
    vec4 fifth = texelFetch(ModelRecords, modelId * FAR_MODEL_TEXELS + 4);
    vec4 sixth = texelFetch(ModelRecords, modelId * FAR_MODEL_TEXELS + 5);
    vec4 seventh = texelFetch(ModelRecords, modelId * FAR_MODEL_TEXELS + 6);
    vec3 boundsMin = vec3(second.z, second.w, third.x);
    vec3 boundsMax = vec3(third.y, third.z, third.w);

    bool fluid = (floatBitsToInt(fourth.x) & FLUID_FLAG) != 0;
    uint corners = 0u;
    vertex.tint = vec3(1.0);
    vec3 offset = vec3(0.0);
    if (colourIndex != 0) {
        uvec2 entry = texelFetch(Quads, int(mesh.z + mesh.w) + colourIndex).rg;
        vertex.tint = vec3((entry.r >> 16u) & 255u, (entry.r >> 8u) & 255u, entry.r & 255u) / 255.0;
        if (fluid) {
            corners = entry.g;
        } else {
            offset = vec3(far_offset_axis(entry.g, FAR_OFFSET_X_SHIFT), far_offset_axis(entry.g, FAR_OFFSET_Y_SHIFT),
                          far_offset_axis(entry.g, FAR_OFFSET_Z_SHIFT));
        }
    }

    vec3 local = vec3(voxel) + offset;
    vec2 extent;

    if (face >= FIRST_BLADE_FACE) {
        float slide = mix(boundsMin.x, boundsMax.x, unit.x);
        float across = face == FIRST_BLADE_FACE ? slide : boundsMin.x + boundsMax.x - slide;
        float up = unit.y * float(height - 1) + mix(boundsMin.y, boundsMax.y, unit.y);

        local.x += across;
        local.y += up;
        local.z += mix(boundsMin.z, boundsMax.z, unit.x);
        extent = vec2(face == FIRST_BLADE_FACE ? unit.x : 1.0 - unit.x, unit.y * float(height));
    } else {
        int normalAxis = face < 2 ? 1 : (face < 4 ? 2 : 0);
        int widthAxis = face < 4 ? 0 : 2;
        int heightAxis = face < 2 ? 2 : 1;
        extent = vec2(unit.x * float(width - 1)
                          + mix(far_axis(boundsMin, widthAxis), far_axis(boundsMax, widthAxis), unit.x),
                      unit.y * float(height - 1)
                          + mix(far_axis(boundsMin, heightAxis), far_axis(boundsMax, heightAxis), unit.y));
        float inset = far_inset(first, second, face) + dot(far_slope(fifth, sixth, seventh, face), extent);
        if (fluid && face != 0) {
            float top = far_fluid_corner(far_fluid_corners(corners, boundsMax.y), face, unit);
            if (face == 1) {
                inset = 1.0 - top;
            } else {
                extent.y = unit.y * float(height - 1) + mix(boundsMin.y, top, unit.y);
            }
        }

        local = far_axis_add(local, normalAxis, (face & 1) == 1 ? 1.0 - inset : inset);
        local = far_axis_add(local, widthAxis, extent.x);
        local = far_axis_add(local, heightAxis, extent.y);
    }

    int cellBlocks = VOXELS_PER_SIDE << level;
    ivec3 origin = ivec3(cellX * cellBlocks, cellY * cellBlocks + MinBlockY, cellZ * cellBlocks);
    vertex.position = vec3(origin - CameraBlockPos) + CameraOffset + local * float(1 << level);

    vec3 faceLocal = local;
    if (face < FIRST_BLADE_FACE) {
        int faceAxis = face < 2 ? 1 : (face < 4 ? 2 : 0);
        faceLocal = far_axis_set(faceLocal, faceAxis,
                float(far_axis_int(voxel, faceAxis)) + FAR_HALF_VOXEL + far_axis(offset, faceAxis));
    }
    vertex.facePoint = vec3(origin - CameraBlockPos) + faceLocal * float(1 << level);
    vertex.voxelPoint = faceLocal - offset;
    vertex.cellOrigin = origin;
    vertex.level = level;

    if (face >= FIRST_BLADE_FACE) {
        vec2 span = vec2(boundsMax.x - boundsMin.x, boundsMax.z - boundsMin.z);
        vec3 painted = vec3(span.y, 0.0, face == FIRST_BLADE_FACE ? -span.x : span.x);
        vertex.faceUV = vec2(dot(vertex.position, painted) > 0.0 ? 1.0 - extent.x : extent.x, extent.y);
    } else {
        vertex.faceUV = vec2(face == 2 || face == 5 ? float(width) - extent.x : extent.x,
                             face == 1 ? float(height) - extent.y : extent.y);
    }

    vertex.faceSlot = face >= FIRST_BLADE_FACE ? face - FIRST_BLADE_FACE : face;
    int slot = modelId * MODEL_FACES + vertex.faceSlot;
    vertex.atlasCell = ivec2(slot % AtlasCells, slot / AtlasCells);
    vertex.variantStart = int(fourth.z);
    vertex.variantCount = int(fourth.w);

    vertex.face = face;
    vertex.blockLight = light & FAR_NIBBLE;
    vertex.skyLight = (light >> 4) & FAR_NIBBLE;

    return vertex;
}
