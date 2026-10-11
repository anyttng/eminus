**See terrain far beyond your render distance.** Eminus keeps a low-detail (LOD) copy of the world you have explored and draws it past the edge of the game's own terrain.

## What it does

- Every chunk you see is saved to disk as a low-detail copy, per world and per dimension, so it is still there the next time you join.
- That copy is drawn behind the game's own terrain, from 32 up to 2048 chunks away, 512 by default.
- LOD never draws over near terrain.
- Block changes in the chunks around you reach LOD too.
- LOD shows water and plants, carries sky and block light, and blends grass, leaf and water colours across biomes the way your Biome Blend is set.
- Runs on both of the game's graphics APIs, OpenGL and Vulkan.
- Client-only: works on any server, with nothing installed there.

## Settings

Open the settings screen from the mod list (Config on NeoForge, Configure through Mod Menu on Fabric) or from the Eminus page in Sodium's video settings. The same values live in `config/eminus.json`.

- **Ingestion**: turns saving of newly seen chunks on or off.
- **Lowest stored level**: the finest detail kept on disk; raising it from Full to High cuts disk use to about a seventh.
- **LOD distance**: how far LOD reaches.
- **Worker threads**: background threads that build LOD.
- **Detail distance**: how far out the finer detail of LOD reaches; each step up doubles that distance and costs more video memory.
- **Fog**: fog over LOD, carried on from the game's own.
- **Fade**: fades LOD out over its last 512 blocks instead of ending it in a hard line.

Two more appear only with Iris installed:

- **Shader pack LOD**: how LOD is drawn under a shader pack not made for Eminus. Default draws it over the pack's image in Eminus's own look. Distant Horizons lets a pack that supports Distant Horizons draw it with the pack's light and fog, one flat colour per block, while Distant Horizons itself is not installed.
- **LOD animations**: lets a shader pack made for Eminus sway grass, leaves and water in LOD; off saves the GPU time this costs.

Every setting applies at once. Lowest stored level also restarts LOD and reads the chunks around you again.

## Compatibility

Runs on **NeoForge** and **Fabric**. Mod Menu is optional on Fabric.

**Sodium** is supported on both loaders.

**Shader packs**:

- **Iris** and **Vitrail**: LOD shows under any pack, in Eminus's own look.
- An Iris pack can draw LOD itself, with its own light, fog and shadows. No pack does this yet.

## Before you download

- LOD appears only where you have already been: there is no import of existing worlds and no pregeneration.

## Bug reports

Found something broken? Report it on the [issue tracker](https://github.com/anyttng/eminus/issues).
