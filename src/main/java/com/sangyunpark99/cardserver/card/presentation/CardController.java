package com.sangyunpark99.cardserver.card.presentation;

import com.sangyunpark99.cardserver.card.application.CardService;
import com.sangyunpark99.cardserver.card.application.LatencySimulator;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class CardController {
    private final CardService cardService;
    private final LatencySimulator latencySimulator;

    @PostMapping("/approvals")
    public ApproveResponse approve(@RequestBody ApproveRequest request) {
        latencySimulator.await();
        return ApproveResponse.from(cardService.approve(request.transactionKey(), request.paymentKey(), request.amount()));
    }

    @PostMapping("/cancels")
    public CancelResponse cancel(@RequestBody CancelRequest request) {
        latencySimulator.await();
        return CancelResponse.from(cardService.cancel(request.transactionKey(), request.approveNo(), request.amount()));
    }

    @PostMapping("/approvals/{transactionKey}/reversal")
    public TransactionResponse reverse(@PathVariable String transactionKey, @RequestBody ReversalRequest request) {
        latencySimulator.await();
        return TransactionResponse.from(cardService.reverse(transactionKey, request.paymentKey(), request.amount()));
    }

    @GetMapping("/transactions")
    public TransactionListResponse list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String startingAfter,
            @RequestParam(defaultValue = "1000") int limit) {
        return TransactionListResponse.from(cardService.list(from, to, startingAfter, limit));
    }

    @PostMapping("/transactions/lookup")
    public TransactionLookupResponse lookup(@RequestBody TransactionLookupRequest request) {
        return TransactionLookupResponse.from(cardService.lookup(request.transactionKeys()));
    }

    @GetMapping("/transactions/{transactionKey}")
    public TransactionResponse get(@PathVariable String transactionKey) {
        return TransactionResponse.from(cardService.get(transactionKey));
    }
}
