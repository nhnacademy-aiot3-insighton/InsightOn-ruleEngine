package com.nhnacademy.insightonruleengine.flow.domain.node.params.trigger;

import com.nhnacademy.insightonruleengine.flow.domain.node.params.NodeParams;

/**
 * node_type = LOCATION.
 * LocationNode는 사용자 선택형 source/aggregation policy를 갖지 않는다.
 *
 * <p>현재 공간 metric 정책은 모든 센서를 포함하고, 동일 metric은 최신 이벤트 값을 사용한다.
 */
public record LocationParams(
) implements NodeParams {
}
