package com.sangyunpark99.cardserver.card;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CardFaultDisabledTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("장애 주입을 켜지 않으면 장애 설정 API가 없다")
    void disabledByDefault() throws Exception {
        mockMvc.perform(put("/internal/faults")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latencyMs":0,"failing":true}
                                """))
                .andExpect(status().isNotFound());
    }
}
