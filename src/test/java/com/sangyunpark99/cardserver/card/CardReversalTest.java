package com.sangyunpark99.cardserver.card;

import com.jayway.jsonpath.JsonPath;
import com.sangyunpark99.cardserver.card.infrastructure.CardTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CardReversalTest {

    private static final int ROUNDS = 10;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CardTransactionRepository repository;

    @Test
    @DisplayName("승인된 거래를 망취소하면 REVERSED가 되고, 같은 키로 다시 승인해도 승인되지 않는다")
    void reverseApproved() throws Exception {
        String key = newKey();
        approve(key).andExpect(jsonPath("$.approved").value(true));

        reverse(key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVERSED"));

        approve(key).andExpect(jsonPath("$.approved").value(false));
        transaction(key).andExpect(jsonPath("$.status").value("REVERSED"));
    }

    @Test
    @DisplayName("승인이 도착하기 전에 망취소하면 REVERSED 기록이 남고, 늦게 도착한 승인은 승인되지 않는다")
    void reverseBeforeApproval() throws Exception {
        String key = newKey();

        reverse(key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVERSED"));

        approve(key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(false))
                .andExpect(jsonPath("$.failureCode").value("REVERSED"));
        assertThat(countByKey(key)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 키로 망취소를 다시 보내도 같은 결과이고 기록은 1건이다")
    void reverseIdempotent() throws Exception {
        String key = newKey();
        approve(key);

        reverse(key).andExpect(jsonPath("$.status").value("REVERSED"));
        reverse(key).andExpect(jsonPath("$.status").value("REVERSED"));

        assertThat(countByKey(key)).isEqualTo(1);
    }

    @Test
    @DisplayName("망취소된 승인은 승인번호로 취소할 수 없다")
    void cancelReversedApproval() throws Exception {
        String key = newKey();
        String approveNo = JsonPath.read(approve(key).andReturn().getResponse().getContentAsString(), "$.approveNo");
        reverse(key);

        mockMvc.perform(post("/v1/cancels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"transactionKey":"%s","approveNo":"%s","amount":3000}
                                """.formatted(newKey(), approveNo)))
                .andExpect(jsonPath("$.canceled").value(false))
                .andExpect(jsonPath("$.failureCode").value("NOT_FOUND_APPROVAL"));
    }

    @Test
    @DisplayName("취소에 쓴 transactionKey로 망취소를 요청하면 409")
    void keyUsedByCancel() throws Exception {
        String approveNo = JsonPath.read(approve(newKey()).andReturn().getResponse().getContentAsString(), "$.approveNo");
        String cancelKey = newKey();
        mockMvc.perform(post("/v1/cancels")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"transactionKey":"%s","approveNo":"%s","amount":3000}
                        """.formatted(cancelKey, approveNo)));

        reverse(cancelKey).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("늦은 승인과 망취소가 동시에 도착해도 결과는 항상 REVERSED이고 기록은 1건이다")
    void approveAndReverseConcurrently() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<String> finals = new ArrayList<>();
        try {
            for (int i = 0; i < ROUNDS; i++) {
                String key = newKey();
                CountDownLatch start = new CountDownLatch(1);
                Future<String> approval = executor.submit(() -> {
                    start.await();
                    return body(approve(key));
                });
                Future<String> reversal = executor.submit(() -> {
                    start.await();
                    return body(reverse(key));
                });
                start.countDown();
                String approveResult = approval.get(10, TimeUnit.SECONDS);
                String reverseResult = reversal.get(10, TimeUnit.SECONDS);
                String finalStatus = JsonPath.read(body(transaction(key)), "$.status");

                System.out.printf("[승인·망취소 동시] 승인 응답 %s / 망취소 응답 %s / 최종 %s%n",
                        approveResult, reverseResult, finalStatus);
                finals.add(finalStatus);
                assertThat(countByKey(key)).isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(finals).hasSize(ROUNDS).allMatch("REVERSED"::equals);
    }

    @Test
    @DisplayName("승인이 없는 서로 다른 키로 망취소가 동시에 몰려도 모두 REVERSED로 남는다")
    void reverseUnknownKeysConcurrently() throws Exception {
        int requests = 20;
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        int failed = 0;
        try {
            for (int round = 0; round < ROUNDS; round++) {
                String prefix = newKey() + "_";
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Integer>> results = new ArrayList<>();
                for (int i = 0; i < requests; i++) {
                    String key = prefix + String.format("%04d", i);
                    results.add(executor.submit(() -> {
                        start.await();
                        try {
                            return reverse(key).andReturn().getResponse().getStatus();
                        } catch (Exception e) {
                            return 500;
                        }
                    }));
                }
                start.countDown();
                int roundFailed = 0;
                for (Future<Integer> result : results) {
                    if (result.get(10, TimeUnit.SECONDS) != 200) {
                        roundFailed++;
                    }
                }
                long reversed = repository.findAll().stream()
                        .filter(t -> t.getTransactionKey().startsWith(prefix))
                        .count();
                System.out.printf("[승인 없는 망취소 동시] 실패 %d/%d, 남은 기록 %d%n", roundFailed, requests, reversed);
                failed += roundFailed;
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(failed).isZero();
    }

    @Test
    @DisplayName("부분 취소된 승인을 망취소하면 409이고 승인은 APPROVED로 남는다")
    void reversePartiallyCanceled() throws Exception {
        String key = newKey();
        String approveNo = approveNo(approve(key));
        cancel(newKey(), approveNo, 3_000).andExpect(jsonPath("$.canceled").value(true));

        reverse(key)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVERSAL_NOT_ALLOWED"));

        transaction(key).andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("부분 취소와 망취소가 동시에 와도 둘 다 성공하지 않는다")
    void cancelAndReverseConcurrently() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        int bothSucceeded = 0;
        try {
            for (int i = 0; i < ROUNDS; i++) {
                String key = newKey();
                String approveNo = approveNo(approve(key));
                CountDownLatch start = new CountDownLatch(1);
                Future<String> cancellation = executor.submit(() -> {
                    start.await();
                    return body(cancel(newKey(), approveNo, 3_000));
                });
                Future<MvcResult> reversal = executor.submit(() -> {
                    start.await();
                    return reverse(key).andReturn();
                });
                start.countDown();
                String cancelResult = cancellation.get(10, TimeUnit.SECONDS);
                MvcResult reverseResult = reversal.get(10, TimeUnit.SECONDS);
                boolean canceled = cancelResult.contains("\"canceled\":true");
                boolean reversed = reverseResult.getResponse().getStatus() == 200;
                String finalStatus = JsonPath.read(body(transaction(key)), "$.status");

                System.out.printf("[부분 취소·망취소 동시] 취소 %s / 망취소 %d %s / 최종 %s%n",
                        canceled ? "성공" : "거절", reverseResult.getResponse().getStatus(),
                        reverseResult.getResponse().getContentAsString(), finalStatus);
                if (canceled && reversed) {
                    bothSucceeded++;
                }
                assertThat(canceled || reversed).isTrue();
                assertThat(finalStatus).isEqualTo(canceled ? "APPROVED" : "REVERSED");
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(bothSucceeded).isZero();
    }

    private ResultActions cancel(String key, String approveNo, long amount) throws Exception {
        return mockMvc.perform(post("/v1/cancels")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"transactionKey":"%s","approveNo":"%s","amount":%d}
                        """.formatted(key, approveNo, amount)));
    }

    private String approveNo(ResultActions result) throws Exception {
        return JsonPath.read(body(result), "$.approveNo");
    }

    private ResultActions approve(String key) throws Exception {
        return mockMvc.perform(post("/v1/approvals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"transactionKey":"%s","paymentKey":"pk_1","amount":10000}
                        """.formatted(key)));
    }

    private ResultActions reverse(String key) throws Exception {
        return mockMvc.perform(post("/v1/approvals/{transactionKey}/reversal", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"paymentKey":"pk_1","amount":10000}
                        """));
    }

    private ResultActions transaction(String key) throws Exception {
        return mockMvc.perform(get("/v1/transactions/{transactionKey}", key));
    }

    private String body(ResultActions result) throws Exception {
        MvcResult mvcResult = result.andReturn();
        return mvcResult.getResponse().getContentAsString();
    }

    private long countByKey(String key) {
        return repository.findAll().stream().filter(t -> t.getTransactionKey().equals(key)).count();
    }

    private String newKey() {
        return "tx_" + UUID.randomUUID().toString().replace("-", "");
    }
}
