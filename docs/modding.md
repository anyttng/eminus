# Eminus from Another Mod

Eminus exposes one package to other mods: `com.eminus.api.v1`. It answers three questions about the far layer, the terrain Eminus draws past the game's render distance: whether it runs on this client and, if not, why; how far it reaches; and whether it has finished loading for the camera. It also takes boxes from your mod and draws them with the far layer.

Everything outside that package is internal: it moves between releases without notice, and mixins into it are unsupported.

## Depending on it

```groovy
repositories {
    maven {
        name = 'Eminus'
        url = 'https://raw.githubusercontent.com/anyttng/eminus/maven/'
    }
}

dependencies {
    compileOnly 'com.eminus:eminus-api:<mod version>'
}
```

The API version is the mod version; `maven-metadata.xml` under the coordinate lists what is published. Sources and javadoc are published beside the jar. Within a major version, members are not removed or changed incompatibly.

`compileOnly` is the point: the artifact carries the API package alone, and at runtime the classes come from the copy of Eminus the player installed. Declare the mod as an optional dependency of yours and check that it is loaded before you touch any `com.eminus` class; the mod id is `eminus`. A class that names one of them fails to load when the mod is absent, so keep those calls in a class you reach only after the check, not in a field initialiser of a class that always loads.

Eminus is a client mod. A dedicated server loads its jar and runs nothing of it, so every call below belongs in client code.

## Whether the far layer runs

`EminusApi.status()` answers on the render thread with a `FarLayerStatus`:

- **`RUNNING`**: a far renderer draws the dimension the client holds.
- **`NO_LEVEL`**: the client holds no level: before a world is joined, after it is left, and while the client changes dimension.
- **`REFUSED`**: the client holds a level, but the far renderer was refused, because the render backend lacks a feature it needs or one of its programs did not compile.

Under `REFUSED`, `EminusApi.refusal()` gives the reason as a sentence fit for a log line; under the other two it is `null`. A refusal is not final: a resource reload, a change of dimension, or a change of the far render distance or the detail distance starts the renderer again.

```java
if (EminusApi.status() == FarLayerStatus.REFUSED) {
    LOGGER.info("Eminus draws no far terrain here: {}", EminusApi.refusal());
}
```

## How far it reaches

`EminusApi.farDistanceBlocks()` is the far render distance the player set, in blocks of horizontal distance from the camera. The far layer starts where the game's own render distance ends and draws out to it. A mod that culls its own distant content, places a far plane or sizes fog reads this instead of the game's render distance.

It answers the setting whether or not a far renderer runs, and it may be called on any thread once mod loading has finished.

## Whether it has settled

`EminusApi.farLayer()` reads the far layer's state on the render thread. It answers `null` unless `status()` answers `RUNNING`. Otherwise it hands back a `CompletableFuture<FarLayerState>`, because the node tree lives on a thread of its own: the future completes once that thread has taken the request, and completes exceptionally when the renderer stops first.

`FarLayerState` carries two things:

- **`dimension()`**: the dimension the renderer draws, as its identifier, such as `minecraft:overworld`.
- **`settled()`**: whether the far layer has nothing left to do for the camera it last saw: no mesh build is queued or running, the last walk over the view asked for nothing more, and no chunk section waits to be merged. Block changes still waiting to be merged do not count: they revise terrain already drawn, and a live world never runs out of them.

`settled()` is what a mod waits on before it captures the view, such as a screenshot or a timelapse frame. The future completes on Eminus's tree thread, so hand anything that touches the game back to the client:

```java
CompletableFuture<FarLayerState> reading = EminusApi.farLayer();
if (reading != null) {
    reading.thenAcceptAsync(state -> {
        if (state.settled()) {
            captureFrame();
        }
    }, Minecraft.getInstance());
}
```

A reading answers for the camera the far layer last saw, so a mod that waits for it polls again after the camera moves.

## Drawing boxes

The game stops drawing at its render distance, and so does whatever your mod draws there. A waypoint, a claim border or a marker over a structure can go to Eminus instead: `EminusApi.registerBoxes(dimension, boxes)` takes a list of `FarBox` in world coordinates and draws them at every distance from the camera out to `farDistanceBlocks()`, fogged like the terrain around them, faded with the far layer at its far end, and hidden behind any terrain nearer than they are. One call covers the near field too, so your mod draws nothing of its own for them.

A `FarBox` carries:

- **the bounds**: `minX`, `minY`, `minZ`, `maxX`, `maxY`, `maxZ`, in blocks, each minimum no greater than its maximum;
- **`argb`**: the colour as `0xAARRGGBB`; an alpha below `0xFF` blends the box over what lies behind it, and boxes blend in the order their groups were registered;
- **`emissive`**: `true` keeps the colour in the dark; `false` lights the box as daylight lights the open terrain, so it dims at night.

`registerBoxes` hands back a `FarBoxGroup`. `update(boxes)` replaces its boxes, `remove()` takes it away; both apply from the next frame. Every call may come from any thread, and none needs a far renderer running: a group registered before the renderer starts, or while it is refused, is drawn once it runs.

A group belongs to one dimension, named by its identifier, and is drawn only while the client is there; a change of dimension keeps it for when the client comes back. Leaving the world drops every group, after which the old handles do nothing, so register yours again when the player joins a world.

```java
FarBoxGroup marker = EminusApi.registerBoxes("minecraft:overworld", List.of(
        new FarBox(1000, 64, -2000, 1001, 320, -1999, 0xFFFF4040, true)));

marker.update(List.of(
        new FarBox(1000, 64, -2000, 1001, 256, -1999, 0xFF40FF40, true)));

marker.remove();
```

Under a shader pack, the boxes come with the far layer over the pack's finished frame. A pack that ships the Eminus contract, or one Eminus draws through its Distant Horizons programs, shades the far layer itself, and no group is drawn there.
