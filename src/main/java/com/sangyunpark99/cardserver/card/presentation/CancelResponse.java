package com.sangyunpark99.cardserver.card.presentation;

import com.sangyunpark99.cardserver.card.domain.CardTransaction;

public record CancelResponse(
        String transactionKey,
        boolean canceled,
        String failureCode,
        String failureMessage
) {
    public static CancelResponse from(CardTransaction tx) {
        return new CancelResponse(tx.getTransactionKey(), tx.isApproved(), tx.getFailureCode(), tx.getFailureMessage());
    }
}
