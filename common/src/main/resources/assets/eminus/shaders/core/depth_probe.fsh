#version 330

out vec4 fragColor;

void main() {
    fragColor = vec4(1, 1, 1, 1);
    gl_FragDepth = 0.5;
}
