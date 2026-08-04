package com.nhnacademy.insightonruleengine.flow.validation;

import com.nhnacademy.insightonruleengine.flow.dto.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.validation.FlowLinkValidator.LinkRulesResult;
import com.nhnacademy.insightonruleengine.flow.validation.FlowLinkValidator.LinkValidationResult;
import com.nhnacademy.insightonruleengine.flow.validation.FlowNodeValidator.NodeRoleValidationResult;
import com.nhnacademy.insightonruleengine.flow.validation.FlowNodeValidator.NodeValidationResult;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

//DB 저장 전 요청 검증
@Component
@RequiredArgsConstructor
public class FlowStructureValidator {

    private final FlowLinkValidator linkValidator;
    private final FlowNodeValidator nodeValidator;

    //기본 연결 규칙, 참조, 연결 탐색 순서로 검증 오류를 수집합니다.
    public List<FlowStructureValidationError> validate(
            List<FlowNodeRequest> nodes,
            List<FlowLinkRequest> links
    ) {
        List<FlowStructureValidationError> errors = new ArrayList<>();
        NodeValidationResult nodeResult = nodeValidator.validate(nodes);
        errors.addAll(nodeResult.errors());

        LinkValidationResult linkResult = linkValidator.validate(
                links,
                nodeResult.nodesByKey()
        );
        errors.addAll(linkResult.errors());

        NodeRoleValidationResult nodeRoleResult = nodeValidator.validateRoles(nodeResult);
        errors.addAll(nodeRoleResult.errors());

        LinkRulesResult linkRules = linkValidator.validateLinkRules(
                linkResult,
                nodeResult.nodesByKey()
        );
        errors.addAll(linkRules.errors());
        errors.addAll(
                linkValidator.validateMissingOutputLinks(
                        nodeResult,
                        linkResult,
                        linkRules.sourceNodeKey()
                )
        );
        return List.copyOf(errors);
    }
}
