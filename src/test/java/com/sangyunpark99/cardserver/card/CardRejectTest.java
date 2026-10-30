package com.sangyunpark99.cardserver.card;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "card.reject-rate=1.0")
@AutoConfigureMockMvc
class CardRejectTest {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("거절 확률이 100%면 승인을 거절하고 장부에 REJECTED로 남긴다")
    void rejectAll() throws Exception {
        mockMvc.perform(post("/v1/approvals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"transactionKey":"tx_reject_1","paymentKey":"pk_1","amount":10000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(false))
                .andExpect(jsonPath("$.failureCode").value("REJECT_CARD"));
    }
}
