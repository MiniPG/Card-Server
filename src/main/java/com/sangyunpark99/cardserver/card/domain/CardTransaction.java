package com.sangyunpark99.cardserver.card.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "card_transaction",
        uniqueConstraints = @UniqueConstraint(name = "uk_card_tx_transaction_key", columnNames = "transaction_key"),
        indexes = {
                @Index(name = "idx_card_tx_approve_no", columnList = "approve_no"),
                @Index(name = "idx_card_tx_payment_key", columnList = "payment_key")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardTransaction {
    public static final String ISSUER_CODE = "11";
    public static final String MASKED_NUMBER = "1234-****-****-5678";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_key", nullable = false, length = 64)
    private String transactionKey;

    @Column(name = "payment_key", length = 200)
    private String paymentKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private CardTransactionType type;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "approve_no", length = 8)
    private String approveNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private CardTransactionStatus status;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "failure_message", length = 200)
    private String failureMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static CardTransaction approved(String transactionKey, String paymentKey, long amount, LocalDateTime now) {
        return of(transactionKey, paymentKey, CardTransactionType.APPROVE, amount, null,
                CardTransactionStatus.APPROVED, null, null, now);
    }

    public static CardTransaction canceled(String transactionKey, String approveNo, long amount, LocalDateTime now) {
        return of(transactionKey, null, CardTransactionType.CANCEL, amount, approveNo,
                CardTransactionStatus.APPROVED, null, null, now);
    }

    public static CardTransaction rejected(String transactionKey, String paymentKey, CardTransactionType type,
                                           long amount, String approveNo, String failureCode,
                                           String failureMessage, LocalDateTime now) {
        return of(transactionKey, paymentKey, type, amount, approveNo,
                CardTransactionStatus.REJECTED, failureCode, failureMessage, now);
    }

    private static CardTransaction of(String transactionKey, String paymentKey, CardTransactionType type,
                                      long amount, String approveNo, CardTransactionStatus status,
                                      String failureCode, String failureMessage, LocalDateTime now) {
        if (amount <= 0) {
            throw new IllegalArgumentException("금액은 0보다 커야 합니다.");
        }
        CardTransaction tx = new CardTransaction();
        tx.transactionKey = transactionKey;
        tx.paymentKey = paymentKey;
        tx.type = type;
        tx.amount = amount;
        tx.approveNo = approveNo;
        tx.status = status;
        tx.failureCode = failureCode;
        tx.failureMessage = failureMessage;
        tx.createdAt = now;
        return tx;
    }

    public void assignApproveNo() {
        if (type != CardTransactionType.APPROVE || status != CardTransactionStatus.APPROVED) {
            throw new IllegalStateException("승인된 승인 거래에만 승인번호를 부여합니다.");
        }
        this.approveNo = String.format("%08d", id % 100_000_000);
    }

    public boolean isApproved() {
        return status == CardTransactionStatus.APPROVED;
    }
}
