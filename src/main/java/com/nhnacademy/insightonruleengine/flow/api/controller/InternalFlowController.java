package com.nhnacademy.insightonruleengine.flow.api.controller;

import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowCreateRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.response.FlowResponse;
import com.nhnacademy.insightonruleengine.flow.application.FlowService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서비스 간 호출 전용 Flow API입니다. 유저 인증(X-User-Id)과 그룹 권한 체크가 없어서, 유저용 FlowController와 분리했습니다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/flows")
public class InternalFlowController {

    private final FlowService flowService;

    // AI가 리포트 분석 결과로 자동화 flow 초안을 요청합니다.
    @PostMapping
    public ResponseEntity<FlowResponse> createAiDraft(
            @RequestParam Long groupId,
            @Valid @RequestBody FlowCreateRequest request) {
        FlowResponse response = flowService.createAiDraft(groupId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // AI가 그 위치에 이미 동작 중인 자동화가 있는지 확인하기 위해 조회합니다. AI/유저가 만든 것을
    // 구분하지 않고 그 위치의 ACTIVE flow를 전부 반환하며, AI 쪽은 "[AI] " 접두어로 자기 것만 걸러
    // 씁니다.
    @GetMapping
    public ResponseEntity<List<FlowResponse>> findActiveFlows(
            @RequestParam Long groupId,
            @RequestParam Long locationId) {
        return ResponseEntity.ok(flowService.findActiveFlows(groupId, locationId));
    }
}
