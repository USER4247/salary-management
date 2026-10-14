package com.acme.salary.db;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Wires the read pool and the write pool into one DataSource that the rest of the app (JPA, Flyway) just uses.
 *
 * <p><b>The one rule for developers:</b> put {@code @Transactional(readOnly = true)} on code that only reads.
 * That sends it to the reader pool. Everything else goes to the single writer connection, so a service method that
 * merely reads but is not marked read-only waits in line with the writes. (Spring Data's finder methods such as
 * findById are already read-only.)
 */
@Configuration
class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Bean(destroyMethod = "close")
    HikariDataSource writerDataSource(@Value("${app.db.url}") String url,
                                      @Value("${app.db.busy-timeout}") Duration busyTimeout) {
        log.info("Database {}: WAL mode, 1 writer connection, busy timeout {}", url, busyTimeout);
        return SqliteDataSourceFactory.writer(url, busyTimeout);
    }

    @Bean(destroyMethod = "close")
    HikariDataSource readerDataSource(@Value("${app.db.url}") String url,
                                      @Value("${app.db.reader-pool-size}") int readerPoolSize,
                                      @Value("${app.db.busy-timeout}") Duration busyTimeout) {
        log.info("Database {}: {} read-only connections", url, readerPoolSize);
        return SqliteDataSourceFactory.reader(url, readerPoolSize, busyTimeout);
    }

    /** The DataSource everything else injects. It picks the pool for each transaction. */
    @Bean
    @Primary
    DataSource dataSource(HikariDataSource writerDataSource, HikariDataSource readerDataSource) {
        ReadWriteRoutingDataSource routing = new ReadWriteRoutingDataSource();
        routing.setTargetDataSources(Map.of(Target.WRITER, writerDataSource, Target.READER, readerDataSource));
        routing.setDefaultTargetDataSource(writerDataSource);
        routing.afterPropertiesSet();
        // Lazy: do not take a connection when a transaction starts, only when the first query runs. By then Spring
        // knows whether the transaction is read-only, which is what the routing decision needs.
        return new LazyConnectionDataSourceProxy(routing);
    }

    private enum Target { WRITER, READER }

    /** Chooses the reader pool for read-only transactions and the writer for everything else. */
    private static final class ReadWriteRoutingDataSource extends AbstractRoutingDataSource {
        @Override
        protected Object determineCurrentLookupKey() {
            return TransactionSynchronizationManager.isCurrentTransactionReadOnly() ? Target.READER : Target.WRITER;
        }
    }
}
