package com.sangyunpark99.cardserver.card.infrastructure;

import com.sangyunpark99.cardserver.card.domain.CardTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CardTransactionRepository extends JpaRepository<CardTransaction, Long> {

    Optional<CardTransaction> findByTransactionKey(String transactionKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from CardTransaction t where t.transactionKey = :transactionKey")
    Optional<CardTransaction> findByTransactionKeyForUpdate(@Param("transactionKey") String transactionKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t from CardTransaction t
            where t.approveNo = :approveNo
              and t.type = com.sangyunpark99.cardserver.card.domain.CardTransactionType.APPROVE
              and t.status = com.sangyunpark99.cardserver.card.domain.CardTransactionStatus.APPROVED
            """)
    Optional<CardTransaction> findApprovalForUpdate(@Param("approveNo") String approveNo);

    @Query("""
            select coalesce(sum(t.amount), 0) from CardTransaction t
            where t.approveNo = :approveNo
              and t.type = com.sangyunpark99.cardserver.card.domain.CardTransactionType.CANCEL
              and t.status = com.sangyunpark99.cardserver.card.domain.CardTransactionStatus.APPROVED
            """)
    long sumCanceledAmount(@Param("approveNo") String approveNo);
}
