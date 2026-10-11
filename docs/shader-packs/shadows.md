# Shadows

Part of the [Eminus shader-pack contract](../shader-pack-contract.md).

`eminus_shadow.glsl` runs for the LOD's opaque and cutout faces in your shadow pass, and `eminus_shadow_translucent.glsl` for its translucent ones, with `fragment.translucent` set. Without the second file, translucent LOD faces cast no shadow, so an `eminus_shadow.glsl` written before version 6 never receives a translucent fragment.

Both files' `fragment` carries the same fields as in the other two programs ([The fragment function](fragment.md)), with `viewPos` and `normal` in the shadow view's space, so the body of your `shadow` fragment code moves over the way your terrain code did. `gl_ProjectionMatrix` and `gl_ModelViewMatrix` are the shadow pass's own, and everything Iris hands your `shadow` program is available.

The translucent faces are drawn after Iris has copied the opaque shadow depth into `shadowtex1`, as the near field's translucent terrain is. `shadowtex1` therefore holds the opaque casters alone, near and LOD alike, and `shadowtex0` the translucent ones in front of them.

Both shadow files take the same vertex hook. Your shadow sampling reads the map through your distortion, so the LOD has to be written through it too: where `vertex.shadow` is `true`, `eminus_vertexPosition` returns `eminus_project(vertex.playerPos)` with the distortion your `shadow` vertex program applies to its own clip position ([The vertex hook](vertex-hook.md)). A pack whose shadow map is not distorted returns the projected position as it is.

A pack written for version 3 carries the distortion in `eminus_shadow_vertex.glsl` instead, which Eminus reads only while `eminus_vertex.glsl` is absent. It is a vertex-stage source like `eminus_vertex.glsl` and defines

```glsl
vec4 eminus_shadowPosition(vec4 shadowClipPosition);
```

and no `main`. Eminus hands it each LOD vertex in shadow clip space (`shadowProjection * shadowModelView` applied) and writes what it returns to `gl_Position`.

Faces are drawn whichever way they face, as Iris draws the near terrain into the shadow map, except a few translucent faces Eminus builds one-sided, such as water against the seabed, which keep their back-face culling. The LOD reaches your shadow map only as far as your `shadowDistance` does: Iris's shadow map is a square of that half-width around the camera, so a pack whose shadow distance stays inside the render distance shows no LOD shadow at all.

## Shadow planes

The shadow map's depth runs between `shadowNearPlane` and `shadowFarPlane` along the light, and Iris's defaults, `-100.05` and `156.0` blocks, cut away the LOD past them under a low sun. Set both to `-1.0`, as a pack does for Distant Horizons:

```glsl
const float shadowNearPlane = -1.0;
const float shadowFarPlane = -1.0;
```

Iris then spans the depth over the render distance, and while the LOD casts shadows Eminus extends that span to the LOD's render distance.

## Your own shadow program

Without `eminus_shadow.glsl`, Eminus draws the LOD's opaque and cutout faces through the program Iris would use for cutout terrain in your shadow pass: `shadow_cutout`, or `shadow` where the pack has no `shadow_cutout`. Both stages are yours, so your distortion, your alpha test and whatever you write to `shadowcolor0` apply to the LOD as they do to near terrain.

Your vertex stage runs here, not `eminus_vertex.glsl`. Its `mc_Entity.x` carries the real block id at every `level` and with LOD animations off: the limits of [The vertex hook](vertex-hook.md) do not apply to it.

Eminus fills the inputs your vertex stage reads from each LOD vertex:

| Input | Value |
| --- | --- |
| `gl_Vertex`, `gl_Normal` | The LOD vertex and its face's normal. |
| `gl_MultiTexCoord0` | Coordinates in voxels across the face, so `fract` of them is the place within one voxel: one block at `level` 0, `2^level` blocks past it. Keep them in the varying your fragment stage samples with. |
| `gl_MultiTexCoord1`, `gl_MultiTexCoord2` | The LOD's block and sky light in the game's units, `0` to `240`, as for terrain. |
| `gl_TextureMatrix[0]`, `gl_TextureMatrix[1]` | In both stages: the identity, and the game's lightmap matrix that takes those units to the lightmap's texel centres. |
| `gl_Color` | White: the biome tint is already in the texture colour below. |
| `mc_Entity` | The block's id from `block.properties` in `x`, the other components `0`. |
| `mc_midTexCoord`, `at_midBlock`, `at_tangent` | `0`. |

Where your fragment stage samples its texture (`gtexture`, `tex`, `texture` or `gcolor`) with `texture`, `texture2D`, `textureLod`, `texture2DLod`, `textureGrad`, `texture2DGrad` or `texture2DGradARB`, Eminus replaces the lookup with one into the LOD's own textures, tinted and repeated block by block, at the coordinates you pass.

Eminus does not run the program, and the LOD casts no shadow, when it:

- samples that texture in its vertex stage, or names it anywhere other than in its `uniform` declaration and as the first argument of one of the lookups above (passed into a function of your own, `textureSize`, `texelFetch`);
- declares one of the inputs above together with another name in one declaration;
- declares `mc_Entity` as a type other than `int`, `uint`, `float` or one of their vectors;
- has no `main`, or more than one;
- carries a geometry or tessellation stage.

The game log then carries one line naming the program and the reason. Ship `eminus_shadow.glsl` for such a pack.

## When the shadows are drawn

- **Shadow:** inside your shadow pass, after the near field's opaque terrain, into `shadowcolor0`, `shadowcolor1` and `shadowtex0` in the flip state your `shadow` program sees.
- **Translucent shadow:** inside your shadow pass, after Iris copies the opaque shadow depth into `shadowtex1`, beside the near field's translucent terrain, into `shadowcolor0`, `shadowcolor1` and `shadowtex0`. Only while your pack draws translucent terrain into its shadow map: `shadowTranslucent` in `shaders.properties`, on unless you set it `false`.
