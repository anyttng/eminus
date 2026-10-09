# Eminus from Another Mod

Eminus exposes one package to other mods: `com.eminus.api.v1`. It answers three questions about the far layer, the terrain Eminus draws past the game's render distance: whether it runs on this client and, if not, why; how far it reaches; and whether it has finished loading for the camera.

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
