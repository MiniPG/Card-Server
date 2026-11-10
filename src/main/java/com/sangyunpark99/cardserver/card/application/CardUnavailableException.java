package com.sangyunpark99.cardserver.card.application;

public class CardUnavailableException extends RuntimeException {
    public CardUnavailableException() {
        super("카드사가 요청을 처리할 수 없습니다.");
    }
}
