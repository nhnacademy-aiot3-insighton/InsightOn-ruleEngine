package com.nhnacademy.insightonruleengine.flow.controller;

import com.nhnacademy.insightonruleengine.flow.authorization.GroupAuthorizationService;
import com.nhnacademy.insightonruleengine.flow.authorization.GroupRole;
import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.dto.FlowCreateRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowDefinitionResponse;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/flows")
public class FlowController {

    private final FlowService flowService;
    private final FlowDefinitionAssembler flowDefinitionAssembler;
    private final GroupAuthorizationService groupAuthorizationService;

    // Flow를 만들고 생성 성공 상태인 201을 반환합니다.
    @PostMapping
    public ResponseEntity<FlowResponse> createFlow(
            @RequestParam Long groupId,
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody FlowCreateRequest request) {
        FlowResponse response = flowService.create(groupId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 입력한 조회 조건에 맞는 Flow 목록을 반환합니다.
    @GetMapping
    public ResponseEntity<List<FlowResponse>> findAll(
            @RequestParam Long groupId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) FlowStatus status) {
        if (locationId == null && status == null) {
            return ResponseEntity.ok(flowService.findAll(groupId, userId));
        }
        if (locationId == null) {
            return ResponseEntity.ok(flowService.findAll(groupId, userId, status));
        }
        if (status == null) {
            throw new InvalidFlowQueryException();
        }
        return ResponseEntity.ok(flowService.findAll(groupId, userId, locationId, status));
    }

    // 휴지통에 있는 Flow를 포함해 상세 정보를 조회합니다.
    @GetMapping("/{flowId}")
    public ResponseEntity<FlowDefinitionResponse> findById(
            @RequestParam Long groupId,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long flowId) {
        groupAuthorizationService.requireRole(groupId, userId, GroupRole.MEMBER);
        FlowDefinition definition = flowDefinitionAssembler.assemble(groupId, flowId);
        FlowDefinitionResponse response = FlowDefinitionResponse.builder()
                .flowId(definition.flowId())
                .groupId(definition.groupId())
                .locationId(definition.locationId())
                .name(definition.name())
                .description(definition.description())
                .status(definition.status())
                .createdAt(definition.createdAt())
                .nodes(definition.nodes())
                .links(definition.links())
                .build();
        return ResponseEntity.ok(response);
    }

    // Flow를 ACTIVE 또는 INACTIVE 상태로 변경합니다.
    @PutMapping("/{flowId}/status")
    public ResponseEntity<FlowResponse> changeStatus(
            @RequestParam Long groupId,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long flowId,
            @Valid @RequestBody FlowStatusChangeRequest request) {
        FlowResponse response = flowService.changeActivationStatus(groupId, userId, flowId, request);
        return ResponseEntity.ok(response);
    }

    // 기존 Flow를 보관하고 수정한 내용을 새 Flow로 저장합니다.
    @PutMapping("/{flowId}")
    public ResponseEntity<FlowResponse> update(
            @RequestParam Long groupId,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long flowId,
            @Valid @RequestBody FlowUpdateRequest request) {
        FlowResponse response = flowService.update(groupId, userId, flowId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/flowId")
    public ResponseEntity<FlowResponse> archive(
            //todo Archive 로직 필요
    }

    // 휴지통에서 선택한 Flow를 INACTIVE 상태로 복구합니다.
    @PostMapping("/{archivedFlowId}/restore")
    public ResponseEntity<FlowResponse> restore(
            @RequestParam Long groupId,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long archivedFlowId) {
        FlowResponse response = flowService.restore(groupId, userId, archivedFlowId);
        return ResponseEntity.ok(response);
    }

    // 휴지통의 Flow를 삭제하고 본문 없이 204를 반환합니다.
    @DeleteMapping("/{flowId}")
    public ResponseEntity<Void> delete(
            @RequestParam Long groupId,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long flowId) {
        flowService.delete(groupId, userId, flowId);
        return ResponseEntity.noContent().build();
    }
}
