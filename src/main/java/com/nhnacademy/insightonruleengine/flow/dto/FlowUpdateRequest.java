package com.nhnacademy.insightonruleengine.flow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Flow 수정에 필요한 이름과 설명을 받습니다.
public record FlowUpdateRequest(
        @NotBlank
        @Size(max = 100)
        String name,
        String description
        //List<NodeRequest> nodes,
        //List<LinkRequest> links
) {
}
