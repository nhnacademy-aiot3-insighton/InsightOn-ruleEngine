package com.nhnacademy.insightonruleengine.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TelemetryRoutingPropertiesTest {

    private static final List<Integer> ENGINE_A_INDICES = List.of(0, 2, 4, 6, 8, 10, 12, 14);
    private static final List<Integer> ENGINE_B_INDICES = List.of(1, 3, 5, 7, 9, 11, 13, 15);

    // 확정된 두 Engine의 정상 Queue 소유 집합을 모두 허용하는지 검증합니다.
    @Test
    @DisplayName("Engine A의 짝수 Queue와 Engine B의 홀수 Queue 소유 설정은 유효하다")
    void validIndicesTest() {
        assertDoesNotThrow(() -> properties(ENGINE_A_INDICES).validateEnabledConfiguration());
        assertDoesNotThrow(() -> properties(ENGINE_B_INDICES).validateEnabledConfiguration());
    }

    // 비활성 환경에서 외부 RabbitMQ 설정이 없어도 시작할 수 있게 합니다.
    @Test
    @DisplayName("Telemetry Routing이 비활성화되면 나머지 설정을 검증하지 않는다")
    void disabledConfigurationTest() {
        TelemetryRoutingProperties properties = new TelemetryRoutingProperties(
                false,
                null,
                null,
                null,
                null
        );

        assertDoesNotThrow(properties::validateEnabledConfiguration);
    }

    // Exchange와 Queue prefix 누락으로 잘못된 토폴로지가 선언되는 것을 막습니다.
    @Test
    @DisplayName("활성화된 Routing은 Exchange와 Queue prefix가 모두 필요하다")
    void missingTopologyNameTest() {
        TelemetryRoutingProperties props1 = new TelemetryRoutingProperties(
                true,
                null,
                "telemetry.",
                ENGINE_A_INDICES,
                null
        );
        assertThrows(IllegalStateException.class, props1::validateEnabledConfiguration);

        TelemetryRoutingProperties props2 = new TelemetryRoutingProperties(
                true,
                " ",
                "telemetry.",
                ENGINE_A_INDICES,
                null
        );
        assertThrows(IllegalStateException.class, props2::validateEnabledConfiguration);

        TelemetryRoutingProperties props3 = new TelemetryRoutingProperties(
                true,
                "insighton.core.telemetry.exchange-v2",
                null,
                ENGINE_A_INDICES,
                null
        );
        assertThrows(IllegalStateException.class, props3::validateEnabledConfiguration);

        TelemetryRoutingProperties props4 = new TelemetryRoutingProperties(
                true,
                "insighton.core.telemetry.exchange-v2",
                " ",
                ENGINE_A_INDICES,
                null
        );
        assertThrows(IllegalStateException.class, props4::validateEnabledConfiguration);
    }

    // 정확히 짝수 또는 홀수 8개가 아닌 임의 Queue 소유 구성을 거부합니다.
    @Test
    @DisplayName("Queue 소유 목록은 확정된 짝수 또는 홀수 인덱스 8개여야 한다")
    void invalidOwnershipTest() {
        TelemetryRoutingProperties emptyProps = properties(List.of());
        assertThrows(IllegalStateException.class, emptyProps::validateEnabledConfiguration);

        TelemetryRoutingProperties contiguousProps = properties(List.of(0, 1, 2, 3, 4, 5, 6, 7));
        assertThrows(IllegalStateException.class, contiguousProps::validateEnabledConfiguration);

        TelemetryRoutingProperties duplicateProps = properties(List.of(0, 0, 2, 4, 6, 8, 10, 12));
        assertThrows(IllegalStateException.class, duplicateProps::validateEnabledConfiguration);
    }

    // Queue 이름이 고정된 00부터 15까지의 두 자리 인덱스를 사용하는지 확인합니다.
    @Test
    @DisplayName("Queue 이름은 prefix 뒤에 00부터 15까지 두 자리 인덱스를 붙인다")
    void queueNameTest() {
        TelemetryRoutingProperties properties = properties(ENGINE_A_INDICES);

        assertEquals("telemetry.00", properties.queueName(0));
        assertEquals("telemetry.15", properties.queueName(15));
        assertThrows(IllegalArgumentException.class, () -> properties.queueName(-1));
        assertThrows(IllegalArgumentException.class, () -> properties.queueName(16));
    }

    // 정상 소유 인덱스가 실제 Queue 이름 목록으로 같은 순서로 변환되는지 검증합니다.
    @Test
    @DisplayName("현재 Engine의 소유 인덱스를 8개 Queue 이름으로 변환한다")
    void ownedQueueNamesTest() {
        assertEquals(
                List.of(
                        "telemetry.00",
                        "telemetry.02",
                        "telemetry.04",
                        "telemetry.06",
                        "telemetry.08",
                        "telemetry.10",
                        "telemetry.12",
                        "telemetry.14"
                ),
                properties(ENGINE_A_INDICES).ownedQueueNames()
        );
    }

    // 외부 목록 변경이 실행 중 Queue 소유 범위를 바꾸지 않도록 생성 시 복사를 확인합니다.
    @Test
    @DisplayName("생성 후 원본 Queue 인덱스 목록을 변경해도 소유 목록은 변하지 않는다")
    void defensiveOwnershipCopyTest() {
        List<Integer> indices = new ArrayList<>(ENGINE_A_INDICES);
        TelemetryRoutingProperties properties = properties(indices);

        indices.clear();

        assertEquals(ENGINE_A_INDICES, properties.ownedQueueIndices());
        List<Integer> owned = properties.ownedQueueIndices();
        assertThrows(UnsupportedOperationException.class, () -> owned.add(1));
    }

    @Test
    @DisplayName("서로 다른 엔진은 서로의 인덱스 8개 큐를 조회할 수 있습니다.")
    void peerQueueIndicesTest() {
        TelemetryRoutingProperties propertiesA = properties(ENGINE_A_INDICES);
        TelemetryRoutingProperties propertiesB = properties(ENGINE_B_INDICES);

        assertEquals(ENGINE_B_INDICES, propertiesA.peerQueueIndices());
        assertEquals(
                List.of(
                        "telemetry.01",
                        "telemetry.03",
                        "telemetry.05",
                        "telemetry.07",
                        "telemetry.09",
                        "telemetry.11",
                        "telemetry.13",
                        "telemetry.15"
                ),
                propertiesA.peerQueueNames()
        );
        assertEquals(ENGINE_A_INDICES, propertiesB.peerQueueIndices());
        assertEquals(
                List.of(
                        "telemetry.00",
                        "telemetry.02",
                        "telemetry.04",
                        "telemetry.06",
                        "telemetry.08",
                        "telemetry.10",
                        "telemetry.12",
                        "telemetry.14"
                ),
                propertiesB.peerQueueNames()
        );
    }

    // 반복되는 유효 토폴로지 값은 고정하고 Queue 소유 목록만 바꿔 테스트합니다.
    private TelemetryRoutingProperties properties(List<Integer> ownedQueueIndices) {
        return new TelemetryRoutingProperties(
                true,
                "insighton.core.telemetry.exchange-v2",
                "telemetry.",
                ownedQueueIndices,
                null
        );
    }
}
