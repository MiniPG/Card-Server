package com.sangyunpark99.cardserver.card.presentation;

public record ApproveRequest(
        String transactionKey,
        String paymentKey,
        long amount
) {
}
