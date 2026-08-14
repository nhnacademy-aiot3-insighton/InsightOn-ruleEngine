package com.nhnacademy.insightonruleengine.common.config;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class RuleEngineInstanceEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Map<String, Object> ENGINE_A = Map.of(
            "rule-engine.heartbeat.engine-id", "engine-a",
            "rule-engine.heartbeat.peer-engine-id", "engine-b",
            "rule-engine.telemetry-routing.owned-queue-indices", "0,2,4,6,8,10,12,14"
    );

    private static final Map<String, Object> ENGINE_B = Map.of(
            "rule-engine.heartbeat.engine-id", "engine-b",
            "rule-engine.heartbeat.peer-engine-id", "engine-a",
            "rule-engine.telemetry-routing.owned-queue-indices", "1,3,5,7,9,11,13,15"
    );

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String hostName = System.getenv("HOSTNAME");
        if (hostName == null) {
            return;
        }

        int lastDash = hostName.lastIndexOf('-');
        if (lastDash < 0 || lastDash == hostName.length() - 1) {
            return;
        }

        String ordinal = hostName.substring(lastDash + 1);
        Map<String, Object> values = switch (ordinal) {
            case "0" -> ENGINE_A;
            case "1" -> ENGINE_B;
            default -> null;
        };
        if (values == null) {
            return;
        }

        environment.getPropertySources()
                .addFirst(new MapPropertySource("ruleEnginePodOrdinal", values));
    }
}
