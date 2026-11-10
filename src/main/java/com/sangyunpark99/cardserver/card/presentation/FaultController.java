package com.sangyunpark99.cardserver.card.presentation;

import com.sangyunpark99.cardserver.card.application.LatencySimulator;
import com.sangyunpark99.cardserver.card.application.LatencySimulator.FaultState;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/faults")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "card", name = "fault-injection-enabled", havingValue = "true")
public class FaultController {
    private final LatencySimulator latencySimulator;

    @GetMapping
    public FaultState current() {
        return latencySimulator.current();
    }

    @PutMapping
    public FaultState change(@RequestBody FaultState request) {
        return latencySimulator.change(request.latencyMs(), request.failing());
    }
}
