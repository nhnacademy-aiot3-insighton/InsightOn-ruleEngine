package com.nhnacademy.insightonruleengine.runner.execution.state.alert;

import com.nhnacademy.insightonruleengine.flow.domain.node.params.action.AlertParams;
import com.nhnacademy.insightonruleengine.runner.infrastructure.persistence.redis.AlertCountRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

//ALERT Action Node의 반복 도달과 Cooldown을 함께 판정합니다.
@Service
@RequiredArgsConstructor
public class AlertCountService {

    private final AlertCountRedisRepository alertCountRedisRepository;

    public boolean shouldPublish(Long flowId, Long alertActionNodeId, AlertParams alertParams) {
        if(alertParams == null) {
            throw new IllegalArgumentException("alertParams는 필수입니다.");
        }
        if(alertParams.requiredCount() == 1 && alertParams.cooldownSeconds() == 0){
            return true;
        }
        int countTimeoutSeconds = alertParams.countTimeoutSeconds() == null ? 0 : alertParams.countTimeoutSeconds();
        return alertCountRedisRepository.incrementAndCheck(
                flowId,
                alertActionNodeId,
                alertParams.requiredCount(),
                countTimeoutSeconds,
                alertParams.cooldownSeconds()
                );
    }

}
