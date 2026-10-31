# The vertex hook

Part of the [Eminus shader-pack contract](../shader-pack-contract.md).

`eminus_vertex.glsl` is a vertex-stage source in your pack's dialect: it opens with `#version`, may `#include` your files, and defines

```glsl
vec4 eminus_vertexPosition(inout EminusVertex vertex);
```

and no `main`. Since version 4. Eminus calls it once per LOD vertex in every program built from your contract files and writes what it returns to `gl_Position`. Its body does what your terrain vertex code does once it has a position. It projects the position, then applies your jitter or your render scale, and in the shadow programs your shadow distortion.

| Field | Type | Meaning |
| --- | --- | --- |
| `playerPos` | `vec3` | Position relative to the camera on the world axes, in the shadow programs as well. |
| `blockId` | `int` | As `fragment.blockId`; `-1` past `level` 0 and while the player has LOD Animations off. |
| `face` | `int` | As `fragment.face`. |
| `blade` | `bool` | As `fragment.blade`; `false` past `level` 0 and while the player has LOD Animations off. |
| `top` | `bool` | `true` on the upper edge of a side face or a plant blade; `false` on up and down faces. A face Eminus merged over several voxels has vertices at its corners alone, so `top` is `true` on the two corners above its whole span. `false` past `level` 0 and while the player has LOD Animations off. |
| `lmcoord` | `vec2` | As `fragment.lmcoord`. |
| `level` | `int` | The LOD cell's detail level: a voxel spans `2^level` blocks along each axis. |
| `shadow` | `bool` | `true` in both shadow programs. |

On a beacon beam's vertices ([Beacon beams](beacon-beams.md)) the hook gets `blockId` `-1`, `level` `0`, and `top`, `blade` and `shadow` `false`.

Eminus compiles this file as you wrote it, in the core profile of its `#version`. Iris does not patch it, so the renames Iris applies to your `gbuffers` programs (`gl_ProjectionMatrix`, `gl_ModelViewMatrix`, `texture2D`, `attribute`) do not happen here. Your uniforms and custom uniforms are available; build the position with `eminus_project`.

`vec4 eminus_project(vec3 playerPos)` takes a position to the clip space the LOD is drawn in:

- in the opaque and translucent programs, through the LOD's projection and the camera's rotation. Add your jitter or your render scale to `x` and `y` and leave `z` and `w` as they come: the LOD's depth follows the device's convention, not the -1..1 one;
- in the shadow programs, through `shadowProjection * shadowModelView`, in the -1..1 range your `shadow` program writes, so your distortion applies to it as it does there.

Your function may move `vertex.playerPos`. Eminus then derives `fragment.playerPos` and `fragment.viewPos` from the moved position, while the texture and the test against the near field keep the unmoved one. Move it the way your terrain vertex code moves a vertex, before you project it: plants on `top`, leaves and the water surface on every vertex, and in the shadow programs as well, so the shadows follow. Eminus moves nothing on its own.

Past `level` 0, and everywhere while the player has Eminus's LOD Animations setting off, the hook gets `blockId` `-1` and `top` and `blade` `false`. The fragment still gets the real `blockId`. Pick what you move by `blockId`, as your `gbuffers_terrain` picks it by `mc_Entity`, and your code moves nothing there. Past `level` 0 a block covers at most a few pixels, so a sway there would move a pixel or less. Eminus switches to finer detail once a piece of LOD looks larger than the Detail Distance setting's 16 to 256 pixels. Changing LOD Animations reloads the shader pack.

A pack applies its own animation (here `animateVertex`, the function your terrain vertex code calls) and jitter:

```glsl
uniform vec3 cameraPosition;
uniform vec2 taa_offset;

vec4 eminus_vertexPosition(inout EminusVertex vertex) {
    vec3 worldPos = vertex.playerPos + cameraPosition;
    vertex.playerPos = animateVertex(worldPos, vertex.top, vertex.blockId) - cameraPosition;

    vec4 clip = eminus_project(vertex.playerPos);
    if (!vertex.shadow) {
        clip.xy += taa_offset * clip.w;
    }
    return clip;
}
```

## Blocks your hook moves

Eminus joins neighboring faces of one block into one larger face, with vertices at its corners alone. Where a smaller face meets the middle of a larger face's edge, your function moves the smaller face's corner by its value at that point. The larger face's edge stays a straight line between its own moved corners, so a gap opens between them. Eminus cannot read from your code which blocks it moves, so you name them: list their ids from your `block.properties` under the key `moved` in `shaders/eminus.properties`. Since version 6.

```properties
moved = \
#ifdef WAVING_PLANTS
    10002 10003 10004 \
#endif
#ifdef WAVING_LEAVES
    10005 \
#endif
```

- Eminus reads the file through Iris's properties preprocessor with your options applied, as Iris reads your `block.properties`. Wrap each id in the `#ifdef` your vertex code moves it under, and a player who turns the option off gets those faces joined again.
- Ids are separated by spaces; anything that is not a block id is skipped with one line in the log.
- A list that ends in `\` takes in the next line that is not blank, so write every other key, `cutout` included, above `moved`.
- The faces of the named blocks stay one block wide and tall at `level` 0, the only level where your hook moves them. Past `level` 0, and everywhere while the player has LOD Animations off, they are joined as before.
- The price is quads: every face of a named block at `level` 0 is one quad, where its neighbors of the same look would have shared one. Name only what your hook moves.
- Without the file, every face is joined as before.

Each pack load logs the list as `Shader pack declares moved blocks … in eminus.properties`. Changing the list (a pack switch, a changed option, LOD Animations turned on or off) rebuilds the LOD once.
