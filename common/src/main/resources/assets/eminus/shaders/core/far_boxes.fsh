#version 330

#moj_import <eminus:far_boxes.glsl>

in vec4 vertexColor;
in vec3 eyePoint;

out vec4 fragColor;

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
