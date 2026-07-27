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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/flows")
public class FlowController {

    private final FlowService flowService;

    // Flow를 만들고 생성 성공 상태인 201을 반환합니다.
    @PostMapping
    public ResponseEntity<FlowResponse> createFlow(
            @RequestParam Long groupId,
            @Valid @RequestBody FlowCreateRequest request) {
        FlowResponse response = flowService.create(groupId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 입력한 조회 조건에 맞는 Flow 목록을 반환합니다.
    @GetMapping
    public List<FlowResponse> findAll(
            @RequestParam Long groupId,
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

    // 휴지통에 있는 Flow를 포함해 상세 정보를 조회합니다.
    @GetMapping("/{flowId}")
    public FlowResponse findById(
            @RequestParam Long groupId,
            @PathVariable Long flowId) {
        return flowService.findById(groupId, flowId);
    }

    // Flow를 ACTIVE 또는 INACTIVE 상태로 변경합니다.
    @PutMapping("/{flowId}/status")
    public FlowResponse changeStatus(
            @RequestParam Long groupId,
            @PathVariable Long flowId,
            @Valid @RequestBody FlowStatusChangeRequest request) {
        return flowService.changeActivationStatus(groupId, flowId, request);
    }

    // 기존 Flow를 보관하고 수정한 내용을 새 Flow로 저장합니다.
    @PutMapping("/{flowId}")
    public FlowResponse update(
            @RequestParam Long groupId,
            @PathVariable Long flowId,
            @Valid @RequestBody FlowUpdateRequest request) {
        return flowService.update(groupId, flowId, request);
    }

    // 휴지통에서 선택한 Flow를 INACTIVE 상태로 복구합니다.
    @PostMapping("/{archivedFlowId}/restore")
    public FlowResponse restore(
            @RequestParam Long groupId,
            @PathVariable Long archivedFlowId) {
        return flowService.restore(groupId, archivedFlowId);
    }

    // 휴지통의 Flow를 삭제하고 본문 없이 204를 반환합니다.
    @DeleteMapping("/{flowId}")
    public ResponseEntity<Void> delete(
            @RequestParam Long groupId,
            @PathVariable Long flowId) {
        flowService.delete(groupId, flowId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
