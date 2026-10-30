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
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CardApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired CardTransactionRepository repository;

    @Test
    @DisplayName("승인하면 8자리 승인번호와 카드 정보를 돌려준다")
    void approve() throws Exception {
        approve(newKey(), "pk_1", 10_000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(true))
                .andExpect(jsonPath("$.approveNo").value(matchesPattern("\\d{8}")))
                .andExpect(jsonPath("$.issuerCode").value("11"))
                .andExpect(jsonPath("$.maskedNumber").value("1234-****-****-5678"));
    }

    @Test
    @DisplayName("승인번호는 거래마다 다르다")
    void approveNoUnique() throws Exception {
        String first = approveNo(approve(newKey(), "pk_1", 10_000));
        String second = approveNo(approve(newKey(), "pk_2", 10_000));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("같은 transactionKey로 다시 승인하면 처음 결과를 그대로 돌려주고 장부는 1건이다")
    void approveIdempotent() throws Exception {
        String key = newKey();
        String first = approveNo(approve(key, "pk_1", 10_000));

        String second = approveNo(approve(key, "pk_1", 10_000));

        assertThat(second).isEqualTo(first);
        assertThat(repository.findByTransactionKey(key)).isPresent();
        assertThat(repository.findAll().stream().filter(t -> t.getTransactionKey().equals(key)).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 paymentKey의 승인이라도 transactionKey가 다르면 각각 승인된다")
    void sameAmountDifferentKey() throws Exception {
        approve(newKey(), "pk_same", 10_000).andExpect(jsonPath("$.approved").value(true));
        approve(newKey(), "pk_same", 10_000).andExpect(jsonPath("$.approved").value(true));
    }

    @Test
    @DisplayName("승인 금액 안에서 여러 번 부분 취소할 수 있다")
    void partialCancels() throws Exception {
        String approveNo = approveNo(approve(newKey(), "pk_1", 10_000));

        cancel(newKey(), approveNo, 3_000).andExpect(jsonPath("$.canceled").value(true));
        cancel(newKey(), approveNo, 7_000).andExpect(jsonPath("$.canceled").value(true));
    }

    @Test
    @DisplayName("취소 가능 금액을 넘으면 거절한다")
    void exceedCancel() throws Exception {
        String approveNo = approveNo(approve(newKey(), "pk_1", 10_000));
        cancel(newKey(), approveNo, 8_000).andExpect(jsonPath("$.canceled").value(true));

        cancel(newKey(), approveNo, 3_000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canceled").value(false))
                .andExpect(jsonPath("$.failureCode").value("EXCEED_CANCEL_AMOUNT"));
    }

    @Test
    @DisplayName("거절된 취소는 취소 가능 금액에 포함하지 않는다")
    void rejectedCancelNotCounted() throws Exception {
        String approveNo = approveNo(approve(newKey(), "pk_1", 10_000));
        cancel(newKey(), approveNo, 20_000).andExpect(jsonPath("$.canceled").value(false));

        cancel(newKey(), approveNo, 10_000).andExpect(jsonPath("$.canceled").value(true));
    }

    @Test
    @DisplayName("없는 승인번호로 취소하면 거절한다")
    void cancelUnknownApproval() throws Exception {
        cancel(newKey(), "99999999", 1_000)
                .andExpect(jsonPath("$.canceled").value(false))
                .andExpect(jsonPath("$.failureCode").value("NOT_FOUND_APPROVAL"));
    }

    @Test
    @DisplayName("같은 transactionKey로 다시 취소하면 처음 결과를 돌려주고 두 번 취소되지 않는다")
    void cancelIdempotent() throws Exception {
        String approveNo = approveNo(approve(newKey(), "pk_1", 10_000));
        String cancelKey = newKey();

        cancel(cancelKey, approveNo, 6_000).andExpect(jsonPath("$.canceled").value(true));
        cancel(cancelKey, approveNo, 6_000).andExpect(jsonPath("$.canceled").value(true));

        assertThat(repository.sumCanceledAmount(approveNo)).isEqualTo(6_000);
    }

    @Test
    @DisplayName("승인에 쓴 transactionKey로 취소를 요청하면 409")
    void keyUsedByOtherType() throws Exception {
        String key = newKey();
        String approveNo = approveNo(approve(key, "pk_1", 10_000));

        cancel(key, approveNo, 1_000).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("transactionKey로 거래 결과를 조회한다")
    void getTransaction() throws Exception {
        String key = newKey();
        approve(key, "pk_1", 10_000);

        mockMvc.perform(get("/v1/transactions/{key}", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("APPROVE"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.paymentKey").value("pk_1"));
    }

    @Test
    @DisplayName("없는 거래를 조회하면 404")
    void getNotFound() throws Exception {
        mockMvc.perform(get("/v1/transactions/{key}", "tx_none"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("금액이 0 이하면 400")
    void invalidAmount() throws Exception {
        approve(newKey(), "pk_1", 0).andExpect(status().isBadRequest());
    }

    private String newKey() {
        return "tx_" + UUID.randomUUID().toString().replace("-", "");
    }

    private ResultActions approve(String key, String paymentKey, long amount) throws Exception {
        return mockMvc.perform(post("/v1/approvals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"transactionKey":"%s","paymentKey":"%s","amount":%d}
                        """.formatted(key, paymentKey, amount)));
    }

    private ResultActions cancel(String key, String approveNo, long amount) throws Exception {
        return mockMvc.perform(post("/v1/cancels")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"transactionKey":"%s","approveNo":"%s","amount":%d}
                        """.formatted(key, approveNo, amount)));
    }

    private String approveNo(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.approveNo");
    }
}
