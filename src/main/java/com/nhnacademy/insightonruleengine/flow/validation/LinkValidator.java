package com.nhnacademy.insightonruleengine.flow.validation;

import com.nhnacademy.insightonruleengine.flow.dto.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.validation.domain.FlowStructureValidationError;
import com.nhnacademy.insightonruleengine.flow.validation.domain.FlowValidationErrorReason;
import com.nhnacademy.insightonruleengine.flow.validation.domain.LinkErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

//단일 링크 필수값 및 유효성을 검사합니다.
@Component
public class LinkValidator {

    //링크 필수값을 확인해 유효한 링크를 모아줍니다.
    public LinkValidationResult validate(List<FlowLinkRequest> links) {
        List<FlowStructureValidationError> errors = new ArrayList<>();
        if (links == null || links.isEmpty()) {
            addError(
                    errors,
                    LinkErrorCode.EMPTY_LINKS,
                    null,
                    "links",
                    "링크는 필수입니다."
            );
            return new LinkValidationResult(List.of(), false, List.copyOf(errors));
        }
        List<IndexedLink> indexedLinks = new ArrayList<>();
        boolean canValidateConnections = true;

        for (int i = 0; i < links.size(); i++) {
            FlowLinkRequest link = links.get(i);
            String fieldPath = "links[" + i + "]";
            if (link == null) {
                addError(
                        errors,
                        LinkErrorCode.NULL_LINK,
                        null,
                        fieldPath,
                        "링크는 null일 수 없습니다."
                );
                canValidateConnections = false;
                continue;
            }
            if (!validateLinkRequiredFields(link, fieldPath, errors)) {
                canValidateConnections = false;
                continue;
            }
            indexedLinks.add(new IndexedLink(i, link));
        }
        return new LinkValidationResult(
                List.copyOf(indexedLinks),
                canValidateConnections,
                List.copyOf(errors));
    }

    //누락된 링크 필드를 모두 기록한 뒤 검증이 가능한지 알려줍니다.
    public boolean validateLinkRequiredFields(
            FlowLinkRequest link,
            String fieldPath,
            List<FlowStructureValidationError> errors
    ) {
        boolean hasAllRequiredFields = true;
        if (link.sourceClientNodeKey() == null || link.sourceClientNodeKey().isBlank()) {
            addError(
                    errors,
                    LinkErrorCode.MISSING_SOURCE_CLIENT_NODE_KEY,
                    null,
                    fieldPath + ".sourceClientNodeKey",
                    "sourceClientNodeKey는 필수입니다."
            );
            hasAllRequiredFields = false;
        }
        if (link.targetClientNodeKey() == null || link.targetClientNodeKey().isBlank()) {
            addError(
                    errors,
                    LinkErrorCode.MISSING_TARGET_CLIENT_NODE_KEY,
                    null,
                    fieldPath + ".targetClientNodeKey",
                    "targetClientNodeKey는 필수입니다."
            );
            hasAllRequiredFields = false;
        }
        if (link.sourcePort() == null || link.sourcePort().isBlank()) {
            addError(
                    errors,
                    LinkErrorCode.MISSING_SOURCE_PORT,
                    link.sourceClientNodeKey(),
                    fieldPath + ".sourcePort",
                    "sourcePort는 필수입니다."
            );
            hasAllRequiredFields = false;
        }
        if (link.targetPort() == null || link.targetPort().isBlank()) {
            addError(
                    errors,
                    LinkErrorCode.MISSING_TARGET_PORT,
                    link.targetClientNodeKey(),
                    fieldPath + ".targetPort",
                    "targetPort는 필수입니다."
            );
            hasAllRequiredFields = false;
        }
        return hasAllRequiredFields;
    }

    private void addError(
            List<FlowStructureValidationError> errors,
            FlowValidationErrorReason code,
            String clientNodeKey,
            String fieldPath,
            String message
    ) {
        errors.add(new FlowStructureValidationError(code, clientNodeKey, fieldPath, message));
    }

    public record LinkValidationResult(
            List<IndexedLink> indexedLinks,
            boolean canValidateConnections,
            List<FlowStructureValidationError> errors
    ) {
    }

    public record IndexedLink(
            int requestIndex,
            FlowLinkRequest link
    ) {
    }
}
