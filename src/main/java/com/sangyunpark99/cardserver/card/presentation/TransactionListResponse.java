package com.sangyunpark99.cardserver.card.presentation;

import com.sangyunpark99.cardserver.card.application.CardTransactionPage;

import java.util.List;

public record TransactionListResponse(List<TransactionResponse> transactions, boolean hasMore) {
    public static TransactionListResponse from(CardTransactionPage page) {
        return new TransactionListResponse(
                page.transactions().stream().map(TransactionResponse::from).toList(), page.hasMore());
    }
}
