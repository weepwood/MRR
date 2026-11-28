package com.zjcxph.imgapi.service.impl;

import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

/**
 * Deadline-aware acquisition from the existing pool, scoped to quality task statements.
 * At most four acquisition workers per service; no waiting queue or second pool.
 * A connection that cannot be handed to its caller is closed by its acquiring worker.
 */
final class QualityTaskConnections {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(QualityTaskConnections.class);
    private final Semaphore slots = new Semaphore(4);
    private record Outcome(Connection connection, SQLException error) {}

    JdbcTemplate template(JdbcTemplate original, long deadline, int querySeconds) {
        DataSource source = original.getDataSource();
        if (source == null) return original; // Unit-test doubles, not an alternative production pool.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("质量任务不得在调用方事务中执行");
        }
        JdbcTemplate bounded = new JdbcTemplate(new AbstractDataSource() {
            @Override public Connection getConnection() throws SQLException {
                return acquire(source, deadline);
            }
            @Override public Connection getConnection(String username, String password) throws SQLException {
                throw new SQLFeatureNotSupportedException("Task connection uses configured datasource");
            }
        }) {
            @Override protected void applyStatementSettings(Statement statement) throws SQLException {
                super.applyStatementSettings(statement);
                applyTimeout(statement, deadline);
            }
        };
        bounded.setQueryTimeout(querySeconds);
        return bounded;
    }

    static void applyTimeout(Statement statement, long deadline) throws SQLException {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0 || Thread.currentThread().isInterrupted()) {
            throw new QueryTimeoutException("数据质量检查执行预算已耗尽或中断");
        }
        long millis = Math.min(10_000L, (nanos + 999_999L) / 1_000_000L);
        // pgjdbc exposes a millisecond timeout on PgStatement (the pinned existing driver).
        // Never set zero, which disables query cancellation.
        if (statement.isWrapperFor(org.postgresql.jdbc.PgStatement.class)) {
            statement.unwrap(org.postgresql.jdbc.PgStatement.class).setQueryTimeoutMs(millis);
        } else {
            statement.setQueryTimeout((int)Math.min(10, (nanos + 999_999_999L) / 1_000_000_000L));
        }
    }

    Connection acquire(DataSource source, long deadline) throws SQLException {
        if (deadline - System.nanoTime() <= 0 || Thread.currentThread().isInterrupted()) throw expired();
        if (!slots.tryAcquire()) throw new QueryTimeoutException("质量任务连接获取容量已满");
        var handoff = new SynchronousQueue<Outcome>();
        Thread worker;
        try {
            worker = Thread.ofVirtual().name("mrr-quality-connection").start(() -> {
                Connection connection = null;
                boolean transferred = false;
                try {
                    Outcome outcome;
                    try {
                        connection = source.getConnection();
                        outcome = new Outcome(connection, null);
                    } catch (SQLException failure) {
                        outcome = new Outcome(null, failure);
                    }
                    long remaining = deadline - System.nanoTime();
                    if (remaining > 0) transferred = handoff.offer(outcome, remaining, TimeUnit.NANOSECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException failure) {
                    LOG.warn("Quality connection acquisition failed: {}", failure.getClass().getSimpleName());
                } finally {
                    if (!transferred && connection != null) {
                        try { connection.close(); }
                        catch (SQLException failure) { LOG.warn("Late quality connection release failed: {}", failure.getClass().getSimpleName()); }
                    }
                    slots.release();
                }
            });
        } catch (RuntimeException | Error failure) {
            slots.release();
            throw failure;
        }
        try {
            long remaining = deadline - System.nanoTime();
            Outcome result = remaining > 0 ? handoff.poll(remaining, TimeUnit.NANOSECONDS) : null;
            if (result == null) throw expired();
            if (result.error() != null) throw result.error();
            return result.connection();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw expired();
        } finally {
            // Once handoff succeeds the worker no longer touches the connection.
            worker.interrupt();
        }
    }

    private QueryTimeoutException expired() {
        return new QueryTimeoutException("质量任务连接获取预算已耗尽或中断");
    }
}
