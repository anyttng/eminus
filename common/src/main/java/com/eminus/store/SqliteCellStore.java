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

import com.uber.nullaway.annotations.Initializer;

import org.jspecify.annotations.Nullable;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

public final class SqliteCellStore implements CellStore {
    public static final String FILE_NAME = "cells.db";

    private static final int VACUUM_FREE_PAGES = 4096;
    private static final int PAGE_SIZE_BYTES = 1024;

    private static final String URL_PREFIX = "jdbc:sqlite:";
    private static final String SET_PAGE_SIZE = "PRAGMA page_size = " + PAGE_SIZE_BYTES;
    private static final String USE_WAL = "PRAGMA journal_mode = WAL";
    private static final String RAISE_LOWEST = "UPDATE columns SET lowest = ? WHERE lowest < ?";
    private static final String READ_FREE_PAGES = "PRAGMA freelist_count";
    private static final String READ_SQLITE_VERSION = "SELECT sqlite_version()";
    private static final String VACUUM = "VACUUM";
    private static final String CHECKPOINT = "PRAGMA wal_checkpoint(FULL)";
    private static final String SELECT_CELL = "SELECT data FROM cells WHERE key = ?";
    private static final String UPSERT_CELL = "INSERT OR REPLACE INTO cells (key, occupancy, data) VALUES (?, ?, ?)";
    private static final String DELETE_CELL = "DELETE FROM cells WHERE key = ?";
    private static final String DELETE_BELOW_LEVEL = "DELETE FROM cells WHERE key < ?";
    private static final String UPSERT_DICTIONARY =
            "INSERT OR REPLACE INTO dictionary (name, id, value) VALUES (?, ?, ?)";
    private static final String SELECT_DICTIONARY = "SELECT id, value FROM dictionary WHERE name = ? ORDER BY id";
    private static final String INSERT_COLUMN = "INSERT INTO columns (chunk, lowest, format) VALUES (?, ?, ?)"
            + " ON CONFLICT (chunk) DO UPDATE SET lowest = min(lowest, excluded.lowest), format = excluded.format";
    private static final String SELECT_COLUMNS = "SELECT chunk, lowest, format FROM columns";
    private static final String CREATE_META =
            "CREATE TABLE IF NOT EXISTS meta (name TEXT PRIMARY KEY, value INTEGER NOT NULL)";
    private static final String SELECT_META = "SELECT value FROM meta WHERE name = ?";
    private static final String UPSERT_META = "INSERT OR REPLACE INTO meta (name, value) VALUES (?, ?)";
    private static final String ADD_META = "UPDATE meta SET value = value + ? WHERE name = ?";
    private static final String DELETE_META = "DELETE FROM meta WHERE name = ?";
    private static final String CREATE_BEAMS =
            "CREATE TABLE IF NOT EXISTS beams (pos INTEGER PRIMARY KEY, sections BLOB NOT NULL)";
    private static final String UPSERT_BEAM = "INSERT OR REPLACE INTO beams (pos, sections) VALUES (?, ?)";
    private static final String DELETE_BEAM = "DELETE FROM beams WHERE pos = ?";
    private static final String SELECT_BEAMS = "SELECT pos, sections FROM beams";
    private static final String SELECT_LOWEST_KEY = "SELECT min(key) FROM cells";
    private static final String RAISED_LEVEL_KEY = "raised_level";
    private static final String PLAYED_MILLIS_KEY = "raised_played_millis";
    private static final String OPEN_KEY = "open";
    private static final long OPEN = 1L;
    private static final String OUTDATE_COLUMNS = "UPDATE columns SET format = " + StoreSchema.OLDER_COLUMN_FORMAT;
    private static final long MILLIS_PER_MINUTE = 60_000L;

    private final Path file;
    private final int heldLevel;
    private final boolean keepsCellsBelow;
    private final boolean upgraded;
    private final LongSupplier clock;
    private final long openedAt;
    private final Connection connection;
    private PreparedStatement selectCell;
    private PreparedStatement upsertCell;
    private PreparedStatement deleteCell;
    private PreparedStatement upsertDictionary;
    private PreparedStatement selectDictionary;
    private PreparedStatement insertColumn;
    private PreparedStatement selectColumns;
    private PreparedStatement upsertBeam;
    private PreparedStatement deleteBeam;
    private PreparedStatement selectBeams;

    private boolean closed;
    private boolean unclean;

    public static SqliteCellStore open(Path folder, int lowestStoredLevel, long dropGraceMillis, LongSupplier clock) {
        Path file = folder.resolve(FILE_NAME);
        SQLiteConfig config = new SQLiteConfig();
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);

        SQLiteDataSource source = new SQLiteDataSource(config);
        source.setUrl(URL_PREFIX + file);

        Connection connection = null;
        try {
            connection = source.getConnection();
            try (Statement statement = connection.createStatement()) {
                statement.execute(SET_PAGE_SIZE);
                statement.execute(USE_WAL);
            }

            boolean upgraded = StoreSchema.prepare(connection, file);
            try (Statement statement = connection.createStatement()) {
                statement.execute(CREATE_META);
                statement.execute(CREATE_BEAMS);
            }

            int heldLevel = settleHeldLevel(connection, lowestStoredLevel, dropGraceMillis, file);
            outdateColumnsLeftOpen(connection, file);
            SqliteCellStore store = new SqliteCellStore(file, heldLevel, heldLevel < lowestStoredLevel, upgraded,
                    clock, connection);
            writeMeta(connection, OPEN_KEY, OPEN);
            try (Statement statement = connection.createStatement()) {
                statement.execute(CHECKPOINT);
            }

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

    private SqliteCellStore(Path file, int heldLevel, boolean keepsCellsBelow, boolean upgraded, LongSupplier clock,
            Connection connection) throws SQLException {
        this.file = file;
        this.heldLevel = heldLevel;
        this.keepsCellsBelow = keepsCellsBelow;
        this.upgraded = upgraded;
        this.clock = clock;
        this.openedAt = clock.getAsLong();
        this.connection = connection;
        prepareStatements();
    }

    @Initializer
    private void prepareStatements() throws SQLException {
        selectCell = connection.prepareStatement(SELECT_CELL);
        upsertCell = connection.prepareStatement(UPSERT_CELL);
        deleteCell = connection.prepareStatement(DELETE_CELL);
        upsertDictionary = connection.prepareStatement(UPSERT_DICTIONARY);
        selectDictionary = connection.prepareStatement(SELECT_DICTIONARY);
        insertColumn = connection.prepareStatement(INSERT_COLUMN);
        selectColumns = connection.prepareStatement(SELECT_COLUMNS);
        upsertBeam = connection.prepareStatement(UPSERT_BEAM);
        deleteBeam = connection.prepareStatement(DELETE_BEAM);
        selectBeams = connection.prepareStatement(SELECT_BEAMS);
    }

    private void closeStatements() throws SQLException {
        selectCell.close();
        upsertCell.close();
        deleteCell.close();
        upsertDictionary.close();
        selectDictionary.close();
        insertColumn.close();
        selectColumns.close();
        upsertBeam.close();
        deleteBeam.close();
        selectBeams.close();
    }

    public int heldLevel() {
        return heldLevel;
    }

    public boolean upgraded() {
        return upgraded;
    }

    @Override
    public @Nullable Cell read(long key) {
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
    public void write(long key, int occupancy, byte[] record) {
        checkLevel(key);

        synchronized (this) {
            try {
                upsertCell.setLong(1, key);
                upsertCell.setInt(2, occupancy);
                upsertCell.setBytes(3, record);
                upsertCell.executeUpdate();
            } catch (SQLException failure) {
                throw new StoreException("Could not write cell " + key + " to " + file, failure);
            }
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
            insertColumn.setInt(3, StoreFormat.VERSION);
            insertColumn.executeUpdate();
        } catch (SQLException failure) {
            throw new StoreException("Could not write column " + chunk + " to " + file, failure);
        }
    }

    @Override
    public synchronized void readColumns(ColumnCoverage.Loader into) {
        try (ResultSet rows = selectColumns.executeQuery()) {
            while (rows.next()) {
                into.load(rows.getLong(1), rows.getInt(2), rows.getInt(3) >= StoreFormat.VERSION);
            }
        } catch (SQLException failure) {
            throw new StoreException("Could not read the covered columns from " + file, failure);
        }
    }

    @Override
    public synchronized void putBeam(long pos, byte[] sections) {
        try {
            upsertBeam.setLong(1, pos);
            upsertBeam.setBytes(2, sections);
            upsertBeam.executeUpdate();
        } catch (SQLException failure) {
            throw new StoreException("Could not write the beam at " + pos + " to " + file, failure);
        }
    }

    @Override
    public synchronized void deleteBeam(long pos) {
        try {
            deleteBeam.setLong(1, pos);
            deleteBeam.executeUpdate();
        } catch (SQLException failure) {
            throw new StoreException("Could not delete the beam at " + pos + " from " + file, failure);
        }
    }

    @Override
    public synchronized void readBeams(Rows into) {
        try (ResultSet rows = selectBeams.executeQuery()) {
            while (rows.next()) {
                into.row(rows.getLong(1), rows.getBytes(2));
            }
        } catch (SQLException failure) {
            throw new StoreException("Could not read the beams from " + file, failure);
        }
    }

    @Override
    public synchronized void maintain() {
        if (closed) {
            return;
        }

        try {
            if (!keepsCellsBelow) {
                dropBelowLevel(connection, heldLevel, file);
                deleteMeta(connection, RAISED_LEVEL_KEY);
                deleteMeta(connection, PLAYED_MILLIS_KEY);
            }

            if (sparse(connection)) {
                vacuumBetweenStatements();
            }
        } catch (SQLException failure) {
            throw new StoreException("Could not maintain the cell store " + file, failure);
        }
    }

    @Override
    public synchronized void leaveUnclean() {
        unclean = true;
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

            if (!unclean) {
                deleteMeta(connection, OPEN_KEY);
            }

            closeStatements();
            if (sparse(connection)) {
                vacuum(connection);
            }

            connection.close();
        } catch (SQLException failure) {
            closeQuietly(connection);
            throw new StoreException("Could not close the cell store " + file, failure);
        }
    }

    private synchronized byte @Nullable [] selectRecord(long key) {
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
        if (held >= lowestStoredLevel) {
            deleteMeta(connection, RAISED_LEVEL_KEY);
            deleteMeta(connection, PLAYED_MILLIS_KEY);
            return lowestStoredLevel;
        }

        long played = playedAt(connection, lowestStoredLevel);
        if (played >= dropGraceMillis) {
            return lowestStoredLevel;
        }

        writeMeta(connection, RAISED_LEVEL_KEY, lowestStoredLevel);
        writeMeta(connection, PLAYED_MILLIS_KEY, played);
        Eminus.LOGGER.info("Kept the cells from level {} below level {} in {}, still written by ingest: {} of"
                + " {} minutes played at it", held, lowestStoredLevel, file, played / MILLIS_PER_MINUTE,
                dropGraceMillis / MILLIS_PER_MINUTE);
        return held;
    }

    private static void outdateColumnsLeftOpen(Connection connection, Path file) throws SQLException {
        if (readMeta(connection, OPEN_KEY) == null) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(OUTDATE_COLUMNS);
        }

        Eminus.LOGGER.info("The cell store {} was not closed cleanly; its cells are kept and each column merges every "
                + "level when it is ingested again", file);
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

    private static @Nullable Long readMeta(Connection connection, String name) throws SQLException {
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

    static long lowestKeyAt(int level) {
        return CellKey.pack(level, CellKey.MIN_HORIZONTAL, CellKey.MIN_VERTICAL, CellKey.MIN_HORIZONTAL);
    }

    private static void dropBelowLevel(Connection connection, int lowestStoredLevel, Path file) throws SQLException {
        deleteBelow(connection, lowestStoredLevel, file);
        raiseLowest(connection, lowestStoredLevel);
    }

    private static void deleteBelow(Connection connection, int lowestStoredLevel, Path file) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DELETE_BELOW_LEVEL)) {
            statement.setLong(1, lowestKeyAt(lowestStoredLevel));
            int dropped = statement.executeUpdate();
            if (dropped > 0) {
                Eminus.LOGGER.info("Dropped {} cells below level {} from {}", dropped, lowestStoredLevel, file);
            }
        }
    }

    private static void raiseLowest(Connection connection, int lowestStoredLevel) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(RAISE_LOWEST)) {
            statement.setInt(1, lowestStoredLevel);
            statement.setInt(2, lowestStoredLevel);
            statement.executeUpdate();
        }
    }

    private void vacuumBetweenStatements() throws SQLException {
        closeStatements();
        try {
            vacuum(connection);
        } finally {
            prepareStatements();
        }
    }

    private static boolean sparse(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(READ_FREE_PAGES)) {
            return rows.next() && rows.getInt(1) > VACUUM_FREE_PAGES;
        }
    }

    private static void vacuum(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(VACUUM);
        }
    }

    private static String sqliteVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery(READ_SQLITE_VERSION)) {
                return rows.next() ? rows.getString(1) : "";
            }
        }
    }

    private static void closeQuietly(@Nullable Connection connection) {
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
