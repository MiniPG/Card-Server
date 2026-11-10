package com.sangyunpark99.cardserver.card.application;

import com.sangyunpark99.cardserver.card.domain.CardTransaction;
import com.sangyunpark99.cardserver.card.domain.CardTransactionStatus;
import com.sangyunpark99.cardserver.card.domain.CardTransactionType;
import com.sangyunpark99.cardserver.card.infrastructure.CardTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardService {
    private static final int MAX_LIMIT = 1_000;

    private final CardTransactionRepository repository;
    private final CardProperties properties;
    private final TransactionTemplate transactionTemplate;

    public CardTransaction approve(String transactionKey, String paymentKey, long amount) {
        require(transactionKey, "transactionKey");
        try {
            return transactionTemplate.execute(status -> doApprove(transactionKey, paymentKey, amount));
        } catch (DataIntegrityViolationException e) {
            return replayAfterConflict(transactionKey, CardTransactionType.APPROVE, e);
        }
    }

    public CardTransaction cancel(String transactionKey, String approveNo, long amount) {
        require(transactionKey, "transactionKey");
        require(approveNo, "approveNo");
        try {
            return transactionTemplate.execute(status -> doCancel(transactionKey, approveNo, amount));
        } catch (DataIntegrityViolationException e) {
            return replayAfterConflict(transactionKey, CardTransactionType.CANCEL, e);
        }
    }

    public CardTransaction reverse(String transactionKey, String paymentKey, long amount) {
        require(transactionKey, "transactionKey");
        boolean exists = repository.existsByTransactionKey(transactionKey);
        try {
            return transactionTemplate.execute(status -> exists
                    ? reverseExisting(transactionKey, paymentKey, amount)
                    : recordReversal(transactionKey, paymentKey, amount));
        } catch (DataIntegrityViolationException e) {
            return transactionTemplate.execute(status -> reverseExisting(transactionKey, paymentKey, amount));
        }
    }

    private CardTransaction recordReversal(String transactionKey, String paymentKey, long amount) {
        return logged(repository.save(CardTransaction.reversed(transactionKey, paymentKey, amount, LocalDateTime.now())));
    }

    private CardTransaction reverseExisting(String transactionKey, String paymentKey, long amount) {
        Optional<CardTransaction> existing = repository.findByTransactionKeyForUpdate(transactionKey);
        if (existing.isEmpty()) {
            return recordReversal(transactionKey, paymentKey, amount);
        }
        if (existing.get().getType() != CardTransactionType.APPROVE) {
            throw new DuplicatedTransactionKeyException();
        }
        if (existing.get().isApproved() && repository.sumCanceledAmount(existing.get().getApproveNo()) > 0) {
            throw new ReversalNotAllowedException();
        }
        existing.get().reverse();
        return logged(existing.get());
    }

    private CardTransaction doApprove(String transactionKey, String paymentKey, long amount) {
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

    private CardTransaction doCancel(String transactionKey, String approveNo, long amount) {
        Optional<CardTransaction> original = repository.findApprovalForUpdate(approveNo);

        Optional<CardTransaction> existing = findSameType(transactionKey, CardTransactionType.CANCEL);
        if (existing.isPresent()) {
            logReplay(existing.get());
            return existing.get();
        }

        LocalDateTime now = LocalDateTime.now();
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
    public CardTransactionPage list(LocalDateTime from, LocalDateTime to, String startingAfter, int limit) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("조회 기간이 올바르지 않습니다.");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit은 1 이상 " + MAX_LIMIT + " 이하여야 합니다.");
        }
        PageRequest page = PageRequest.of(0, limit + 1);
        List<CardTransaction> rows;
        if (startingAfter == null) {
            rows = repository.findFirstPage(from, to, page);
        } else {
            CardTransaction cursor = repository.findByTransactionKey(startingAfter)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 startingAfter입니다."));
            rows = repository.findPageAfter(from, to, cursor.getCreatedAt(), cursor.getId(), page);
        }
        boolean hasMore = rows.size() > limit;
        return new CardTransactionPage(hasMore ? rows.subList(0, limit) : rows, hasMore);
    }

    @Transactional(readOnly = true)
    public List<CardTransaction> lookup(List<String> transactionKeys) {
        if (transactionKeys == null || transactionKeys.isEmpty()) {
            return List.of();
        }
        if (transactionKeys.size() > MAX_LIMIT) {
            throw new IllegalArgumentException("한 번에 " + MAX_LIMIT + "개까지 조회할 수 있습니다.");
        }
        return repository.findByTransactionKeyIn(transactionKeys);
    }

    @Transactional(readOnly = true)
    public CardTransaction get(String transactionKey) {
        return repository.findByTransactionKey(transactionKey)
                .orElseThrow(TransactionNotFoundException::new);
    }

    private CardTransaction replayAfterConflict(String transactionKey, CardTransactionType type,
                                                DataIntegrityViolationException conflict) {
        CardTransaction existing = transactionTemplate.execute(status -> findSameType(transactionKey, type))
                .orElseThrow(() -> conflict);
        logReplay(existing);
        return existing;
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
        log.atInfo().setMessage(logMessage(tx))
                .addKeyValue("transactionKey", tx.getTransactionKey())
                .addKeyValue("paymentKey", tx.getPaymentKey())
                .addKeyValue("approveNo", tx.getApproveNo())
                .addKeyValue("amount", tx.getAmount())
                .addKeyValue("status", tx.getStatus())
                .addKeyValue("failureCode", tx.getFailureCode())
                .log();
        return tx;
    }

    private String logMessage(CardTransaction tx) {
        if (tx.getStatus() == CardTransactionStatus.REVERSED) {
            return "카드 망취소 처리";
        }
        return tx.getType() == CardTransactionType.APPROVE ? "카드 승인 처리" : "카드 취소 처리";
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
