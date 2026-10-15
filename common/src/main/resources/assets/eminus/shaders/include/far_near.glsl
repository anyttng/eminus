#ifndef EMINUS_FAR_NEAR_GLSL
#define EMINUS_FAR_NEAR_GLSL

bool eminus_in_near_section(vec3 nearPoint) {
    ivec3 section = EminusCameraBlockPos + ivec3(floor(nearPoint)) - EminusNearOrigin;
    if (any(lessThan(section, ivec3(0)))) {
        return false;
    }

    section /= NEAR_SECTION_BLOCKS;
    if (section.x >= EminusNearSide || section.y >= EminusNearHeight || section.z >= EminusNearSide) {
        return false;
    }

    int index = (section.z * EminusNearSide + section.x) * EminusNearHeight + section.y;
    uint bits = texelFetch(EminusNearSections, index >> NEAR_TEXEL_SHIFT).r;
    return ((bits >> uint(index & (NEAR_TEXEL_BITS - 1))) & 1u) != 0u;
}

#endif
