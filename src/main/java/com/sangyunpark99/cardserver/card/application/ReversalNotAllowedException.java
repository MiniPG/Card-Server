package com.sangyunpark99.cardserver.card.application;

public class ReversalNotAllowedException extends RuntimeException {

    public ReversalNotAllowedException() {
        super("취소 내역이 있는 승인은 망취소할 수 없습니다.");
    }
}
