package com.sangyunpark99.cardserver.card.presentation;

import java.util.List;

public record TransactionLookupRequest(List<String> transactionKeys) {
}
