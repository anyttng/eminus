#ifndef EMINUS_FAR_VERTEX_GLSL
#define EMINUS_FAR_VERTEX_GLSL

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
    bool culled;
};

const int FAR_CORNERS_PER_QUAD = 4;
const int FAR_WORD_BITS = 32;
const float FAR_HALF_VOXEL = 0.5;

uint far_field(uint value, int shift, int bits) {
    return (value >> uint(shift)) & ((1u << uint(bits)) - 1u);
}

uint far_field(uvec2 value, int shift, int bits) {
    if (shift >= FAR_WORD_BITS) {
        return far_field(value.y, shift - FAR_WORD_BITS, bits);
    }

    uint low = value.x >> uint(shift);
    if (shift + bits > FAR_WORD_BITS) {
        low |= value.y << uint(FAR_WORD_BITS - shift);
    }
    return low & ((1u << uint(bits)) - 1u);
}

float far_low(float model, float lowGap, float blocks) {
    return (lowGap + model) / blocks;
}

float far_high(float model, float highGap, float blocks) {
    return (blocks - 1.0 - highGap + model) / blocks;
}

float far_offset_axis(uint placement, int shift) {
    int steps = int(far_field(placement, shift, OFFSET_AXIS_BITS));
    int signBit = 1 << (OFFSET_AXIS_BITS - 1);
    return float(steps >= signBit ? steps - 2 * signBit : steps) / OFFSET_STEPS_PER_BLOCK;
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

    return vec4(float(far_field(corners, CORNER_NORTH_WEST_SHIFT, CORNER_BITS)),
                float(far_field(corners, CORNER_NORTH_EAST_SHIFT, CORNER_BITS)),
                float(far_field(corners, CORNER_SOUTH_WEST_SHIFT, CORNER_BITS)),
                float(far_field(corners, CORNER_SOUTH_EAST_SHIFT, CORNER_BITS))) / steps;
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

int far_normal_axis(int face) {
    return face < 2 ? 1 : (face < 4 ? 2 : 0);
}

vec3 far_face_normal(int face) {
    return far_axis_set(vec3(0.0), far_normal_axis(face), (face & 1) == 1 ? 1.0 : -1.0);
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
    int level = int(far_field(mesh.xy, CELL_LEVEL_SHIFT, CELL_LEVEL_BITS));
    int cellX = int(far_field(mesh.xy, CELL_X_SHIFT, CELL_HORIZONTAL_BITS)) + MIN_HORIZONTAL;
    int cellZ = int(far_field(mesh.xy, CELL_Z_SHIFT, CELL_HORIZONTAL_BITS)) + MIN_HORIZONTAL;
    int cellY = int(far_field(mesh.xy, CELL_Y_SHIFT, CELL_VERTICAL_BITS)) + MIN_VERTICAL;

    uvec2 quad = texelFetch(Quads, quadIndex).xy;
    int face = int(far_field(quad, QUAD_FACE_SHIFT, QUAD_FACE_BITS));
    ivec3 voxel = ivec3(far_field(quad, QUAD_X_SHIFT, QUAD_COORDINATE_BITS),
                        far_field(quad, QUAD_Y_SHIFT, QUAD_COORDINATE_BITS),
                        far_field(quad, QUAD_Z_SHIFT, QUAD_COORDINATE_BITS));
    int width = int(far_field(quad, QUAD_WIDTH_SHIFT, QUAD_SIDE_BITS)) + 1;
    int height = int(far_field(quad, QUAD_HEIGHT_SHIFT, QUAD_SIDE_BITS)) + 1;
    uint light = far_field(quad, QUAD_LIGHT_SHIFT, QUAD_LIGHT_BITS);
    int modelId = int(far_field(quad, QUAD_MODEL_SHIFT, QUAD_MODEL_BITS));
    int colourIndex = int(far_field(quad, QUAD_COLOUR_SHIFT, QUAD_COLOUR_BITS));

    vec4 first = texelFetch(ModelRecords, modelId * MODEL_TEXELS);
    vec4 second = texelFetch(ModelRecords, modelId * MODEL_TEXELS + 1);
    vec4 third = texelFetch(ModelRecords, modelId * MODEL_TEXELS + 2);
    vec4 fourth = texelFetch(ModelRecords, modelId * MODEL_TEXELS + 3);
    vec4 fifth = texelFetch(ModelRecords, modelId * MODEL_TEXELS + 4);
    vec4 sixth = texelFetch(ModelRecords, modelId * MODEL_TEXELS + 5);
    vec4 seventh = texelFetch(ModelRecords, modelId * MODEL_TEXELS + 6);
    vec3 boundsMin = vec3(second.z, second.w, third.x);
    vec3 boundsMax = vec3(third.y, third.z, third.w);

    int flags = floatBitsToInt(fourth.x);
    bool fluid = (flags & FLUID_FLAG) != 0;
    bool inward = (flags & INWARD_FLAG) != 0;
    uint placement = 0u;
    vertex.tint = vec3(1.0);
    if (colourIndex != 0) {
        uvec2 entry = texelFetch(Quads, int(mesh.z + mesh.w) + colourIndex).rg;
        vertex.tint = vec3(far_field(entry.r, TINT_RED_SHIFT, TINT_CHANNEL_BITS),
                           far_field(entry.r, TINT_GREEN_SHIFT, TINT_CHANNEL_BITS),
                           far_field(entry.r, TINT_BLUE_SHIFT, TINT_CHANNEL_BITS))
                / float((1 << TINT_CHANNEL_BITS) - 1);
        placement = entry.g;
    }

    uint corners = 0u;
    vec3 offset = vec3(0.0);
    float lowGap = 0.0;
    float highGap = 0.0;
    if (level != 0) {
        lowGap = float(far_field(placement, LOW_GAP_SHIFT, NIBBLE_BITS));
        highGap = float(far_field(placement, HIGH_GAP_SHIFT, NIBBLE_BITS));
    } else if (fluid) {
        corners = placement;
    } else {
        offset = vec3(far_offset_axis(placement, OFFSET_X_SHIFT), far_offset_axis(placement, OFFSET_Y_SHIFT),
                      far_offset_axis(placement, OFFSET_Z_SHIFT));
    }

    float blocks = float(1 << level);
    float drawnBottom = far_low(boundsMin.y, lowGap, blocks);
    float drawnTop = far_high(boundsMax.y, highGap, blocks);
    vec3 local = vec3(voxel) + offset;
    vec2 extent;

    if (face >= FIRST_BLADE_FACE) {
        float slide = mix(boundsMin.x, boundsMax.x, unit.x);
        float across = face == FIRST_BLADE_FACE ? slide : boundsMin.x + boundsMax.x - slide;
        float up = unit.y * float(height - 1) + mix(drawnBottom, drawnTop, unit.y);

        local.x += across;
        local.y += up;
        local.z += mix(boundsMin.z, boundsMax.z, unit.x);
        extent = vec2(face == FIRST_BLADE_FACE ? unit.x : 1.0 - unit.x, unit.y * float(height));
    } else {
        int normalAxis = far_normal_axis(face);
        int widthAxis = face < 4 ? 0 : 2;
        int heightAxis = face < 2 ? 2 : 1;
        bool vertical = heightAxis == 1;
        extent = vec2(unit.x * float(width - 1)
                          + mix(far_axis(boundsMin, widthAxis), far_axis(boundsMax, widthAxis), unit.x),
                      unit.y * float(height - 1)
                          + mix(far_axis(boundsMin, heightAxis), far_axis(boundsMax, heightAxis), unit.y));
        float inset = far_inset(first, second, face) + dot(far_slope(fifth, sixth, seventh, face), extent);
        float placedHeight = vertical ? unit.y * float(height - 1) + mix(drawnBottom, drawnTop, unit.y) : extent.y;
        if (fluid && face != 0) {
            float surface = far_fluid_corner(far_fluid_corners(corners, boundsMax.y), inward ? face ^ 1 : face, unit);
            if (face == 1) {
                inset = 1.0 - surface;
            } else {
                extent.y = unit.y * float(height - 1) + mix(boundsMin.y, surface, unit.y);
                placedHeight = unit.y * float(height - 1) + mix(drawnBottom, far_high(surface, highGap, blocks), unit.y);
            }
        }

        float depth = (face & 1) == 1 ? 1.0 - inset : inset;
        if (face == 0) {
            depth = far_low(inset, lowGap, blocks);
        } else if (face == 1) {
            depth = far_high(1.0 - inset, highGap, blocks);
        } else if (inward) {
            depth = 1.0 - depth;
        }

        local = far_axis_add(local, normalAxis, depth);
        local = far_axis_add(local, widthAxis, extent.x);
        local = far_axis_add(local, heightAxis, placedHeight);
    }

    int cellBlocks = VOXELS_PER_SIDE << level;
    ivec3 origin = ivec3(cellX * cellBlocks, cellY * cellBlocks + MinBlockY, cellZ * cellBlocks);
    vertex.position = vec3(origin - CameraBlockPos) + CameraOffset + local * float(1 << level);
    vertex.culled = (flags & ONE_SIDED_FLAG) != 0 && face < FIRST_BLADE_FACE
            && dot(far_face_normal(face), vertex.position) >= 0.0;

    vec3 faceLocal = local;
    if (face < FIRST_BLADE_FACE) {
        int faceAxis = far_normal_axis(face);
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
    vertex.blockLight = int(far_field(light, BLOCK_LIGHT_SHIFT, NIBBLE_BITS));
    vertex.skyLight = int(far_field(light, SKY_LIGHT_SHIFT, NIBBLE_BITS));

    return vertex;
}

#endif
