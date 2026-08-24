package com.nhnacademy.insightonruleengine.runner.action;

import com.nhnacademy.insightonruleengine.runner.action.dto.AiSuggestionActionEvent;
import com.nhnacademy.insightonruleengine.runner.action.dto.EngineAlertActionEvent;

public interface ActionPublisher {

    void publishAlert(EngineAlertActionEvent event);

    void publishSuggestion(AiSuggestionActionEvent event);
}
