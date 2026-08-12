package com.nhnacademy.insightonruleengine.runner.alert;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AlertCooldownService {

    private final AlertCooldownRedisRepository alertCooldownRedisRepository;

    public boolean tryAcquire(Long flowId, Long alertActionNodeId, Duration duration) {
        if (flowId == null || alertActionNodeId == null) {
            throw new IllegalArgumentException("flowId와 alertActionNodeId는 null이면 안됩니다.");
        }
        return alertCooldownRedisRepository.tryAcquire(flowId, alertActionNodeId, duration);
    }
}
