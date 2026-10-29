#version 330

#include <eminus:far_frame.glsl>

uniform usamplerBuffer EminusBeamRecords;

#include <eminus:far_beam_prism.glsl>
#ifdef PACK_VERTEX
#include <eminus:far_pack_hook.glsl>
#endif

out vec3 eminus_viewPos;
out vec3 eminus_playerPos;
out float eminus_height;
flat out vec4 eminus_color;
flat out vec3 eminus_normal;
flat out int eminus_face;
flat out vec2 eminus_lmcoord;
out vec3 iris_vBlockPos;
flat out uvec2 iris_TexId;

const vec4 EMINUS_CULLED_POSITION = vec4(2.0, 2.0, 2.0, 1.0);
const float EMINUS_LIGHT_LEVELS = 16.0;
const float EMINUS_LIGHT_CENTER = 0.5;
const vec2 EMINUS_FULL_LIGHT = vec2((EMINUS_LIGHT_LEVELS - 1.0 + EMINUS_LIGHT_CENTER) / EMINUS_LIGHT_LEVELS);
const int EMINUS_NO_BLOCK_ID = -1;
const int EMINUS_BEAM_LEVEL = 0;
#ifdef BEAM_GLOW
const bool EMINUS_GLOW = true;
#else
const bool EMINUS_GLOW = false;
#endif

void main() {
    EminusBeamVertex beam = eminus_beam_vertex(gl_VertexIndex / BEAM_PRISM_VERTICES,
            gl_VertexIndex % BEAM_PRISM_VERTICES, EMINUS_GLOW, EminusCameraBlockPos, EminusCameraOffset);
    vec3 position = beam.position;
#ifdef PACK_VERTEX
    EminusVertex hooked = EminusVertex(position, EMINUS_NO_BLOCK_ID, beam.face, false, false, EMINUS_FULL_LIGHT,
            EMINUS_BEAM_LEVEL, false);
    vec4 clipPosition = eminus_vertexPosition(hooked);
    position = hooked.playerPos;
#else
    vec4 clipPosition = EminusProjView * vec4(position, 1.0);
#endif
    gl_Position = beam.culled ? EMINUS_CULLED_POSITION : clipPosition;

    eminus_viewPos = (EminusView * vec4(position, 1.0)).xyz;
    eminus_playerPos = position;
    eminus_height = beam.height;
    eminus_color = beam.color;
    eminus_normal = mat3(EminusView) * beam.normal;
    eminus_face = beam.face;
    eminus_lmcoord = EMINUS_FULL_LIGHT;
    iris_vBlockPos = beam.position;
    iris_TexId = uvec2(0u, uint(beam.face));
}
