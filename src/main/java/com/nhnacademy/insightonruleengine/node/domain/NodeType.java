package com.nhnacademy.insightonruleengine.node.domain;

import com.nhnacademy.insightonruleengine.node.domain.params.*;

/**
 *
 */
public enum NodeType {

    SENSOR(Category.TRIGGER, SensorParams.class, PortSchema.fixed("out")),
    SCHEDULE(Category.TRIGGER, ScheduleParams.class, PortSchema.fixed("out")),

    THRESHOLD(Category.FILTER, ThresholdParams.class, PortSchema.fixed("true", "false")),
    TIME_WINDOW(Category.FILTER, TimeWindowParams.class, PortSchema.fixed("true", "false")),
    TIMER(Category.FILTER, TimerParams.class, PortSchema.fixed("true", "false")),

    DEVICE_CONTROL(Category.ACTION, DeviceControlParams.class, PortSchema.terminal()),
    ALERT(Category.ACTION, AlertParams.class, PortSchema.terminal()),
    AI_SUGGESTION(Category.ACTION, AiSuggestionParams.class, PortSchema.terminal()),
    EXTERNAL_NOTIFICATION(Category.ACTION, ExternalNotificationParams.class, PortSchema.terminal());

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
