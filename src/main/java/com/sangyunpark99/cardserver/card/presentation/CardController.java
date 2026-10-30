package com.sangyunpark99.cardserver.card.presentation;

import com.sangyunpark99.cardserver.card.application.CardService;
import com.sangyunpark99.cardserver.card.application.LatencySimulator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/transactions/{transactionKey}")
    public TransactionResponse get(@PathVariable String transactionKey) {
        return TransactionResponse.from(cardService.get(transactionKey));
    }
}
