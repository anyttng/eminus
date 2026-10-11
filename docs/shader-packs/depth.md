# Depth

Part of the [Eminus shader-pack contract](../shader-pack-contract.md).

The LOD does not write `depthtex0`: your `deferred` and `composite` code reconstructs near-field positions from it, and an LOD fragment there would break that. The LOD has its own depth instead, readable in every program of the pack:

| Name | Type | Meaning |
| --- | --- | --- |
| `eminusDepthTex0` | `sampler2D` | LOD depth after the translucent faces. |
| `eminusDepthTex1` | `sampler2D` | LOD depth after the opaque faces only. |
| `eminusProjection` | `mat4` | The LOD's projection. Its far plane is 48 000 blocks. |
| `eminusProjectionInverse` | `mat4` | Its inverse. |
| `eminusPreviousProjection` | `mat4` | The LOD's projection of the previous frame. |
| `eminusRenderDistance` | `int` | The LOD's render distance in blocks; `0` on a frame with no LOD drawn. |

## The position helper

```glsl
vec4 eminus_viewPosition(vec2 texcoord, bool opaqueOnly);
```

Since version 5. Eminus supplies it in every program of the pack that calls it, in any stage; a program that does not name it is compiled as you wrote it. Call it where Iris hands your program `depthtex0`: that is where the textures it reads are bound.

It returns the view-space position of the nearest surface at `texcoord`, with `w` at `1.0`:

- where the near field drew, the near field's position, from `depthtex0` (or `depthtex1` when `opaqueOnly` is `true`) through `gbufferProjectionInverse`;
- where only the LOD drew, the LOD's position, from `eminusDepthTex0` (or `eminusDepthTex1`) through `eminusProjectionInverse`;
- on sky, `w` is `0.0` and `xyz` is the point on the near projection's far plane, so `normalize(xyz)` is the view ray.

On a frame where `eminusRenderDistance` is `0` it reads the near field alone. Both kinds of position are in the one view space `gbufferModelViewInverse` takes to the world axes. The example pack's [`composite.fsh`](../example-pack/shaders/composite.fsh) fogs the near field and the LOD through it.

The helper reads its own copies of those textures and matrices, so it works whether or not your program declares `depthtex0` and the others, and none of your declarations collides with it. Do not declare a name that begins with `eminus_view` in any program of the pack.

## By hand

A pack that keeps its own reconstruction reads the LOD depth itself. Both depth textures read the way `depthtex0` reads (`1.0` where nothing is) and turn into a view-space position through `eminusProjectionInverse` the way `depthtex0` does through `gbufferProjectionInverse`, with `gbufferModelView` for the camera.

Where the near field drew, `eminusDepthTex0` holds that surface's depth in the LOD's projection, so read `depthtex0` first and fall back to the LOD depth only where `depthtex0` holds nothing. On a frame where `eminusRenderDistance` is `0` the depth textures carry no LOD: test it before reading them.

A pack that already supports Distant Horizons makes the same change where it reads `dhDepthTex`, `dhProjectionInverse` and `dhRenderDistance`.
