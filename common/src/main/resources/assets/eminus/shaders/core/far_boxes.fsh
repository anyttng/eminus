#version 330
#extension GL_ARB_separate_shader_objects : require

#include <eminus:far_boxes.glsl>

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec3 eyePoint;

layout(location = 0) out vec4 fragColor;

float box_fog(float distance) {
    if (distance <= BoxFogStart) {
        return 0.0;
    }
    if (distance >= BoxFogEnd) {
        return 1.0;
    }

    return (distance - BoxFogStart) / (BoxFogEnd - BoxFogStart);
}

void main() {
    vec4 colour = vertexColor;
    if (colour.a == 0.0) {
        discard;
    }

#ifdef NEAR_RANGE
    if (-(BoxView * vec4(eyePoint, 1.0)).z >= BoxNearPlane) {
        discard;
    }

    colour.rgb = mix(colour.rgb, BoxFogColour.rgb, box_fog(length(eyePoint)) * BoxFogColour.a);
#endif

    fragColor = colour;
}
