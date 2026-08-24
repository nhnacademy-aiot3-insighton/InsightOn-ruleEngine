package com.nhnacademy.insightonruleengine.runner.application.action;

import com.nhnacademy.insightonruleengine.runner.model.action.AiSuggestionActionEvent;
import com.nhnacademy.insightonruleengine.runner.model.action.EngineAlertActionEvent;

public interface ActionPublisher {

    void publishAlert(EngineAlertActionEvent event);

    void publishSuggestion(AiSuggestionActionEvent event);
}
