package com.eminus.cell.cache;

import java.util.concurrent.TimeUnit;

import com.eminus.Eminus;
import com.eminus.cell.CellKey;
import com.eminus.store.StoreException;

final class WriteFailureLog implements WriteLatch.Report {
    @Override
    public void firstFailure(long key, StoreException failure) {
        Eminus.LOGGER.error("Could not save the cell at level {} ({}, {}, {}); it stays dirty.",
                CellKey.level(key), CellKey.x(key), CellKey.y(key), CellKey.z(key), failure);
    }

    @Override
    public void latched(int failures) {
        Eminus.LOGGER.warn("{} cell writes in a row failed; saving is paused and one write is tried every {} seconds.",
                failures, TimeUnit.MILLISECONDS.toSeconds(WriteLatch.PROBE_MILLIS));
    }

    @Override
    public void dropping(int cap) {
        Eminus.LOGGER.warn("More than {} unsaved cells are held while saving is paused; the oldest are dropped from"
                + " now on.", cap);
    }

    @Override
    public void recovered(int dropped) {
        Eminus.LOGGER.warn("A cell write succeeded and saving resumes; {} unsaved cells were dropped while it was"
                + " paused.", dropped);
    }

    @Override
    public void stillFailing(int dropped) {
        Eminus.LOGGER.warn("Cell writes still fail at the close; {} unsaved cells were dropped while saving was"
                + " paused.", dropped);
    }
}
