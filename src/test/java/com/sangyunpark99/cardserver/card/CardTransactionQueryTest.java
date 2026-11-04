package com.sangyunpark99.cardserver.card;

import com.jayway.jsonpath.JsonPath;
import com.sangyunpark99.cardserver.card.domain.CardTransaction;
import com.sangyunpark99.cardserver.card.infrastructure.CardTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CardTransactionQueryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CardTransactionRepository repository;

    @Test
    @DisplayName("기간 안의 거래만 created_at, id 순서로 돌려준다")
    void listInPeriod() throws Exception {
        LocalDateTime day = LocalDateTime.of(2020, 1, 1, 0, 0);
        save(day.minusNanos(1_000));
        String first = save(day);
        String second = save(day.plusHours(12));
        save(day.plusDays(1));

        list(day, day.plusDays(1), null, 100)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions", hasSize(2)))
                .andExpect(jsonPath("$.transactions[0].transactionKey").value(first))
                .andExpect(jsonPath("$.transactions[1].transactionKey").value(second))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    @DisplayName("같은 시각 거래가 여러 건이어도 startingAfter로 넘기면 누락·중복 없이 모두 받는다")
    void pageWithSameTime() throws Exception {
        LocalDateTime at = LocalDateTime.of(2020, 1, 2, 10, 0);
        List<String> saved = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            saved.add(save(at));
        }

        List<String> received = new ArrayList<>();
        String startingAfter = null;
        boolean hasMore = true;
        while (hasMore) {
            String body = list(at.toLocalDate().atStartOfDay(), at.toLocalDate().plusDays(1).atStartOfDay(), startingAfter, 2)
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            List<String> keys = JsonPath.read(body, "$.transactions[*].transactionKey");
            received.addAll(keys);
            hasMore = JsonPath.read(body, "$.hasMore");
            startingAfter = keys.isEmpty() ? null : keys.getLast();
        }

        assertThat(received).containsExactlyElementsOf(saved);
    }

    @Test
    @DisplayName("기간이 잘못되었거나 limit이 범위를 벗어나면 400")
    void invalidRequest() throws Exception {
        LocalDateTime day = LocalDateTime.of(2020, 1, 3, 0, 0);

        list(day, day, null, 100).andExpect(status().isBadRequest());
        list(day, day.plusDays(1), null, 0).andExpect(status().isBadRequest());
        list(day, day.plusDays(1), null, 1_001).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("없는 startingAfter면 400")
    void unknownStartingAfter() throws Exception {
        LocalDateTime day = LocalDateTime.of(2020, 1, 4, 0, 0);

        list(day, day.plusDays(1), "tx_not_exist", 100).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("transactionKey 목록으로 한 번에 조회하면 있는 거래만 돌려준다")
    void lookup() throws Exception {
        LocalDateTime at = LocalDateTime.of(2020, 1, 5, 9, 0);
        String first = save(at);
        String second = save(at);

        String body = lookup(List.of(first, "tx_not_exist", second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions", hasSize(2)))
                .andReturn().getResponse().getContentAsString();

        List<String> keys = JsonPath.read(body, "$.transactions[*].transactionKey");
        assertThat(keys).containsExactlyInAnyOrder(first, second);
    }

    @Test
    @DisplayName("한 번에 1,000개보다 많은 키로 조회하면 400")
    void lookupTooMany() throws Exception {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 1_001; i++) {
            keys.add(newKey());
        }

        lookup(keys).andExpect(status().isBadRequest());
    }

    private String save(LocalDateTime createdAt) {
        String key = newKey();
        repository.save(CardTransaction.approved(key, "pk_1", 10_000, createdAt));
        return key;
    }

    private ResultActions list(LocalDateTime from, LocalDateTime to, String startingAfter, int limit) throws Exception {
        var request = get("/v1/transactions")
                .param("from", from.toString())
                .param("to", to.toString())
                .param("limit", String.valueOf(limit));
        if (startingAfter != null) {
            request.param("startingAfter", startingAfter);
        }
        return mockMvc.perform(request);
    }

    private ResultActions lookup(List<String> keys) throws Exception {
        String body = keys.stream().map(k -> "\"" + k + "\"").collect(Collectors.joining(",", "{\"transactionKeys\":[", "]}"));
        return mockMvc.perform(post("/v1/transactions/lookup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String newKey() {
        return "tx_" + UUID.randomUUID().toString().replace("-", "");
    }
}
