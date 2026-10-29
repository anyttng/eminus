package com.eminus.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.eminus.beam.BeamRecord;
import com.eminus.beam.BeamSection;
import com.eminus.cell.Cell;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.VoxelEntry;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

@Timeout(60)
class SqliteBeamStoreTest {
    private static final int LOWEST_LEVEL = 0;
    private static final long NO_GRACE_MILLIS = 0L;
    private static final long BEACON = BlockPos.asLong(130, 64, -45);
    private static final long OTHER_BEACON = BlockPos.asLong(-2000, -12, 7);
    private static final byte[] RED_UP = BeamRecord.encode(List.of(new BeamSection(0xFFB02E26, 3),
            new BeamSection(0xFFF9FFFE, 200)));
    private static final byte[] WHITE = BeamRecord.encode(List.of(new BeamSection(0xFFF9FFFE, 1)));
    private static final long CELL = CellKey.pack(DetailLevel.MIN, 4, 2, -3);
    private static final long STONE = VoxelEntry.pack(17, 4, VoxelEntry.light(0, 0));
    private static final String COUNT_BEAMS_TABLE =
            "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'beams'";

    @TempDir
    Path folder;

    @Test
    void writtenBeamsComeBackAfterAReopen() {
        try (SqliteCellStore store = open()) {
            store.putBeam(BEACON, RED_UP);
            store.putBeam(OTHER_BEACON, WHITE);
        }

        Map<Long, byte[]> read = beams();
        assertEquals(2, read.size());
        assertArrayEquals(RED_UP, read.get(BEACON));
        assertArrayEquals(WHITE, read.get(OTHER_BEACON));
    }

    @Test
    void aBeamWrittenAgainKeepsOnlyItsNewestSections() {
        try (SqliteCellStore store = open()) {
            store.putBeam(BEACON, RED_UP);
            store.putBeam(BEACON, WHITE);
        }

        assertArrayEquals(WHITE, beams().get(BEACON));
    }

    @Test
    void aDeletedBeamIsGoneAfterAReopen() {
        try (SqliteCellStore store = open()) {
            store.putBeam(BEACON, RED_UP);
            store.putBeam(OTHER_BEACON, WHITE);
            store.deleteBeam(BEACON);
        }

        assertEquals(List.of(OTHER_BEACON), List.copyOf(beams().keySet()));
    }

    @Test
    void aFormatFourStoreWithoutTheBeamsTableKeepsItsCellsAndGainsTheTable() throws SQLException {
        Cell cell = Cell.blank(CELL);
        cell.set(1, 2, 3, STONE);
        try (SqliteCellStore store = open()) {
            store.write(cell);
        }
        execute("DROP TABLE beams");

        try (SqliteCellStore store = open()) {
            assertEquals(STONE, store.read(CELL).get(1, 2, 3));
            store.putBeam(BEACON, WHITE);
        }

        assertEquals(1, readInt(COUNT_BEAMS_TABLE));
        assertEquals(StoreFormat.VERSION, readInt("PRAGMA user_version"));
        assertTrue(beams().containsKey(BEACON));
    }

    private Map<Long, byte[]> beams() {
        Map<Long, byte[]> read = new HashMap<>();
        try (SqliteCellStore store = open()) {
            store.readBeams(read::put);
        }

        return read;
    }

    private SqliteCellStore open() {
        return SqliteCellStore.open(folder, LOWEST_LEVEL, NO_GRACE_MILLIS, () -> 0L);
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

    private Connection rawConnection() throws SQLException {
        SQLiteDataSource source = new SQLiteDataSource();
        source.setUrl("jdbc:sqlite:" + folder.resolve(SqliteCellStore.FILE_NAME));
        return source.getConnection();
    }
}
