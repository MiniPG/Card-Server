package com.sangyunpark99.cardserver.card;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class CardConcurrencyTest {

    private static final int REQUESTS = 5;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("같은 transactionKey로 승인이 동시에 와도 모두 같은 승인 결과를 돌려준다")
    void approveSameKeyConcurrently() throws Exception {
        String key = newKey();

        List<String> results = runConcurrently(() -> post("/v1/approvals", """
                {"transactionKey":"%s","paymentKey":"pk_1","amount":10000}
                """.formatted(key)));

        System.out.printf("[같은 키 동시 승인] 요청 %d, 결과 %s%n", REQUESTS, results);

        assertThat(results).allMatch(r -> r.startsWith("200"));
        assertThat(results.stream().map(this::approveNo).distinct()).hasSize(1);
    }

    @Test
    @DisplayName("같은 transactionKey로 취소가 동시에 와도 모두 같은 취소 결과를 돌려준다")
    void cancelSameKeyConcurrently() throws Exception {
        String approveNo = approveNo(post("/v1/approvals", """
                {"transactionKey":"%s","paymentKey":"pk_1","amount":10000}
                """.formatted(newKey())));
        String key = newKey();

        List<String> results = runConcurrently(() -> post("/v1/cancels", """
                {"transactionKey":"%s","approveNo":"%s","amount":3000}
                """.formatted(key, approveNo)));

        System.out.printf("[같은 키 동시 취소] 요청 %d, 결과 %s%n", REQUESTS, results);

        assertThat(results).allMatch(r -> r.startsWith("200"));
        assertThat(results.stream().map(r -> r.substring(4)).distinct()).hasSize(1);
    }

    private List<String> runConcurrently(Callable<String> request) throws Exception {
        List<String> results = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(REQUESTS);
        CountDownLatch ready = new CountDownLatch(REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < REQUESTS; i++) {
            executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    results.add(request.call());
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    results.add("예외 " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
                }
                return null;
            });
        }
        ready.await();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(results).hasSize(REQUESTS);
        return results;
    }

    private String post(String path, String body) throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        return result.getResponse().getStatus() + " " + result.getResponse().getContentAsString();
    }

    private String approveNo(String result) {
        return JsonPath.read(result.substring(4), "$.approveNo");
    }

    private String newKey() {
        return "tx_" + UUID.randomUUID().toString().replace("-", "");
    }
}
