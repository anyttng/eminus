#version 330
#extension GL_ARB_separate_shader_objects : require

#include <eminus:far_beams.glsl>

uniform usamplerBuffer EminusBeamRecords;

#include <eminus:far_beam_prism.glsl>

layout(location = 0) out vec4 vertexColor;

const vec4 CULLED_POSITION = vec4(2.0, 2.0, 2.0, 1.0);

void main() {
    int prismVertices = BeamSections * BEAM_PRISM_VERTICES;
    bool glow = gl_VertexIndex >= prismVertices;
    int local = gl_VertexIndex % prismVertices;
    EminusBeamVertex beam = eminus_beam_vertex(local / BEAM_PRISM_VERTICES, local % BEAM_PRISM_VERTICES, glow,
            BeamCameraBlockPos, BeamCameraOffset);

    gl_Position = beam.culled ? CULLED_POSITION : BeamProjView * vec4(beam.position, 1.0);
    vertexColor = beam.color;
}
