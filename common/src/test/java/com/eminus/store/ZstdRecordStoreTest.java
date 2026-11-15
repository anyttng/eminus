package com.eminus.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.eminus.cell.Cell;
import com.eminus.cell.CellKey;
import com.eminus.cell.ColumnCoverage;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.VoxelEntry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

@Timeout(60)
class ZstdRecordStoreTest {
    private static final String BLOCKS = "blocks";
    private static final String AIR = "minecraft:air";
    private static final int LOWEST_LEVEL = 0;
    private static final long COLUMN = ColumnCoverage.pack(3, -7);
    private static final long SEED = 257L;
    private static final int VOXELS_SET = 400;
    private static final int STATES = 300;
    private static final String REPLACE_DATA = "UPDATE cells SET data = ? WHERE key = ?";
    private static final String READ_DATA = "SELECT data FROM cells WHERE key = ?";

    @TempDir
    Path folder;

    @Test
    void aStoreHoldingZstdRecordsOpensWithEveryCellReadable() throws SQLException {
        Random random = new Random(SEED);
        List<Cell> cells = new ArrayList<>();
        for (int level = DetailLevel.MIN; level <= DetailLevel.MAX; level++) {
            cells.add(scatteredCell(CellKey.pack(level, level - 1, level, 2 - level), random));
        }

        try (SqliteCellStore store = open()) {
            cells.forEach(cell -> CellWrites.write(store, cell));
            store.putDictionaryEntry(BLOCKS, 0, AIR);
            store.putColumn(COLUMN, LOWEST_LEVEL);
        }

        replaceWithZstdRecords(cells);

        Map<Integer, String> blocks = new HashMap<>();
        Map<Long, Integer> columns = new HashMap<>();
        try (SqliteCellStore store = open()) {
            for (Cell cell : cells) {
                Cell read = store.read(cell.key());

                assertNotNull(read);
                assertArrayEquals(cell.copyPalette(), read.copyPalette());
                assertArrayEquals(cell.indices(), read.indices());
            }

            store.readDictionary(BLOCKS, blocks::put);
            store.readColumns((chunk, lowestLevel, current) -> columns.put(chunk, lowestLevel));
        }

        assertEquals(Map.of(0, AIR), blocks);
        assertEquals(Map.of(COLUMN, LOWEST_LEVEL), columns);
        assertEquals(StoreFormat.VERSION, readUserVersion());
        for (Cell cell : cells) {
            assertEquals(StoreFormat.ZSTD_RECORD_VERSION, storedRecord(cell.key())[0]);
        }
    }

    private void replaceWithZstdRecords(List<Cell> cells) throws SQLException {
        try (Connection connection = rawConnection();
                PreparedStatement replace = connection.prepareStatement(REPLACE_DATA)) {
            for (Cell cell : cells) {
                replace.setBytes(1, LegacyRecords.zstd(cell));
                replace.setLong(2, cell.key());

                assertEquals(1, replace.executeUpdate());
            }
        }
    }

    private byte[] storedRecord(long key) throws SQLException {
        try (Connection connection = rawConnection();
                PreparedStatement select = connection.prepareStatement(READ_DATA)) {
            select.setLong(1, key);
            try (ResultSet rows = select.executeQuery()) {
                rows.next();
                return rows.getBytes(1);
            }
        }
    }

    private int readUserVersion() throws SQLException {
        try (Connection connection = rawConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("PRAGMA user_version")) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static Cell scatteredCell(long key, Random random) {
        Cell cell = Cell.blank(key);
        for (int voxel = 0; voxel < VOXELS_SET; voxel++) {
            cell.set(random.nextInt(DetailLevel.VOXELS_PER_SIDE), random.nextInt(DetailLevel.VOXELS_PER_SIDE),
                    random.nextInt(DetailLevel.VOXELS_PER_SIDE),
                    VoxelEntry.pack(random.nextInt(STATES) + 1, 4, VoxelEntry.light(0, 0)));
        }

        return cell;
    }

    private SqliteCellStore open() {
        return SqliteCellStore.open(folder, LOWEST_LEVEL, 0L, () -> 0L);
    }

    private Connection rawConnection() throws SQLException {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + folder.resolve(SqliteCellStore.FILE_NAME));
        return source.getConnection();
    }
}
