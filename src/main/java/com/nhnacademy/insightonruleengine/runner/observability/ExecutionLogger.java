package com.nhnacademy.insightonruleengine.runner.observability;

import com.nhnacademy.insightonruleengine.flow.domain.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.runner.model.NodeExecutionResult;
import com.nhnacademy.insightonruleengine.runner.model.SensorEvent;

public interface ExecutionLogger {

    /**
     * 센서 이벤트가 수신되고 실행 대상 Flow 목록이 결정된 시점의 로그 발생
     *
     * @param event 수신한 센서 텔레메트리 이벤트
     * @param flowCount 해당 이벤트로 라우팅된 실행 대상 Flow 개수
     */
    void eventRouted(SensorEvent event, int flowCount);

    /**
     * 단일 Flow 실행이 시작된 시점의 로그 발생
     *
     * @param context Flow 실행 1건의 공통 로그 컨텍스트
     * @param triggerNodeId Flow 실행을 시작하는 Trigger Node ID
     */
    void flowStarted(ExecutionLogContext context, Long triggerNodeId);

    /**
     * 단일 Flow 실행이 정상 종료된 시점의 로그 발생
     *
     * @param context Flow 실행 1건의 공통 로그 컨텍스트
     * @param terminalNodeId Flow 실행을 종료시킨 마지막 Node ID
     * @param terminalActionReached terminal Action Node 도달 여부
     */
    void flowFinished(ExecutionLogContext context, Long terminalNodeId, boolean terminalActionReached);

    /**
     * Node 실행 직전의 로그 발생
     *
     * @param context Flow 실행 1건의 공통 로그 컨텍스트
     * @param node 실행할 Node 정의
     */
    void nodeStarted(ExecutionLogContext context, NodeDefinition node);

    /**
     * Node 실행이 정상 완료된 시점의 로그 발생
     *
     * @param context Flow 실행 1건의 공통 로그 컨텍스트
     * @param node 실행이 완료된 Node 정의
     * @param result Node 실행 결과와 다음 output port 정보
     */
    void nodeFinished(ExecutionLogContext context, NodeDefinition node, NodeExecutionResult result);

    /**
     * Flow 실행 중 오류가 발생한 경우 로그 발생
     *
     * @param context Flow 실행 1건의 공통 로그 컨텍스트
     * @param exception 발생한 예외
     */
    void flowFailed(ExecutionLogContext context, RuntimeException exception);

    /**
     * Node 실행 중 오류가 발생한 경우 로그 발생
     *
     * @param context Flow 실행 1건의 공통 로그 컨텍스트
     * @param node 오류가 발생한 Node 정의
     * @param exception 발생한 예외
     */
    void nodeFailed(ExecutionLogContext context, NodeDefinition node, RuntimeException exception);
}
