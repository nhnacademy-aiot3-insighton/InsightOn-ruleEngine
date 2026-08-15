package com.nhnacademy.insightonruleengine.flow.event;

import java.util.Set;

//PostgreSQL 커밋 뒤 Redis 실행 정보를 동기화하는 데 필요한 불변 정보입니다.
public record FlowRuntimeChangeEvent(
        Long groupId,
        Long locationId,
        Long flowId,
        FlowRuntimeChangeType changeType,
        Set<Long> runtimeNodeIds
) {
    public FlowRuntimeChangeEvent {
        runtimeNodeIds = runtimeNodeIds != null ? Set.copyOf(runtimeNodeIds) : Set.of();
    }

    public static FlowRuntimeChangeEvent activate(Long groupId, Long locationId, Long flowId) {
        return new FlowRuntimeChangeEvent(groupId, locationId, flowId, FlowRuntimeChangeType.ACTIVATE, Set.of());
    }

    public static FlowRuntimeChangeEvent remove(
            Long groupId,
            Long locationId,
            Long flowId,
            Set<Long> runtimeNodeIds
    ) {
        return new FlowRuntimeChangeEvent(
                groupId,
                locationId,
                flowId,
                FlowRuntimeChangeType.REMOVE,
                runtimeNodeIds
        );
    }
}
