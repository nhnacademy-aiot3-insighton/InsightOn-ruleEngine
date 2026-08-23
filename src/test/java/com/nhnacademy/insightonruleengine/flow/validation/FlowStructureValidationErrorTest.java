package com.nhnacademy.insightonruleengine.flow.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nhnacademy.insightonruleengine.flow.domain.FlowStructureErrorCode;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStructureValidationError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FlowStructureValidationErrorTest {

    // 검증 결과가 분류, 위치, 설명 중 어느 정보도 잃지 않게 한다.
    @Test
    @DisplayName("오류가 생성할 때 전달한 값을 모두 보존한다")
    void preservationTest() {
        FlowStructureValidationError error = new FlowStructureValidationError(
                FlowStructureErrorCode.INVALID_PORT,
                "filter-1",
                "links[0].sourcePort",
                "Source Port가 Node Type과 맞지 않습니다."
        );

        assertEquals(FlowStructureErrorCode.INVALID_PORT, error.code());
        assertEquals("filter-1", error.clientNodeKey());
        assertEquals("links[0].sourcePort", error.fieldPath());
        assertEquals("Source Port가 Node Type과 맞지 않습니다.", error.message());
    }

    // 원인을 분류할 수 없는 검증 결과가 목록에 포함되지 않게 한다.
    @Test
    @DisplayName("오류 코드가 없으면 생성할 수 없다")
    void NullErrorCodeTest() {
        assertThrows(IllegalArgumentException.class, () -> new FlowStructureValidationError(
                null,
                "filter-1",
                "links[0].sourcePort",
                "잘못된 Port입니다."
        ));
    }

    @Test
    @DisplayName("관련 Node가 없는 전체 연결 오류는 null Node Key를 허용한다")
        // Cycle처럼 특정 Node 하나로 한정할 수 없는 오류도 같은 결과 모델로 표현하게 한다.
    void allowsNodeKeyTest() {
        FlowStructureValidationError error = new FlowStructureValidationError(
                FlowStructureErrorCode.CYCLE_DETECTED,
                null,
                "links",
                "Flow의 Node와 Link 연결에 Cycle이 있습니다."
        );

        assertNull(error.clientNodeKey());
    }

    // 수정할 요청 위치가 누락된 검증 결과를 공백과 동일하게 차단한다.
    @Test
    @DisplayName("필드 경로가 null이면 생성할 수 없다")
    void NullFieldPathTest() {
        assertThrows(IllegalArgumentException.class, () -> new FlowStructureValidationError(
                FlowStructureErrorCode.INVALID_PORT,
                "filter-1",
                null,
                "잘못된 Port입니다."
        ));
    }

    // 수정할 요청 위치가 없는 불완전한 검증 결과를 차단한다.
    @Test
    @DisplayName("필드 경로가 비어 있으면 생성할 수 없다")
    void BlankFieldPathTest() {
        assertThrows(IllegalArgumentException.class, () -> new FlowStructureValidationError(
                FlowStructureErrorCode.INVALID_PORT,
                "filter-1",
                " ",
                "잘못된 Port입니다."
        ));
    }

    // 사용자에게 실패 원인을 전달하지 못하는 null 설명을 생성 시점에 차단합니다.
    @Test
    @DisplayName("오류 메시지가 null이면 생성할 수 없다")
    void NullMessageTest() {
        assertThrows(IllegalArgumentException.class, () -> new FlowStructureValidationError(
                FlowStructureErrorCode.INVALID_PORT,
                "filter-1",
                "links[0].sourcePort",
                null
        ));
    }

    // 사용자에게 원인을 설명할 수 없는 검증 결과를 차단합니다.
    @Test
    @DisplayName("오류 메시지가 비어 있으면 생성할 수 없다")
    void BlankMessageTest() {
        assertThrows(IllegalArgumentException.class, () -> new FlowStructureValidationError(
                FlowStructureErrorCode.INVALID_PORT,
                "filter-1",
                "links[0].sourcePort",
                " "
        ));
    }
}
