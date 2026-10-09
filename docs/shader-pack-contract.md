# Eminus shader-pack contract, version 3

Eminus draws the terrain past the render distance as a level-of-detail layer (LOD). Under an Iris shader pack that
does not know Eminus, the LOD is drawn over the pack's finished frame in Eminus's own shading
([A pack without the contract](#a-pack-without-the-contract)). A pack that ships the contract below shades the LOD itself: Eminus runs the geometry, your code colours each pixel and writes it into your
own buffers, so your lighting, fog and post-processing apply to the LOD the way they apply to near terrain.

The contract is one fragment function.

## Support in three steps

### 1. Shade the LOD with your terrain code

1. Copy [`example-pack/shaders/eminus_opaque.glsl`](example-pack/shaders/eminus_opaque.glsl) into your pack's
   `shaders/` folder.
2. Change its `RENDERTARGETS` to the buffers your `gbuffers_terrain` writes, and replace the body with your terrain
   fragment code. Where that code reads a varying, read the field of the same name on `fragment` instead —
   `texcoord`, `lmcoord`, `glcolor`, `normal`, `viewPos` — and take the texture colour from `fragment.color` rather
   than sampling `gtexture`.
3. Reload shaders with Iris's Reload Shaders key — F3 + R by default. The log line
   `Shader pack carries the Eminus contract: the far layer draws inside the pack` confirms it took.

A pack that lights and fogs terrain in its `gbuffers` programs is done here.

### 2. Let your later passes see the LOD

A `deferred` or `composite` pass that reads `depthtex0` sees nothing where only the LOD is: it treats those pixels
as sky, so deferred lighting overwrites them and distance fog skips them. Wherever such a pass reads `depthtex0`, read
`eminusDepthTex0` (or `eminusDepthTex1`) where `depthtex0` holds nothing, rebuild the position with
`eminusProjectionInverse`, and take `eminusRenderDistance` as the far edge — [Depth](#depth) has the details. A pack
that already supports Distant Horizons makes the same change where it reads `dhDepthTex`, `dhProjectionInverse` and
`dhRenderDistance`.

### 3. Cast shadows from the LOD

Add `eminus_shadow.glsl` beside `eminus_opaque.glsl`, with the body of your `shadow` fragment code, and the LOD's
opaque faces are drawn into your shadow map. If your `shadow` vertex program distorts the shadow map, add
`eminus_shadow_vertex.glsl` too, with the same distortion — [Shadows](#shadows) has the details. Without
`eminus_shadow.glsl` the LOD casts no shadow.

The [example pack](example-pack/) is a complete minimal pack under the same MIT license as Eminus: textured, lit
terrain, the contract file, and a fog composite that is step 2 in a dozen lines.

## Files

| File | Required | Program |
| --- | --- | --- |
| `eminus_opaque.glsl` | yes | Opaque and cutout LOD faces. Its presence turns the contract on. |
| `eminus_translucent.glsl` | no | Translucent LOD faces (water, stained glass, ice). Without it, `eminus_opaque.glsl` runs for them too, with `fragment.translucent` set. |
| `eminus_shadow.glsl` | no | Opaque and cutout LOD faces in your shadow pass. Since version 3. Without it, the LOD casts no shadow. |
| `eminus_shadow_vertex.glsl` | no | The vertex stage's hook of the shadow program: your shadow-map distortion. Since version 3. Without it, LOD positions reach the shadow map undistorted. |

A file is looked up in the folder Iris uses for the current dimension (`world0`, `world-1`, `world1`, or the folder
`dimension.properties` maps it to) and, when that folder does not carry it, in `shaders/` itself. One file at the
root therefore serves every dimension; a copy in a dimension folder overrides it for that dimension alone.

Each file but `eminus_shadow_vertex.glsl` is a fragment-stage source in your pack's dialect:

- it opens with `#version 330` or later;
- it may `#include` your pack's files and use your options, macros and `#ifdef`s like any other program;
- it declares its outputs under its own `/* RENDERTARGETS: … */` (or `DRAWBUFFERS`) directive;
- it defines `void eminus_emitFragment(EminusFragment fragment)` and no `main` — Eminus supplies `main`.

`blend.eminus_opaque`, `blend.eminus_translucent` and `blend.eminus_shadow` in `shaders.properties` set the blending
of the three programs as `blend.<program>` does for any other.

## Macros

Defined in every program of the pack while Eminus is installed:

| Macro | Value |
| --- | --- |
| `EMINUS` | defined, empty |
| `EMINUS_CONTRACT_VERSION` | `3` |

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
| `blockId` | `int` | The block's id from your `block.properties`, the value `mc_Entity.x` carries in `gbuffers_terrain`; `-1` where your mapping names nothing. Since version 2. |
| `translucent` | `bool` | `true` in the translucent program. |
| `blade` | `bool` | `true` on a plant blade (`face >= 6`). |

`blockId` follows Iris's own rules: a block takes the id of its state, and a fluid — the water in a waterlogged
block included — takes the id of the fluid's own block. A pack that tells blocks apart by `mc_Entity.x` in its terrain
fragment code therefore reads `fragment.blockId` in the same place; code that needs version 2 tests
`#if EMINUS_CONTRACT_VERSION >= 2`.

The LOD's geometry is built for one mapping. Loading a pack with this contract, leaving it, or switching to one
whose `block.properties` maps differently rebuilds the LOD once, as Iris rebuilds the near terrain on the same switch.

## Inside the contract files

Everything Iris hands a `gbuffers` program is available: its uniforms, your custom uniforms, `colortex*` and
`depthtex*`, `noisetex`, custom textures and images, and your shader storage buffers at the bindings your
`shaders.properties` gives them. Besides:

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
- **Shadow:** inside your shadow pass, after the near field's opaque terrain, into `shadowcolor0`, `shadowcolor1` and
  `shadowtex0` in the flip state your `shadow` program sees.

## Shadows

`eminus_shadow.glsl` runs for the LOD's opaque and cutout faces in your shadow pass; translucent LOD faces cast no
shadow. Its `fragment` carries the same fields as in the other two programs, with `viewPos` and `normal` in the
shadow view's space, so the body of your `shadow` fragment code moves over the way your terrain code did in step 1.
`gl_ProjectionMatrix` and `gl_ModelViewMatrix` are the shadow pass's own, and everything Iris hands your `shadow`
program is available.

Your shadow sampling reads the map through your distortion, so the LOD has to be written through it too.
`eminus_shadow_vertex.glsl` is a vertex-stage source in your pack's dialect: it opens with `#version`, may `#include`
your files, and defines

```glsl
vec4 eminus_shadowPosition(vec4 shadowClipPosition);
```

and no `main`. Eminus hands it each LOD vertex in shadow clip space — `shadowProjection * shadowModelView` applied —
and writes what it returns to `gl_Position`, so the body is the distortion your `shadow` vertex program applies to its
own clip position. A pack whose shadow map is not distorted leaves the file out.

Faces are drawn whichever way they face, as Iris draws the near terrain into the shadow map. The LOD reaches your
shadow map only as far as your `shadowDistance` does: Iris's shadow map is a square of that half-width around the
camera, so a pack whose shadow distance stays inside the render distance shows no LOD shadow at all.

The shadow map's depth runs between `shadowNearPlane` and `shadowFarPlane` along the light, and Iris's defaults,
`-100.05` and `156.0` blocks, cut away the LOD past them under a low sun. Set both to `-1.0`, as a pack does for
Distant Horizons:

```glsl
const float shadowNearPlane = -1.0;
const float shadowFarPlane = -1.0;
```

Iris then spans the depth over the render distance, and while your pack draws the LOD through `eminus_shadow.glsl`
Eminus extends that span to the LOD's render distance.

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
goes back to being drawn over the pack's finished frame until the next reload. An `eminus_shadow.glsl` or
`eminus_shadow_vertex.glsl` that does not build leaves the other two programs running and the LOD without shadows. Fix the file and reload shaders.

With Iris's debug options on, the source Eminus compiled — your file, Eminus's declarations in front of it and the
generated `main` — is written to `patched_shaders/` in the game folder, under the program name `eminus_opaque`,
`eminus_translucent` or `eminus_shadow`; the line numbers in the log refer to it.

## A pack without the contract

Eminus's setting **Shader pack LOD** decides what a pack without the contract gets. On **Default** the LOD is drawn
over the pack's finished frame. On **Distant Horizons**, a pack that ships no contract file anywhere, but ships
`dh_terrain.vsh` and `dh_terrain.fsh` in its root or a dimension folder, has the LOD drawn through its Distant Horizons
programs while Distant Horizons itself is not installed (with it installed, Iris runs its own path). Changing the
setting reloads the pack. On that path:

- `DISTANT_HORIZONS` is defined for the pack; `DISTANT_HORIZONS_TEXTURES` is not. A pack that ships a contract file
  never gets `DISTANT_HORIZONS`, so `#if defined EMINUS && !defined DISTANT_HORIZONS` tells the contract apart from
  this path.
- `dh_terrain` draws the opaque LOD, `dh_water` the translucent one (`dh_terrain` where the pack has no `dh_water`).
  `dh_shadow` draws the LOD into the shadow map, with the same inputs as `dh_terrain`, unless the pack sets
  `dhShadow.enabled = false`; without `dh_shadow` the LOD casts no shadow.
- `gl_Color` is each face's mean texture colour with its biome tint, so far terrain comes out one flat colour per
  block face; `dhMaterialId` is one of the 16 `DH_BLOCK_*` classes; `gl_MultiTexCoord1` carries block and sky light.
- `dhDepthTex0`, `dhDepthTex1`, `dhProjection`, `dhNearPlane`, `dhFarPlane` and `dhRenderDistance` describe the LOD,
  as `eminusDepthTex0`, `eminusDepthTex1`, `eminusProjection` and `eminusRenderDistance` do. A pack whose shadow
  planes are `-1.0` has them span the LOD's render distance, as under Distant Horizons.
- The LOD lies under the whole near field, as under Distant Horizons: a pixel the near field drew keeps the near
  surface, and where your terrain program thins the near field towards the render distance, the LOD shows through.
  Eminus still discards the cut-out texels of leaves and plants before the pack's own `main` runs.

The names Eminus declares in front of the pack's code are those of
[Inside the contract files](#inside-the-contract-files), plus `eminus_packMain`, which the pack's `main` is renamed
to. A program that does not build logs one line and the LOD goes back over the pack's finished frame, as in
[When a file does not build](#when-a-file-does-not-build); its source is written under `eminus_dh_terrain` or
`eminus_dh_water`. A `dh_shadow` that does not build leaves the LOD drawn without a shadow, its source written under
`eminus_dh_shadow`.
