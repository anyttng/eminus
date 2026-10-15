package com.eminus.store;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.LongSupplier;

import com.eminus.Eminus;
import com.eminus.cell.Cell;
import com.eminus.cell.CellKey;
import com.eminus.cell.ColumnCoverage;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.Dictionary;
import com.eminus.cell.OccupancyMask;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

public final class SqliteCellStore implements CellStore {
    public static final String FILE_NAME = "cells.db";

    private static final int FRESH_VERSION = 0;
    private static final int VACUUM_FREE_PAGES = 4096;

    private static final String URL_PREFIX = "jdbc:sqlite:";
    private static final String CREATE_CELLS =
            "CREATE TABLE cells (key INTEGER PRIMARY KEY, occupancy INTEGER NOT NULL, data BLOB NOT NULL)";
    private static final String CREATE_DICTIONARY =
            "CREATE TABLE dictionary (name TEXT NOT NULL, id INTEGER NOT NULL, value TEXT NOT NULL,"
                    + " PRIMARY KEY (name, id))";
    private static final String CREATE_COLUMNS =
            "CREATE TABLE columns (chunk INTEGER PRIMARY KEY, lowest INTEGER NOT NULL)";
    private static final String ADD_LOWEST = "ALTER TABLE columns ADD COLUMN lowest INTEGER NOT NULL DEFAULT 0";
    private static final String SELECT_FINEST_OCCUPANCY = "SELECT key, occupancy FROM cells WHERE key < ?";
    private static final String SET_LOWEST = "UPDATE columns SET lowest = ? WHERE chunk = ?";
    private static final String RAISE_LOWEST = "UPDATE columns SET lowest = ? WHERE lowest < ?";
    private static final String DROP_CELLS = "DROP TABLE IF EXISTS cells";
    private static final String DROP_DICTIONARY = "DROP TABLE IF EXISTS dictionary";
    private static final String DROP_COLUMNS = "DROP TABLE IF EXISTS columns";
    private static final String READ_VERSION = "PRAGMA user_version";
    private static final String WRITE_VERSION = "PRAGMA user_version = ";
    private static final String READ_FREE_PAGES = "PRAGMA freelist_count";
    private static final String READ_SQLITE_VERSION = "SELECT sqlite_version()";
    private static final String VACUUM = "VACUUM";
    private static final String SELECT_CELL = "SELECT data FROM cells WHERE key = ?";
    private static final String UPSERT_CELL = "INSERT OR REPLACE INTO cells (key, occupancy, data) VALUES (?, ?, ?)";
    private static final String DELETE_CELL = "DELETE FROM cells WHERE key = ?";
    private static final String DELETE_BELOW_LEVEL = "DELETE FROM cells WHERE key < ?";
    private static final String UPSERT_DICTIONARY =
            "INSERT OR REPLACE INTO dictionary (name, id, value) VALUES (?, ?, ?)";
    private static final String SELECT_DICTIONARY = "SELECT id, value FROM dictionary WHERE name = ? ORDER BY id";
    private static final String INSERT_COLUMN = "INSERT INTO columns (chunk, lowest) VALUES (?, ?)"
            + " ON CONFLICT (chunk) DO UPDATE SET lowest = min(lowest, excluded.lowest)";
    private static final String SELECT_COLUMNS = "SELECT chunk, lowest FROM columns";
    private static final String CREATE_META =
            "CREATE TABLE IF NOT EXISTS meta (name TEXT PRIMARY KEY, value INTEGER NOT NULL)";
    private static final String SELECT_META = "SELECT value FROM meta WHERE name = ?";
    private static final String UPSERT_META = "INSERT OR REPLACE INTO meta (name, value) VALUES (?, ?)";
    private static final String ADD_META = "UPDATE meta SET value = value + ? WHERE name = ?";
    private static final String DELETE_META = "DELETE FROM meta WHERE name = ?";
    private static final String SELECT_LOWEST_KEY = "SELECT min(key) FROM cells";
    private static final String RAISED_LEVEL_KEY = "raised_level";
    private static final String PLAYED_MILLIS_KEY = "raised_played_millis";
    private static final long MILLIS_PER_MINUTE = 60_000L;
    private static final int CHUNK_SHIFT = 4;
    private static final int CHUNKS_PER_FINEST_CELL = DetailLevel.blocksPerCell(DetailLevel.MIN) >> CHUNK_SHIFT;

    private final Path file;
    private final int heldLevel;
    private final boolean keepsCellsBelow;
    private final LongSupplier clock;
    private final long openedAt;
    private final Connection connection;
    private final PreparedStatement selectCell;
    private final PreparedStatement upsertCell;
    private final PreparedStatement deleteCell;
    private final PreparedStatement upsertDictionary;
    private final PreparedStatement selectDictionary;
    private final PreparedStatement insertColumn;
    private final PreparedStatement selectColumns;

    private boolean closed;

    public static SqliteCellStore open(Path folder, int lowestStoredLevel, long dropGraceMillis, LongSupplier clock) {
        Path file = folder.resolve(FILE_NAME);
        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);

        SQLiteDataSource source = new SQLiteDataSource(config);
        source.setUrl(URL_PREFIX + file);

        Connection connection = null;
        try {
            connection = source.getConnection();
            prepareSchema(connection, file, lowestStoredLevel);
            try (Statement statement = connection.createStatement()) {
                statement.execute(CREATE_META);
            }

            int heldLevel = settleHeldLevel(connection, lowestStoredLevel, dropGraceMillis, file);
            vacuumWhenSparse(connection);
            SqliteCellStore store = new SqliteCellStore(file, heldLevel, heldLevel < lowestStoredLevel, clock,
                    connection);
            Eminus.LOGGER.info("Cell store opened at {} on SQLite {}", file, sqliteVersion(connection));
            return store;
        } catch (SQLException failure) {
            closeQuietly(connection);
            throw new StoreException("Could not open the cell store " + file, failure);
        } catch (RuntimeException failure) {
            closeQuietly(connection);
            throw failure;
        }
    }

    private SqliteCellStore(Path file, int heldLevel, boolean keepsCellsBelow, LongSupplier clock,
            Connection connection) throws SQLException {
        this.file = file;
        this.heldLevel = heldLevel;
        this.keepsCellsBelow = keepsCellsBelow;
        this.clock = clock;
        this.openedAt = clock.getAsLong();
        this.connection = connection;
        selectCell = connection.prepareStatement(SELECT_CELL);
        upsertCell = connection.prepareStatement(UPSERT_CELL);
        deleteCell = connection.prepareStatement(DELETE_CELL);
        upsertDictionary = connection.prepareStatement(UPSERT_DICTIONARY);
        selectDictionary = connection.prepareStatement(SELECT_DICTIONARY);
        insertColumn = connection.prepareStatement(INSERT_COLUMN);
        selectColumns = connection.prepareStatement(SELECT_COLUMNS);
    }

    public int heldLevel() {
        return heldLevel;
    }

    @Override
    public synchronized Cell read(long key) {
        byte[] record = selectRecord(key);
        if (record == null) {
            return null;
        }

        try {
            return CellRecord.decode(key, record);
        } catch (RuntimeException damaged) {
            delete(key);
            Eminus.LOGGER.error("Damaged cell record at level {} ({}, {}, {}) deleted from {}: {}",
                    CellKey.level(key), CellKey.x(key), CellKey.y(key), CellKey.z(key), file, damaged.toString());
            return null;
        }
    }

    @Override
    public synchronized void write(Cell cell) {
        long key = cell.key();
        checkLevel(key);

        try {
            upsertCell.setLong(1, key);
            upsertCell.setInt(2, cell.occupancy());
            upsertCell.setBytes(3, CellRecord.encode(cell));
            upsertCell.executeUpdate();
        } catch (SQLException failure) {
            throw new StoreException("Could not write cell " + key + " to " + file, failure);
        }
    }

    @Override
    public synchronized void delete(long key) {
        try {
            deleteCell.setLong(1, key);
            deleteCell.executeUpdate();
        } catch (SQLException failure) {
            throw new StoreException("Could not delete cell " + key + " from " + file, failure);
        }
    }

    @Override
    public synchronized void putDictionaryEntry(String name, int id, String value) {
        try {
            upsertDictionary.setString(1, name);
            upsertDictionary.setInt(2, id);
            upsertDictionary.setString(3, value);
            upsertDictionary.executeUpdate();
        } catch (SQLException failure) {
            throw new StoreException("Could not write dictionary entry " + name + "/" + id + " to " + file, failure);
        }
    }

    @Override
    public synchronized void readDictionary(String name, Dictionary.Persistence<String> into) {
        try {
            selectDictionary.setString(1, name);
            try (ResultSet rows = selectDictionary.executeQuery()) {
                while (rows.next()) {
                    into.persist(rows.getInt(1), rows.getString(2));
                }
            }
        } catch (SQLException failure) {
            throw new StoreException("Could not read dictionary " + name + " from " + file, failure);
        }
    }

    @Override
    public synchronized void putColumn(long chunk, int lowestLevel) {
        try {
            insertColumn.setLong(1, chunk);
            insertColumn.setInt(2, lowestLevel);
            insertColumn.executeUpdate();
        } catch (SQLException failure) {
            throw new StoreException("Could not write column " + chunk + " to " + file, failure);
        }
    }

    @Override
    public synchronized void readColumns(ColumnCoverage.Persistence into) {
        try (ResultSet rows = selectColumns.executeQuery()) {
            while (rows.next()) {
                into.persist(rows.getLong(1), rows.getInt(2));
            }
        } catch (SQLException failure) {
            throw new StoreException("Could not read the covered columns from " + file, failure);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }

        closed = true;
        try {
            if (keepsCellsBelow) {
                addMeta(connection, PLAYED_MILLIS_KEY, clock.getAsLong() - openedAt);
            }

            selectCell.close();
            upsertCell.close();
            deleteCell.close();
            upsertDictionary.close();
            selectDictionary.close();
            insertColumn.close();
            selectColumns.close();
            vacuumWhenSparse(connection);
            connection.close();
        } catch (SQLException failure) {
            closeQuietly(connection);
            throw new StoreException("Could not close the cell store " + file, failure);
        }
    }

    private byte[] selectRecord(long key) {
        try {
            selectCell.setLong(1, key);
            try (ResultSet rows = selectCell.executeQuery()) {
                return rows.next() ? rows.getBytes(1) : null;
            }
        } catch (SQLException failure) {
            throw new StoreException("Could not read cell " + key + " from " + file, failure);
        }
    }

    private void checkLevel(long key) {
        int level = CellKey.level(key);
        if (level < heldLevel || level > DetailLevel.MAX) {
            throw new IllegalArgumentException("Cell level " + level + " is outside the stored range "
                    + heldLevel + ".." + DetailLevel.MAX);
        }
    }

    private static int settleHeldLevel(Connection connection, int lowestStoredLevel, long dropGraceMillis, Path file)
            throws SQLException {
        int held = lowestLevelHeld(connection, lowestStoredLevel);
        if (held < lowestStoredLevel) {
            long played = playedAt(connection, lowestStoredLevel);
            if (played < dropGraceMillis) {
                writeMeta(connection, RAISED_LEVEL_KEY, lowestStoredLevel);
                writeMeta(connection, PLAYED_MILLIS_KEY, played);
                Eminus.LOGGER.info("Kept the cells from level {} below level {} in {}, still written by ingest: {} of"
                        + " {} minutes played at it", held, lowestStoredLevel, file, played / MILLIS_PER_MINUTE,
                        dropGraceMillis / MILLIS_PER_MINUTE);
                return held;
            }
        }

        deleteMeta(connection, RAISED_LEVEL_KEY);
        deleteMeta(connection, PLAYED_MILLIS_KEY);
        dropBelowLevel(connection, lowestStoredLevel, file);
        return lowestStoredLevel;
    }

    private static int lowestLevelHeld(Connection connection, int lowestStoredLevel) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(SELECT_LOWEST_KEY)) {
            long key = rows.next() ? rows.getLong(1) : 0L;
            return rows.wasNull() ? lowestStoredLevel : CellKey.level(key);
        }
    }

    private static long playedAt(Connection connection, int lowestStoredLevel) throws SQLException {
        Long raised = readMeta(connection, RAISED_LEVEL_KEY);
        if (raised == null || raised != lowestStoredLevel) {
            return 0L;
        }

        Long played = readMeta(connection, PLAYED_MILLIS_KEY);
        return played == null ? 0L : played;
    }

    private static Long readMeta(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_META)) {
            statement.setString(1, name);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getLong(1) : null;
            }
        }
    }

    private static void writeMeta(Connection connection, String name, long value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_META)) {
            statement.setString(1, name);
            statement.setLong(2, value);
            statement.executeUpdate();
        }
    }

    private static void addMeta(Connection connection, String name, long value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(ADD_META)) {
            statement.setLong(1, value);
            statement.setString(2, name);
            statement.executeUpdate();
        }
    }

    private static void deleteMeta(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DELETE_META)) {
            statement.setString(1, name);
            statement.executeUpdate();
        }
    }

    private static long lowestKeyAt(int level) {
        return CellKey.pack(level, CellKey.MIN_HORIZONTAL, CellKey.MIN_VERTICAL, CellKey.MIN_HORIZONTAL);
    }

    private static void dropBelowLevel(Connection connection, int lowestStoredLevel, Path file) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DELETE_BELOW_LEVEL)) {
            statement.setLong(1, lowestKeyAt(lowestStoredLevel));
            int dropped = statement.executeUpdate();
            if (dropped > 0) {
                Eminus.LOGGER.info("Dropped {} cells below level {} from {}", dropped, lowestStoredLevel, file);
            }
        }

        try (PreparedStatement statement = connection.prepareStatement(RAISE_LOWEST)) {
            statement.setInt(1, lowestStoredLevel);
            statement.setInt(2, lowestStoredLevel);
            statement.executeUpdate();
        }
    }

    private static void addLowest(Connection connection, Statement statement, int lowestStoredLevel, Path file)
            throws SQLException {
        statement.execute(ADD_LOWEST);
        if (lowestStoredLevel != DetailLevel.MIN) {
            return;
        }

        LongOpenHashSet held = new LongOpenHashSet();
        try (PreparedStatement select = connection.prepareStatement(SELECT_FINEST_OCCUPANCY)) {
            select.setLong(1, lowestKeyAt(DetailLevel.MIN + 1));
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    long key = rows.getLong(1);
                    int occupancy = rows.getInt(2);
                    for (int octant = 0; octant < OccupancyMask.OCTANTS; octant++) {
                        if (OccupancyMask.isSet(occupancy, octant)) {
                            held.add(ColumnCoverage.pack(
                                    CellKey.x(key) * CHUNKS_PER_FINEST_CELL + OccupancyMask.x(octant),
                                    CellKey.z(key) * CHUNKS_PER_FINEST_CELL + OccupancyMask.z(octant)));
                        }
                    }
                }
            }
        }

        LongArrayList bare = new LongArrayList();
        try (ResultSet rows = statement.executeQuery(SELECT_COLUMNS)) {
            while (rows.next()) {
                long chunk = rows.getLong(1);
                if (!held.contains(chunk)) {
                    bare.add(chunk);
                }
            }
        }

        try (PreparedStatement update = connection.prepareStatement(SET_LOWEST)) {
            for (int at = 0; at < bare.size(); at++) {
                update.setInt(1, DetailLevel.MIN + 1);
                update.setLong(2, bare.getLong(at));
                update.addBatch();
            }

            update.executeBatch();
        }

        Eminus.LOGGER.info("The cell store {} kept {} covered columns with no level-0 cell from level {} up",
                file, bare.size(), DetailLevel.MIN + 1);
    }

    private static void vacuumWhenSparse(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery(READ_FREE_PAGES)) {
                if (!rows.next() || rows.getInt(1) <= VACUUM_FREE_PAGES) {
                    return;
                }
            }

            statement.execute(VACUUM);
        }
    }

    private static void prepareSchema(Connection connection, Path file, int lowestStoredLevel) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            int version;
            try (ResultSet rows = statement.executeQuery(READ_VERSION)) {
                rows.next();
                version = rows.getInt(1);
            }

            if (version == StoreFormat.VERSION) {
                return;
            }

            if (version == StoreFormat.BEFORE_LOWEST) {
                connection.setAutoCommit(false);
                try {
                    addLowest(connection, statement, lowestStoredLevel, file);
                    statement.execute(WRITE_VERSION + StoreFormat.VERSION);
                    connection.commit();
                } catch (SQLException failure) {
                    connection.rollback();
                    throw failure;
                } finally {
                    connection.setAutoCommit(true);
                }

                return;
            }

            if (version == StoreFormat.BEFORE_COVERAGE || version == StoreFormat.BEFORE_GAPS) {
                Eminus.LOGGER.warn("The cell store {} is format version {}, whose coarser voxels carry no height "
                        + "bounds; its cells are dropped and it is rebuilt as version {}", file, version,
                        StoreFormat.VERSION);
                statement.execute(DROP_CELLS);
                statement.execute(DROP_DICTIONARY);
                statement.execute(DROP_COLUMNS);
            } else if (version != FRESH_VERSION) {
                throw new StoreException("The cell store " + file + " is format version " + version
                        + ", this build reads version " + StoreFormat.VERSION, null);
            }

            statement.execute(CREATE_CELLS);
            statement.execute(CREATE_DICTIONARY);
            statement.execute(CREATE_COLUMNS);
            statement.execute(WRITE_VERSION + StoreFormat.VERSION);
        }
    }

    private static String sqliteVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery(READ_SQLITE_VERSION)) {
                return rows.next() ? rows.getString(1) : "";
            }
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }

        try {
            connection.close();
        } catch (SQLException ignored) {
            Eminus.LOGGER.warn("Could not close a cell store connection after a failed open.");
        }
    }
}
