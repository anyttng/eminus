#version 330
#extension GL_ARB_separate_shader_objects : require

void main() {
    vec2 corner = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    gl_Position = vec4(corner * vec2(2, 2) + vec2(-1, -1), 0, 1);
}
