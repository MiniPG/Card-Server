package com.sangyunpark99.cardserver.card.presentation;

public record ReversalRequest(
        String paymentKey,
        long amount
) {
}
