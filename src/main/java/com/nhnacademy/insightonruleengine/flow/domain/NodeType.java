package com.nhnacademy.insightonruleengine.flow.domain;

import com.nhnacademy.insightonruleengine.flow.domain.node.params.NodeParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.action.ActuatorControlParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.action.AlertParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.filter.EventGateParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.filter.ThresholdParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.filter.TimeWindowParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.trigger.LocationParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.trigger.ScheduleParams;
import com.nhnacademy.insightonruleengine.flow.domain.node.params.trigger.SensorParams;

/**
 *
 */
public enum NodeType {

    SENSOR(Category.TRIGGER, SensorParams.class, PortSchema.fixed("out")),
    LOCATION(Category.TRIGGER, LocationParams.class, PortSchema.fixed("out")),
    SCHEDULE(Category.TRIGGER, ScheduleParams.class, PortSchema.fixed("out")),

    THRESHOLD(Category.FILTER, ThresholdParams.class, PortSchema.booleanPorts()),
    TIME_WINDOW(Category.FILTER, TimeWindowParams.class, PortSchema.booleanPorts()),
    EVENT_GATE(Category.FILTER, EventGateParams.class, PortSchema.booleanPorts()),

    ACTUATOR_CONTROL(Category.ACTION, ActuatorControlParams.class, PortSchema.terminal()),
    ALERT(Category.ACTION, AlertParams.class, PortSchema.terminal());

    private final Category category;
    private final Class<? extends NodeParams> paramsType;
    private final PortSchema portSchema;

    NodeType(Category category, Class<? extends NodeParams> paramsType, PortSchema portSchema) {
        this.category = category;
        this.paramsType = paramsType;
        this.portSchema = portSchema;
    }

    public Category getCategory() {
        return category;
    }

    public Class<? extends NodeParams> getParamsType() {
        return paramsType;
    }

    public PortSchema getPortSchema() {
        return portSchema;
    }

    public enum Category {
        TRIGGER, FILTER, ACTION
    }
}
