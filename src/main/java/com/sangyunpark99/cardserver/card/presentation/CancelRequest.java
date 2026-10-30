package com.sangyunpark99.cardserver.card.presentation;

public record CancelRequest(
        String transactionKey,
        String approveNo,
        long amount
) {
}
