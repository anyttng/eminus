#version 330

#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

layout(std140) uniform FarFrame {
    mat4 FarProjView;
    int MinBlockY;
    int AtlasCells;
    int NearSide;
    int NearHeight;
    ivec3 NearOrigin;
    float ShadeDown;
    float ShadeUp;
    float ShadeNorth;
    float ShadeSouth;
    float ShadeWest;
    float ShadeEast;
};

uniform usamplerBuffer Quads;
uniform usamplerBuffer MeshRecords;
uniform samplerBuffer ModelRecords;
uniform sampler2D Lightmap;

#moj_import <eminus:far_vertex.glsl>

out vec2 faceUV;
out vec4 vertexColor;
flat out vec3 tintColour;
flat out ivec2 atlasCell;

#ifdef NEAR_SECTIONS
out vec3 nearPoint;
#endif

const int LIGHT_STEP = 16;

void main() {
    FarVertex vertex = far_vertex(gl_VertexID);
    gl_Position = FarProjView * vec4(vertex.position, 1.0);

#ifdef NEAR_SECTIONS
    nearPoint = vertex.facePoint;
#endif

    faceUV = vertex.faceUV;
    atlasCell = vertex.atlasCell;
    tintColour = vertex.tint;

    vec4 colour = sample_lightmap(Lightmap, ivec2(vertex.blockLight * LIGHT_STEP, vertex.skyLight * LIGHT_STEP));
    float faceShade[8] = float[8](ShadeDown, ShadeUp, ShadeNorth, ShadeSouth, ShadeWest, ShadeEast,
        SHADE_BLADE, SHADE_BLADE);
    colour.rgb *= faceShade[vertex.face];
    vertexColor = colour;
}
