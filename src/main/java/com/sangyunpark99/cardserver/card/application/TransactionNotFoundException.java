package com.sangyunpark99.cardserver.card.application;

public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException() {
        super("존재하지 않는 거래입니다.");
    }
}
