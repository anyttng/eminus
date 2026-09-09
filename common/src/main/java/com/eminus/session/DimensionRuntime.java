package com.eminus.session;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.eminus.Eminus;
import com.eminus.cell.CellFrame;

public final class DimensionRuntime {
    private final WorldIdentity identity;
    private final Path folder;
    private final CellFrame frame;

    private int references;
    private long idleSince;
    private boolean closed;

    DimensionRuntime(WorldIdentity identity, Path folder, CellFrame frame) {
        this.identity = identity;
        this.folder = folder;
        this.frame = frame;
    }

    public WorldIdentity identity() {
        return identity;
    }

    public Path folder() {
        return folder;
    }

    public CellFrame frame() {
        return frame;
    }

    public boolean closed() {
        return closed;
    }

    void createFolder() {
        try {
            Files.createDirectories(folder);
        } catch (IOException failure) {
            Eminus.LOGGER.error("Could not create the store folder {}.", folder, failure);
        }
    }

    void acquire() {
        references++;
    }

    void release(long now) {
        if (references == 0) {
            throw new IllegalStateException("Dimension runtime " + identity.dimension() + " was released more often than acquired.");
        }

        references--;
        if (references == 0) {
            idleSince = now;
        }
    }

    void close() {
        closed = true;
    }

    int references() {
        return references;
    }

    long idleSince() {
        return idleSince;
    }
}
