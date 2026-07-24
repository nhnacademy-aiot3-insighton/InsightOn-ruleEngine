package com.nhnacademy.insightonruleengine.flow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 수정할 이름과 설명만 받아 기존 Flow의 그룹과 장소를 그대로 이어받는다.
public record FlowUpdateRequest(
        @NotBlank
        @Size(max = 100)
        String name,
        String description
) {
}
