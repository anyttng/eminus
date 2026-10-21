#ifndef EMINUS_FAR_FRAME_GLSL
#define EMINUS_FAR_FRAME_GLSL

layout(std140) uniform EminusFrame {
    mat4 EminusProjView;
    mat4 EminusView;
    int EminusMinBlockY;
    int EminusAtlasCells;
    int EminusAtlasCellSide;
    int EminusAtlasMeanLevel;
    int EminusNearSide;
    int EminusNearHeight;
    ivec3 EminusNearOrigin;
    float EminusShadeDown;
    float EminusShadeUp;
    float EminusShadeNorth;
    float EminusShadeSouth;
    float EminusShadeWest;
    float EminusShadeEast;
    ivec3 EminusCameraBlockPos;
    vec3 EminusCameraOffset;
    int EminusSmoothLighting;
    int EminusRgss;
    int EminusAnisotropy;
};

#endif
