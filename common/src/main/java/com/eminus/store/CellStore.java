package com.eminus.store;

import com.eminus.cell.Cell;
import com.eminus.cell.ColumnCoverage;
import com.eminus.cell.Dictionary;

public interface CellStore extends AutoCloseable {
    Cell read(long key);

    void write(Cell cell);

    void delete(long key);

    void putDictionaryEntry(String name, int id, String value);

    void readDictionary(String name, Dictionary.Persistence<String> into);

    void putColumn(long chunk, int lowestLevel);

    void readColumns(ColumnCoverage.Persistence into);

    @Override
    void close();
}
