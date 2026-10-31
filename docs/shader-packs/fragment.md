# The fragment function

Part of the [Eminus shader-pack contract](../shader-pack-contract.md).

```glsl
void eminus_emitFragment(EminusFragment fragment);
```

Eminus calls it once per LOD pixel that survives its own tests, after those tests have run. Pixels the near field draws are already discarded, and so are pixels that fail the alpha test ([Cutout](#cutout)). Your function never sees a pixel the near field owns.

| Field | Type | Meaning |
| --- | --- | --- |
| `color` | `vec4` | The block's texture at this pixel, filtered across the tiles of the LOD face and tinted (grass, foliage, water). Alpha is `1.0` in the opaque program, and the texture's own alpha where your pack declares `cutout = pack`. |
| `texcoord` | `vec2` | Position in Eminus's own block atlas, not the game's atlas, so not a coordinate for `gtexture`. |
| `texcoordDx` | `vec2` | Screen-space x derivative of `texcoord`. |
| `texcoordDy` | `vec2` | Screen-space y derivative of `texcoord`. |
| `lmcoord` | `vec2` | Block light in `x`, sky light in `y`, in the range `gbuffers_terrain` gets: `(level + 0.5) / 16`. |
| `glcolor` | `vec4` | The tint times the corner occlusion the game's Smooth Lighting draws, alpha `1.0`; where the pack declares `separateAo=true`, the tint alone with the occlusion in alpha, as Iris hands both to the game's terrain. It carries no face shading. |
| `normal` | `vec3` | Face normal in view space. Plant blades report straight up. |
| `face` | `int` | `0` down, `1` up, `2` north, `3` south, `4` west, `5` east; `6` and above are plant blades. |
| `viewPos` | `vec3` | Position in view space. |
| `playerPos` | `vec3` | Position relative to the camera on the world axes, as `gbufferModelViewInverse * viewPos` gives it. |
| `emission` | `float` | The block's light emission, `level / 15`. |
| `blockId` | `int` | The block's id from your `block.properties`, the value `mc_Entity.x` carries in `gbuffers_terrain`; `-1` where your mapping names nothing. Since version 2. |
| `translucent` | `bool` | `true` in the translucent program. |
| `blade` | `bool` | `true` on a plant blade (`face >= 6`). |

`blockId` follows Iris's own rules: a block takes the id of its state, and a fluid, the water in a waterlogged block included, takes the id of the fluid's own block. A pack that tells blocks apart by `mc_Entity.x` in its terrain fragment code therefore reads `fragment.blockId` in the same place; code that needs version 2 tests `#if EMINUS_CONTRACT_VERSION >= 2`.

The LOD's geometry is built for one mapping. Loading a pack with this contract, leaving it, or switching to one whose `block.properties` maps differently rebuilds the LOD once, as Iris rebuilds the near terrain on the same switch.

## Cutout

Each contract program discards the pixels that fail the alpha test Iris gives the matching game program. Since version 8.

| Contract file | Game program |
| --- | --- |
| `eminus_opaque.glsl` | `gbuffers_terrain` (cutout) |
| `eminus_translucent.glsl` | `gbuffers_water` |
| `eminus_shadow.glsl` | `shadow_cutout` |
| `eminus_shadow_translucent.glsl` | `shadow_water` |

The test is your `alphaTest.` key for that program in `shaders.properties`, or Iris's own default where you set none. A test you set to `off` leaves the cut to your code, so tell Eminus where that code runs:

- With `cutout = pack` in `shaders/eminus.properties`, Eminus cuts nothing more, and `fragment.color` carries the texture's alpha. Keep your terrain's cutout line in `eminus_emitFragment`, in each contract file whose game program sets the test to `off`.
- Without the key, Eminus cuts at `0.5` in the opaque programs and `0.1` in the translucent ones.

```glsl
void eminus_emitFragment(EminusFragment fragment) {
    if (fragment.color.a < 0.1) {
        discard;
    }
    color = shadeTerrain(fragment.color, fragment.lmcoord);
}
```

## When it is drawn

- **Opaque:** at the start of the translucent phase, after the solid `gbuffers` programs and the hand, before your `deferred` passes. Buffers are written in the flip state your `gbuffers` programs see.
- **Translucent:** after the game's translucent terrain, before your `composite` passes. Buffers are written in the flip state your `gbuffers_water` sees.

The shadow programs: [Shadows](shadows.md#when-the-shadows-are-drawn).
