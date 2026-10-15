#version 330

#moj_import <eminus:far_lightmap.glsl>
#moj_import <eminus:far_frame.glsl>

uniform usamplerBuffer EminusQuads;
uniform usamplerBuffer EminusMeshRecords;
uniform samplerBuffer EminusModelRecords;
uniform sampler2D Lightmap;

#moj_import <eminus:far_vertex.glsl>

out vec2 faceUV;
out vec4 vertexColor;
flat out vec3 tintColour;
flat out ivec2 atlasCell;
flat out ivec4 variantInfo;
flat out ivec3 cellOrigin;
out vec3 voxelPoint;

#ifdef NEAR_SECTIONS
out vec3 nearPoint;
#endif

const int LIGHT_STEP = 16;
const vec4 CULLED_POSITION = vec4(2.0, 2.0, 2.0, 1.0);

void main() {
    EminusQuadVertex vertex = eminus_quad_vertex(gl_VertexID);
    gl_Position = vertex.culled ? CULLED_POSITION : EminusProjView * vec4(vertex.position, 1.0);

#ifdef NEAR_SECTIONS
    nearPoint = vertex.facePoint;
#endif

    faceUV = vertex.faceUV;
    atlasCell = vertex.atlasCell;
    variantInfo = ivec4(vertex.variantStart, vertex.variantCount, vertex.faceSlot, vertex.level);
    cellOrigin = vertex.cellOrigin;
    voxelPoint = vertex.voxelPoint;
    tintColour = vertex.tint;

    vec4 colour = far_lightmap(Lightmap, ivec2(vertex.blockLight * LIGHT_STEP, vertex.skyLight * LIGHT_STEP));
    float faceShade = vertex.face == 0 ? EminusShadeDown
        : vertex.face == 1 ? EminusShadeUp
        : vertex.face == 2 ? EminusShadeNorth
        : vertex.face == 3 ? EminusShadeSouth
        : vertex.face == 4 ? EminusShadeWest
        : vertex.face == 5 ? EminusShadeEast
        : SHADE_BLADE;
    colour.rgb *= faceShade;
    vertexColor = colour;
}
