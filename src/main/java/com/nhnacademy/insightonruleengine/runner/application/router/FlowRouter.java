package com.nhnacademy.insightonruleengine.runner.application.router;

import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.runner.model.SensorEvent;
import java.util.List;

public interface FlowRouter {
    List<FlowDefinition> route(SensorEvent event);
}
