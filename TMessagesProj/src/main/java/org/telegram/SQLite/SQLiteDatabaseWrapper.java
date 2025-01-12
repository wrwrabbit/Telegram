package org.telegram.SQLite;

import org.telegram.messenger.partisan.PartisanLog;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SQLiteDatabaseWrapper extends SQLiteDatabase {
    private final Set<String> sqlPrefixesForBothDB = new HashSet<>(Arrays.asList(
            "PRAGMA", "CREATE TABLE", "CREATE INDEX", "VACUUM", "DROP TABLE", "DELETE FROM", "UPDATE"
    ));
    private final Set<String> queryStartsForSpecificDB = new HashSet<>(Arrays.asList(
            "INSERT INTO", "REPLACE INTO", "SELECT"
    ));
    private final Set<String> onlyMemoryTables = new HashSet<>(Arrays.asList(
            "messages_v2", "chats", "contacts", "dialogs", "messages_holes"
    ));
    private enum DbSelector {
        FILE_DB,
        MEMORY_DB,
        BOTH_DB
    }

    private final SQLiteDatabase fileDatabase;
    private final SQLiteDatabase memoryDatabase;

    public SQLiteDatabaseWrapper(String fileName) throws SQLiteException {
        super(fileName);
        fileDatabase = new SQLiteDatabase(fileName);
        memoryDatabase = new SQLiteDatabase(":memory:");
        fileDatabase.backup(memoryDatabase);
    }

    public long getSQLiteHandle() {
        return fileDatabase.getSQLiteHandle();
    }

    public boolean tableExists(String tableName) throws SQLiteException {
        return fileDatabase.tableExists(tableName);
    }

    private DbSelector checkSqlForMemoryDatabase(String sql) {
        if (sqlPrefixesForBothDB.stream().anyMatch(sql::startsWith)) {
            return DbSelector.BOTH_DB;
        } else if (queryStartsForSpecificDB.stream().anyMatch(sql::startsWith)) {
            if (sql.startsWith("SELECT")) {
                return DbSelector.MEMORY_DB;
            } else {
                if (onlyMemoryTables.stream().anyMatch(table -> sql.contains(" " + table))) {
                    return DbSelector.MEMORY_DB;
                } else {
                    return DbSelector.BOTH_DB;
                }
            }
        } else {
            if (PartisanLog.logsAllowed()) {
                PartisanLog.e("Failed execute sql: " + sql);
                throw new RuntimeException();
            }
            return DbSelector.BOTH_DB;
        }
    }

    private interface DbFunction<R> {
        R apply(SQLiteDatabase db) throws SQLiteException;
    }

    private interface DbProcedure {
        void apply(SQLiteDatabase db) throws SQLiteException;
    }

    private <R> List<R> executeFunctionInSpecificDB(String sql, DbFunction<R> function) throws SQLiteException {
        return executeFunctionInSpecificDB(checkSqlForMemoryDatabase(sql), function);
    }

    private <R> List<R> executeFunctionInSpecificDB(DbSelector dbSelector, DbFunction<R> function) throws SQLiteException {
        switch (dbSelector) {
            case FILE_DB:
                return Collections.singletonList(function.apply(fileDatabase));
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
                second = function.apply(fileDatabase);
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

    public SQLitePreparedStatementMultiple executeFastForBothDb(String sql) throws SQLiteException {
        return new SQLitePreparedStatementMultiple(executeFunctionInSpecificDB(DbSelector.BOTH_DB, db -> db.executeFast(sql)));
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
        fileDatabase.close();
        memoryDatabase.close();
    }

    @Override
    public void finalize() throws Throwable {
        super.finalize();
        close();
    }

    @Override
    public void beginTransaction() throws SQLiteException {
        fileDatabase.beginTransaction();
        memoryDatabase.beginTransaction();
    }

    @Override
    public void commitTransaction() {
        fileDatabase.commitTransaction();
        memoryDatabase.commitTransaction();
    }
}
