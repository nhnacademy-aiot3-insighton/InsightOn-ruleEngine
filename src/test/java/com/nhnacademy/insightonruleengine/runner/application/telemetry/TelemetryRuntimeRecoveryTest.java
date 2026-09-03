package com.nhnacademy.insightonruleengine.runner.application.telemetry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.application.assembly.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.domain.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.definition.LinkDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.node.parser.NodeParamsParser;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.FlowRepository;
import com.nhnacademy.insightonruleengine.runner.application.FlowRunner;
import com.nhnacademy.insightonruleengine.runner.application.router.ActiveFlowRouter;
import com.nhnacademy.insightonruleengine.runner.execution.executor.NodeExecutor;
import com.nhnacademy.insightonruleengine.runner.execution.executor.NodeExecutorRegistry;
import com.nhnacademy.insightonruleengine.runner.infrastructure.cache.ActiveFlowDefinitionProvider;
import com.nhnacademy.insightonruleengine.runner.infrastructure.cache.FlowDefinitionCache;
import com.nhnacademy.insightonruleengine.runner.model.FlowExecutionContext;
import com.nhnacademy.insightonruleengine.runner.model.NodeExecutionResult;
import com.nhnacademy.insightonruleengine.runner.model.SensorEvent;
import com.nhnacademy.insightonruleengine.runner.observability.ExecutionLogger;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TelemetryRuntimeRecoveryTest {

    @Test
    @DisplayName("Telemetry 실행 중 Flow 캐시 MISS가 발생하면 DB에서 복구해 실행합니다")
    void recoverCacheMissAndExecuteFlowTest() {
        FlowRepository flowRepository = mock(FlowRepository.class);
        FlowDefinitionAssembler assembler = mock(FlowDefinitionAssembler.class);
        FlowDefinitionCache cache = mock(FlowDefinitionCache.class);
        Flow flow = mock(Flow.class);
        FlowDefinition definition = flowDefinition();

        when(cache.find(1L, 10L)).thenReturn(Optional.empty());
        when(flowRepository.findAllByGroupIdAndLocationIdAndStatus(1L, 10L, FlowStatus.ACTIVE))
                .thenReturn(List.of(flow));
        when(flow.getGroupId()).thenReturn(1L);
        when(flow.getId()).thenReturn(100L);
        when(assembler.assemble(1L, 100L)).thenReturn(definition);

        ActiveFlowDefinitionProvider provider = new ActiveFlowDefinitionProvider(
                flowRepository,
                assembler,
                cache
        );
        ActiveFlowRouter router = new ActiveFlowRouter(provider, mock(NodeParamsParser.class));

        NodeExecutor locationExecutor = executor(NodeType.LOCATION, NodeExecutionResult.next("out"));
        NodeExecutor alertExecutor = executor(NodeType.ALERT, NodeExecutionResult.complete());
        FlowRunner flowRunner = new FlowRunner(
                router,
                new NodeExecutorRegistry(List.of(locationExecutor, alertExecutor)),
                mock(ExecutionLogger.class)
        );
        TelemetryExecutionOrchestrator orchestrator = new TelemetryExecutionOrchestrator(
                new StaleTelemetryDetector(),
                flowRunner
        );

        orchestrator.orchestrate(new SensorEvent(
                1L,
                10L,
                200L,
                Map.of("temperature", 25.0),
                Instant.parse("2026-08-24T00:00:00Z")
        ));

        verify(cache).replace(1L, 10L, List.of(definition));
        verify(locationExecutor).execute(any(NodeDefinition.class), any(FlowExecutionContext.class));
        verify(alertExecutor).execute(any(NodeDefinition.class), any(FlowExecutionContext.class));
    }

    private NodeExecutor executor(NodeType nodeType, NodeExecutionResult result) {
        NodeExecutor executor = mock(NodeExecutor.class);
        when(executor.nodeType()).thenReturn(nodeType);
        when(executor.execute(any(NodeDefinition.class), any(FlowExecutionContext.class))).thenReturn(result);
        return executor;
    }

    private FlowDefinition flowDefinition() {
        return new FlowDefinition(
                100L,
                1L,
                10L,
                "cache miss recovery flow",
                null,
                FlowStatus.ACTIVE,
                OffsetDateTime.parse("2026-08-24T00:00:00Z"),
                List.of(
                        new NodeDefinition(1L, NodeType.LOCATION, JsonNodeFactory.instance.objectNode()),
                        new NodeDefinition(2L, NodeType.ALERT, JsonNodeFactory.instance.objectNode())
                ),
                List.of(new LinkDefinition(1L, 100L, 1L, 2L, "out", "in"))
        );
    }
}
