package com.eminus.store;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import com.eminus.Eminus;
import com.eminus.cell.DetailLevel;

final class StoreSchema {
    private static final int FRESH_VERSION = 0;
    static final int OLDER_COLUMN_FORMAT = 0;
    private static final String LOWEST_FIELD = "lowest";
    private static final String FORMAT_FIELD = "format";
    private static final String FIELD_NAME = "name";

    private static final String CREATE_CELLS = "CREATE TABLE IF NOT EXISTS cells"
            + " (key INTEGER PRIMARY KEY, occupancy INTEGER NOT NULL, data BLOB NOT NULL)";
    private static final String CREATE_DICTIONARY = "CREATE TABLE IF NOT EXISTS dictionary"
            + " (name TEXT NOT NULL, id INTEGER NOT NULL, value TEXT NOT NULL, PRIMARY KEY (name, id))";
    private static final String CREATE_COLUMNS = "CREATE TABLE IF NOT EXISTS columns (chunk INTEGER PRIMARY KEY)";
    private static final String ADD_LOWEST =
            "ALTER TABLE columns ADD COLUMN " + LOWEST_FIELD + " INTEGER NOT NULL DEFAULT " + DetailLevel.MIN;
    private static final String ADD_FORMAT =
            "ALTER TABLE columns ADD COLUMN " + FORMAT_FIELD + " INTEGER NOT NULL DEFAULT " + OLDER_COLUMN_FORMAT;
    private static final String READ_COLUMN_FIELDS = "PRAGMA table_info(columns)";
    private static final String READ_VERSION = "PRAGMA user_version";
    private static final String WRITE_VERSION = "PRAGMA user_version = ";

    private StoreSchema() {
    }

    static boolean prepare(Connection connection, Path file) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            int version = version(statement);
            if (version == StoreFormat.VERSION) {
                return false;
            }

            if (version < FRESH_VERSION || version > StoreFormat.VERSION) {
                throw new StoreException("The cell store " + file + " is format version " + version
                        + ", this build reads version " + StoreFormat.VERSION, null);
            }

            bringToCurrent(connection, statement);
            if (version == FRESH_VERSION) {
                return false;
            }

            Eminus.LOGGER.info("The cell store {} was format version {}; its cells are kept and each column is "
                    + "rebuilt as version {} when it is ingested again", file, version, StoreFormat.VERSION);
            return true;
        }
    }

    private static int version(Statement statement) throws SQLException {
        try (ResultSet rows = statement.executeQuery(READ_VERSION)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static void bringToCurrent(Connection connection, Statement statement) throws SQLException {
        connection.setAutoCommit(false);
        try {
            statement.execute(CREATE_CELLS);
            statement.execute(CREATE_DICTIONARY);
            statement.execute(CREATE_COLUMNS);
            Set<String> fields = columnFields(statement);
            if (!fields.contains(LOWEST_FIELD)) {
                statement.execute(ADD_LOWEST);
            }

            if (!fields.contains(FORMAT_FIELD)) {
                statement.execute(ADD_FORMAT);
            }

            statement.execute(WRITE_VERSION + StoreFormat.VERSION);
            connection.commit();
        } catch (SQLException failure) {
            connection.rollback();
            throw failure;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static Set<String> columnFields(Statement statement) throws SQLException {
        Set<String> fields = new HashSet<>();
        try (ResultSet rows = statement.executeQuery(READ_COLUMN_FIELDS)) {
            while (rows.next()) {
                fields.add(rows.getString(FIELD_NAME));
            }
        }

        return fields;
    }
}
