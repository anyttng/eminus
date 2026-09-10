package com.eminus.cell.cache;

@FunctionalInterface
public interface CellSaver {
    void submit(CellHandle handle);
}
