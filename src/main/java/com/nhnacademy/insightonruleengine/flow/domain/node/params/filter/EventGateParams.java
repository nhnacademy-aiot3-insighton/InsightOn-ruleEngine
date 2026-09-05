package com.nhnacademy.insightonruleengine.flow.domain.node.params.filter;

import com.nhnacademy.insightonruleengine.flow.domain.node.params.NodeParams;
import jakarta.validation.constraints.Min;

/**
 * node_type = EVENT_GATE.
 * 이 지점에 도달한 실행을 "몇 번 쌓였을 때"와 "얼마나 자주"로 걸러 Action 실행을 통제한다.
 *
 * <p>판정 순서는 쿨다운 확인 → 횟수 증가 → 도달 시 초기화 → 쿨다운 시작이며, Redis Lua 한 번으로
 * 원자적으로 처리한다. 쿨다운 중에는 횟수를 세지 않으므로, 억제가 끝난 뒤 처음부터 다시 쌓인다.
 *
 * <p>{@code requiredCount}가 1이면 "한 번 통과 후 {@code cooldownSeconds}만큼 차단"하는
 * 최소 실행 간격으로 동작한다. 2 이상이면 {@code countWindowSeconds} 안에 그만큼 도달해야 통과한다.
 *
 * @param requiredCount   통과에 필요한 도달 횟수
 * @param countWindowSeconds 도달 횟수를 세는 시간 창. requiredCount가 1이면 쓰이지 않아 생략할 수 있다
 * @param cooldownSeconds 통과 뒤 다시 통과시키지 않을 시간. 0이면 억제하지 않는다
 */
public record EventGateParams(
        @Min(1) Integer requiredCount,
        @Min(1) Integer countWindowSeconds,
        @Min(0) Integer cooldownSeconds
) implements NodeParams {

    public EventGateParams {
        if (requiredCount == null) {
            throw new IllegalArgumentException("requiredCount는 필수입니다.");
        }
        if (cooldownSeconds == null) {
            throw new IllegalArgumentException("cooldownSeconds는 필수입니다.");
        }
        if (requiredCount >= 2 && (countWindowSeconds == null || countWindowSeconds <= 0)) {
            throw new IllegalArgumentException(
                    "requiredCount가 2 이상이면 countWindowSeconds는 양수여야 합니다."
            );
        }
        if (requiredCount == 1 && cooldownSeconds == 0) {
            throw new IllegalArgumentException(
                    "requiredCount 1과 cooldownSeconds 0은 아무것도 걸러내지 않습니다. "
                            + "Node를 두지 않는 것과 같으므로 허용하지 않습니다."
            );
        }
    }

    /** requiredCount가 1이면 시간 창을 쓰지 않으므로 0으로 넘긴다. */
    public int effectiveCountWindowSeconds() {
        return countWindowSeconds == null ? 0 : countWindowSeconds;
    }
}
