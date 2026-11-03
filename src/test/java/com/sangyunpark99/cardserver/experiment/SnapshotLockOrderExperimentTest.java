package com.sangyunpark99.cardserver.experiment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@EnabledIfEnvironmentVariable(named = "CARD_EXPERIMENT", matches = "snapshot-lock-order")
class SnapshotLockOrderExperimentTest {

    private static final String URL = "jdbc:mysql://localhost:3307/card?serverTimezone=Asia/Seoul";
    private static final long APPROVED = 10_000;
    private static final long CANCEL = 7_000;

    private final List<String> out = new ArrayList<>();

    @Test
    void run() throws Exception {
        try (Connection admin = connect()) {
            exec(admin, "drop table if exists exp_cancel");
            exec(admin, "drop table if exists exp_approval");
            exec(admin, "create table exp_approval (id bigint primary key, amount bigint not null)");
            exec(admin, "create table exp_cancel (id bigint auto_increment primary key, approve_id bigint not null, amount bigint not null)");
            out.add("MySQL " + query(admin, "select version()"));
            out.add("승인 10,000원에 취소 X(7,000원)가 먼저 잠금·저장하고, 커밋 전에 취소 Y가 들어옴");
            out.add("Y가 계산한 취소 합계로 7,000원 취소를 허용하는지 확인");
            out.add("");
            out.add(String.format("%-18s %-28s %-14s %s", "격리 수준", "Y의 순서", "Y가 본 취소 합계", "Y의 판단"));

            for (String isolation : List.of("REPEATABLE READ", "READ COMMITTED")) {
                for (boolean plainFirst : List.of(true, false)) {
                    out.add(scenario(admin, isolation, plainFirst));
                }
            }
        } finally {
            try (Connection admin = connect()) {
                exec(admin, "drop table if exists exp_cancel");
                exec(admin, "drop table if exists exp_approval");
            }
            Path file = Path.of("build/experiments/snapshot-lock-order-result.txt");
            Files.createDirectories(file.getParent());
            Files.write(file, out);
        }
    }

    private String scenario(Connection admin, String isolation, boolean plainFirst) throws Exception {
        exec(admin, "delete from exp_cancel");
        exec(admin, "delete from exp_approval");
        exec(admin, "insert into exp_approval values (1, " + APPROVED + ")");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection x = begin(isolation); Connection y = begin(isolation)) {
            query(x, "select amount from exp_approval where id = 1 for update");
            query(x, "select coalesce(sum(amount), 0) from exp_cancel where approve_id = 1");
            exec(x, "insert into exp_cancel (approve_id, amount) values (1, " + CANCEL + ")");

            if (plainFirst) {
                query(y, "select count(*) from exp_cancel where approve_id = 1");
            }
            Future<String> ySum = executor.submit(() -> {
                query(y, "select amount from exp_approval where id = 1 for update");
                return query(y, "select coalesce(sum(amount), 0) from exp_cancel where approve_id = 1");
            });
            Thread.sleep(300);
            String waiting = ySum.isDone() ? "(대기 안 함)" : "";
            x.commit();

            long seen = Long.parseLong(ySum.get(10, TimeUnit.SECONDS));
            y.rollback();
            String decision = APPROVED - seen >= CANCEL ? "허용 → 취소 합계 14,000원 (초과)" : "거절 (정상)";
            String order = plainFirst ? "일반 SELECT 먼저 → 잠금" : "잠금 먼저 → 일반 SELECT";
            return String.format("%-18s %-28s %-14s %s %s", isolation, order, String.format("%,d원", seen), decision, waiting);
        } finally {
            executor.shutdownNow();
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(URL, "root", "root");
    }

    private Connection begin(String isolation) throws SQLException {
        Connection c = connect();
        exec(c, "set session transaction isolation level " + isolation);
        c.setAutoCommit(false);
        return c;
    }

    private void exec(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private String query(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
