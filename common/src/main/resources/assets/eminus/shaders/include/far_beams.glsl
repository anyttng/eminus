#ifndef EMINUS_FAR_BEAMS_GLSL
#define EMINUS_FAR_BEAMS_GLSL

layout(std140) uniform Beams {
    mat4 BeamProjView;
    ivec3 BeamCameraBlockPos;
    vec3 BeamCameraOffset;
    int BeamSections;
};

#endif
