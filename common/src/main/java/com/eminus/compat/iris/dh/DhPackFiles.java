package com.eminus.compat.iris.dh;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.eminus.Eminus;
import com.eminus.compat.iris.PackContract;

public final class DhPackFiles {
    public static final String MACRO = "DISTANT_HORIZONS";

    private static final List<String> TERRAIN_FILES = List.of("dh_terrain.vsh", "dh_terrain.fsh");

    private DhPackFiles() {
    }

    public static boolean takesDhPath(Path root, boolean chosen, boolean distantHorizonsLoaded) {
        if (!chosen || distantHorizonsLoaded) {
            return false;
        }

        List<Path> folders;
        try (Stream<Path> children = Files.list(root)) {
            folders = Stream.concat(Stream.of(root), children.filter(Files::isDirectory)).toList();
        } catch (IOException | UncheckedIOException unreadable) {
            Eminus.LOGGER.warn("Shader pack folder {} could not be listed: {}", root, unreadable.toString());
            return false;
        }

        return folders.stream().noneMatch(DhPackFiles::carriesContract)
                && folders.stream().anyMatch(DhPackFiles::carriesTerrain);
    }

    private static boolean carriesContract(Path folder) {
        return PackContract.FILES.stream().anyMatch(file -> Files.exists(folder.resolve(file)));
    }

    private static boolean carriesTerrain(Path folder) {
        return TERRAIN_FILES.stream().allMatch(file -> Files.exists(folder.resolve(file)));
    }
}
