package com.eminus.store;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.eminus.Eminus;
import com.eminus.cell.Cell;
import com.eminus.cell.CellKey;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.Dictionary;

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
    private static final String READ_VERSION = "PRAGMA user_version";
    private static final String WRITE_VERSION = "PRAGMA user_version = ";
    private static final String READ_FREE_PAGES = "PRAGMA freelist_count";
    private static final String READ_SQLITE_VERSION = "SELECT sqlite_version()";
    private static final String VACUUM = "VACUUM";
    private static final String SELECT_CELL = "SELECT data FROM cells WHERE key = ?";
    private static final String UPSERT_CELL = "INSERT OR REPLACE INTO cells (key, occupancy, data) VALUES (?, ?, ?)";
    private static final String DELETE_CELL = "DELETE FROM cells WHERE key = ?";
    private static final String UPSERT_DICTIONARY =
            "INSERT OR REPLACE INTO dictionary (name, id, value) VALUES (?, ?, ?)";
    private static final String SELECT_DICTIONARY = "SELECT id, value FROM dictionary WHERE name = ? ORDER BY id";

    private final Path file;
    private final int lowestStoredLevel;
    private final Connection connection;
    private final PreparedStatement selectCell;
    private final PreparedStatement upsertCell;
    private final PreparedStatement deleteCell;
    private final PreparedStatement upsertDictionary;
    private final PreparedStatement selectDictionary;

    private boolean closed;

    public static SqliteCellStore open(Path folder, int lowestStoredLevel) {
        Path file = folder.resolve(FILE_NAME);
        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);

        SQLiteDataSource source = new SQLiteDataSource(config);
        source.setUrl(URL_PREFIX + file);

        Connection connection = null;
        try {
            connection = source.getConnection();
            prepareSchema(connection, file);
            SqliteCellStore store = new SqliteCellStore(file, lowestStoredLevel, connection);
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

    private SqliteCellStore(Path file, int lowestStoredLevel, Connection connection) throws SQLException {
        this.file = file;
        this.lowestStoredLevel = lowestStoredLevel;
        this.connection = connection;
        selectCell = connection.prepareStatement(SELECT_CELL);
        upsertCell = connection.prepareStatement(UPSERT_CELL);
        deleteCell = connection.prepareStatement(DELETE_CELL);
        upsertDictionary = connection.prepareStatement(UPSERT_DICTIONARY);
        selectDictionary = connection.prepareStatement(SELECT_DICTIONARY);
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
    public synchronized void close() {
        if (closed) {
            return;
        }

        closed = true;
        try {
            selectCell.close();
            upsertCell.close();
            deleteCell.close();
            upsertDictionary.close();
            selectDictionary.close();
            vacuumWhenSparse();
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
        if (level < lowestStoredLevel || level > DetailLevel.MAX) {
            throw new IllegalArgumentException("Cell level " + level + " is outside the stored range "
                    + lowestStoredLevel + ".." + DetailLevel.MAX);
        }
    }

    private void vacuumWhenSparse() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery(READ_FREE_PAGES)) {
                if (!rows.next() || rows.getInt(1) <= VACUUM_FREE_PAGES) {
                    return;
                }
            }

            statement.execute(VACUUM);
        }
    }

    private static void prepareSchema(Connection connection, Path file) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            int version;
            try (ResultSet rows = statement.executeQuery(READ_VERSION)) {
                rows.next();
                version = rows.getInt(1);
            }

            if (version == StoreFormat.VERSION) {
                return;
            }

            if (version != FRESH_VERSION) {
                throw new StoreException("The cell store " + file + " is format version " + version
                        + ", this build reads version " + StoreFormat.VERSION, null);
            }

            statement.execute(CREATE_CELLS);
            statement.execute(CREATE_DICTIONARY);
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
