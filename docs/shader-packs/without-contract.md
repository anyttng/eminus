# A pack without the contract

Part of the [Eminus shader-pack contract](../shader-pack-contract.md).

Eminus's setting **Shader Pack LOD**, shown while Iris is installed, decides what a pack without the contract gets. Changing it reloads the pack.

- **Default:** the LOD is drawn over the pack's finished frame. The log says `Shader pack in use: the far layer draws over the pack's finished frame`.
- **Distant Horizons:** the LOD is drawn through the pack's Distant Horizons programs while Distant Horizons itself is not installed (with it installed, Iris runs its own path). The pack must ship no contract `.glsl` file and must ship `dh_terrain.vsh` and `dh_terrain.fsh`; Eminus looks for both in `shaders/` and every folder directly under it. Where the current dimension has no `dh_terrain`, the LOD is drawn over the finished frame. The log says `Shader pack has no Eminus contract: the far layer draws through its Distant Horizons programs`.

## The Distant Horizons path

- `DISTANT_HORIZONS` is defined for the pack; `DISTANT_HORIZONS_TEXTURES` is not. A pack that ships a contract file never gets `DISTANT_HORIZONS`, so `#if defined EMINUS && !defined DISTANT_HORIZONS` tells the contract apart from this path.
- `dh_terrain` draws the opaque LOD, `dh_water` the translucent one (`dh_terrain` where the pack has no `dh_water`). `dh_shadow` draws the LOD into the shadow map, with the same inputs as `dh_terrain`, unless the pack sets `dhShadow.enabled = false`; without `dh_shadow` the LOD casts no shadow.
- The programs run as `eminus_dh_terrain`, `eminus_dh_water` and `eminus_dh_shadow`, so `blend.` keys take those names; the pack's `blend.dh_terrain`, `blend.dh_water` and `blend.dh_shadow` do not apply.
- `gl_Color` is each face's mean texture color with its biome tint, so LOD comes out one flat color per block face; `dhMaterialId` is one of the 16 `DH_BLOCK_*` classes; `gl_MultiTexCoord1` carries block and sky light.
- `dhDepthTex0`, `dhDepthTex1`, `dhProjection`, `dhProjectionInverse`, `dhPreviousProjection`, `dhNearPlane` and `dhFarPlane` describe the LOD. `dhRenderDistance` is the LOD's render distance in chunks, not in blocks as under Distant Horizons, and never less than the game's render distance. A pack whose shadow planes are `-1.0` has them span the LOD's render distance, as under Distant Horizons.
- The LOD lies under the whole near field, as under Distant Horizons: a pixel the near field drew keeps the near surface, and where your terrain program thins the near field towards the render distance, the LOD shows through. Eminus still discards the cut-out texels of leaves and plants before the pack's own `main` runs.

The names Eminus declares in front of the pack's code are those of [Inside the contract files](files.md#inside-the-contract-files), in the vertex stage also those of [The vertex hook](vertex-hook.md), plus `eminus_packMain`, which the pack's `main` is renamed to.

A program that does not build logs one line and the LOD goes back over the pack's finished frame, as in [When a file does not build](troubleshooting.md); its source is written under `eminus_dh_terrain` or `eminus_dh_water`. A `dh_shadow` that does not build leaves the LOD drawn without a shadow, its source written under `eminus_dh_shadow`.
