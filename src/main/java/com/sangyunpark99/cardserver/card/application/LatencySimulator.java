package com.sangyunpark99.cardserver.card.application;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class LatencySimulator {
    private final AtomicLong latencyMs;
    private volatile boolean failing;

    public LatencySimulator(CardProperties properties) {
        this.latencyMs = new AtomicLong(properties.latencyMs());
    }

    public void await() {
        long latency = latencyMs.get();
        if (latency > 0) {
            try {
                Thread.sleep(latency);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("지연 대기 중 인터럽트되었습니다.", e);
            }
        }
        if (failing) {
            throw new CardUnavailableException();
        }
    }

    public FaultState change(long latencyMs, boolean failing) {
        if (latencyMs < 0) {
            throw new IllegalArgumentException("latencyMs는 0 이상이어야 합니다.");
        }
        this.latencyMs.set(latencyMs);
        this.failing = failing;
        return current();
    }

    public FaultState current() {
        return new FaultState(latencyMs.get(), failing);
    }

    public record FaultState(long latencyMs, boolean failing) {}
}
