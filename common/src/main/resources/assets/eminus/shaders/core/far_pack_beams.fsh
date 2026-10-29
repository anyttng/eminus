#version 330

in vec3 eminus_viewPos;
in vec3 eminus_playerPos;
in float eminus_height;
flat in vec4 eminus_color;
flat in vec3 eminus_normal;
flat in int eminus_face;
flat in vec2 eminus_lmcoord;

struct EminusBeam {
    vec4 color;
    bool glow;
    vec3 normal;
    int face;
    vec2 lmcoord;
    vec3 viewPos;
    vec3 playerPos;
    float height;
};

EminusBeam eminus_beam() {
    EminusBeam beam;
    beam.color = eminus_color;
#ifdef BEAM_GLOW
    beam.glow = true;
#else
    beam.glow = false;
#endif
    beam.normal = eminus_normal;
    beam.face = eminus_face;
    beam.lmcoord = eminus_lmcoord;
    beam.viewPos = eminus_viewPos;
    beam.playerPos = eminus_playerPos;
    beam.height = eminus_height;
    return beam;
}
