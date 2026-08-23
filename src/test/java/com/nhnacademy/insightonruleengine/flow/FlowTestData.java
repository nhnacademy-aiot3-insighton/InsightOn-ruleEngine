package com.nhnacademy.insightonruleengine.flow;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowCreateRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowUpdateRequest;
import java.util.List;

public abstract class FlowTestData {

    public static List<FlowNodeRequest> createValidNodes() {
        return List.of(
                FlowNodeRequest.builder()
                        .clientNodeKey("sensor")
                        .nodeType(NodeType.LOCATION)
                        .configuration(JsonNodeFactory.instance.objectNode())
                        .build(),
                FlowNodeRequest.builder()
                        .clientNodeKey("alert")
                        .nodeType(NodeType.ALERT)
                        .configuration(JsonNodeFactory.instance.objectNode()
                                .put("title", "테스트 알림")
                                .put("severity", "WARNING")
                                .put("message", "테스트 알림 메시지"))
                        .build()
        );
    }

    public static List<FlowLinkRequest> createValidLinks() {
        return List.of(
                FlowLinkRequest.builder()
                        .sourceClientNodeKey("sensor")
                        .targetClientNodeKey("alert")
                        .sourcePort("out")
                        .targetPort("in")
                        .build()
        );
    }

    public static FlowUpdateRequest createValidUpdateRequest(String name, String description) {
        return FlowUpdateRequest.builder()
                .name(name)
                .description(description)
                .nodes(createValidNodes())
                .links(createValidLinks())
                .build();
    }

    //시나리오 1: 기초 온도 30도 이상 경보 플로우 (LOCATION -> THRESHOLD -> ALERT)
    public static FlowCreateRequest createTemperatureThreshold30FlowRequest(Long locationId) {
        List<FlowNodeRequest> nodes = List.of(
                FlowNodeRequest.builder()
                        .clientNodeKey("temp_sensor")
                        .nodeType(NodeType.LOCATION)
                        .configuration(JsonNodeFactory.instance.objectNode())
                        .build(),
                FlowNodeRequest.builder()
                        .clientNodeKey("temp_threshold_30")
                        .nodeType(NodeType.THRESHOLD)
                        .configuration(JsonNodeFactory.instance.objectNode()
                                .put("expression", "#metrics['temperature'] > 30"))
                        .build(),
                FlowNodeRequest.builder()
                        .clientNodeKey("temp_alert")
                        .nodeType(NodeType.ALERT)
                        .configuration(JsonNodeFactory.instance.objectNode()
                                .put("title", "온도 경보")
                                .put("severity", "WARNING")
                                .put("message", "온도 30도 초과 경보"))
                        .build()
        );

        List<FlowLinkRequest> links = List.of(
                FlowLinkRequest.builder()
                        .sourceClientNodeKey("temp_sensor")
                        .targetClientNodeKey("temp_threshold_30")
                        .sourcePort("out")
                        .targetPort("in")
                        .build(),
                FlowLinkRequest.builder()
                        .sourceClientNodeKey("temp_threshold_30")
                        .targetClientNodeKey("temp_alert")
                        .sourcePort("true")
                        .targetPort("in")
                        .build()
        );

        return FlowCreateRequest.builder()
                .locationId(locationId != null ? locationId : 10L)
                .name("온도 30도 이상 알람 플로우")
                .description("온도 센서 데이터가 30도를 초과할 때 대시보드 알람을 생성합니다.")
                .nodes(nodes)
                .links(links)
                .build();
    }

    //시나리오 2: 정기 환기 장치 구동 플로우 (SCHEDULE -> ACTUATOR_CONTROL)
    public static FlowCreateRequest createScheduledActuatorFlowRequest(Long locationId) {
        List<FlowNodeRequest> nodes = List.of(
                FlowNodeRequest.builder()
                        .clientNodeKey("hourly_schedule")
                        .nodeType(NodeType.SCHEDULE)
                        .configuration(JsonNodeFactory.instance.objectNode().put("cron", "0 0 * * * *"))
                        .build(),
                FlowNodeRequest.builder()
                        .clientNodeKey("fan_actuator")
                        .nodeType(NodeType.ACTUATOR_CONTROL)
                        .configuration(JsonNodeFactory.instance.objectNode()
                                .put("actuatorType", "FAN")
                                .put("command", "power")
                                .put("commandValue", "ON"))
                        .build()
        );

        List<FlowLinkRequest> links = List.of(
                FlowLinkRequest.builder()
                        .sourceClientNodeKey("hourly_schedule")
                        .targetClientNodeKey("fan_actuator")
                        .sourcePort("out")
                        .targetPort("in")
                        .build()
        );

        return FlowCreateRequest.builder()
                .locationId(locationId != null ? locationId : 10L)
                .name("정기 환기 장치 구동 플로우")
                .description("매시 정각에 환기 팬 장치를 구동합니다.")
                .nodes(nodes)
                .links(links)
                .build();
    }

    //시나리오 3: 이중 조건 직렬 검사 플로우 (LOCATION -> THRESHOLD(온도) -> THRESHOLD(습도) -> ALERT)
    public static FlowCreateRequest createMultiThresholdSerialFlowRequest(Long locationId) {
        List<FlowNodeRequest> nodes = List.of(
                FlowNodeRequest.builder()
                        .clientNodeKey("env_sensor")
                        .nodeType(NodeType.LOCATION)
                        .configuration(JsonNodeFactory.instance.objectNode())
                        .build(),
                FlowNodeRequest.builder()
                        .clientNodeKey("temp_check")
                        .nodeType(NodeType.THRESHOLD)
                        .configuration(
                                JsonNodeFactory.instance.objectNode()
                                        .put("expression", "#metrics['temperature'] > 30"))
                        .build(),
                FlowNodeRequest.builder()
                        .clientNodeKey("humidity_check")
                        .nodeType(NodeType.THRESHOLD)
                        .configuration(JsonNodeFactory.instance.objectNode()
                                .put("expression", "#metrics['humidity'] > 70"))
                        .build(),
                FlowNodeRequest.builder()
                        .clientNodeKey("high_temp_humidity_alert")
                        .nodeType(NodeType.ALERT)
                        .configuration(JsonNodeFactory.instance.objectNode()
                                .put("title", "고온 고습 경보")
                                .put("severity", "WARNING")
                                .put("message", "고온 고습 경보"))
                        .build()
        );

        List<FlowLinkRequest> links = List.of(
                FlowLinkRequest.builder()
                        .sourceClientNodeKey("env_sensor")
                        .targetClientNodeKey("temp_check")
                        .sourcePort("out")
                        .targetPort("in")
                        .build(),
                FlowLinkRequest.builder()
                        .sourceClientNodeKey("temp_check")
                        .targetClientNodeKey("humidity_check")
                        .sourcePort("true")
                        .targetPort("in")
                        .build(),
                FlowLinkRequest.builder()
                        .sourceClientNodeKey("humidity_check")
                        .targetClientNodeKey("high_temp_humidity_alert")
                        .sourcePort("true")
                        .targetPort("in")
                        .build()
        );

        return FlowCreateRequest.builder()
                .locationId(locationId != null ? locationId : 10L)
                .name("이중 조건 직렬 검사 플로우")
                .description("온도 30도 초과 및 습도 70% 초과 조건을 연속 검사하여 경보를 발생시킵니다.")
                .nodes(nodes)
                .links(links)
                .build();
    }

    //시나리오 4: 참/거짓 이중 분기 플로우 (LOCATION -> THRESHOLD -> [true: ALERT, false: ACTUATOR_CONTROL])
    public static FlowCreateRequest createTrueFalseFlowRequest(Long locationId) {
        List<FlowNodeRequest> nodes = List.of(
                FlowNodeRequest.builder()
                        .clientNodeKey("temp_sensor")
                        .nodeType(NodeType.LOCATION)
                        .configuration(JsonNodeFactory.instance.objectNode())
                        .build(),
                FlowNodeRequest.builder()
                        .clientNodeKey("temp_threshold")
                        .nodeType(NodeType.THRESHOLD)
                        .configuration(JsonNodeFactory.instance.objectNode()
                                .put("expression", "#metrics['temperature'] > 30"))
                        .build(),
                FlowNodeRequest.builder()
                        .clientNodeKey("high_temp_alert")
                        .nodeType(NodeType.ALERT)
                        .configuration(JsonNodeFactory.instance.objectNode()
                                .put("title", "고온 경보")
                                .put("severity", "WARNING")
                                .put("message", "고온 경보"))
                        .build(),
                FlowNodeRequest.builder()
                        .clientNodeKey("cooler_off_actuator")
                        .nodeType(NodeType.ACTUATOR_CONTROL)
                        .configuration(JsonNodeFactory.instance.objectNode()
                                .put("actuatorType", "COOLER")
                                .put("command", "power")
                                .put("commandValue", "OFF"))
                        .build()
        );

        List<FlowLinkRequest> links = List.of(
                FlowLinkRequest.builder()
                        .sourceClientNodeKey("temp_sensor")
                        .targetClientNodeKey("temp_threshold")
                        .sourcePort("out")
                        .targetPort("in")
                        .build(),
                FlowLinkRequest.builder()
                        .sourceClientNodeKey("temp_threshold")
                        .targetClientNodeKey("high_temp_alert")
                        .sourcePort("true")
                        .targetPort("in")
                        .build(),
                FlowLinkRequest.builder()
                        .sourceClientNodeKey("temp_threshold")
                        .targetClientNodeKey("cooler_off_actuator")
                        .sourcePort("false")
                        .targetPort("in")
                        .build()
        );

        return FlowCreateRequest.builder()
                .locationId(locationId != null ? locationId : 10L)
                .name("참/거짓 이중 분기 플로우")
                .description("임계값 만족 시 경보를 발생시키고, 미만족 시 냉방기를 끕니다.")
                .nodes(nodes)
                .links(links)
                .build();
    }

    //시나리오 6: 구조 검증 실패 순환 플로우 (SENSOR -> THRESHOLD <-> TIMER -> ALERT)
    public static FlowCreateRequest createCyclicInvalidFlowRequest(Long locationId) {
        List<FlowNodeRequest> nodes = List.of(
                FlowNodeRequest.builder()
                        .clientNodeKey("sensor")
                        .nodeType(NodeType.LOCATION)
                        .configuration(JsonNodeFactory.instance.objectNode())
                        .build(),
                FlowNodeRequest.builder()
                        .clientNodeKey("threshold")
                        .nodeType(NodeType.THRESHOLD)
                        .configuration(JsonNodeFactory.instance.objectNode())
                        .build(),
                FlowNodeRequest.builder()
                        .clientNodeKey("timer")
                        .nodeType(NodeType.TIMER)
                        .configuration(JsonNodeFactory.instance.objectNode())
                        .build(),
                FlowNodeRequest.builder()
                        .clientNodeKey("alert")
                        .nodeType(NodeType.ALERT)
                        .configuration(JsonNodeFactory.instance.objectNode())
                        .build()
        );

        List<FlowLinkRequest> links = List.of(
                FlowLinkRequest.builder()
                        .sourceClientNodeKey("sensor")
                        .targetClientNodeKey("threshold")
                        .sourcePort("out")
                        .targetPort("in")
                        .build(),
                FlowLinkRequest.builder()
                        .sourceClientNodeKey("threshold")
                        .targetClientNodeKey("timer")
                        .sourcePort("true")
                        .targetPort("in")
                        .build(),
                FlowLinkRequest.builder()
                        .sourceClientNodeKey("timer")
                        .targetClientNodeKey("threshold")
                        .sourcePort("true")
                        .targetPort("in")
                        .build(),
                FlowLinkRequest.builder()
                        .sourceClientNodeKey("timer")
                        .targetClientNodeKey("alert")
                        .sourcePort("false")
                        .targetPort("in")
                        .build()
        );

        return FlowCreateRequest.builder()
                .locationId(locationId != null ? locationId : 10L)
                .name("순환 구조 오류 플로우")
                .description("검증기에서 순환(Cycle) 오류로 거부되어야 하는 플로우입니다.")
                .nodes(nodes)
                .links(links)
                .build();
    }
}
