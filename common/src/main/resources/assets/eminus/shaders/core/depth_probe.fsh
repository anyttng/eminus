#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vec4(1, 1, 1, 1);
    gl_FragDepth = 0.5;
}
