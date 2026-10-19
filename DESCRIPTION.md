**See terrain far beyond your render distance.** Eminus keeps a low-detail (LOD) copy of the chunks you have seen and draws it past the edge of the game's own terrain.

The same spot with the game's render distance alone, then with LOD at the default 512 chunks:

![Render distance alone](https://raw.githubusercontent.com/anyttng/eminus/main/docs/images/before.png)

![With LOD](https://raw.githubusercontent.com/anyttng/eminus/main/docs/images/after.png)

## What you get

- Terrain out to 2048 chunks, 512 by default, with water, plants, light and biome colors.
- Works on any server. Nothing is installed on the server side.

## Compatibility

NeoForge and Fabric. OpenGL, and Vulkan where the game offers it.

Sodium on both loaders. Mod Menu is optional on Fabric.

Under an Iris or Vitrail shader pack LOD shows in Eminus's own look. An Iris pack can draw LOD itself, with its own light, fog and shadows; no pack does this yet.

## Settings

Config on NeoForge, Configure through Mod Menu on Fabric, or the Eminus page in Sodium's video settings. The main ones:

- **LOD Quality**: Low, Medium, High, Ultra or Custom.
- **LOD Distance**: 32 to 2048 chunks, 512 by default.
- **Detail Distance**: how far the finer detail of LOD reaches. Each step up doubles it and costs more video memory.
- **Fog**: fog over LOD, carried on from the game's own.
- **Fade**: LOD fades out over its last 512 blocks instead of ending in a hard line.

Every setting has a hint on the screen. The same values live in `config/eminus.json`.

## Before you download

LOD appears only where you have already been. There is no import of existing worlds and no pregeneration.

## Bug reports

Found something broken? Report it on the [issue tracker](https://github.com/anyttng/eminus/issues).
