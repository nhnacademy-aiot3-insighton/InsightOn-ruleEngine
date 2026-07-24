package com.nhnacademy.insightonruleengine.flow.controller;

import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.dto.FlowCreateRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowResponse;
import com.nhnacademy.insightonruleengine.flow.dto.FlowStatusChangeRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowUpdateRequest;
import com.nhnacademy.insightonruleengine.flow.exception.InvalidFlowQueryException;
import com.nhnacademy.insightonruleengine.flow.service.FlowService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/groups/{groupId}/flows")
public class FlowController {

    private final FlowService flowService;

    // 생성 성공을 201로 구분해 클라이언트가 새 Flow 저장을 확인할 수 있게 한다.
    @PostMapping
    public ResponseEntity<FlowResponse> createFlow(
            @PathVariable Long groupId,
            @Valid @RequestBody FlowCreateRequest request) {
        FlowResponse response = flowService.create(groupId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 허용된 Query 조합만 Service 조회 메서드에 연결해 목록 계약을 고정한다.
    @GetMapping
    public List<FlowResponse> findAll(
            @PathVariable Long groupId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) FlowStatus status) {
        if (locationId == null && status == null) {
            return flowService.findAll(groupId);
        }
        if (locationId == null) {
            return flowService.findAll(groupId, status);
        }
        if (status == null) {
            throw new InvalidFlowQueryException();
        }
        return flowService.findAll(groupId, locationId, status);
    }

    // 보관된 Flow도 이력 화면에서 같은 응답으로 읽을 수 있게 한다.
    @GetMapping("/{flowId}")
    public FlowResponse findById(
            @PathVariable Long groupId,
            @PathVariable Long flowId) {
        return flowService.findById(groupId, flowId);
    }

    // 일부 상태만 바꾸는 요청이므로 PATCH로 활성화와 비활성화를 제공한다.
    @PatchMapping("/{flowId}/status")
    public FlowResponse changeStatus(
            @PathVariable Long groupId,
            @PathVariable Long flowId,
            @Valid @RequestBody FlowStatusChangeRequest request) {
        return flowService.changeActivationStatus(groupId, flowId, request);
    }

    // 수정된 전체 Flow를 새 행으로 저장하므로 수정 전용 DTO를 PUT 요청으로 전달한다.
    @PutMapping("/{flowId}")
    public FlowResponse update(
            @PathVariable Long groupId,
            @PathVariable Long flowId,
            @Valid @RequestBody FlowUpdateRequest request) {
        return flowService.update(groupId, flowId, request);
    }

    // 요청 Body 없이 경로로 선택한 보관 Flow 하나만 다시 꺼낸다.
    @PostMapping("/{archivedFlowId}/restore")
    public FlowResponse restore(
            @PathVariable Long groupId,
            @PathVariable Long archivedFlowId) {
        return flowService.restore(groupId, archivedFlowId);
    }

    // 보관 Flow의 영구 삭제 성공은 응답 본문 없이 204로 알린다.
    @DeleteMapping("/{flowId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long groupId,
            @PathVariable Long flowId) {
        flowService.delete(groupId, flowId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
