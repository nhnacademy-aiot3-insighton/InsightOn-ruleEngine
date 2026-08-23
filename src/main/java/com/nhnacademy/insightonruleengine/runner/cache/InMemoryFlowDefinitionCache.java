package com.nhnacademy.insightonruleengine.runner.cache;

import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 단일 인스턴스 또는 로컬 테스트용 Flow 정의 캐시다.
 * ActiveFlowDefinitionProvider가 사용하는 계약은 Redis 구현체와 동일하다.
 */
@Component
@ConditionalOnProperty(name = "rule-engine.flow-cache.type", havingValue = "in-memory")
public class InMemoryFlowDefinitionCache implements FlowDefinitionCache {

    private final Map<RouteKey, List<FlowDefinition>> routes = new ConcurrentHashMap<>();

    @Override
    public Optional<List<FlowDefinition>> find(Long groupId, Long locationId) {
        return Optional.ofNullable(routes.get(new RouteKey(groupId, locationId)));
    }

    @Override
    public void replace(Long groupId, Long locationId, List<FlowDefinition> definitions) {
        routes.put(new RouteKey(groupId, locationId), List.copyOf(definitions));
    }

    @Override
    public void evict(Long groupId, Long locationId) {
        routes.remove(new RouteKey(groupId, locationId));
    }

    private record RouteKey(Long groupId, Long locationId) {
    }
}
