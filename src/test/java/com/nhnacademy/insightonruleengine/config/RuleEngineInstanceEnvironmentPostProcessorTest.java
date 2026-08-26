package com.nhnacademy.insightonruleengine.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class RuleEngineInstanceEnvironmentPostProcessorTest {

    private static final String ENGINE_ID = "rule-engine.heartbeat.engine-id";
    private static final String PEER_ENGINE_ID = "rule-engine.heartbeat.peer-engine-id";
    private static final String OWNED_QUEUE_INDICES =
            "rule-engine.telemetry-routing.owned-queue-indices";

    private final RuleEngineInstanceEnvironmentPostProcessor postProcessor =
            new RuleEngineInstanceEnvironmentPostProcessor();

    @Test
    @DisplayName("StatefulSet 0번 Pod는 Engine A와 짝수 Queue를 소유한다")
    void engineAPodTest() {
        MockEnvironment environment = fallbackEnvironment();

        postProcessor.applyPodOrdinal("insighton-ruleengine-0", environment);

        assertEquals("engine-a", environment.getProperty(ENGINE_ID));
        assertEquals("engine-b", environment.getProperty(PEER_ENGINE_ID));
        assertEquals("0,2,4,6,8,10,12,14", environment.getProperty(OWNED_QUEUE_INDICES));
        assertEquals(
                "ruleEnginePodOrdinal",
                environment.getPropertySources().iterator().next().getName()
        );
    }

    @Test
    @DisplayName("StatefulSet 1번 Pod는 Engine B와 홀수 Queue를 소유한다")
    void engineBPodTest() {
        MockEnvironment environment = fallbackEnvironment();

        postProcessor.applyPodOrdinal("insighton-ruleengine-1", environment);

        assertEquals("engine-b", environment.getProperty(ENGINE_ID));
        assertEquals("engine-a", environment.getProperty(PEER_ENGINE_ID));
        assertEquals("1,3,5,7,9,11,13,15", environment.getProperty(OWNED_QUEUE_INDICES));
        assertEquals(
                "ruleEnginePodOrdinal",
                environment.getPropertySources().iterator().next().getName()
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "insighton-ruleengine", "insighton-ruleengine-2", "deployment-random"})
    @DisplayName("지원하지 않는 hostname은 Pod ordinal 설정을 주입하지 않는다")
    void unsupportedHostNameTest(String hostName) {
        MockEnvironment environment = fallbackEnvironment();

        postProcessor.applyPodOrdinal(hostName, environment);

        assertNull(environment.getPropertySources().get("ruleEnginePodOrdinal"));
        assertEquals("fallback-engine", environment.getProperty(ENGINE_ID));
        assertEquals("fallback-peer", environment.getProperty(PEER_ENGINE_ID));
        assertEquals("fallback-indices", environment.getProperty(OWNED_QUEUE_INDICES));
    }

    private MockEnvironment fallbackEnvironment() {
        return new MockEnvironment()
                .withProperty(ENGINE_ID, "fallback-engine")
                .withProperty(PEER_ENGINE_ID, "fallback-peer")
                .withProperty(OWNED_QUEUE_INDICES, "fallback-indices");
    }
}
