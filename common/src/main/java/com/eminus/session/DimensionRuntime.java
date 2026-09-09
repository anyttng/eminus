package com.eminus.session;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.eminus.Eminus;
import com.eminus.cell.CellFrame;
import com.eminus.store.CellStore;
import com.eminus.store.SqliteCellStore;

public final class DimensionRuntime {
    private final WorldIdentity identity;
    private final Path folder;
    private final CellFrame frame;

    private CellStore store;
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

    void openStore(int lowestStoredLevel) {
        try {
            store = SqliteCellStore.open(folder, lowestStoredLevel);
        } catch (RuntimeException failure) {
            Eminus.LOGGER.error("Could not open the cell store in {}; {} runs without one.",
                    folder, identity.dimension(), failure);
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
        if (store == null) {
            return;
        }

        try {
            store.close();
        } catch (RuntimeException failure) {
            Eminus.LOGGER.error("Could not close the cell store in {}.", folder, failure);
        } finally {
            store = null;
        }
    }

    int references() {
        return references;
    }

    long idleSince() {
        return idleSince;
    }
}
