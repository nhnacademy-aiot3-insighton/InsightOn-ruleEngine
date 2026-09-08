package com.nhnacademy.insightonruleengine.runner.infrastructure.cache;

import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import java.util.List;
import java.util.Optional;

public interface FlowDefinitionCache {

    Optional<List<FlowDefinition>> find(Long groupId, Long locationId);

    void replace(Long groupId, Long locationId, List<FlowDefinition> definitions);

    void evict(Long groupId, Long locationId);
}
