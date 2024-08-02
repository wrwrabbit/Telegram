package org.telegram.SQLite;

import org.telegram.tgnet.NativeByteBuffer;

import java.nio.ByteBuffer;
import java.util.List;

public class SQLitePreparedStatementMultiple extends SQLitePreparedStatement {
    private final List<SQLitePreparedStatement> statements;
    private int forceStatementIndex = -1;

    public SQLitePreparedStatementMultiple(List<SQLitePreparedStatement> statements) {
        this.statements = statements;
    }

    public void setForcedStatementIndex(int forceStatementIndex) {
        this.forceStatementIndex = forceStatementIndex;
    }

    private interface StatementFunction<R> {
        R apply(SQLitePreparedStatement statement) throws SQLiteException;
    }

    private interface StatementProcedure {
        void apply(SQLitePreparedStatement statement) throws SQLiteException;
    }

    private <R> R executeFunction(StatementFunction<R> function) throws SQLiteException {
        R result = null;
        if (statements.isEmpty()) {
            throw new RuntimeException();
        }
        if (forceStatementIndex != -1) {
            result = function.apply(statements.get(forceStatementIndex));
        } else {
            for (SQLitePreparedStatement statement : statements) {
                result = function.apply(statement);
            }
        }
        return result;
    }

    private void executeProcedure(StatementProcedure function) throws SQLiteException {
        executeFunction(statement -> {
            function.apply(statement);
            return 0;
        });
    }

    public SQLiteCursor query(Object[] args) throws SQLiteException {
        return executeFunction(statement -> statement.query(args));
    }

    public int step() throws SQLiteException {
        return executeFunction(SQLitePreparedStatement::step);
    }

    public SQLitePreparedStatement stepThis() throws SQLiteException {
        return executeFunction(SQLitePreparedStatement::stepThis);
    }

    public void requery() throws SQLiteException {
        executeProcedure(SQLitePreparedStatement::requery);
    }

    public void dispose() {
        try {
            executeProcedure(SQLitePreparedStatement::dispose);
        } catch (SQLiteException ignore) {
        }
    }

    void checkFinalized() throws SQLiteException {
        executeProcedure(SQLitePreparedStatement::checkFinalized);
    }

    public void finalizeQuery() {
        try {
            executeProcedure(SQLitePreparedStatement::finalizeQuery);
        } catch (SQLiteException ignore) {
        }
    }

    public void bindInteger(int index, int value) throws SQLiteException {
        executeProcedure(statement -> statement.bindInteger(index, value));
    }

    public void bindDouble(int index, double value) throws SQLiteException {
        executeProcedure(statement -> statement.bindDouble(index, value));
    }

    public void bindByteBuffer(int index, ByteBuffer value) throws SQLiteException {
        executeProcedure(statement -> statement.bindByteBuffer(index, value));
    }

    public void bindByteBuffer(int index, NativeByteBuffer value) throws SQLiteException {
        executeProcedure(statement -> statement.bindByteBuffer(index, value));
    }

    public void bindString(int index, String value) throws SQLiteException {
        executeProcedure(statement -> statement.bindString(index, value));
    }

    public void bindLong(int index, long value) throws SQLiteException {
        executeProcedure(statement -> statement.bindLong(index, value));
    }

    public void bindNull(int index) throws SQLiteException {
        executeProcedure(statement -> statement.bindNull(index));
    }
}
