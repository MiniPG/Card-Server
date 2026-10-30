package com.sangyunpark99.cardserver.card.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "card")
public record CardProperties(
        long latencyMs,
        double rejectRate
) {
}
