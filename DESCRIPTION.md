**See terrain far beyond your render distance.** Eminus keeps a low-detail (LOD) copy of the world you have explored and draws it past the edge of the game's own terrain.

## What it does

- Every chunk you see is saved to disk as a low-detail copy, per world and per dimension, so it is still there the next time you join.
- That copy is drawn behind the game's own terrain, from 32 up to 2048 chunks away — 512 by default.
- Far terrain never draws over near terrain.
- Block changes in the chunks around you reach the far terrain too.
- Runs on the game's OpenGL graphics API.
- Client-only: works on any server, with nothing installed there.

## Settings

Open the settings screen from the mod list — Config on NeoForge, Configure through Mod Menu on Fabric — or edit `config/eminus.json`.

- **Ingestion** — turns saving of newly seen chunks on or off.
- **Lowest stored level** — the finest detail kept on disk; raising it from 0 to 1 cuts disk use to about a seventh.
- **Far render distance** — how far the far terrain reaches.
- **Worker threads** — background threads that build far terrain.
- **Subdivision size** — how close finer detail starts; smaller costs more.
- **Fog mode** — fog, fade or both where the far terrain ends.

Worker threads and Lowest stored level take effect on the next world join.

## Compatibility

Runs on **NeoForge** and **Fabric**, Minecraft 26.2. Mod Menu is optional on Fabric. **Sodium** is supported on both loaders.

## Before you download

- Far terrain appears only where you have already been — there is no import of existing worlds and no pregeneration.
- Vulkan support is in development.
- Under a shader pack the far terrain is not lit by the pack.

## Bug reports

Found something broken? Report it on the [issue tracker](https://github.com/vitalikyarina/eminus/issues).
