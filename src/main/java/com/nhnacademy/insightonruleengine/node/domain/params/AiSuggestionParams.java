package com.nhnacademy.insightonruleengine.node.domain.params;


/**
 * node_type = AI_SUGGESTION.
 * AI 서비스로 실행 컨텍스트를 이벤트로 발행한다(비동기). Feign이 아님에 주의.
 * 이후 네이밍 변경 필요할 것으로 보임
 */
public record AiSuggestionParams(
        String context
) implements NodeParams {
}
