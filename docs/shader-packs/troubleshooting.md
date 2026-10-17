# When a file does not build

Part of the [Eminus shader-pack contract](../shader-pack-contract.md).

A program that does not build leaves the rest as follows:

- `eminus_opaque.glsl`, `eminus_translucent.glsl`, or `eminus_vertex.glsl` in those two programs: the LOD goes back to being drawn over the pack's finished frame.
- `eminus_shadow.glsl`, `eminus_shadow_vertex.glsl`, [your own shadow program](shadows.md#your-own-shadow-program), or `eminus_vertex.glsl` in the shadow program alone: the opaque and translucent programs keep running, and the LOD casts no shadow.
- `eminus_shadow_translucent.glsl`: every other program keeps running, and the LOD's translucent faces cast no shadow.

Eminus builds the programs again whenever the far renderer starts: on a shader or resource reload, a change of dimension, or a change of the LOD Distance or Detail Distance setting. Fix the file and reload shaders.

Each failure writes one line to the game log:

- `Shader … did not compile: …` carries the GLSL error. It names the program by its fragment file, such as `pack/eminus_opaque.glsl`, and the stage, `.vsh` or `.fsh`, so an error in `eminus_vertex.glsl` shows under the opaque or translucent fragment file with `.vsh`.
- `Shaders of … could not be read: …` means Eminus could not put the source together, such as a file that does not open with `#version`.
- `Shader pack file … did not bind to the pack: …` means the program compiled but did not bind to Iris's uniforms or samplers.

With Iris's debug options on, the source Eminus compiled is written to `patched_shaders/` in the game folder. It holds your file, Eminus's declarations in front of it and the generated `main`, under the program name `eminus_opaque`, `eminus_translucent`, `eminus_shadow` or `eminus_shadow_translucent`, and `shadow_cutout_eminus` or `shadow_eminus` for your own shadow program. The line numbers in the log refer to it.
