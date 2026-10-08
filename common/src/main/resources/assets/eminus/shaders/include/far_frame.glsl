#ifndef EMINUS_FAR_FRAME_GLSL
#define EMINUS_FAR_FRAME_GLSL

layout(std140) uniform FarFrame {
    mat4 FarProjView;
    int MinBlockY;
    int AtlasCells;
    int NearSide;
    int NearHeight;
    ivec3 NearOrigin;
    float ShadeDown;
    float ShadeUp;
    float ShadeNorth;
    float ShadeSouth;
    float ShadeWest;
    float ShadeEast;
    ivec3 CameraBlockPos;
    vec3 CameraOffset;
};

#endif
