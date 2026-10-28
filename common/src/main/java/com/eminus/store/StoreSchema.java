package com.eminus.store;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.eminus.Eminus;
import com.eminus.cell.CellKey;
import com.eminus.cell.ColumnCoverage;
import com.eminus.cell.DetailLevel;
import com.eminus.cell.OccupancyMask;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

final class StoreSchema {
    private static final int FRESH_VERSION = 0;

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
    private static final String DROP_CELLS = "DROP TABLE IF EXISTS cells";
    private static final String DROP_DICTIONARY = "DROP TABLE IF EXISTS dictionary";
    private static final String DROP_COLUMNS = "DROP TABLE IF EXISTS columns";
    private static final String READ_VERSION = "PRAGMA user_version";
    private static final String WRITE_VERSION = "PRAGMA user_version = ";
    private static final int CHUNK_SHIFT = 4;
    private static final int CHUNKS_PER_FINEST_CELL = DetailLevel.blocksPerCell(DetailLevel.MIN) >> CHUNK_SHIFT;

    private StoreSchema() {
    }

    static void prepare(Connection connection, Path file, int lowestStoredLevel) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            int version = version(statement);
            if (version == StoreFormat.VERSION) {
                return;
            }

            if (version == StoreFormat.BEFORE_LOWEST) {
                migrateToLowest(connection, statement, lowestStoredLevel, file);
                return;
            }

            dropOutdated(statement, version, file);
            create(statement);
        }
    }

    private static int version(Statement statement) throws SQLException {
        try (ResultSet rows = statement.executeQuery(READ_VERSION)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static void migrateToLowest(Connection connection, Statement statement, int lowestStoredLevel, Path file)
            throws SQLException {
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
    }

    private static void dropOutdated(Statement statement, int version, Path file) throws SQLException {
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
    }

    private static void create(Statement statement) throws SQLException {
        statement.execute(CREATE_CELLS);
        statement.execute(CREATE_DICTIONARY);
        statement.execute(CREATE_COLUMNS);
        statement.execute(WRITE_VERSION + StoreFormat.VERSION);
    }

    private static void addLowest(Connection connection, Statement statement, int lowestStoredLevel, Path file)
            throws SQLException {
        statement.execute(ADD_LOWEST);
        if (lowestStoredLevel != DetailLevel.MIN) {
            return;
        }

        LongArrayList bare = bareColumns(statement, finestColumns(connection));
        raiseLowest(connection, bare);
        Eminus.LOGGER.info("The cell store {} kept {} covered columns with no level-0 cell from level {} up",
                file, bare.size(), DetailLevel.MIN + 1);
    }

    private static LongOpenHashSet finestColumns(Connection connection) throws SQLException {
        LongOpenHashSet held = new LongOpenHashSet();
        try (PreparedStatement select = connection.prepareStatement(SELECT_FINEST_OCCUPANCY)) {
            select.setLong(1, SqliteCellStore.lowestKeyAt(DetailLevel.MIN + 1));
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

        return held;
    }

    private static LongArrayList bareColumns(Statement statement, LongOpenHashSet held) throws SQLException {
        LongArrayList bare = new LongArrayList();
        try (ResultSet rows = statement.executeQuery(SqliteCellStore.SELECT_COLUMNS)) {
            while (rows.next()) {
                long chunk = rows.getLong(1);
                if (!held.contains(chunk)) {
                    bare.add(chunk);
                }
            }
        }

        return bare;
    }

    private static void raiseLowest(Connection connection, LongArrayList bare) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(SET_LOWEST)) {
            for (int at = 0; at < bare.size(); at++) {
                update.setInt(1, DetailLevel.MIN + 1);
                update.setLong(2, bare.getLong(at));
                update.addBatch();
            }

            update.executeBatch();
        }
    }
}
