#ifndef EMINUS_FAR_NEAR_GLSL
#define EMINUS_FAR_NEAR_GLSL

bool far_in_near_section(vec3 nearPoint) {
    ivec3 section = CameraBlockPos + ivec3(floor(nearPoint)) - NearOrigin;
    if (any(lessThan(section, ivec3(0)))) {
        return false;
    }

    section /= NEAR_SECTION_BLOCKS;
    if (section.x >= NearSide || section.y >= NearHeight || section.z >= NearSide) {
        return false;
    }

    int index = (section.z * NearSide + section.x) * NearHeight + section.y;
    uint bits = texelFetch(NearSections, index >> NEAR_TEXEL_SHIFT).r;
    return ((bits >> uint(index & (NEAR_TEXEL_BITS - 1))) & 1u) != 0u;
}

#endif
