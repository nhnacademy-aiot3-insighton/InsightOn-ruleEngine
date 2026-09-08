package com.nhnacademy.insightonruleengine.flow.domain.definition;

import com.fasterxml.jackson.databind.JsonNode;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;

public record NodeDefinition(
        Long nodeId,
        NodeType nodeType,
        JsonNode configuration
) {
    // 생성자 입력과 접근자 모두에 deepCopy를 해줌
    // deepCopy == JsonNode 내부의 객체와 배열까지 재귀적으로 복사해서 외부에서 원본 Json을 수정해도 값이 바뀌지 않게 막아줌
    public NodeDefinition {

            if (nodeId == null) {
                throw new IllegalArgumentException("nodeId는 필수입니다.");
            }
            if (nodeType == null) {
                throw new IllegalArgumentException("nodeType은 필수입니다.");
            }
            if (configuration == null) {
                throw new IllegalArgumentException("configuration은 필수입니다.");
            }

            configuration = configuration.deepCopy();
    }

    @Override
    public JsonNode configuration() {
        return configuration.deepCopy();
    }
}
