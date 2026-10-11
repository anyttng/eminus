uniform sampler2D lightmap;

vec4 shadeTerrain(vec4 albedo, vec2 lmcoord) {
    return albedo * texture(lightmap, lmcoord);
}
