package com.nhnacademy.insightonruleengine.flow.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.client.core.CoreActuatorClient;
import com.nhnacademy.insightonruleengine.client.core.LocationResponse;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowCreateRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowLinkRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowNodeRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.request.FlowStatusChangeRequest;
import com.nhnacademy.insightonruleengine.flow.api.dto.response.FlowResponse;
import com.nhnacademy.insightonruleengine.flow.application.assembly.FlowDefinitionAssembler;
import com.nhnacademy.insightonruleengine.flow.application.authorization.GroupAuthorizationService;
import com.nhnacademy.insightonruleengine.flow.application.validation.FlowActivationValidator;
import com.nhnacademy.insightonruleengine.flow.application.validation.FlowStructureValidator;
import com.nhnacademy.insightonruleengine.flow.application.validation.NodeConfigurationValidator;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.FlowRepository;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.LinkRepository;
import com.nhnacademy.insightonruleengine.flow.infrastructure.persistence.NodeRepository;
import com.nhnacademy.insightonruleengine.runner.application.schedule.ScheduleFlowScheduler;
import com.nhnacademy.insightonruleengine.runner.infrastructure.cache.ActiveFlowDefinitionProvider;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.jdbc.Sql;

/**
 * 사용자가 스테이징에서 curl로 확인하려던 신규/무변화/갱신 3케이스를, 실제 Postgres(Testcontainers, dev DB에 적용한 것과 같은 부분 유니크 인덱스 포함)와 실제 FlowService
 * 로직으로 end-to-end 재현한다. Core/Redis/RabbitMQ 등 외부 인프라만 Mockito로 대체하고, DB와 상호작용하는 부분은 전부 진짜로 돈다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "classpath:sql/flow-name-partial-unique-index.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AiDraftLifecycleIntegrationTest {

    private static final long GROUP_ID = 1L;
    private static final long USER_ID = 100L;
    private static final long LOCATION_ID = 42L;
    private static final String DRAFT_NAME = "[AI] co2 예방 자동화";

    @Autowired
    private FlowRepository flowRepository;

    @Autowired
    private NodeRepository nodeRepository;

    @Autowired
    private LinkRepository linkRepository;

    private FlowService flowService;

    @BeforeEach
    void setUp() {
        CoreActuatorClient coreActuatorClient = mock(CoreActuatorClient.class);
        when(coreActuatorClient.getLocation(LOCATION_ID)).thenReturn(
                new LocationResponse(LOCATION_ID, GROUP_ID, "회의실", LocationResponse.AutoControlMode.SUGGESTION));

        flowService = new FlowService(
                flowRepository,
                mock(GroupAuthorizationService.class),
                nodeRepository,
                linkRepository,
                mock(FlowStructureValidator.class),
                mock(NodeConfigurationValidator.class),
                mock(FlowDefinitionAssembler.class),
                mock(FlowActivationValidator.class),
                mock(ActiveFlowDefinitionProvider.class),
                mock(ScheduleFlowScheduler.class),
                coreActuatorClient);
    }

    @Test
    @DisplayName("신규 -> 무변화 -> cron 갱신(ACTIVE 승계) -> 액추에이터 변경(재승인) 전체 라이프사이클")
    void fullLifecycleTest() {
        FlowCreateRequest first = aiDraftRequest("0 0 * * * *", "ON");

        // 케이스 1: 신규 생성
        FlowResponse created = flowService.createAiDraft(GROUP_ID, first);
        Assertions.assertNull(created.replacedFlowId());
        Assertions.assertEquals(FlowStatus.INACTIVE, created.status());
        assertOnlyOneLiveFlowForName();

        // 케이스 2: 완전히 같은 요청 -> 그대로 반환, 새 row 없음
        FlowResponse unchanged = flowService.createAiDraft(GROUP_ID, first);
        Assertions.assertEquals(created.flowId(), unchanged.flowId());
        Assertions.assertNull(unchanged.replacedFlowId());
        assertOnlyOneLiveFlowForName();

        // 사용자가 SUGGESTION 초안을 검토하고 수동으로 활성화한 상태를 재현
        flowService.changeActivationStatus(
                GROUP_ID, USER_ID, created.flowId(), new FlowStatusChangeRequest(FlowStatus.ACTIVE));
        Assertions.assertEquals(FlowStatus.ACTIVE, flowRepository.findById(created.flowId()).orElseThrow().getStatus());

        // 케이스 3: cron만 변경 -> archive 후 재생성, ACTIVE 승계
        FlowCreateRequest cronChanged = aiDraftRequest("0 30 * * * *", "ON");
        FlowResponse afterCronChange = flowService.createAiDraft(GROUP_ID, cronChanged);
        Assertions.assertEquals(created.flowId(), afterCronChange.replacedFlowId());
        Assertions.assertNotEquals(created.flowId(), afterCronChange.flowId());
        Assertions.assertEquals(FlowStatus.ACTIVE, afterCronChange.status());
        Assertions.assertEquals(
                FlowStatus.ARCHIVED, flowRepository.findById(created.flowId()).orElseThrow().getStatus());
        assertOnlyOneLiveFlowForName();

        // 케이스 4: 액추에이터 명령 변경 -> archive 후 재생성, ACTIVE였어도 재승인 필요(INACTIVE)
        FlowCreateRequest actuatorChanged = aiDraftRequest("0 30 * * * *", "OFF");
        FlowResponse afterActuatorChange = flowService.createAiDraft(GROUP_ID, actuatorChanged);
        Assertions.assertEquals(afterCronChange.flowId(), afterActuatorChange.replacedFlowId());
        Assertions.assertEquals(FlowStatus.INACTIVE, afterActuatorChange.status());
        Assertions.assertEquals(
                FlowStatus.ARCHIVED, flowRepository.findById(afterCronChange.flowId()).orElseThrow().getStatus());
        assertOnlyOneLiveFlowForName();

        // 이름을 점유한 살아있는 row는 항상 1개, 전체(ARCHIVED 포함)는 3개여야 부분 유니크 인덱스가
        // 의도대로 동작한 것이다.
        long totalRowsForName = flowRepository.findAll().stream()
                .filter(flow -> flow.getGroupId().equals(GROUP_ID) && flow.getName().equals(DRAFT_NAME))
                .count();
        Assertions.assertEquals(3, totalRowsForName);
    }

    // 휴지통 복구 시 이름 충돌 검증도 실제 DB로 확인한다.
    @Test
    @DisplayName("archive된 flow를 복구하려는데 같은 이름의 살아있는 flow가 있으면 실제 DB 기준으로도 막힌다")
    void restoreCollidesWithLiveFlowTest() {
        FlowCreateRequest first = aiDraftRequest("0 0 * * * *", "ON");
        FlowResponse created = flowService.createAiDraft(GROUP_ID, first);

        FlowCreateRequest cronChanged = aiDraftRequest("0 30 * * * *", "ON");
        flowService.createAiDraft(GROUP_ID, cronChanged);

        Assertions.assertThrows(
                com.nhnacademy.insightonruleengine.flow.domain.exception.DuplicateFlowNameException.class,
                () -> flowService.restore(GROUP_ID, USER_ID, created.flowId()));
    }

    // 이름을 점유한 살아있는(ARCHIVED가 아닌) row는 시나리오 내내 항상 1개여야 한다 — 그 이상이면
    // 부분 유니크 인덱스나 archive 순서가 의도대로 동작하지 않은 것이다.
    private void assertOnlyOneLiveFlowForName() {
        long count = flowRepository.findAll().stream()
                .filter(flow -> flow.getGroupId().equals(GROUP_ID)
                        && flow.getName().equals(DRAFT_NAME)
                        && flow.getStatus() != FlowStatus.ARCHIVED)
                .count();
        Assertions.assertEquals(1, count);
    }

    // actuatorType/command는 이 시나리오에서 고정이라 파라미터로 두지 않는다 — 실제로 바뀌는 건
    // cron과 commandValue뿐이다.
    private FlowCreateRequest aiDraftRequest(String cron, String commandValue) {
        List<FlowNodeRequest> nodes = List.of(
                FlowNodeRequest.builder()
                        .clientNodeKey("hourly_schedule")
                        .nodeType(NodeType.SCHEDULE)
                        .configuration(JsonNodeFactory.instance.objectNode().put("cron", cron))
                        .build(),
                FlowNodeRequest.builder()
                        .clientNodeKey("fan_actuator")
                        .nodeType(NodeType.ACTUATOR_CONTROL)
                        .configuration(JsonNodeFactory.instance.objectNode()
                                .put("actuatorType", "VENTILATION_FAN")
                                .put("command", "power")
                                .put("commandValue", commandValue))
                        .build()
        );
        List<FlowLinkRequest> links = List.of(
                FlowLinkRequest.builder()
                        .sourceClientNodeKey("hourly_schedule")
                        .targetClientNodeKey("fan_actuator")
                        .sourcePort("out")
                        .targetPort("in")
                        .build()
        );
        return FlowCreateRequest.builder()
                .locationId(LOCATION_ID)
                .name(DRAFT_NAME)
                .description("스테이징 재현용")
                .nodes(nodes)
                .links(links)
                .build();
    }
}
