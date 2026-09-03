package com.nhnacademy.insightonruleengine.flow.application.validation;

import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowStructureValidationError;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * DB 저장 전 요청의 구조를 검증한다.
 *
 * <p>필수값을 확인해 그래프로 옮긴 뒤({@link FlowRequestFieldValidator}), 그래프 규칙을 확인한다
 * ({@link FlowGraphValidator}). 필수값 오류와 그래프 오류를 한 번에 모아 사용자가 여러 번 왕복하지 않게 한다.
 */
@Component
@RequiredArgsConstructor
public class FlowStructureValidator {

    private final FlowRequestFieldValidator flowRequestFieldValidator;
    private final FlowGraphValidator flowGraphValidator;

    public List<FlowStructureValidationError> validate(
            List<FlowNodeRequest> nodes,
            List<FlowLinkRequest> links
    ) {
        FlowRequestFieldValidator.Result fieldResult = flowRequestFieldValidator.validate(nodes, links);

        List<FlowStructureValidationError> errors = new ArrayList<>(fieldResult.errors());
        errors.addAll(flowGraphValidator.validate(
                fieldResult.graph(),
                fieldResult.nodeFieldsValid(),
                fieldResult.linkFieldsValid()
        ));
        return List.copyOf(errors);
    }
}
