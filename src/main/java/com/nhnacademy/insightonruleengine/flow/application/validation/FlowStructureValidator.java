package com.nhnacademy.insightonruleengine.flow.application.validation;

import com.nhnacademy.insightonruleengine.flow.api.dto.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.application.validation.FlowLinkValidator.LinkReferenceResult;
import com.nhnacademy.insightonruleengine.flow.application.validation.FlowLinkValidator.LinkRulesResult;
import com.nhnacademy.insightonruleengine.flow.application.validation.FlowNodeValidator.NodeRoleValidationResult;
import com.nhnacademy.insightonruleengine.flow.application.validation.LinkValidator.LinkValidationResult;
import com.nhnacademy.insightonruleengine.flow.application.validation.NodeValidator.NodeValidationResult;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStructureValidationError;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

//DB 저장 전 요청 검증
@Component
@RequiredArgsConstructor
public class FlowStructureValidator {

    private final NodeValidator nodeValidator;
    private final LinkValidator linkValidator;
    private final FlowNodeValidator flowNodeValidator;
    private final FlowLinkValidator flowLinkValidator;
    private final FlowPathValidator flowPathValidator;

    //기본 연결 규칙, 참조, 연결 탐색 순서로 검증 오류를 수집합니다.
    public List<FlowStructureValidationError> validate(
            List<FlowNodeRequest> nodes,
            List<FlowLinkRequest> links
    ) {
        List<FlowStructureValidationError> errors = new ArrayList<>();

        NodeValidationResult nodeResult = nodeValidator.validate(nodes);
        errors.addAll(nodeResult.errors());

        LinkValidationResult linkResult = linkValidator.validate(links);
        errors.addAll(linkResult.errors());

        LinkReferenceResult linkRefResult = flowLinkValidator.validateLinkReferences(
                linkResult,
                nodeResult.nodesByKey()
        );
        errors.addAll(linkRefResult.errors());

        NodeRoleValidationResult nodeRoleResult = flowNodeValidator.validateRoles(nodeResult);
        errors.addAll(nodeRoleResult.errors());

        LinkRulesResult linkRules = flowLinkValidator.validateBusinessRules(
                linkRefResult,
                nodeResult.nodesByKey()
        );
        errors.addAll(linkRules.errors());

        errors.addAll(
                flowLinkValidator.validateMissingOutputLinks(
                        nodeResult,
                        linkRefResult,
                        linkRules.sourceNodeKeys()
                )
        );

        boolean canValidateConnections = nodeResult.canValidateConnections()
                && linkResult.canValidateConnections()
                && linkRefResult.canValidateConnections()
                && linkRules.canValidateConnections()
                && nodeRoleResult.hasValidNodeRoles();
        if (canValidateConnections) {
            errors.addAll(
                    flowPathValidator.validate(
                            nodeResult.nodesByKey(),
                            flowLinkValidator.extractLinks(linkRefResult)
                    )
            );
        }

        return List.copyOf(errors);
    }
}
