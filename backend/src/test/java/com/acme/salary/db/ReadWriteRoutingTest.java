package com.acme.salary.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** In the real app: which pool does a transaction use? Read-only goes to the readers, the rest to the writer. */
@SpringBootTest
@ActiveProfiles("test")
class ReadWriteRoutingTest {

    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    @Qualifier("writerDataSource")
    private HikariDataSource writer;
    @Autowired
    @Qualifier("readerDataSource")
    private HikariDataSource reader;

    @Test
    void aReadOnlyTransactionUsesAReaderConnection() {
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);

        int[] busy = readOnly.execute(status -> {
            jdbc.queryForObject("SELECT 1", Integer.class);   // the first query is when the connection is taken
            return new int[] {writer.getHikariPoolMXBean().getActiveConnections(), reader.getHikariPoolMXBean().getActiveConnections()};
        });

        assertThat(busy).containsExactly(0, 1);
    }

    @Test
    void aNormalTransactionUsesTheWriterConnection() {
        TransactionTemplate readWrite = new TransactionTemplate(transactionManager);

        int[] busy = readWrite.execute(status -> {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return new int[] {writer.getHikariPoolMXBean().getActiveConnections(), reader.getHikariPoolMXBean().getActiveConnections()};
        });

        assertThat(busy).containsExactly(1, 0);
    }

    @Test
    void theWriterPoolHasExactlyOneConnectionAndTheReaderPoolHasSeveral() {
        assertThat(writer.getMaximumPoolSize()).isEqualTo(1);
        assertThat(reader.getMaximumPoolSize()).isEqualTo(2);   // from application-test.yml
    }
}
