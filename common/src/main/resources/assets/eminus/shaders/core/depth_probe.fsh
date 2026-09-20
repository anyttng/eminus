#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform Probe {
    float ProbeDepth;
};

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vec4(1, 1, 1, 1);
    gl_FragDepth = ProbeDepth;
}
