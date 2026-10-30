package com.sangyunpark99.cardserver.card.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LatencySimulator {
    private final CardProperties properties;

    public void await() {
        if (properties.latencyMs() <= 0) {
            return;
        }
        try {
            Thread.sleep(properties.latencyMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("지연 대기 중 인터럽트되었습니다.", e);
        }
    }
}
