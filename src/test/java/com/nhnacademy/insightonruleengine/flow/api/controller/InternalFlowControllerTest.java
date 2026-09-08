package com.nhnacademy.insightonruleengine.flow.api.controller;

import static com.nhnacademy.insightonruleengine.flow.FlowTestData.createValidLinks;
import static com.nhnacademy.insightonruleengine.flow.FlowTestData.createValidNodes;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowCreateRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.response.FlowResponse;
import com.nhnacademy.insightonruleengine.flow.application.FlowService;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalFlowController.class)
class InternalFlowControllerTest {

    private static final String BASE_PATH = "/internal/v1/flows";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FlowService flowService;

    @Test
    @DisplayName("유저 헤더 없이도 AI draft 생성 요청은 201과 생성된 Flow를 반환한다")
    void createAiDraftTest() throws Exception {
        FlowCreateRequest request = FlowCreateRequest.builder()
                .locationId(42L)
                .name("[AI] co2 예방 자동화")
                .description("[AI 자동 생성] co2 예방 자동화 제안")
                .nodes(createValidNodes())
                .links(createValidLinks())
                .build();
        when(flowService.createAiDraft(5L, request)).thenReturn(response(FlowStatus.INACTIVE));

        mockMvc.perform(post(BASE_PATH)
                        .queryParam("groupId", "5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "locationId": 42,
                                  "name": "[AI] co2 예방 자동화",
                                  "description": "[AI 자동 생성] co2 예방 자동화 제안",
                                  "nodes": [
                                    {
                                      "clientNodeKey": "sensor",
                                      "nodeType": "LOCATION",
                                      "configuration": {}
                                    },
                                    {
                                      "clientNodeKey": "alert",
                                      "nodeType": "ALERT",
                                      "configuration": {
                                        "title": "테스트 알림",
                                        "severity": "WARNING",
                                        "message": "테스트 알림 메시지"
                                      }
                                    }
                                  ],
                                  "links": [
                                    {
                                      "sourceClientNodeKey": "sensor",
                                      "targetClientNodeKey": "alert",
                                      "sourcePort": "out",
                                      "targetPort": "in"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.flowId").value(128L))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        verify(flowService).createAiDraft(5L, request);
    }

    @Test
    @DisplayName("groupId 쿼리 파라미터가 없으면 400을 반환한다")
    void missingGroupIdTest() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"locationId": 42, "name": "테스트", "nodes": [], "links": []}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("유저 헤더 없이도 ACTIVE flow 목록 조회는 200과 목록을 반환한다")
    void findActiveFlowsTest() throws Exception {
        when(flowService.findActiveFlows(5L, 42L))
                .thenReturn(List.of(response(FlowStatus.ACTIVE)));

        mockMvc.perform(get(BASE_PATH)
                        .queryParam("groupId", "5")
                        .queryParam("locationId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].flowId").value(128L))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        verify(flowService).findActiveFlows(5L, 42L);
    }

    private FlowResponse response(FlowStatus status) {
        return FlowResponse.builder()
                .flowId(128L)
                .groupId(5L)
                .locationId(42L)
                .name("[AI] co2 예방 자동화")
                .description("[AI 자동 생성] co2 예방 자동화 제안")
                .status(status)
                .createdAt(OffsetDateTime.of(2026, 8, 31, 0, 0, 0, 0, ZoneOffset.UTC))
                .build();
    }
}
