package com.nhnacademy.insightonruleengine.common.config;

import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rule-engine.telemetry-routing")
public record TelemetryRoutingProperties(
        boolean enabled,
        String exchange,
        String queuePrefix,
        List<Integer> ownedQueueIndices,
        String hashHeader
) {

    private static final Set<Integer> ENGINE_A_INDICES = Set.of(0, 2, 4, 6, 8, 10, 12, 14);
    private static final Set<Integer> ENGINE_B_INDICES = Set.of(1, 3, 5, 7, 9, 11, 13, 15);

    public TelemetryRoutingProperties {
        ownedQueueIndices = ownedQueueIndices == null ? List.of() : List.copyOf(ownedQueueIndices);
    }

    // Telemetry 라우팅에 필요한 설정과 현재 인스턴스의 Queue 소유 범위를 검증한다.
    public void validateEnabledConfiguration() {
        if (!enabled) {
            return;
        }
        if (exchange == null || exchange.isBlank() || queuePrefix == null || queuePrefix.isBlank()) {
            throw new IllegalStateException("Telemetry Exchange와 queue prefix는 필수입니다.");
        }
        Set<Integer> ownIndices = Set.copyOf(ownedQueueIndices);
        if (ownedQueueIndices.size() != 8 ||
                (!ownIndices.equals(ENGINE_A_INDICES) && !ownIndices.equals(ENGINE_B_INDICES))) {
            throw new IllegalStateException("Telemetry 소유 큐는 정해져있습니다.");
        }
    }

    //16개의 큐의 실제 이름을 붙여줍니다.
    public String queueName(int index) {
        if (index < 0 || index >= 16) {
            throw new IllegalArgumentException("Telemetry 큐 인덱스는 16개입니다.");
        }
        return queuePrefix + "%02d".formatted(index);
    }

    //현재 인스턴스가 정상일때 8개의 큐를 조회합니다.
    public List<String> ownedQueueNames() {
        return ownedQueueIndices.stream()
                .map(this::queueName)
                .toList();
    }

    //상대 인스턴스가 정상일 때 소유하는 8개의 큐 인덱스를 조회합니다.
    public List<Integer> peerQueueIndices() {
        Set<Integer> ownIndices = Set.copyOf(ownedQueueIndices);
        if (ownIndices.equals(ENGINE_A_INDICES)) {
            return ENGINE_B_INDICES.stream().sorted().toList();
        }
        if (ownIndices.equals(ENGINE_B_INDICES)) {
            return ENGINE_A_INDICES.stream().sorted().toList();
        }
        return List.of();
    }

    //상대 인스턴스가 정상일 때 소유하는 8개의 큐 이름을 조회합니다.
    public List<String> peerQueueNames() {
        return peerQueueIndices().stream()
                .map(this::queueName)
                .toList();
    }
}
