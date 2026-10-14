package com.acme.salary.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Uses a real SQLite file in a temp folder (not memory), because WAL and locking only exist for real files. */
class SqliteDataSourceFactoryTest {

    private static final Duration BUSY_TIMEOUT = Duration.ofSeconds(5);

    private HikariDataSource writer;
    private HikariDataSource reader;
    private String url;

    @BeforeEach
    void openDatabase(@TempDir Path folder) throws Exception {
        url = "jdbc:sqlite:" + folder.resolve("test.db");
        writer = SqliteDataSourceFactory.writer(url, BUSY_TIMEOUT);
        update("CREATE TABLE item (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)");
        reader = SqliteDataSourceFactory.reader(url, 4, BUSY_TIMEOUT);   // after the file exists, as in the app
    }

    @AfterEach
    void closeDatabase() {
        reader.close();
        writer.close();
    }

    // ----- connection settings -----

    @Test
    void databaseIsInWalMode() throws Exception {
        assertThat(pragma(writer, "journal_mode")).isEqualTo("wal");
        assertThat(pragma(reader, "journal_mode")).isEqualTo("wal");   // the mode is stored in the file
    }

    @Test
    void foreignKeysAndBusyTimeoutAreSetOnEveryConnection() throws Exception {
        for (HikariDataSource pool : List.of(writer, reader)) {
            assertThat(pragma(pool, "foreign_keys")).isEqualTo("1");
            assertThat(pragma(pool, "busy_timeout")).isEqualTo("5000");
            assertThat(pragma(pool, "synchronous")).isEqualTo("1");   // 1 = NORMAL
        }
    }

    @Test
    void readerConnectionsCannotWrite() {
        assertThatThrownBy(() -> {
            try (Connection connection = reader.getConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO item (name) VALUES ('nope')");
            }
        }).isInstanceOf(SQLException.class);
    }

    // ----- many readers and writers -----

    @Test
    void readersAreNotBlockedByAnUnfinishedWrite() throws Exception {
        update("INSERT INTO item (name) VALUES ('committed')");

        try (Connection write = writer.getConnection(); Statement statement = write.createStatement()) {
            write.setAutoCommit(false);
            statement.executeUpdate("INSERT INTO item (name) VALUES ('still-being-written')");

            // The write is open (not committed). A reader gets an answer straight away, from the last committed data.
            assertThat(countItems(reader)).isEqualTo(1);

            write.commit();
        }
        assertThat(countItems(reader)).isEqualTo(2);
    }

    @Test
    void manyWritersQueueUpWithoutLockErrors() throws Exception {
        runInParallel(8, () -> insertRows(writer, 50));

        assertThat(countItems(reader)).isEqualTo(400);
    }

    @Test
    void writersFromTwoSeparatePPoolsTakeTurnsToo() throws Exception {
        // Two pools = two "processes" fighting for the one write lock (e.g. the app and the seed script).
        try (HikariDataSource otherProcess = SqliteDataSourceFactory.writer(url, BUSY_TIMEOUT)) {
            List<Callable<Void>> jobs = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                jobs.add(() -> insertRows(writer, 50));
                jobs.add(() -> insertRows(otherProcess, 50));
            }
            runAll(jobs);
        }

        assertThat(countItems(reader)).isEqualTo(400);   // no "database is locked": the busy timeout made them wait
    }

    @Test
    void manyReadersWorkAtTheSameTimeWhileWritesContinue() throws Exception {
        update("INSERT INTO item (name) VALUES ('seed')");
        List<Callable<Void>> jobs = new ArrayList<>();
        jobs.add(() -> insertRows(writer, 200));
        for (int i = 0; i < 12; i++) {   // 12 readers sharing a pool of 4
            jobs.add(() -> {
                for (int n = 0; n < 100; n++) {
                    assertThat(countItems(reader)).isGreaterThanOrEqualTo(1);
                }
                return null;
            });
        }

        runAll(jobs);

        assertThat(countItems(reader)).isEqualTo(201);
    }

    // ----- helpers -----

    private void update(String sql) throws SQLException {
        try (Connection connection = writer.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static String pragma(HikariDataSource pool, String name) throws SQLException {
        try (Connection connection = pool.getConnection(); Statement statement = connection.createStatement();
             var result = statement.executeQuery("PRAGMA " + name)) {
            return result.getString(1);
        }
    }

    private static int countItems(HikariDataSource pool) throws SQLException {
        try (Connection connection = pool.getConnection(); Statement statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM item")) {
            return result.getInt(1);
        }
    }

    /** Each batch is its own transaction, like a request handler saving data. */
    private static Void insertRows(HikariDataSource pool, int rows) throws SQLException {
        try (Connection connection = pool.getConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            for (int i = 0; i < rows; i++) {
                statement.executeUpdate("INSERT INTO item (name) VALUES ('row')");
            }
            connection.commit();
        }
        return null;
    }

    private static void runInParallel(int copies, Callable<Void> job) throws Exception {
        List<Callable<Void>> jobs = new ArrayList<>();
        for (int i = 0; i < copies; i++) {
            jobs.add(job);
        }
        runAll(jobs);
    }

    /** Runs the jobs at the same time and rethrows the first failure, if any. */
    private static void runAll(List<Callable<Void>> jobs) throws Exception {
        ExecutorService threads = Executors.newFixedThreadPool(jobs.size());
        try {
            for (Future<Void> future : threads.invokeAll(jobs)) {
                future.get();
            }
        } finally {
            threads.shutdownNow();
        }
    }
}
