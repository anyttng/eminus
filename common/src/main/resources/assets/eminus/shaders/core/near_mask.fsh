#version 330

uniform sampler2D GameDepth;

void main() {
    if (texelFetch(GameDepth, ivec2(gl_FragCoord.xy), 0).r <= GAME_DEPTH_CLEARED) {
        discard;
    }

    gl_FragDepth = MASKED;
}
