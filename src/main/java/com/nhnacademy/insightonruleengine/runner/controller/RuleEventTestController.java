package com.nhnacademy.insightonruleengine.runner.controller;

import com.nhnacademy.insightonruleengine.runner.FlowRunner;
import com.nhnacademy.insightonruleengine.runner.dto.SensorEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rule-events")
@ConditionalOnProperty(name = "rule-engine.prototype.test-controller.enabled", havingValue = "true")
public class RuleEventTestController {

    private final FlowRunner flowRunner;

    @PostMapping("/test")
    public ResponseEntity<Void> run(@RequestBody SensorEvent event) {
        flowRunner.run(event);
        return ResponseEntity.accepted().build();
    }
}
