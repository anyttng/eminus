#version 330

layout(std140) uniform Probe {
    float ProbeDepth;
};

out vec4 fragColor;

void main() {
    fragColor = vec4(1, 1, 1, 1);
    gl_FragDepth = ProbeDepth;
}
