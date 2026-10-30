package com.sangyunpark99.cardserver.card.application;

public class DuplicatedTransactionKeyException extends RuntimeException {

    public DuplicatedTransactionKeyException() {
        super("다른 종류의 거래에 이미 사용된 transactionKey입니다.");
    }
}
