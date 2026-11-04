package com.sangyunpark99.cardserver.card.presentation;

import com.sangyunpark99.cardserver.card.domain.CardTransaction;

import java.util.List;

public record TransactionLookupResponse(List<TransactionResponse> transactions) {
    public static TransactionLookupResponse from(List<CardTransaction> transactions) {
        return new TransactionLookupResponse(transactions.stream().map(TransactionResponse::from).toList());
    }
}
