package com.nhnacademy.insightonruleengine.flow.controller;

import static com.nhnacademy.insightonruleengine.flow.FlowTestData.createValidLinks;
import static com.nhnacademy.insightonruleengine.flow.FlowTestData.createValidNodes;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.authorization.GroupAuthorizationService;
import com.nhnacademy.insightonruleengine.flow.authorization.GroupRole;
import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinition;
import com.nhnacademy.insightonruleengine.flow.definition.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.definition.LinkDefinition;
import com.nhnacademy.insightonruleengine.flow.definition.NodeDefinition;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.dto.FlowCreateRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowResponse;
import com.nhnacademy.insightonruleengine.flow.dto.FlowStatusChangeRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowUpdateRequest;
import com.nhnacademy.insightonruleengine.flow.exception.CoreDependencyException;
import com.nhnacademy.insightonruleengine.flow.exception.DuplicateFlowNameException;
import com.nhnacademy.insightonruleengine.flow.exception.FlowDeletionNotAllowedException;
import com.nhnacademy.insightonruleengine.flow.exception.FlowNotFoundException;
import com.nhnacademy.insightonruleengine.flow.exception.ForbiddenException;
import com.nhnacademy.insightonruleengine.flow.exception.InvalidFlowStatusTransitionException;
import com.nhnacademy.insightonruleengine.flow.exception.InvalidFlowStructureException;
import com.nhnacademy.insightonruleengine.flow.service.FlowService;
import com.nhnacademy.insightonruleengine.flow.validation.domain.FlowStructureErrorCode;
import com.nhnacademy.insightonruleengine.flow.validation.domain.FlowStructureValidationError;
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

@WebMvcTest(FlowController.class)
class FlowControllerTest {

    private static final String BASE_PATH = "/api/v1/flows";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final long USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FlowService flowService;

    @MockitoBean
    private FlowDefinitionAssembler flowDefinitionAssembler;

    @MockitoBean
    private GroupAuthorizationService groupAuthorizationService;

    @Test
    @DisplayName("Flow 생성 요청은 201과 생성된 Flow를 반환한다")
    void createFlowTest() throws Exception {
        FlowCreateRequest request = FlowCreateRequest
                .builder()
                .locationId(10L)
                .name("온도 경고")
                .description("설명")
                .nodes(createValidNodes())
                .links(createValidLinks())
                .build();
        when(flowService.create(1L, USER_ID, request)).thenReturn(response(101L, FlowStatus.INACTIVE));

        mockMvc.perform(post(BASE_PATH)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "locationId": 10,
                                  "name": "온도 경고",
                                  "description": "설명",
                                  "nodes": [
                                    {
                                      "clientNodeKey": "sensor",
                                      "nodeType": "SENSOR",
                                      "configuration": {}
                                    },
                                    {
                                      "clientNodeKey": "alert",
                                      "nodeType": "ALERT",
                                      "configuration": {}
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
                .andExpect(jsonPath("$.flowId").value(101L))
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.createdAt").value("2026-07-24T00:00:00Z"));

        verify(flowService).create(1L, USER_ID, request);
    }

    @Test
    @DisplayName("잘못된 구조의 Flow 생성 요청 시 400 Bad Request와 검증 실패 상세를 반환한다")
    void invalidFlow400Test() throws Exception {
        FlowCreateRequest request = FlowCreateRequest.builder()
                .locationId(10L)
                .name("잘못된 플로우")
                .description("순환 발생 플로우")
                .nodes(createValidNodes())
                .links(createValidLinks())
                .build();

        doThrow(new InvalidFlowStructureException(List.of(
                new FlowStructureValidationError(FlowStructureErrorCode.CYCLE_DETECTED, "sensor", "links",
                        "노드 연결 순환이 감지되었습니다.")
        ))).when(flowService).create(1L, USER_ID, request);

        mockMvc.perform(post(BASE_PATH)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "locationId": 10,
                                  "name": "잘못된 플로우",
                                  "description": "순환 발생 플로우",
                                  "nodes": [
                                    {
                                      "clientNodeKey": "sensor",
                                      "nodeType": "SENSOR",
                                      "configuration": {}
                                    },
                                    {
                                      "clientNodeKey": "alert",
                                      "nodeType": "ALERT",
                                      "configuration": {}
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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Flow 생성 요청 시 nodes 필드가 null이면 Validation 400으로 거부한다")
    void nullNodes400Test() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "locationId": 10,
                                  "name": "노드 누락 플로우",
                                  "links": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("요청값이 올바르지 않습니다."));

        verifyNoInteractions(flowService);
    }

    @Test
    @DisplayName("Flow 생성 요청 시 links 필드가 null이면 Validation 400으로 거부한다")
    void nullLinks400Test() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "locationId": 10,
                                  "name": "링크 누락 플로우",
                                  "nodes": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("요청값이 올바르지 않습니다."));

        verifyNoInteractions(flowService);
    }

    @Test
    @DisplayName("Flow 생성 요청의 NodeType이 누락되면 Service 호출 전에 400으로 거부한다")
    void missingCreateNodeType400Test() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "locationId": 10,
                                  "name": "노드 타입 누락 플로우",
                                  "nodes": [
                                    {
                                      "clientNodeKey": "sensor",
                                      "configuration": {}
                                    },
                                    {
                                      "clientNodeKey": "alert",
                                      "nodeType": "ALERT",
                                      "configuration": {}
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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("요청값이 올바르지 않습니다."));

        verifyNoInteractions(flowService);
    }

    @Test
    @DisplayName("Flow 생성 요청 시 name 필드가 공백이면 Validation 400으로 거부한다")
    void blankName400Test() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "locationId": 10,
                                  "name": "   ",
                                  "nodes": [],
                                  "links": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("요청값이 올바르지 않습니다."));

        verifyNoInteractions(flowService);
    }


    @Test
    @DisplayName("필수 groupId Query가 없으면 Service 호출 전에 400으로 거부한다")
    void MissingGroupIdTest() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .header(USER_ID_HEADER, USER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("요청값이 올바르지 않습니다."));

        verifyNoInteractions(flowService);
    }

    // Gateway 사용자 헤더가 빠진 요청이 Flow 작업까지 도달하지 않도록 확인합니다.
    @Test
    @DisplayName("필수 X-User-Id Header가 없으면 Service 호출 전에 400으로 거부한다")
    void MissingUserIdTest() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .queryParam("groupId", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("요청값이 올바르지 않습니다."));

        verifyNoInteractions(flowService);
    }

    @Test
    @DisplayName("선택 조회 조건이 없으면 ARCHIVED를 제외하는 기본 목록을 호출한다")
    void findFlowListTest() throws Exception {
        when(flowService.findAllUnarchivedFlows(1L, USER_ID))
                .thenReturn(List.of(response(101L, FlowStatus.ACTIVE)));

        mockMvc.perform(get(BASE_PATH)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].flowId").value(101L));

        verify(flowService).findAllUnarchivedFlows(1L, USER_ID);
    }

    @Test
    @DisplayName("상태만 지정하면 그룹 상태 목록을 호출한다")
    void findFlowListByStatusTest() throws Exception {
        when(flowService.findByGroupIdAndStatus(1L, USER_ID, FlowStatus.ARCHIVED))
                .thenReturn(List.of(response(101L, FlowStatus.ARCHIVED)));

        mockMvc.perform(get(BASE_PATH)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1")
                        .queryParam("status", "ARCHIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ARCHIVED"));

        verify(flowService).findByGroupIdAndStatus(1L, USER_ID, FlowStatus.ARCHIVED);
    }

    @Test
    @DisplayName("장소와 상태를 지정하면 그룹 장소 상태 목록을 호출한다")
    void FlowLocationAndStatusTest() throws Exception {
        when(flowService.findByGroupIdAndLocationIdAndStatus(
                1L,
                USER_ID,
                10L,
                FlowStatus.ACTIVE))
                .thenReturn(List.of(response(101L, FlowStatus.ACTIVE)));

        mockMvc.perform(get(BASE_PATH)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1")
                        .queryParam("locationId", "10")
                        .queryParam("status", "ACTIVE"))
                .andExpect(status().isOk());

        verify(flowService).findByGroupIdAndLocationIdAndStatus(
                1L,
                USER_ID,
                10L,
                FlowStatus.ACTIVE);
    }

    @Test
    @DisplayName("locationId만 지정한 목록 요청은 400으로 거부한다")
    void Location400Test() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1")
                        .queryParam("locationId", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value(
                                "locationId를 조회 조건으로 사용하려면 status도 함께 입력해야 합니다."
                        ));

        verifyNoInteractions(flowService);
    }

    @Test
    @DisplayName("ARCHIVED Flow도 전체 Node와 Link를 포함한 상세 응답으로 조회한다")
    void findArchivedFlowTest() throws Exception {
        FlowDefinition definition = definition(FlowStatus.ARCHIVED);
        when(flowDefinitionAssembler.assemble(1L, 101L)).thenReturn(definition);

        mockMvc.perform(get(BASE_PATH + "/{flowId}", 101L)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flowId").value(101L))
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.nodes[0].nodeId").value(201L))
                .andExpect(jsonPath("$.links[0].linkId").value(301L));

        verify(groupAuthorizationService).requireRole(1L, USER_ID, GroupRole.MEMBER);
        verify(flowDefinitionAssembler).assemble(1L, 101L);
    }

    @Test
    @DisplayName("상태 변경 요청은 PUT의 경로와 상태를 Service에 전달한다")
    void changeFlowStatusTest() throws Exception {
        FlowStatusChangeRequest request = new FlowStatusChangeRequest(FlowStatus.ACTIVE);
        when(flowService.changeActivationStatus(1L, USER_ID, 101L, request))
                .thenReturn(response(101L, FlowStatus.ACTIVE));

        mockMvc.perform(put(BASE_PATH + "/{flowId}/status", 101L)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "ACTIVE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(flowService).changeActivationStatus(1L, USER_ID, 101L, request);
    }

    // 전용 보관 API가 Service 결과와 HTTP 응답을 그대로 전달하는지 확인합니다.
    @Test
    @DisplayName("휴지통 이동 요청은 선택한 Flow를 ARCHIVED 상태로 반환한다")
    void archiveFlowTest() throws Exception {
        when(flowService.archive(1L, USER_ID, 101L)).thenReturn(response(101L, FlowStatus.ARCHIVED));

        mockMvc.perform(post(BASE_PATH + "/{flowId}/archive", 101L)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flowId").value(101L))
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        verify(flowService).archive(1L, USER_ID, 101L);
    }

    @Test
    @DisplayName("Flow 수정 요청")
    void updateFlowTest() throws Exception {
        FlowUpdateRequest request = updateRequest("온도 경고 v2", "수정 설명");
        when(flowService.update(1L, USER_ID, 101L, request)).thenReturn(response(102L, FlowStatus.INACTIVE));

        mockMvc.perform(put(BASE_PATH + "/{flowId}", 101L)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "온도 경고 v2",
                                  "description": "수정 설명",
                                  "nodes": [
                                    {
                                      "clientNodeKey": "sensor",
                                      "nodeType": "SENSOR",
                                      "configuration": {}
                                    },
                                    {
                                      "clientNodeKey": "alert",
                                      "nodeType": "ALERT",
                                      "configuration": {}
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flowId").value(102L))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        verify(flowService).update(1L, USER_ID, 101L, request);
    }

    @Test
    @DisplayName("복구 요청은 Body 없이 선택한 보관 Flow ID를 전달한다")
    void restoreFlowTest() throws Exception {
        when(flowService.restore(1L, USER_ID, 101L)).thenReturn(response(101L, FlowStatus.INACTIVE));

        mockMvc.perform(post(BASE_PATH + "/{archivedFlowId}/restore", 101L)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flowId").value(101L))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        verify(flowService).restore(1L, USER_ID, 101L);
    }

    @Test
    @DisplayName("보관 Flow 삭제 성공은 본문 없는 204를 반환한다")
    void deleteFlowTest() throws Exception {
        mockMvc.perform(delete(BASE_PATH + "/{flowId}", 101L)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(flowService).delete(1L, USER_ID, 101L);
    }

    @Test
    @DisplayName("필수 생성값이 없으면 Service 호출 전에 400으로 거부한다")
    void InvalidCreateRequestTest() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": " "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("요청값이 올바르지 않습니다."));

        verifyNoInteractions(flowService);
    }

    @Test
    @DisplayName("100자를 초과한 생성 이름은 Service 호출 전에 400으로 거부한다")
    void LongCreateNameTest() throws Exception {
        String longName = "가".repeat(101);

        mockMvc.perform(post(BASE_PATH)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "locationId": 10,
                                  "name": "%s"
                                }
                                """.formatted(longName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(flowService);
    }

    @Test
    @DisplayName("공백인 수정 이름은 Service 호출 전에 400으로 거부한다")
    void BlankUpdateNameTest() throws Exception {
        mockMvc.perform(put(BASE_PATH + "/{flowId}", 101L)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": " "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청값이 올바르지 않습니다."));

        verifyNoInteractions(flowService);
    }

    @Test
    @DisplayName("상태가 누락된 변경 요청은 Service 호출 전에 400으로 거부한다")
    void MissingStatusTest() throws Exception {
        mockMvc.perform(put(BASE_PATH + "/{flowId}/status", 101L)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(flowService);
    }

    @Test
    @DisplayName("읽을 수 없는 JSON은 내부 메시지 없이 공통 400을 반환한다")
    void MalformedJsonTest() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"locationId": "1L"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("요청값이 올바르지 않습니다."));

        verifyNoInteractions(flowService);
    }

    @Test
    @DisplayName("잘못된 enum은 내부 변환 메시지 없이 공통 400을 반환한다")
    void InvalidStatusEnumTest() throws Exception {
        mockMvc.perform(put(BASE_PATH + "/{flowId}/status", 101L)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "RUNNING"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("요청값이 올바르지 않습니다."));

        verifyNoInteractions(flowService);
    }

    @Test
    @DisplayName("Flow 미존재 예외는 상세 메시지와 404를 반환한다")
    void handleNotFoundTest() throws Exception {
        FlowNotFoundException exception = new FlowNotFoundException(1L, 101L);
        when(flowDefinitionAssembler.assemble(1L, 101L)).thenThrow(exception);

        mockMvc.perform(get(BASE_PATH + "/{flowId}", 101L)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(exception.getMessage()));
    }

    // Service의 권한 거부가 사용자에게 공통 403 응답으로 전달되는지 확인합니다.
    @Test
    @DisplayName("권한 부족 예외는 상세 메시지와 403을 반환한다")
    void handleForbiddenTest() throws Exception {
        FlowCreateRequest request = FlowCreateRequest
                .builder()
                .locationId(10L)
                .name("온도 경고")
                .description("설명")
                .nodes(createValidNodes())
                .links(createValidLinks())
                .build();
        ForbiddenException exception = new ForbiddenException("MANAGER 이상 권한이 필요합니다.");
        when(flowService.create(1L, USER_ID, request)).thenThrow(exception);

        mockMvc.perform(post(BASE_PATH)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "locationId": 10,
                                  "name": "온도 경고",
                                  "description": "설명",
                                  "nodes": [
                                    {
                                      "clientNodeKey": "sensor",
                                      "nodeType": "SENSOR",
                                      "configuration": {}
                                    },
                                    {
                                      "clientNodeKey": "alert",
                                      "nodeType": "ALERT",
                                      "configuration": {}
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
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(exception.getMessage()));
    }

    // Core 응답 계약 오류가 권한 거부가 아닌 502로 전달되는지 확인합니다.
    @Test
    @DisplayName("Core 의존성 예외는 상세 메시지와 502를 반환한다")
    void CoreExceptionTest() throws Exception {

        FlowCreateRequest request = FlowCreateRequest
                .builder()
                .locationId(10L)
                .name("온도 경고")
                .description("설명")
                .nodes(createValidNodes())
                .links(createValidLinks())
                .build();
        CoreDependencyException exception =
                new CoreDependencyException("Core 그룹 멤버 응답을 처리할 수 없습니다.");
        when(flowService.create(1L, USER_ID, request)).thenThrow(exception);

        mockMvc.perform(post(BASE_PATH)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "locationId": 10,
                                  "name": "온도 경고",
                                  "description": "설명",
                                  "nodes": [
                                    {
                                      "clientNodeKey": "sensor",
                                      "nodeType": "SENSOR",
                                      "configuration": {}
                                    },
                                    {
                                      "clientNodeKey": "alert",
                                      "nodeType": "ALERT",
                                      "configuration": {}
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
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").value(exception.getMessage()));
    }

    @Test
    @DisplayName("이름 중복 예외는 상세 메시지와 409를 반환한다")
    void DuplicateNameTest() throws Exception {

        FlowCreateRequest request = FlowCreateRequest
                .builder()
                .locationId(10L)
                .name("온도 경고")
                .description("설명")
                .nodes(createValidNodes())
                .links(createValidLinks())
                .build();
        DuplicateFlowNameException exception = new DuplicateFlowNameException(1L, 10L, "온도 경고");
        when(flowService.create(1L, USER_ID, request)).thenThrow(exception);

        mockMvc.perform(post(BASE_PATH)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "locationId": 10,
                                  "name": "온도 경고",
                                  "description": "설명",
                                  "nodes": [
                                    {
                                      "clientNodeKey": "sensor",
                                      "nodeType": "SENSOR",
                                      "configuration": {}
                                    },
                                    {
                                      "clientNodeKey": "alert",
                                      "nodeType": "ALERT",
                                      "configuration": {}
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
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(exception.getMessage()));
    }

    @Test
    @DisplayName("잘못된 상태 전환 예외는 상세 메시지와 409를 반환한다")
    void InvalidStatusExceptionTest() throws Exception {
        FlowStatusChangeRequest request = new FlowStatusChangeRequest(FlowStatus.ARCHIVED);
        InvalidFlowStatusTransitionException exception =
                new InvalidFlowStatusTransitionException(FlowStatus.ACTIVE, FlowStatus.ARCHIVED);
        when(flowService.changeActivationStatus(1L, USER_ID, 101L, request)).thenThrow(exception);

        mockMvc.perform(put(BASE_PATH + "/{flowId}/status", 101L)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "ARCHIVED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(exception.getMessage()));
    }

    @Test
    @DisplayName("삭제 정책 위반 예외는 상세 메시지와 409를 반환한다")
    void DeletionNotAllowedTest() throws Exception {
        FlowDeletionNotAllowedException exception =
                new FlowDeletionNotAllowedException(101L, FlowStatus.ACTIVE);
        doThrow(exception).when(flowService).delete(1L, USER_ID, 101L);

        mockMvc.perform(delete(BASE_PATH + "/{flowId}", 101L)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(exception.getMessage()));
    }

    private FlowResponse response(Long flowId, FlowStatus status) {
        return FlowResponse.builder()
                .flowId(flowId)
                .groupId(1L)
                .locationId(10L)
                .name("온도 경고")
                .description("30도 이상 경고")
                .status(status)
                .createdAt(OffsetDateTime.of(2026, 7, 24, 0, 0, 0, 0, ZoneOffset.UTC))
                .build();
    }

    // 상세 API가 실행 모델의 전체 Node와 Link를 응답하는지 확인할 Fixture를 만듭니다.
    private FlowDefinition definition(FlowStatus status) {
        NodeDefinition node = new NodeDefinition(
                201L,
                NodeType.SENSOR,
                JsonNodeFactory.instance.objectNode().put("metric", "temperature")
        );
        LinkDefinition link = new LinkDefinition(301L, 101L, 201L, 202L, "out", "in");
        return new FlowDefinition(
                101L,
                1L,
                10L,
                "온도 경고",
                "30도 이상 경고",
                status,
                OffsetDateTime.of(2026, 7, 24, 0, 0, 0, 0, ZoneOffset.UTC),
                List.of(node),
                List.of(link)
        );
    }

    private FlowUpdateRequest updateRequest(String name, String description) {
        return FlowUpdateRequest.builder()
                .name(name)
                .description(description)
                .nodes(List.of(
                        FlowNodeRequest.builder()
                                .clientNodeKey("sensor")
                                .nodeType(NodeType.SENSOR)
                                .configuration(JsonNodeFactory.instance.objectNode())
                                .build(),
                        FlowNodeRequest.builder()
                                .clientNodeKey("alert")
                                .nodeType(NodeType.ALERT)
                                .configuration(JsonNodeFactory.instance.objectNode())
                                .build()))
                .links(List.of(FlowLinkRequest.builder()
                        .sourceClientNodeKey("sensor")
                        .targetClientNodeKey("alert")
                        .sourcePort("out")
                        .targetPort("in")
                        .build()))
                .build();
    }
}
