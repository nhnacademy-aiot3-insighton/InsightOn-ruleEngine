package com.nhnacademy.insightonruleengine.runner.alert;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AlertCountService {

    private final AlertCountRedisRepository alertCountRedisRepository;

    public boolean shouldPublish(Long flowId, Long alertActionNodeId, int requiredCount) {
        if(requiredCount < 1) {
            throw new IllegalArgumentException("목표 Count는 1 이상이어야 합니다.");
        }
        if(requiredCount == 1) {
            return true;
        }
        return alertCountRedisRepository.incrementAndCheck(flowId, alertActionNodeId, requiredCount);
    }

}
