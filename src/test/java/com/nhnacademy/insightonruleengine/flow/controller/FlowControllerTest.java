package com.nhnacademy.insightonruleengine.flow.controller;

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

import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.dto.FlowCreateRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowResponse;
import com.nhnacademy.insightonruleengine.flow.dto.FlowStatusChangeRequest;
import com.nhnacademy.insightonruleengine.flow.dto.FlowUpdateRequest;
import com.nhnacademy.insightonruleengine.flow.exception.DuplicateFlowNameException;
import com.nhnacademy.insightonruleengine.flow.exception.FlowDeletionNotAllowedException;
import com.nhnacademy.insightonruleengine.flow.exception.FlowNotFoundException;
import com.nhnacademy.insightonruleengine.flow.exception.ForbiddenException;
import com.nhnacademy.insightonruleengine.flow.exception.InvalidFlowStatusTransitionException;
import com.nhnacademy.insightonruleengine.flow.service.FlowService;
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

    @Test
    @DisplayName("Flow 생성 요청은 201과 생성된 Flow를 반환한다")
    void createFlow() throws Exception {
        FlowCreateRequest request = new FlowCreateRequest(10L, "온도 경고", "30도 이상 경고");
        when(flowService.create(1L, USER_ID, request)).thenReturn(response(101L, FlowStatus.INACTIVE));

        mockMvc.perform(post(BASE_PATH)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "locationId": 10,
                                  "name": "온도 경고",
                                  "description": "30도 이상 경고"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.flowId").value(101L))
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.createdAt").value("2026-07-24T00:00:00Z"));

        verify(flowService).create(1L, USER_ID, request);
    }

    @Test
    @DisplayName("필수 groupId Query가 없으면 Service 호출 전에 400으로 거부한다")
    void rejectMissingGroupId() throws Exception {
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
    void rejectMissingUserId() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .queryParam("groupId", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("요청값이 올바르지 않습니다."));

        verifyNoInteractions(flowService);
    }

    @Test
    @DisplayName("선택 조회 조건이 없으면 ARCHIVED를 제외하는 기본 목록을 호출한다")
    void findDefaultList() throws Exception {
        when(flowService.findAll(1L, USER_ID)).thenReturn(List.of(response(101L, FlowStatus.ACTIVE)));

        mockMvc.perform(get(BASE_PATH)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].flowId").value(101L));

        verify(flowService).findAll(1L, USER_ID);
    }

    @Test
    @DisplayName("상태만 지정하면 그룹 상태 목록을 호출한다")
    void findListByStatus() throws Exception {
        when(flowService.findAll(1L, USER_ID, FlowStatus.ARCHIVED))
                .thenReturn(List.of(response(101L, FlowStatus.ARCHIVED)));

        mockMvc.perform(get(BASE_PATH)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1")
                        .queryParam("status", "ARCHIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ARCHIVED"));

        verify(flowService).findAll(1L, USER_ID, FlowStatus.ARCHIVED);
    }

    @Test
    @DisplayName("장소와 상태를 지정하면 그룹 장소 상태 목록을 호출한다")
    void findListByLocationAndStatus() throws Exception {
        when(flowService.findAll(1L, USER_ID, 10L, FlowStatus.ACTIVE))
                .thenReturn(List.of(response(101L, FlowStatus.ACTIVE)));

        mockMvc.perform(get(BASE_PATH)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1")
                        .queryParam("locationId", "10")
                        .queryParam("status", "ACTIVE"))
                .andExpect(status().isOk());

        verify(flowService).findAll(1L, USER_ID, 10L, FlowStatus.ACTIVE);
    }

    @Test
    @DisplayName("locationId만 지정한 목록 요청은 400으로 거부한다")
    void rejectLocationOnlyQuery() throws Exception {
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
    @DisplayName("ARCHIVED Flow도 공통 상세 응답으로 조회한다")
    void findArchivedFlow() throws Exception {
        when(flowService.findById(1L, USER_ID, 101L)).thenReturn(response(101L, FlowStatus.ARCHIVED));

        mockMvc.perform(get(BASE_PATH + "/{flowId}", 101L)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flowId").value(101L))
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        verify(flowService).findById(1L, USER_ID, 101L);
    }

    @Test
    @DisplayName("상태 변경 요청은 PUT의 경로와 상태를 Service에 전달한다")
    void changeFlowStatus() throws Exception {
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

    @Test
    @DisplayName("Flow 수정 요청")
    void updateFlow() throws Exception {
        FlowUpdateRequest request = new FlowUpdateRequest("온도 경고 v2", "수정 설명");
        when(flowService.update(1L, USER_ID, 101L, request)).thenReturn(response(102L, FlowStatus.INACTIVE));

        mockMvc.perform(put(BASE_PATH + "/{flowId}", 101L)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "온도 경고 v2",
                                  "description": "수정 설명"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flowId").value(102L))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        verify(flowService).update(1L, USER_ID, 101L, request);
    }

    @Test
    @DisplayName("복구 요청은 Body 없이 선택한 보관 Flow ID를 전달한다")
    void restoreFlow() throws Exception {
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
    void deleteFlow() throws Exception {
        mockMvc.perform(delete(BASE_PATH + "/{flowId}", 101L)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(flowService).delete(1L, USER_ID, 101L);
    }

    @Test
    @DisplayName("필수 생성값이 없으면 Service 호출 전에 400으로 거부한다")
    void rejectInvalidCreateRequest() throws Exception {
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
    void rejectLongCreateName() throws Exception {
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
    void rejectBlankUpdateName() throws Exception {
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
    void rejectMissingStatus() throws Exception {
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
    void rejectMalformedJson() throws Exception {
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
    void rejectInvalidStatusEnum() throws Exception {
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
    void handleNotFound() throws Exception {
        FlowNotFoundException exception = new FlowNotFoundException(1L, 101L);
        when(flowService.findById(1L, USER_ID, 101L)).thenThrow(exception);

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
    void handleForbidden() throws Exception {
        FlowCreateRequest request = new FlowCreateRequest(10L, "온도 경고", null);
        ForbiddenException exception = new ForbiddenException("MANAGER 이상 권한이 필요합니다.");
        when(flowService.create(1L, USER_ID, request)).thenThrow(exception);

        mockMvc.perform(post(BASE_PATH)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "locationId": 10,
                                  "name": "온도 경고"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(exception.getMessage()));
    }

    @Test
    @DisplayName("이름 중복 예외는 상세 메시지와 409를 반환한다")
    void handleDuplicateName() throws Exception {
        FlowCreateRequest request = new FlowCreateRequest(10L, "온도 경고", null);
        DuplicateFlowNameException exception = new DuplicateFlowNameException(1L, 10L, "온도 경고");
        when(flowService.create(1L, USER_ID, request)).thenThrow(exception);

        mockMvc.perform(post(BASE_PATH)
                        .header(USER_ID_HEADER, USER_ID)
                        .queryParam("groupId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "locationId": 10,
                                  "name": "온도 경고"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(exception.getMessage()));
    }

    @Test
    @DisplayName("잘못된 상태 전환 예외는 상세 메시지와 409를 반환한다")
    void handleInvalidStatusTransition() throws Exception {
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
    void handleDeletionNotAllowed() throws Exception {
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
        return new FlowResponse(
                flowId,
                1L,
                10L,
                "온도 경고",
                "30도 이상 경고",
                status,
                OffsetDateTime.of(2026, 7, 24, 0, 0, 0, 0, ZoneOffset.UTC)
        );
    }
}
