# Files and names

Part of the [Eminus shader-pack contract](../shader-pack-contract.md).

## Files

| File | Required | Program |
| --- | --- | --- |
| `eminus_opaque.glsl` | yes | Opaque and cutout LOD faces. Its presence turns the contract on. |
| `eminus_translucent.glsl` | no | Translucent LOD faces (water, stained glass, ice). Without it, `eminus_opaque.glsl` runs for them too, with `fragment.translucent` set. |
| `eminus_shadow.glsl` | no | Opaque and cutout LOD faces in your shadow pass. Since version 3. Without it, your own shadow program runs for them ([Your own shadow program](shadows.md#your-own-shadow-program)). |
| `eminus_shadow_translucent.glsl` | no | Translucent LOD faces in your shadow pass. Since version 6. Used whenever the opaque LOD faces cast shadows, through `eminus_shadow.glsl` or your own shadow program. Without it, translucent LOD faces cast no shadow. |
| `eminus_vertex.glsl` | no | The vertex stage's hook of every program built from your contract files: your vertex animation, your jitter, your render scale, your shadow-map distortion. Since version 4. Your own shadow program runs its own vertex stage instead. Without it, LOD positions reach your buffers as Eminus projects them. |
| `eminus_shadow_vertex.glsl` | no | The version 3 form of the shadow program's vertex hook: your shadow-map distortion. Read only while `eminus_vertex.glsl` is absent. |
| `eminus_beaconbeam.glsl` | no | Beacon beams past the render distance ([Beacon beams](beacon-beams.md)). Since version 7. Without it, no beam is drawn past the render distance inside your pack. |
| `eminus.properties` | no | Not a program: the blocks your vertex hook moves ([Blocks your hook moves](vertex-hook.md#blocks-your-hook-moves)). Since version 6. Read from `shaders/` itself, never from a dimension folder. |

A `.glsl` file is looked up in the folder Iris uses for the current dimension (`world0`, `world-1`, `world1`, or the folder `dimension.properties` maps it to) and, when that folder does not carry it, in `shaders/` itself. One file at the root therefore serves every dimension; a copy in a dimension folder overrides it for that dimension alone.

Every `.glsl` file except the two vertex files is a fragment-stage source in your pack's dialect:

- it opens with `#version 330` or later;
- it may `#include` your pack's files and use your options, macros and `#ifdef`s like any other program;
- it declares its outputs under its own `/* RENDERTARGETS: … */` (or `DRAWBUFFERS`) directive;
- it defines `void eminus_emitFragment(EminusFragment fragment)`, or `void eminus_emitBeam(EminusBeam beam)` in `eminus_beaconbeam.glsl`, and no `main`; Eminus supplies `main`.

`blend.eminus_opaque`, `blend.eminus_translucent`, `blend.eminus_shadow` and `blend.eminus_shadow_translucent` in `shaders.properties` set the blending of the four programs as `blend.<program>` does for any other. Without one, the translucent program blends as the game's translucent terrain does, and the other three do not blend, as Iris's own shadow programs do not. Your own shadow program runs as `shadow_cutout_eminus` or `shadow_eminus`, so its key is `blend.shadow_cutout_eminus` or `blend.shadow_eminus`, not the one your pack sets for `shadow_cutout`. The beam file runs as two programs, `eminus_beaconbeam` and `eminus_beaconbeam_glow` ([Beacon beams](beacon-beams.md#when-the-beams-are-drawn)).

## Macros

Defined in every program of the pack while Eminus is installed:

| Macro | Value |
| --- | --- |
| `EMINUS` | defined, empty |
| `EMINUS_CONTRACT_VERSION` | `7` |

## Inside the contract files

Everything Iris hands a `gbuffers` program is available: its uniforms, your custom uniforms, `colortex*` and `depthtex*`, `noisetex`, custom textures and images, and your shader storage buffers at the bindings your `shaders.properties` gives them. Three names differ from a `gbuffers` program:

- `lightmap` is the game's lightmap, so `texture(lightmap, fragment.lmcoord)` lights a LOD pixel as vanilla would;
- `gtexture` is a white pixel: the LOD's texture arrives in `fragment.color`;
- `gl_ProjectionMatrix` is the LOD's own projection ([Depth](depth.md)) and `gl_ModelViewMatrix` the camera's rotation.

Do not declare a name that begins with `eminus`, `Eminus` or `EMINUS`: Eminus declares its own names under these prefixes in front of your code. Iris reserves `iris_`, `dh_` and `dhBlockAtlas`.
