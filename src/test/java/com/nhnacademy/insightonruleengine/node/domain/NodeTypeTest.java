package com.nhnacademy.insightonruleengine.node.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nhnacademy.insightonruleengine.node.domain.params.action.ActuatorControlParams;
import com.nhnacademy.insightonruleengine.node.domain.params.action.AiSuggestionParams;
import com.nhnacademy.insightonruleengine.node.domain.params.action.AlertParams;
import com.nhnacademy.insightonruleengine.node.domain.params.action.ExternalNotificationParams;
import com.nhnacademy.insightonruleengine.node.domain.params.filter.ThresholdParams;
import com.nhnacademy.insightonruleengine.node.domain.params.filter.TimeWindowParams;
import com.nhnacademy.insightonruleengine.node.domain.params.filter.TimerParams;
import com.nhnacademy.insightonruleengine.node.domain.params.trigger.ScheduleParams;
import com.nhnacademy.insightonruleengine.node.domain.params.trigger.SensorParams;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NodeTypeTest {

    @Test
    @DisplayName("NodeType은 올바른 category를 가진다")
    void categoryMapping() {
        assertEquals(NodeType.Category.TRIGGER, NodeType.SENSOR.getCategory());
        assertEquals(NodeType.Category.TRIGGER, NodeType.SCHEDULE.getCategory());
        assertEquals(NodeType.Category.FILTER, NodeType.THRESHOLD.getCategory());
        assertEquals(NodeType.Category.FILTER, NodeType.TIME_WINDOW.getCategory());
        assertEquals(NodeType.Category.FILTER, NodeType.TIMER.getCategory());
        assertEquals(NodeType.Category.ACTION, NodeType.ACTUATOR_CONTROL.getCategory());
        assertEquals(NodeType.Category.ACTION, NodeType.ALERT.getCategory());
        assertEquals(NodeType.Category.ACTION, NodeType.AI_SUGGESTION.getCategory());
        assertEquals(NodeType.Category.ACTION, NodeType.EXTERNAL_NOTIFICATION.getCategory());
    }

    @Test
    @DisplayName("NodeType은 올바른 paramsType을 가진다")
    void paramsTypeMapping() {
        Map<NodeType, Class<?>> expectedTypes = Map.of(
                NodeType.SENSOR, SensorParams.class,
                NodeType.SCHEDULE, ScheduleParams.class,
                NodeType.THRESHOLD, ThresholdParams.class,
                NodeType.TIME_WINDOW, TimeWindowParams.class,
                NodeType.TIMER, TimerParams.class,
                NodeType.ACTUATOR_CONTROL, ActuatorControlParams.class,
                NodeType.ALERT, AlertParams.class,
                NodeType.AI_SUGGESTION, AiSuggestionParams.class,
                NodeType.EXTERNAL_NOTIFICATION, ExternalNotificationParams.class
        );

        expectedTypes.forEach((nodeType, paramsType) ->
                assertEquals(paramsType, nodeType.getParamsType()));
    }

    @Test
    @DisplayName("Trigger Node는 out 출력 포트를 가진다")
    void triggerOutputPorts() {
        assertEquals(Set.of("out"), NodeType.SENSOR.getPortSchema().outputPorts(null));
        assertEquals(Set.of("out"), NodeType.SCHEDULE.getPortSchema().outputPorts(null));
    }

    @Test
    @DisplayName("Filter Node는 true, false 출력 포트를 가진다")
    void filterOutputPorts() {
        assertEquals(Set.of("true", "false"), NodeType.THRESHOLD.getPortSchema().outputPorts(null));
        assertEquals(Set.of("true", "false"), NodeType.TIME_WINDOW.getPortSchema().outputPorts(null));
        assertEquals(Set.of("true", "false"), NodeType.TIMER.getPortSchema().outputPorts(null));
    }

    @Test
    @DisplayName("Action Node는 terminal 포트 스키마를 가진다")
    void actionOutputPorts() {
        assertTrue(NodeType.ACTUATOR_CONTROL.getPortSchema().outputPorts(null).isEmpty());
        assertTrue(NodeType.ALERT.getPortSchema().outputPorts(null).isEmpty());
        assertTrue(NodeType.AI_SUGGESTION.getPortSchema().outputPorts(null).isEmpty());
        assertTrue(NodeType.EXTERNAL_NOTIFICATION.getPortSchema().outputPorts(null).isEmpty());
    }
}
