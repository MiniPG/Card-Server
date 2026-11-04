package com.sangyunpark99.cardserver.card.application;

import com.sangyunpark99.cardserver.card.domain.CardTransaction;

import java.util.List;

public record CardTransactionPage(List<CardTransaction> transactions, boolean hasMore) {
}
