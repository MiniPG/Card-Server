package com.sangyunpark99.cardserver.card.presentation;

import com.sangyunpark99.cardserver.card.domain.CardTransaction;

public record ApproveResponse(
        String transactionKey,
        boolean approved,
        String approveNo,
        String issuerCode,
        String maskedNumber,
        String failureCode,
        String failureMessage
) {
    public static ApproveResponse from(CardTransaction tx) {
        if (tx.isApproved()) {
            return new ApproveResponse(tx.getTransactionKey(), true, tx.getApproveNo(),
                    CardTransaction.ISSUER_CODE, CardTransaction.MASKED_NUMBER, null, null);
        }
        return new ApproveResponse(tx.getTransactionKey(), false, null, null, null,
                tx.getFailureCode(), tx.getFailureMessage());
    }
}
