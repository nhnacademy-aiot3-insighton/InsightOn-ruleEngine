package com.nhnacademy.insightonruleengine.runner.execution.executor;

import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NodeExecutorRegistry {

    private final Map<NodeType, NodeExecutor> executors;

    public NodeExecutorRegistry(List<NodeExecutor> nodeExecutors) {
        Map<NodeType, NodeExecutor> mapped = new EnumMap<>(NodeType.class);
        for (NodeExecutor executor : nodeExecutors) {
            NodeExecutor duplicate = mapped.putIfAbsent(executor.nodeType(), executor);
            if (duplicate != null) {
                throw new IllegalStateException("중복 NodeExecutor입니다: " + executor.nodeType());
            }
        }
        executors = Map.copyOf(mapped);
    }

    public NodeExecutor get(NodeType nodeType) {
        if (nodeType == null) {
            throw new IllegalArgumentException("nodeType은 필수입니다.");
        }
        NodeExecutor executor = executors.get(nodeType);
        if (executor == null) {
            throw new IllegalArgumentException("지원하지 않는 NodeType입니다: " + nodeType);
        }
        return executor;
    }
}
