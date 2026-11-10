package com.sangyunpark99.cardserver.card;

import org.junit.jupiter.api.AfterEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "card.fault-injection-enabled=true")
@AutoConfigureMockMvc
class CardFaultTest {

    @Autowired
    MockMvc mockMvc;

    @AfterEach
    void reset() throws Exception {
        changeFault(0, false);
    }

    @Test
    @DisplayName("장애를 켜면 승인 요청에 503을 돌려주고, 끄면 다시 승인한다")
    void failing() throws Exception {
        changeFault(0, true).andExpect(status().isOk()).andExpect(jsonPath("$.failing").value(true));

        approve().andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CARD_UNAVAILABLE"));

        changeFault(0, false);
        approve().andExpect(status().isOk()).andExpect(jsonPath("$.approved").value(true));
    }

    @Test
    @DisplayName("지연을 바꾸면 서버를 다시 띄우지 않아도 다음 요청부터 그만큼 늦게 응답한다")
    void latency() throws Exception {
        changeFault(300, false).andExpect(jsonPath("$.latencyMs").value(300));

        long start = System.currentTimeMillis();
        approve().andExpect(status().isOk());
        long elapsed = System.currentTimeMillis() - start;

        System.out.printf("[장애 주입] 지연 300ms 설정 후 승인 응답 %dms%n", elapsed);
        assertThat(elapsed).isGreaterThanOrEqualTo(300);
    }

    private ResultActions changeFault(long latencyMs, boolean failing) throws Exception {
        return mockMvc.perform(put("/internal/faults")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"latencyMs":%d,"failing":%s}
                        """.formatted(latencyMs, failing)));
    }

    private ResultActions approve() throws Exception {
        return mockMvc.perform(post("/v1/approvals")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"transactionKey":"tx_fault_%s","paymentKey":"pk_1","amount":10000}
                        """.formatted(UUID.randomUUID())));
    }
}
