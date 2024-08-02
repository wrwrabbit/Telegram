package org.telegram.SQLite;

import org.telegram.messenger.partisan.PartisanLog;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SQLiteDatabaseWrapper extends SQLiteDatabase {
    private final Set<String> sqlPrefixesForBothDB = new HashSet<>(Arrays.asList(
            "PRAGMA", "CREATE TABLE", "CREATE INDEX", "VACUUM", "DROP TABLE"
    ));
    private final Set<String> queryStartsForSpecificDB = new HashSet<>(Arrays.asList(
            "INSERT INTO", "UPDATE", "REPLACE INTO", "DELETE FROM", "SELECT"
    ));
    private final Set<String> onlyMemoryTables = new HashSet<>(Arrays.asList(
            "messages_v2", "chats", "contacts", "dialogs", "messages_holes"
    ));
    private enum SQL_FOR {
        REAL_DB,
        MEMORY_DB,
        BOTH_DB
    }

    private SQLiteDatabase realDatabase;
    private SQLiteDatabase memoryDatabase;

    public SQLiteDatabaseWrapper(String fileName) throws SQLiteException {
        super(fileName);
        realDatabase = new SQLiteDatabase(fileName);
        memoryDatabase = new SQLiteDatabase(":memory:");
        realDatabase.backup(memoryDatabase);
    }

    public long getSQLiteHandle() {
        return realDatabase.getSQLiteHandle();
    }

    public boolean tableExists(String tableName) throws SQLiteException {
        return realDatabase.tableExists(tableName);
    }

    private SQL_FOR checkSqlForMemoryDatabase(String sql) {
        if (sqlPrefixesForBothDB.stream().anyMatch(sql::startsWith)) {
            return SQL_FOR.BOTH_DB;
        } else if (queryStartsForSpecificDB.stream().anyMatch(sql::startsWith)) {
            if (sql.startsWith("SELECT")) {
                return SQL_FOR.MEMORY_DB;
            } else {
                if (onlyMemoryTables.stream().anyMatch(table -> sql.contains(" " + table))) {
                    return SQL_FOR.MEMORY_DB;
                } else {
                    return SQL_FOR.BOTH_DB;
                }
            }
        } else {
            if (PartisanLog.logsAllowed()) {
                PartisanLog.e("Failed execute sql: " + sql);
                throw new RuntimeException();
            }
            return SQL_FOR.BOTH_DB;
        }
    }

    private interface DbFunction<R> {
        R apply(SQLiteDatabase db) throws SQLiteException;
    }

    private interface DbProcedure {
        void apply(SQLiteDatabase db) throws SQLiteException;
    }

    private <R> List<R> executeFunctionInSpecificDB(String sql, DbFunction<R> function) throws SQLiteException {
        switch (checkSqlForMemoryDatabase(sql)) {
            case REAL_DB:
                return Collections.singletonList(function.apply(realDatabase));
            case MEMORY_DB:
                return Collections.singletonList(function.apply(memoryDatabase));
            default:
            case BOTH_DB:
                R first;
                R second;
                try {
                    first = function.apply(memoryDatabase);
                } catch (Exception e) {
                    PartisanLog.e("e", e);
                    throw e;
                }
                second = function.apply(realDatabase);
                return Arrays.asList(first, second);
        }
    }

    private void executeProcedureInSpecificDB(String sql, DbProcedure procedure) throws SQLiteException {
        executeFunctionInSpecificDB(sql, db -> {
            procedure.apply(db);
            return 0;
        });
    }

    @Override
    public SQLitePreparedStatement executeFast(String sql) throws SQLiteException {
        return new SQLitePreparedStatementMultiple(executeFunctionInSpecificDB(sql, db -> db.executeFast(sql)));
    }

    @Override
    public Integer executeInt(String sql, Object... args) throws SQLiteException {
        List<Integer> ints = executeFunctionInSpecificDB(sql, db -> db.executeInt(sql, args));
        return ints.get(ints.size() - 1);
    }

    @Override
    public void explainQuery(String sql, Object... args) throws SQLiteException {
        executeProcedureInSpecificDB(sql, db -> db.explainQuery(sql, args));
    }

    @Override
    public SQLiteCursor queryFinalized(String sql, Object... args) throws SQLiteException {
        List<SQLiteCursor> cursors = executeFunctionInSpecificDB(sql, db -> db.queryFinalized(sql, args));
        return cursors.get(cursors.size() - 1);
    }

    @Override
    public void close() {
        realDatabase.close();
        memoryDatabase.close();
    }

    @Override
    public void finalize() throws Throwable {
        super.finalize();
        close();
    }

    @Override
    public void beginTransaction() throws SQLiteException {
        realDatabase.beginTransaction();
        memoryDatabase.beginTransaction();
    }

    @Override
    public void commitTransaction() {
        realDatabase.commitTransaction();
        memoryDatabase.commitTransaction();
    }
}
