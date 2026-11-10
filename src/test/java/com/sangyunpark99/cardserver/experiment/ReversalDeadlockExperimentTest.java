package com.sangyunpark99.cardserver.experiment;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@EnabledIfEnvironmentVariable(named = "CARD_EXPERIMENT", matches = "reversal-deadlock")
class ReversalDeadlockExperimentTest {

    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0")
            .withUsername("root")
            .withPassword("test");

    @BeforeAll
    static void start() throws Exception {
        MYSQL.start();
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute("""
                    create table card_transaction (
                        id bigint auto_increment primary key,
                        transaction_key varchar(64) not null,
                        status varchar(20) not null,
                        constraint uk_card_transaction_key unique (transaction_key)
                    )""");
            s.execute("insert into card_transaction (transaction_key, status) values ('tx_a_existing', 'APPROVED'), ('tx_z_existing', 'APPROVED')");
        }
    }

    @AfterAll
    static void stop() {
        MYSQL.stop();
    }

    @Test
    void repeatableRead() throws Exception {
        run("REPEATABLE READ", "tx_m_0010", "tx_m_0014");
    }

    @Test
    void readCommitted() throws Exception {
        run("READ COMMITTED", "tx_n_0010", "tx_n_0014");
    }

    private void run(String isolation, String keyA, String keyB) throws Exception {
        System.out.printf("%n===== %s =====%n", isolation);
        try (Connection a = begin(isolation); Connection b = begin(isolation)) {
            System.out.printf("A: select %s for update → %s%n", keyA, selectForUpdate(a, keyA));
            System.out.printf("B: select %s for update → %s%n", keyB, selectForUpdate(b, keyB));
            printLocks("두 SELECT FOR UPDATE 직후 잡힌 락");

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<String> insertA = executor.submit(() -> insert(a, keyA));
            Thread.sleep(500);
            String resultB = insert(b, keyB);
            String resultA = insertA.get(10, TimeUnit.SECONDS);
            executor.shutdownNow();
            System.out.printf("A insert: %s%nB insert: %s%n", resultA, resultB);
            a.rollback();
            b.rollback();
        }
        printLatestDeadlock();
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", "test");
    }

    private Connection begin(String isolation) throws SQLException {
        Connection c = connect();
        try (Statement s = c.createStatement()) {
            s.execute("set session transaction isolation level " + isolation);
        }
        c.setAutoCommit(false);
        return c;
    }

    private String selectForUpdate(Connection c, String key) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("select id from card_transaction where transaction_key = '" + key + "' for update")) {
            return rs.next() ? "있음" : "없음";
        }
    }

    private String insert(Connection c, String key) {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("insert into card_transaction (transaction_key, status) values ('" + key + "', 'REVERSED')");
            return "성공";
        } catch (SQLException e) {
            return "실패 " + e.getMessage();
        }
    }

    private void printLocks(String title) throws SQLException {
        System.out.println("-- " + title);
        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("""
                     select engine_transaction_id, index_name, lock_type, lock_mode, lock_status, lock_data
                     from performance_schema.data_locks
                     where object_name = 'card_transaction'
                     order by engine_transaction_id, index_name""")) {
            while (rs.next()) {
                System.out.printf("  tx %s | %s | %s | %s | %s | %s%n",
                        rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6));
            }
        }
    }

    private void printLatestDeadlock() throws SQLException {
        try (Connection c = connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("show engine innodb status")) {
            rs.next();
            String status = rs.getString("Status");
            int start = status.indexOf("LATEST DETECTED DEADLOCK");
            int end = status.indexOf("TRANSACTIONS", start + 1);
            System.out.println(start < 0 ? "-- 데드락 기록 없음" : status.substring(start, end));
        }
    }
}
