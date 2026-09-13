package com.eminus.store;

import java.util.function.LongConsumer;

import com.eminus.cell.Cell;
import com.eminus.cell.Dictionary;

public final class EmptyCellStore implements CellStore {
    public static final EmptyCellStore INSTANCE = new EmptyCellStore();

    private EmptyCellStore() {
    }

    @Override
    public Cell read(long key) {
        return null;
    }

    @Override
    public void write(Cell cell) {
    }

    @Override
    public void delete(long key) {
    }

    @Override
    public void putDictionaryEntry(String name, int id, String value) {
    }

    @Override
    public void readDictionary(String name, Dictionary.Persistence<String> into) {
    }

    @Override
    public void putColumn(long chunk) {
    }

    @Override
    public void readColumns(LongConsumer into) {
    }

    @Override
    public void close() {
    }
}
