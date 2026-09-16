package com.eminus.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.eminus.cell.Cell;
import com.eminus.cell.CellKey;
import com.eminus.cell.ColumnCoverage;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.Dictionary;
import com.eminus.cell.VoxelEntry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

@Timeout(60)
class SqliteCellStoreTest {
    private static final String BLOCKS = "blocks";
    private static final String BIOMES = "biomes";
    private static final long STONE = VoxelEntry.pack(17, 4, VoxelEntry.light(0, 0));
    private static final int LOWEST_LEVEL = 0;
    private static final int SPARSE_PAGE_SIZE = 512;
    private static final int SPARSE_CELLS = 12;
    private static final long SPARSE_GROWN_BYTES = 1024L * 1024L;
    private static final long SPARSE_VACUUMED_BYTES = 256L * 1024L;
    private static final long COLUMN_A = ColumnCoverage.pack(3, -7);
    private static final long COLUMN_B = ColumnCoverage.pack(-12, 40);
    private static final String COUNT_COLUMNS_TABLE =
            "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'columns'";

    @TempDir
    Path folder;

    @Test
    void aFreshStoreGetsTheSchemaAndTheFormatVersion() throws SQLException {
        SqliteCellStore.open(folder, LOWEST_LEVEL).close();

        assertEquals(StoreFormat.VERSION, readInt("PRAGMA user_version"));
        assertEquals(1, readInt("SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'cells'"));
        assertEquals(1, readInt("SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'dictionary'"));
        assertEquals(1, readInt(COUNT_COLUMNS_TABLE));
    }

    @Test
    void coveredColumnsComeBackOnceEachAfterAReopen() {
        try (SqliteCellStore store = SqliteCellStore.open(folder, LOWEST_LEVEL)) {
            store.putColumn(COLUMN_A);
            store.putColumn(COLUMN_B);
            store.putColumn(COLUMN_A);
        }

        List<Long> read = new ArrayList<>();
        try (SqliteCellStore store = SqliteCellStore.open(folder, LOWEST_LEVEL)) {
            store.readColumns(read::add);
        }

        assertEquals(2, read.size());
        assertEquals(Set.of(COLUMN_A, COLUMN_B), new HashSet<>(read));
    }

    @Test
    void aStoreWrittenBeforeCoverageIsRebuiltEmptyUnderTheCurrentVersion() throws SQLException {
        long key = CellKey.pack(1, 2, 3, 4);
        try (SqliteCellStore store = SqliteCellStore.open(folder, LOWEST_LEVEL)) {
            store.write(Cell.blank(key));
            store.putDictionaryEntry(BLOCKS, 0, "minecraft:air");
        }

        execute("DROP TABLE columns");
        execute("PRAGMA user_version = " + StoreFormat.BEFORE_COVERAGE);

        List<String> blocks = new ArrayList<>();
        List<Long> columns = new ArrayList<>();
        try (SqliteCellStore store = SqliteCellStore.open(folder, LOWEST_LEVEL)) {
            assertNull(store.read(key));
            store.readDictionary(BLOCKS, (id, value) -> blocks.add(value));
            store.readColumns(columns::add);
        }

        assertTrue(blocks.isEmpty());
        assertTrue(columns.isEmpty());
        assertEquals(StoreFormat.VERSION, readInt("PRAGMA user_version"));
        assertEquals(1, readInt(COUNT_COLUMNS_TABLE));
    }

    @Test
    void theStoreRunsInWalMode() throws SQLException {
        SqliteCellStore.open(folder, LOWEST_LEVEL).close();

        assertEquals("wal", readString("PRAGMA journal_mode"));
    }

    @Test
    void aStoreOfAnotherFormatVersionRefusesToOpen() throws SQLException {
        SqliteCellStore.open(folder, LOWEST_LEVEL).close();
        execute("PRAGMA user_version = " + (StoreFormat.VERSION + 1));

        StoreException refused = assertThrows(StoreException.class, () -> SqliteCellStore.open(folder, LOWEST_LEVEL));
        assertTrue(refused.getMessage().contains(String.valueOf(StoreFormat.VERSION + 1)));
    }

    @Test
    void aWrittenCellComesBackAtEveryLevel() {
        for (int level = DetailLevel.MIN; level <= DetailLevel.MAX; level++) {
            long key = CellKey.pack(level, level - 2, level, -level);
            Cell cell = Cell.blank(key);
            cell.set(level, 1, 2, STONE);

            try (SqliteCellStore store = SqliteCellStore.open(folder, LOWEST_LEVEL)) {
                store.write(cell);
            }

            try (SqliteCellStore store = SqliteCellStore.open(folder, LOWEST_LEVEL)) {
                Cell read = store.read(key);

                assertNotNull(read);
                assertEquals(key, read.key());
                assertEquals(cell.occupancy(), read.occupancy());
                assertEquals(STONE, read.get(level, 1, 2));
                assertEquals(VoxelEntry.AIR, read.get(0, 0, 0));
            }
        }
    }

    @Test
    void anAbsentCellAndADeletedCellBothReadAsNull() {
        long key = CellKey.pack(1, 4, 5, 6);

        try (SqliteCellStore store = SqliteCellStore.open(folder, LOWEST_LEVEL)) {
            assertNull(store.read(key));

            store.write(Cell.blank(key));
            assertNotNull(store.read(key));

            store.delete(key);
            assertNull(store.read(key));
        }
    }

    @Test
    void aDamagedRecordIsDeletedAndReadsAsAbsent() throws SQLException {
        long key = CellKey.pack(2, 7, 0, -7);

        try (SqliteCellStore store = SqliteCellStore.open(folder, LOWEST_LEVEL)) {
            store.write(Cell.blank(key));
        }

        execute("UPDATE cells SET data = x'0102030405060708' WHERE key = " + key);

        try (SqliteCellStore store = SqliteCellStore.open(folder, LOWEST_LEVEL)) {
            assertNull(store.read(key));
        }

        assertEquals(0, readInt("SELECT count(*) FROM cells"));
    }

    @Test
    void aLevelOutsideTheStoredRangeIsRefused() {
        int lowest = 2;

        try (SqliteCellStore store = SqliteCellStore.open(folder, lowest)) {
            Cell tooFine = Cell.blank(CellKey.pack(lowest - 1, 0, 0, 0));
            Cell tooCoarse = Cell.blank(CellKey.pack(DetailLevel.MAX + 1, 0, 0, 0));

            assertThrows(IllegalArgumentException.class, () -> store.write(tooFine));
            assertThrows(IllegalArgumentException.class, () -> store.write(tooCoarse));
            store.write(Cell.blank(CellKey.pack(lowest, 0, 0, 0)));
        }
    }

    @Test
    void dictionaryEntriesComeBackInIdOrderUnderTheirOwnName() {
        try (SqliteCellStore store = SqliteCellStore.open(folder, LOWEST_LEVEL)) {
            store.putDictionaryEntry(BLOCKS, 2, "minecraft:grass_block");
            store.putDictionaryEntry(BLOCKS, 0, "minecraft:air");
            store.putDictionaryEntry(BLOCKS, 1, "minecraft:stone");
            store.putDictionaryEntry(BIOMES, 0, "minecraft:plains");
        }

        List<String> blocks = new ArrayList<>();
        List<String> biomes = new ArrayList<>();
        Dictionary<String> dictionary = new Dictionary<>((id, value) -> {
        });

        try (SqliteCellStore store = SqliteCellStore.open(folder, LOWEST_LEVEL)) {
            store.readDictionary(BLOCKS, (id, value) -> blocks.add(id + "=" + value));
            store.readDictionary(BIOMES, (id, value) -> biomes.add(id + "=" + value));
            store.readDictionary(BLOCKS, dictionary::load);
        }

        assertEquals(List.of("0=minecraft:air", "1=minecraft:stone", "2=minecraft:grass_block"), blocks);
        assertEquals(List.of("0=minecraft:plains"), biomes);
        assertEquals("minecraft:stone", dictionary.value(1));
        assertEquals(3, dictionary.size());
    }

    @Test
    void closingAStoreOfMostlyFreePagesShrinksTheFile() throws SQLException, IOException {
        givenThePageSize(SPARSE_PAGE_SIZE);
        Random random = new Random(20260909L);

        try (SqliteCellStore store = SqliteCellStore.open(folder, LOWEST_LEVEL)) {
            for (int cell = 0; cell < SPARSE_CELLS; cell++) {
                store.write(noisyCell(CellKey.pack(0, cell, 0, 0), random));
            }
        }

        long grown = Files.size(file());

        try (SqliteCellStore store = SqliteCellStore.open(folder, LOWEST_LEVEL)) {
            for (int cell = 0; cell < SPARSE_CELLS; cell++) {
                store.delete(CellKey.pack(0, cell, 0, 0));
            }
        }

        assertTrue(grown > SPARSE_GROWN_BYTES, "the store never grew: " + grown + " bytes");
        assertTrue(Files.size(file()) < SPARSE_VACUUMED_BYTES,
                "the store was not vacuumed: " + Files.size(file()) + " bytes");
    }

    @Test
    void reopeningAtAHigherLevelDropsTheCellsBelowItAndShrinksTheFile() throws SQLException, IOException {
        givenThePageSize(SPARSE_PAGE_SIZE);
        Random random = new Random(20260916L);
        int raised = 1;

        try (SqliteCellStore store = SqliteCellStore.open(folder, LOWEST_LEVEL)) {
            for (int cell = 0; cell < SPARSE_CELLS; cell++) {
                store.write(noisyCell(CellKey.pack(0, cell, 0, 0), random));
            }
            for (int level = raised; level <= DetailLevel.MAX; level++) {
                store.write(Cell.blank(CellKey.pack(level, 0, 0, 0)));
            }
        }

        long grown = Files.size(file());

        try (SqliteCellStore store = SqliteCellStore.open(folder, raised)) {
            for (int cell = 0; cell < SPARSE_CELLS; cell++) {
                assertNull(store.read(CellKey.pack(0, cell, 0, 0)));
            }
            for (int level = raised; level <= DetailLevel.MAX; level++) {
                assertNotNull(store.read(CellKey.pack(level, 0, 0, 0)));
            }
        }

        assertTrue(grown > SPARSE_GROWN_BYTES, "the store never grew: " + grown + " bytes");
        assertTrue(Files.size(file()) < SPARSE_VACUUMED_BYTES,
                "the store was not vacuumed: " + Files.size(file()) + " bytes");
        assertEquals(DetailLevel.MAX - raised + 1, readInt("SELECT count(*) FROM cells"));
    }

    private static Cell noisyCell(long key, Random random) {
        Cell cell = Cell.blank(key);
        for (int y = 0; y < DetailLevel.VOXELS_PER_SIDE; y++) {
            for (int z = 0; z < DetailLevel.VOXELS_PER_SIDE; z++) {
                for (int x = 0; x < DetailLevel.VOXELS_PER_SIDE; x++) {
                    cell.set(x, y, z, VoxelEntry.pack(random.nextInt(1 << 24) + 1, random.nextInt(1 << 12),
                            VoxelEntry.light(random.nextInt(16), random.nextInt(16))));
                }
            }
        }

        return cell;
    }

    private void givenThePageSize(int pageSize) throws SQLException {
        try (Connection connection = rawConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA page_size = " + pageSize);
            statement.execute("CREATE TABLE sizing (x INTEGER)");
            statement.execute("DROP TABLE sizing");
        }
    }

    private Path file() {
        return folder.resolve(SqliteCellStore.FILE_NAME);
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = rawConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int readInt(String sql) throws SQLException {
        try (Connection connection = rawConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private String readString(String sql) throws SQLException {
        try (Connection connection = rawConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getString(1);
        }
    }

    private Connection rawConnection() throws SQLException {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + file());
        return source.getConnection();
    }
}
