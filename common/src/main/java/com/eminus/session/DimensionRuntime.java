package com.eminus.session;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import com.eminus.Eminus;
import com.eminus.cell.CellFrame;
import com.eminus.cell.Dictionary;
import com.eminus.cell.StateTable;
import com.eminus.cell.cache.CellCache;
import com.eminus.ingest.CellChangeListener;
import com.eminus.ingest.CellMerger;
import com.eminus.ingest.IngestService;
import com.eminus.ingest.SectionConverter;
import com.eminus.ingest.SectionPyramid;
import com.eminus.store.CellStore;
import com.eminus.store.EmptyCellStore;
import com.eminus.store.SaveService;
import com.eminus.store.SqliteCellStore;
import com.eminus.work.WorkService;

public final class DimensionRuntime {
    private final WorldIdentity identity;
    private final Path folder;
    private final CellFrame frame;
    private final int lowestStoredLevel;

    private final AtomicReference<CellChangeListener> changes = new AtomicReference<>();

    private CellStore store;
    private SaveService saves;
    private CellCache cells;
    private StateTable states;
    private Dictionary<String> biomes;
    private CellMerger merger;
    private IngestService ingest;
    private int references;
    private long idleSince;
    private boolean closed;

    DimensionRuntime(WorldIdentity identity, Path folder, CellFrame frame, int lowestStoredLevel) {
        this.identity = identity;
        this.folder = folder;
        this.frame = frame;
        this.lowestStoredLevel = lowestStoredLevel;
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

    public CellCache cells() {
        return cells;
    }

    public StateTable states() {
        return states;
    }

    public Dictionary<String> biomes() {
        return biomes;
    }

    public CellMerger merger() {
        return merger;
    }

    public IngestService ingest() {
        return ingest;
    }

    public boolean closed() {
        return closed;
    }

    // A listener that takes the handle replaces the one that releases it, so a cell reaches exactly one owner.
    public void listenTo(CellChangeListener listener) {
        changes.set(listener);
    }

    public void stopListening() {
        changes.set((handle, faceMask) -> cells.release(handle));
    }

    void createFolder() {
        try {
            Files.createDirectories(folder);
        } catch (IOException failure) {
            Eminus.LOGGER.error("Could not create the store folder {}.", folder, failure);
        }
    }

    void openStore() {
        try {
            store = SqliteCellStore.open(folder, lowestStoredLevel);
        } catch (RuntimeException failure) {
            store = EmptyCellStore.INSTANCE;
            Eminus.LOGGER.error("Could not open the cell store in {}; {} runs without one.",
                    folder, identity.dimension(), failure);
        }
    }

    void openCells(WorkService<Void> saveService, WorkService<SectionPyramid> ingestService, LongSupplier clock) {
        Dictionary<String> stateIds = openDictionary(StateTable.DICTIONARY_NAME);
        Dictionary<String> biomeIds = openDictionary(SectionConverter.DICTIONARY_NAME);

        saves = new SaveService(store, saveService);
        cells = new CellCache(store, saves, clock);
        states = new StateTable(stateIds);
        biomes = biomeIds;
        changes.set((handle, faceMask) -> cells.release(handle));
        merger = new CellMerger(cells, frame, lowestStoredLevel, (handle, faceMask) -> changes.get().changed(handle, faceMask));
        ingest = new IngestService(ingestService, states, biomes, merger);
    }

    private Dictionary<String> openDictionary(String name) {
        Dictionary<String> dictionary = new Dictionary<>((id, value) -> store.putDictionaryEntry(name, id, value));

        try {
            store.readDictionary(name, dictionary::load);
        } catch (RuntimeException failure) {
            Eminus.LOGGER.error("Could not read the {} dictionary in {}; {} runs without a store rather than "
                    + "reassigning ids over the cells already there.", name, folder, identity.dimension(), failure);
            closeStore();
            store = EmptyCellStore.INSTANCE;
        }

        return dictionary;
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
        ingest.stop();
        if (store == null) {
            return;
        }

        saves.flush();
        cells.flush();
        closeStore();
        store = null;
    }

    private void closeStore() {
        try {
            store.close();
        } catch (RuntimeException failure) {
            Eminus.LOGGER.error("Could not close the cell store in {}.", folder, failure);
        }
    }

    int references() {
        return references;
    }

    long idleSince() {
        return idleSince;
    }
}
