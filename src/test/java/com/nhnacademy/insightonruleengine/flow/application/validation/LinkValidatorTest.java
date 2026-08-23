package com.nhnacademy.insightonruleengine.flow.application.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.application.validation.LinkValidator;
import com.nhnacademy.insightonruleengine.flow.application.validation.LinkValidator.LinkValidationResult;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowStructureValidationError;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.FlowValidationErrorReason;
import com.nhnacademy.insightonruleengine.flow.application.validation.model.LinkErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LinkValidatorTest {

    private final LinkValidator validator = new LinkValidator();

    @Test
    @DisplayName("Link 목록이 없으면 빈 목록 오류를 반환합니다.")
    void EmptyLinkListTest() {
        LinkValidationResult actual = validator.validate(null);

        assertEquals(List.of(LinkErrorCode.EMPTY_LINKS), errorCodes(actual.errors()));
        assertTrue(actual.indexedLinks().isEmpty());
        assertFalse(actual.canValidateConnections());
    }

    @Test
    @DisplayName("null Link 요소의 요청 위치를 반환합니다.")
    void NullLinkTest() {
        List<FlowLinkRequest> links = new ArrayList<>();
        links.add(null);

        LinkValidationResult actual = validator.validate(links);

        assertEquals(List.of(LinkErrorCode.NULL_LINK), errorCodes(actual.errors()));
        assertEquals("links[0]", actual.errors().getFirst().fieldPath());
        assertFalse(actual.canValidateConnections());
    }

    @Test
    @DisplayName("링크 필수값 누락을 반환합니다.")
    void returnsErrorsLinkTest() {
        FlowLinkRequest link = FlowLinkRequest.builder()
                .sourceClientNodeKey("trigger")
                .targetClientNodeKey("action")
                .build();

        LinkValidationResult actual = validator.validate(List.of(link));

        assertEquals(
                List.of(LinkErrorCode.MISSING_SOURCE_PORT, LinkErrorCode.MISSING_TARGET_PORT),
                errorCodes(actual.errors())
        );
        assertTrue(actual.indexedLinks().isEmpty());
        assertFalse(actual.canValidateConnections());
    }

    private List<FlowValidationErrorReason> errorCodes(List<FlowStructureValidationError> errors) {
        return errors.stream()
                .map(FlowStructureValidationError::code)
                .toList();
    }
}
