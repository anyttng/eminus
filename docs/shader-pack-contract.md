# Eminus shader-pack contract, version 1

Eminus draws the terrain past the render distance as a level-of-detail layer (LOD). Under an Iris shader pack that
does not know Eminus, the LOD is drawn over the pack's finished frame in Eminus's own shading. A pack that ships the
contract below shades the LOD itself: Eminus runs the geometry, your code colours each pixel and writes it into your
own buffers, so your lighting, fog and post-processing apply to the LOD the way they apply to near terrain.

The contract is one fragment function. Eminus reads no other LOD mod's pack files.

## Support in five minutes

1. Copy [`example-pack/shaders/eminus_opaque.glsl`](example-pack/shaders/eminus_opaque.glsl) into your pack's
   `shaders/` folder.
2. Change its `RENDERTARGETS` to the buffers your `gbuffers_terrain` writes, and replace the body with your terrain
   fragment code. Where that code reads a varying, read the field of the same name on `fragment` instead —
   `texcoord`, `lmcoord`, `glcolor`, `normal`, `viewPos` — and take the texture colour from `fragment.color` rather
   than sampling `gtexture`.
3. Reload shaders with Iris's Reload Shaders key — F3 + R by default. The log line
   `Shader pack carries the Eminus contract: the far layer draws inside the pack` confirms it took.

The [example pack](example-pack/) is a complete minimal pack under the same MIT license as Eminus: textured, lit
terrain, a fog composite that reads both the near and the LOD depth, and the one contract file.

## Files

| File | Required | Program |
| --- | --- | --- |
| `eminus_opaque.glsl` | yes | Opaque and cutout LOD faces. Its presence turns the contract on. |
| `eminus_translucent.glsl` | no | Translucent LOD faces (water, stained glass, ice). Without it, `eminus_opaque.glsl` runs for them too, with `fragment.translucent` set. |

A file is looked up in the folder Iris uses for the current dimension (`world0`, `world-1`, `world1`, or the folder
`dimension.properties` maps it to) and, when that folder does not carry it, in `shaders/` itself. One file at the
root therefore serves every dimension; a copy in a dimension folder overrides it for that dimension alone.

Each file is a fragment-stage source in your pack's dialect:

- it opens with `#version 330` or later;
- it may `#include` your pack's files and use your options, macros and `#ifdef`s like any other program;
- it declares its outputs under its own `/* RENDERTARGETS: … */` (or `DRAWBUFFERS`) directive;
- it defines `void eminus_emitFragment(EminusFragment fragment)` and no `main` — Eminus supplies `main`.

`blend.eminus_opaque` and `blend.eminus_translucent` in `shaders.properties` set the blending of the two programs as
`blend.<program>` does for any other.

## Macros

Defined in every program of the pack while Eminus is installed:

| Macro | Value |
| --- | --- |
| `EMINUS` | defined, empty |
| `EMINUS_CONTRACT_VERSION` | `1` |

## The function

```glsl
void eminus_emitFragment(EminusFragment fragment);
```

Eminus calls it once per LOD pixel that survives its own tests, after those tests have run: pixels the near field
draws are already discarded, and so are pixels whose texture alpha falls under the cutout — `0.5` in the opaque
program, `0.1` in the translucent one. Your function never sees a pixel the near field owns.

| Field | Type | Meaning |
| --- | --- | --- |
| `color` | `vec4` | The block's texture at this pixel, filtered across the tiles of the LOD face and tinted (grass, foliage, water). Alpha is `1.0` in the opaque program. |
| `texcoord` | `vec2` | Position in Eminus's own block atlas — not the game's atlas, so not a coordinate for `gtexture`. |
| `texcoordDx` | `vec2` | Screen-space x derivative of `texcoord`. |
| `texcoordDy` | `vec2` | Screen-space y derivative of `texcoord`. |
| `lmcoord` | `vec2` | Block light in `x`, sky light in `y`, in the range `gbuffers_terrain` gets: `(level + 0.5) / 16`. |
| `glcolor` | `vec4` | The tint alone, alpha `1.0`. It carries no ambient occlusion and no face shading. |
| `normal` | `vec3` | Face normal in view space. Plant blades report straight up. |
| `face` | `int` | `0` down, `1` up, `2` north, `3` south, `4` west, `5` east; `6` and above are plant blades. |
| `viewPos` | `vec3` | Position in view space. |
| `playerPos` | `vec3` | Position relative to the camera on the world axes, as `gbufferModelViewInverse * viewPos` gives it. |
| `emission` | `float` | The block's light emission, `level / 15`. |
| `translucent` | `bool` | `true` in the translucent program. |
| `blade` | `bool` | `true` on a plant blade (`face >= 6`). |

Per-block ids (`mc_Entity`, your `block.properties`) are not part of version 1.

## Inside the contract files

Everything Iris hands a `gbuffers` program is available: its uniforms, your custom uniforms, `colortex*` and
`depthtex*`, `noisetex`, custom textures and images. Besides:

- `lightmap` is the game's lightmap, so `texture(lightmap, fragment.lmcoord)` lights a LOD pixel as vanilla would;
- `gtexture` is a white pixel — the LOD's texture arrives in `fragment.color`;
- `gl_ProjectionMatrix` is the LOD's own projection (see below) and `gl_ModelViewMatrix` the camera's rotation.

Do not declare a name that begins with `eminus`, `Eminus`, `far_`, `FAR_` or `Far`, nor any of `Atlas`, `TintMask`,
`ModelVariants`, `NearSections`, `MinBlockY`, `AtlasCells`, `NearSide`, `NearHeight`, `NearOrigin`, `ShadeDown`,
`ShadeUp`, `ShadeNorth`, `ShadeSouth`, `ShadeWest`, `ShadeEast`, `CameraBlockPos`, `CameraOffset`,
`MIN_AXIS_LENGTH` — Eminus declares them in front of your code. Iris reserves `iris_`, `dh_` and `dhBlockAtlas`.

## When the LOD is drawn

- **Opaque:** at the start of the translucent phase — after the solid `gbuffers` programs and the hand, before your
  `deferred` passes. Buffers are written in the flip state your `gbuffers` programs see.
- **Translucent:** after the game's translucent terrain, before your `composite` passes. Buffers are written in the
  flip state your `gbuffers_water` sees.

Shadows are not drawn for the LOD in version 1.

## Depth

The LOD does not write `depthtex0`: your `deferred` and `composite` code reconstructs near-field positions from it,
and an LOD fragment there would break that. The LOD has its own depth instead, readable in every program of the pack:

| Name | Type | Meaning |
| --- | --- | --- |
| `eminusDepthTex0` | `sampler2D` | LOD depth after the translucent faces. |
| `eminusDepthTex1` | `sampler2D` | LOD depth after the opaque faces only. |
| `eminusProjection` | `mat4` | The LOD's projection. Its far plane is 48 000 blocks. |
| `eminusProjectionInverse` | `mat4` | Its inverse. |
| `eminusPreviousProjection` | `mat4` | The LOD's projection of the previous frame. |
| `eminusRenderDistance` | `int` | The LOD's render distance in blocks; `0` on a frame with no LOD drawn. |

Both depth textures read the way `depthtex0` reads — `1.0` where nothing is — and turn into a view-space position
through `eminusProjectionInverse` the way `depthtex0` does through `gbufferProjectionInverse`, with `gbufferModelView`
for the camera. Where the near field drew, `eminusDepthTex0` holds that surface's depth in the LOD's projection, so
read `depthtex0` first and fall back to the LOD depth only where `depthtex0` holds nothing. On a frame where
`eminusRenderDistance` is `0` the depth textures carry no LOD — test it before reading them. The example pack's
[`composite.fsh`](example-pack/shaders/composite.fsh) does exactly this.

## When a file does not build

A compile or link error writes one line to the game log naming the pack file and carrying the GLSL error, and the LOD
goes back to being drawn over the pack's finished frame until the next reload. Fix the file and reload shaders.

With Iris's debug options on, the source Eminus compiled — your file, Eminus's declarations in front of it and the
generated `main` — is written to `patched_shaders/` in the game folder, under the program name `eminus_opaque` or
`eminus_translucent`; the line numbers in the log refer to it.
