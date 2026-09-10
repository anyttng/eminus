package com.eminus.cell.cache;

public interface CellAccess {
    CellHandle open(long key);

    void release(CellHandle handle);
}
