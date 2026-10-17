# Eminus shader-pack contract, version 6

Eminus draws the terrain past the render distance as a level-of-detail layer (LOD). Under an Iris shader pack that does not know Eminus, the LOD is drawn over the pack's finished frame in Eminus's own shading ([A pack without the contract](shader-packs/without-contract.md)). A pack that ships the contract below shades the LOD itself. Eminus runs the geometry, your code colors each pixel and writes it into your own buffers, so your lighting, fog and post-processing apply to the LOD the way they apply to near terrain.

The contract is one fragment function, and optionally one vertex function.

It works under Iris on the game's OpenGL backend. On Vulkan, Eminus logs `Shader pack file … is not used: the far layer runs on the game's GPU path` and draws the LOD over the pack's finished frame. Under a Vitrail shader pack, the LOD is always drawn over the finished frame.

## Adding support

### 1. Shade the LOD with your terrain code

Your terrain fragment code then runs in two programs: `gbuffers_terrain` for the near terrain and `eminus_opaque.glsl` for the LOD. Keep it in one include that both call, and a change to your terrain shading reaches both.

1. Move the part of your `gbuffers_terrain` fragment code that comes after its texture sample and alpha cutout into a function in an include, with a parameter for each input it reads. The example pack's function reads the tinted texture color and the light coordinates; a pack whose code reads `normal`, `viewPos` or `blockId` adds them as parameters.

   ```glsl
   // shaders/lib/terrain.glsl
   uniform sampler2D lightmap;

   vec4 shadeTerrain(vec4 albedo, vec2 lmcoord) {
       return albedo * texture(lightmap, lmcoord);
   }
   ```

2. Call it from `gbuffers_terrain` with your varyings:

   ```glsl
   #include "/lib/terrain.glsl"

   void main() {
       vec4 albedo = texture(gtexture, texcoord) * glcolor;
       if (albedo.a < ALPHA_CUTOUT) {
           discard;
       }
       color = shadeTerrain(albedo, lmcoord);
   }
   ```

3. Copy [`example-pack/shaders/eminus_opaque.glsl`](example-pack/shaders/eminus_opaque.glsl) into your pack's `shaders/` folder, change its `RENDERTARGETS` to the buffers your `gbuffers_terrain` writes, and call the same function with the fields of `fragment`:

   ```glsl
   #include "/lib/terrain.glsl"

   void eminus_emitFragment(EminusFragment fragment) {
       color = shadeTerrain(fragment.color, fragment.lmcoord);
   }
   ```

   `fragment.color` takes the place of the texture color times `glcolor`: it is already tinted and past the cutout, so pass it where `gbuffers_terrain` passes that product, and never multiply it by `fragment.glcolor` again. Every other input is the field named after the varying it replaces (`texcoord`, `lmcoord`, `glcolor`, `normal`, `viewPos`), and `fragment.blockId` replaces `mc_Entity.x`.
4. Reload shaders with Iris's Reload Shaders key (F3 + R by default). The log line `Shader pack carries the Eminus contract: the far layer draws inside the pack` confirms it took.

Without an include, copy your terrain fragment code into `eminus_emitFragment` instead, reading the field of `fragment` wherever the code reads a varying and taking the texture color from `fragment.color` rather than sampling `gtexture`. The copy then has to follow every later change to your terrain code by hand.

A pack that lights and fogs terrain in its `gbuffers` programs is done here. Every field of `fragment`: [The fragment function](shader-packs/fragment.md).

### 2. Let your later passes see the LOD

A `deferred` or `composite` pass that reads `depthtex0` sees nothing where only the LOD is: it treats those pixels as sky, so deferred lighting overwrites them and distance fog skips them. Wherever such a pass turns `depthtex0` into a position, call `eminus_viewPosition` instead, and take `eminusRenderDistance` as the far edge where it is not `0`:

```glsl
#if EMINUS_CONTRACT_VERSION >= 5
    vec4 position = eminus_viewPosition(texcoord, false);
#endif
```

`position.xyz` is the view-space position of the near field where it drew and of the LOD where only the LOD did; `position.w` is `0.0` on sky. [Depth](shader-packs/depth.md) has the details, and the reconstruction for a pack that keeps its own.

### 3. Cast shadows from the LOD

Nothing to add in most packs: without `eminus_shadow.glsl`, Eminus runs your own `shadow_cutout` program (or `shadow`, as Iris falls back) on the LOD's opaque faces, distortion included ([Your own shadow program](shader-packs/shadows.md#your-own-shadow-program)). Set your shadow planes to `-1.0` ([Shadow planes](shader-packs/shadows.md#shadow-planes)) so the shadow map reaches the LOD.

Add `eminus_shadow.glsl` beside `eminus_opaque.glsl` where your shadow program does not run on the LOD, where you want the LOD's shadows written differently, or where your vertex hook (step 4) animates blocks and their shadows should move with them. Put the body of your `shadow` fragment code in it. If your `shadow` vertex program distorts the shadow map, apply the same distortion in `eminus_vertex.glsl`.

If your `shadow` program colors the shadow of stained glass or water, add `eminus_shadow_translucent.glsl` too, with the same body, and the LOD's translucent faces cast those shadows as well.

### 4. Move the LOD's vertices with your vertex code

If your `gbuffers_terrain` vertex program changes the position it projects (temporal anti-aliasing jitter, a render scale, a shadow distortion), add `eminus_vertex.glsl` with the same code, so the LOD reaches your later passes where they expect your terrain. [The vertex hook](shader-packs/vertex-hook.md) has the details. If it animates blocks, name the blocks it moves in `eminus.properties` ([Blocks your hook moves](shader-packs/vertex-hook.md#blocks-your-hook-moves)), or gaps open in the LOD where they sway.

The [example pack](example-pack/) is a complete minimal pack: textured, lit terrain, the contract file sharing its shading through one include, and a fog composite that is step 2 in one call. It carries its own MIT [license](example-pack/LICENSE), so a pack may copy any part of it.

## Reference

- [Files and names](shader-packs/files.md): every contract file and where it is looked up, the macros, and what a contract file can read and must not declare.
- [The fragment function](shader-packs/fragment.md): every field of `fragment`, and when the opaque and translucent LOD is drawn.
- [Depth](shader-packs/depth.md): the LOD's depth textures and projection, the position helper, and reconstruction by hand.
- [Shadows](shader-packs/shadows.md): the shadow files, shadow planes, and your own shadow program run on the LOD.
- [The vertex hook](shader-packs/vertex-hook.md): `eminus_vertexPosition`, `eminus_project`, and the blocks your hook moves.
- [When a file does not build](shader-packs/troubleshooting.md): what a failure leaves running, and the log lines.
- [A pack without the contract](shader-packs/without-contract.md): the Shader Pack LOD setting and the Distant Horizons path.
