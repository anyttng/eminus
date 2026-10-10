#ifndef EMINUS_FAR_BOXES_GLSL
#define EMINUS_FAR_BOXES_GLSL

layout(std140) uniform Boxes {
    mat4 BoxProjView;
    mat4 BoxView;
    vec4 BoxFogColour;
    ivec3 BoxCameraBlockPos;
    vec3 BoxCameraOffset;
    float BoxShadeDown;
    float BoxShadeUp;
    float BoxShadeNorth;
    float BoxShadeSouth;
    float BoxShadeWest;
    float BoxShadeEast;
    float BoxNearPlane;
    float BoxFogStart;
    float BoxFogEnd;
};

#endif
