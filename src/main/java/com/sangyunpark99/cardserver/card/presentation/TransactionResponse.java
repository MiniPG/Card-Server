package com.sangyunpark99.cardserver.card.presentation;

import com.sangyunpark99.cardserver.card.domain.CardTransaction;
import com.sangyunpark99.cardserver.card.domain.CardTransactionStatus;
import com.sangyunpark99.cardserver.card.domain.CardTransactionType;

import java.time.LocalDateTime;

public record TransactionResponse(
        String transactionKey,
        String paymentKey,
        CardTransactionType type,
        long amount,
        String approveNo,
        CardTransactionStatus status,
        String failureCode,
        String failureMessage,
        LocalDateTime createdAt
) {
    public static TransactionResponse from(CardTransaction tx) {
        return new TransactionResponse(tx.getTransactionKey(), tx.getPaymentKey(), tx.getType(), tx.getAmount(),
                tx.getApproveNo(), tx.getStatus(), tx.getFailureCode(), tx.getFailureMessage(), tx.getCreatedAt());
    }
}
