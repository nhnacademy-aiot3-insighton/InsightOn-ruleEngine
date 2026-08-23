package com.nhnacademy.insightonruleengine.node.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.action.ActuatorControlParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.action.AlertParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.action.ExternalNotificationParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.filter.ThresholdParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.filter.TimeWindowParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.filter.TimerParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.trigger.LocationParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.trigger.ScheduleParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.trigger.SensorParams;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NodeTypeTest {

    @Test
    @DisplayName("NodeType은 올바른 category를 가진다")
    void categoryMapping() {
        Map<NodeType, NodeType.Category> expectedCategories = Map.of(
                NodeType.SENSOR, NodeType.Category.TRIGGER,
                NodeType.LOCATION, NodeType.Category.TRIGGER,
                NodeType.SCHEDULE, NodeType.Category.TRIGGER,
                NodeType.THRESHOLD, NodeType.Category.FILTER,
                NodeType.TIME_WINDOW, NodeType.Category.FILTER,
                NodeType.TIMER, NodeType.Category.FILTER,
                NodeType.ACTUATOR_CONTROL, NodeType.Category.ACTION,
                NodeType.ALERT, NodeType.Category.ACTION,
                NodeType.EXTERNAL_NOTIFICATION, NodeType.Category.ACTION
        );

        assertCoversAllNodeTypes(expectedCategories);
        expectedCategories.forEach((nodeType, category) ->
                assertEquals(category, nodeType.getCategory()));
    }

    @Test
    @DisplayName("NodeType은 올바른 paramsType을 가진다")
    void paramsTypeMapping() {
        Map<NodeType, Class<?>> expectedTypes = Map.of(
                NodeType.SENSOR, SensorParams.class,
                NodeType.LOCATION, LocationParams.class,
                NodeType.SCHEDULE, ScheduleParams.class,
                NodeType.THRESHOLD, ThresholdParams.class,
                NodeType.TIME_WINDOW, TimeWindowParams.class,
                NodeType.TIMER, TimerParams.class,
                NodeType.ACTUATOR_CONTROL, ActuatorControlParams.class,
                NodeType.ALERT, AlertParams.class,
                NodeType.EXTERNAL_NOTIFICATION, ExternalNotificationParams.class
        );

        assertCoversAllNodeTypes(expectedTypes);
        expectedTypes.forEach((nodeType, paramsType) ->
                assertEquals(paramsType, nodeType.getParamsType()));
    }

    @Test
    @DisplayName("NodeType은 올바른 출력 포트 스키마를 가진다")
    void outputPorts() {
        Map<NodeType, Set<String>> expectedPorts = Map.of(
                NodeType.SENSOR, Set.of("out"),
                NodeType.LOCATION, Set.of("out"),
                NodeType.SCHEDULE, Set.of("out"),
                NodeType.THRESHOLD, Set.of("true", "false"),
                NodeType.TIME_WINDOW, Set.of("true", "false"),
                NodeType.TIMER, Set.of("true", "false"),
                NodeType.ACTUATOR_CONTROL, Set.of(),
                NodeType.ALERT, Set.of(),
                NodeType.EXTERNAL_NOTIFICATION, Set.of()
        );

        assertCoversAllNodeTypes(expectedPorts);
        expectedPorts.forEach((nodeType, ports) ->
                assertEquals(ports, nodeType.getPortSchema().outputPorts(null)));
    }

    private void assertCoversAllNodeTypes(Map<NodeType, ?> expectedMappings) {
        assertEquals(Set.of(NodeType.values()), expectedMappings.keySet());
    }
}
