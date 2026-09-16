vec4 far_surface(ivec2 atlasCell, vec2 faceUV, vec3 tint) {
    vec2 cell = vec2(1.0) / float(AtlasCells);
    float margin = 0.5 / float(FACE_SIDE);
    vec2 within = clamp(fract(faceUV), margin, 1.0 - margin);
    vec2 uv = (vec2(atlasCell) + within) * cell;

    vec2 gradX = dFdx(faceUV) * cell;
    vec2 gradY = dFdy(faceUV) * cell;

    vec4 colour = textureGrad(Atlas, uv, gradX, gradY);
    colour.rgb *= mix(vec3(1.0), tint, textureGrad(TintMask, uv, gradX, gradY).r);
    return colour;
}
