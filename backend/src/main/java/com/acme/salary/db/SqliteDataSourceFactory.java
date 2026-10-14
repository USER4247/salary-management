package com.acme.salary.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/**
 * Builds the two connection pools that talk to the SQLite file.
 *
 * <p>How SQLite handles many users: any number of connections may READ at the same time, but only ONE connection
 * may WRITE at a time. So we keep the two kinds of work apart:
 * <ul>
 *   <li><b>writer pool</b>: exactly one connection. Writes line up and run one after another, in this app.</li>
 *   <li><b>reader pool</b>: several read-only connections. Reads never wait for a write.</li>
 * </ul>
 *
 * <p>This only works because the database uses <b>WAL mode</b> ("write-ahead log"): a write goes to a side file
 * first, and readers keep seeing the last committed data until the write is done. Without WAL, a write would
 * block every reader.
 */
public final class SqliteDataSourceFactory {

    private SqliteDataSourceFactory() {
    }

    /** One connection that may write. Opens the file in WAL mode (the mode is saved in the file itself). */
    public static HikariDataSource writer(String url, Duration busyTimeout) {
        SQLiteConfig config = commonSettings(busyTimeout);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        // Start every transaction as "I am going to write" (BEGIN IMMEDIATE). The write lock is taken up front,
        // so a transaction can never fail halfway with "database is locked" when it tries to upgrade from read to write.
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        return pool("sqlite-writer", url, config, 1, false);
    }

    /** Several connections that can only read. Many can be busy at once, even while a write is happening. */
    public static HikariDataSource reader(String url, int poolSize, Duration busyTimeout) {
        SQLiteConfig config = commonSettings(busyTimeout);
        config.setReadOnly(true);   // the database itself refuses any write on these connections
        // SQLite fixes "read-only" when the connection opens and refuses changes afterwards, so the pool must
        // be told the same thing; otherwise it tries to set the flag itself and SQLite complains.
        return pool("sqlite-reader", url, config, poolSize, true);
    }

    /** Settings every connection gets. SQLite applies these per connection, so they must be repeated each time. */
    private static SQLiteConfig commonSettings(Duration busyTimeout) {
        SQLiteConfig config = new SQLiteConfig();
        // If the database is busy (for example another process is writing), wait this long instead of failing at once.
        config.setBusyTimeout((int) busyTimeout.toMillis());
        // In WAL mode NORMAL is safe against crashes of the app and still fast; FULL would be slower for no gain.
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        // SQLite ignores foreign keys unless asked to enforce them.
        config.enforceForeignKeys(true);
        return config;
    }

    private static HikariDataSource pool(String name, String url, SQLiteConfig config, int size, boolean readOnly) {
        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl(url);

        HikariConfig pool = new HikariConfig();
        pool.setPoolName(name);
        pool.setDataSource(dataSource);
        pool.setMaximumPoolSize(size);
        pool.setReadOnly(readOnly);
        // Open connections on demand: the database file may not exist yet when the app starts (Flyway creates it).
        pool.setMinimumIdle(0);
        pool.setInitializationFailTimeout(-1);
        // Never recycle connections by age. Local files do not need it, and recycling the only connection of an
        // in-memory test database would erase the data.
        pool.setMaxLifetime(0);
        // Waiting longer than this for a free connection means something is wrong; fail with a clear error.
        pool.setConnectionTimeout(Duration.ofSeconds(10).toMillis());
        return new HikariDataSource(pool);
    }
}
