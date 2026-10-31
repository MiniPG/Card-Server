package com.sangyunpark99.cardserver.card.application;

import com.sangyunpark99.cardserver.card.domain.CardTransaction;
import com.sangyunpark99.cardserver.card.domain.CardTransactionStatus;
import com.sangyunpark99.cardserver.card.domain.CardTransactionType;
import com.sangyunpark99.cardserver.card.infrastructure.CardTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardService {
    private final CardTransactionRepository repository;
    private final CardProperties properties;

    @Transactional
    public CardTransaction approve(String transactionKey, String paymentKey, long amount) {
        require(transactionKey, "transactionKey");
        Optional<CardTransaction> existing = findSameType(transactionKey, CardTransactionType.APPROVE);
        if (existing.isPresent()) {
            logReplay(existing.get());
            return existing.get();
        }

        LocalDateTime now = LocalDateTime.now();
        if (shouldReject()) {
            return logged(repository.save(CardTransaction.rejected(transactionKey, paymentKey, CardTransactionType.APPROVE,
                    amount, null, "REJECT_CARD", "카드사에서 승인을 거절했습니다.", now)));
        }

        CardTransaction tx = repository.save(CardTransaction.approved(transactionKey, paymentKey, amount, now));
        tx.assignApproveNo();
        return logged(tx);
    }

    @Transactional
    public CardTransaction cancel(String transactionKey, String approveNo, long amount) {
        require(transactionKey, "transactionKey");
        require(approveNo, "approveNo");
        Optional<CardTransaction> existing = findSameType(transactionKey, CardTransactionType.CANCEL);
        if (existing.isPresent()) {
            logReplay(existing.get());
            return existing.get();
        }

        LocalDateTime now = LocalDateTime.now();
        Optional<CardTransaction> original = repository.findByApproveNoAndTypeAndStatus(
                approveNo, CardTransactionType.APPROVE, CardTransactionStatus.APPROVED);
        if (original.isEmpty()) {
            return logged(repository.save(CardTransaction.rejected(transactionKey, null, CardTransactionType.CANCEL,
                    amount, approveNo, "NOT_FOUND_APPROVAL", "원거래를 찾을 수 없습니다.", now)));
        }

        long cancelable = original.get().getAmount() - repository.sumCanceledAmount(approveNo);
        if (amount > cancelable) {
            return logged(repository.save(CardTransaction.rejected(transactionKey, null, CardTransactionType.CANCEL,
                    amount, approveNo, "EXCEED_CANCEL_AMOUNT", "취소 가능 금액을 초과했습니다. 취소 가능 금액=" + cancelable, now)));
        }

        if (shouldReject()) {
            return logged(repository.save(CardTransaction.rejected(transactionKey, null, CardTransactionType.CANCEL,
                    amount, approveNo, "REJECT_CANCEL", "카드사에서 취소를 거절했습니다.", now)));
        }

        return logged(repository.save(CardTransaction.canceled(transactionKey, approveNo, amount, now)));
    }

    @Transactional(readOnly = true)
    public CardTransaction get(String transactionKey) {
        return repository.findByTransactionKey(transactionKey)
                .orElseThrow(TransactionNotFoundException::new);
    }

    private Optional<CardTransaction> findSameType(String transactionKey, CardTransactionType type) {
        Optional<CardTransaction> existing = repository.findByTransactionKey(transactionKey);
        if (existing.isPresent() && existing.get().getType() != type) {
            throw new DuplicatedTransactionKeyException();
        }
        return existing;
    }

    private void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "는 필수입니다.");
        }
    }

    private CardTransaction logged(CardTransaction tx) {
        log.atInfo().setMessage(tx.getType() == CardTransactionType.APPROVE ? "카드 승인 처리" : "카드 취소 처리")
                .addKeyValue("transactionKey", tx.getTransactionKey())
                .addKeyValue("paymentKey", tx.getPaymentKey())
                .addKeyValue("approveNo", tx.getApproveNo())
                .addKeyValue("amount", tx.getAmount())
                .addKeyValue("status", tx.getStatus())
                .addKeyValue("failureCode", tx.getFailureCode())
                .log();
        return tx;
    }

    private void logReplay(CardTransaction tx) {
        log.atInfo().setMessage("중복 요청: 기존 결과 반환")
                .addKeyValue("transactionKey", tx.getTransactionKey())
                .addKeyValue("type", tx.getType())
                .addKeyValue("status", tx.getStatus())
                .log();
    }

    private boolean shouldReject() {
        return properties.rejectRate() > 0 && ThreadLocalRandom.current().nextDouble() < properties.rejectRate();
    }
}
