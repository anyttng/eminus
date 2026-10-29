# Beacon beams

Part of the [Eminus shader-pack contract](../shader-pack-contract.md).

Eminus draws a beacon's beam past the render distance as a colored column. `eminus_beaconbeam.glsl` shades those beams inside your pack, as your `gbuffers_beaconbeam` shades the near ones. Since version 7.

```glsl
void eminus_emitBeam(EminusBeam beam);
```

The file follows the rules of every contract fragment file ([Files and names](files.md)). It opens with `#version`, may `#include` your files, declares its own `RENDERTARGETS` and defines no `main`. Write the buffers and values your `gbuffers_beaconbeam` writes, and a far beam meets the near one at the render distance with the same look.

```glsl
/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 color;

void eminus_emitBeam(EminusBeam beam) {
    color = beam.color;
}
```

Without the file, Eminus draws no beam past the render distance inside your pack, so no beam stands unlit in your frame.

| Field | Type | Meaning |
| --- | --- | --- |
| `color` | `vec4` | The beam's color at this height, from the glass the beacon shines through. Alpha is `1.0` on the beam and `32 / 255` on its glow. |
| `glow` | `bool` | `true` on the wider glow around the beam, which the game draws translucent. A pack that discards the glow in `gbuffers_beaconbeam` discards it here too. |
| `normal` | `vec3` | Face normal in view space. |
| `face` | `int` | The beam's side, numbered as `fragment.face`: `2` north, `3` south, `4` west, `5` east. |
| `lmcoord` | `vec2` | Full block and sky light, `15.5 / 16` in both, as the game lights a beam. |
| `viewPos` | `vec3` | Position in view space. |
| `playerPos` | `vec3` | Position relative to the camera on the world axes. |
| `height` | `float` | Blocks above the beacon. |

The beam carries no texture. `color` stays the same along each colored section.

Your `eminus_vertex.glsl` runs on the beam's vertices too ([The vertex hook](vertex-hook.md)). It gets `blockId` `-1`, `level` `0`, and `top`, `blade` and `shadow` `false`, so your jitter applies and nothing sways.

## When the beams are drawn

- **The beam:** right after the opaque LOD, before your `deferred` passes, into the buffers your `gbuffers` programs see and the LOD's depth. Your later passes find it through `eminus_viewPosition`, like the rest of the LOD.
- **The glow:** right before the translucent LOD, before your `composite` passes. It writes no depth.

`blend.eminus_beaconbeam` and `blend.eminus_beaconbeam_glow` in `shaders.properties` set their blending. Without them, the beam does not blend and the glow blends as the game's translucent beam does.

The game casts no shadow from a beacon beam, and neither does the LOD.
