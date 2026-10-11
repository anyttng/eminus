# Eminus

A NeoForge and Fabric client mod that draws terrain far beyond the game's render distance. Eminus keeps a coarse, persistent copy of every chunk the client has seen and renders that copy as a far layer behind the game's own terrain.

*Eminus* is Latin for "from afar".

## Versions

- Minecraft **26.3**
- NeoForge
- Fabric Loader with Fabric API

## Status

In development. Runs on both of the game's backends, OpenGL and Vulkan. Sodium is supported on both loaders. Under an Iris or Vitrail shader pack, the far layer is drawn over the pack's finished frame; an Iris pack that ships the [shader-pack contract](docs/shader-pack-contract.md) shades it itself. Nothing is uploaded to Modrinth or CurseForge yet.

## For mod developers

`com.eminus.api.v1` is the client-side surface for other mods. It answers whether a far renderer runs and why it was refused, how far the far layer reaches, and whether it has settled for the camera. It also draws boxes another mod hands it, such as waypoints or claim borders, at every distance out to the far layer's edge.

```groovy
repositories {
    maven { url = 'https://raw.githubusercontent.com/anyttng/eminus/maven/' }
}

dependencies {
    compileOnly 'com.eminus:eminus-api:<mod version>'
}
```

The artifact carries that one package, with sources and javadoc beside it. Within a major version its members are not removed or changed incompatibly. Everything outside the package is internal: it moves without notice, and mixins into it are unsupported.

How to depend on it and what each member answers is in [docs/modding.md](docs/modding.md).
