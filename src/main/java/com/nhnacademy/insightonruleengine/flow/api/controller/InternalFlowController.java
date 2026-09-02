package com.nhnacademy.insightonruleengine.flow.api.controller;

import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowCreateRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.response.FlowResponse;
import com.nhnacademy.insightonruleengine.flow.application.FlowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
}
