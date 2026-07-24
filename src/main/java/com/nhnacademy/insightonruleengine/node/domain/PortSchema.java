package com.nhnacademy.insightonruleengine.node.domain;


import com.nhnacademy.insightonruleengine.node.domain.params.NodeParams;
import java.util.Set;

/**
 * NodeType이 가질 수 있는 출력 포트 집합을 선언한다.
 *
 * <p>대부분의 타입(THRESHOLD, TIME_WINDOW 등)은 params와 무관하게 고정된 포트("true"/"false")를 가지는데,
 * 라우팅형 노드처럼 노드 인스턴스마다 포트 집합이 달라지는 타입은 {@link #outputPorts}가 params를 읽어 동적으로
 * 계산할 수 있다. 그래서 Category(구조적 역할)가 아니라 NodeType(그리고 필요하면 params) 레벨에 이 책임을 둔다.
 */
@FunctionalInterface
public interface PortSchema {

    /**
     * @param params 이 노드의 파싱된 파라미터. 고정 포트 타입은 무시해도 된다.
     */
    Set<String> outputPorts(NodeParams params);

    /** 항상 같은 포트 집합을 갖는 타입용 (예: TRIGGER의 "out", FILTER의 "true"/"false"). */
    static PortSchema fixed(String... ports) {
        Set<String> fixedPorts = Set.of(ports);
        return params -> fixedPorts;
    }

    /** 출력이 없는 타입용 (ACTION 계열).
     *
     */
    static PortSchema terminal() {
        return params -> Set.of();
    }

}
