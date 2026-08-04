package com.nhnacademy.insightonruleengine.runner;

import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.runner.dto.SensorEvent;
import java.util.List;

public interface FlowRouter {
    List<FlowDefinition> route(SensorEvent event);
}
