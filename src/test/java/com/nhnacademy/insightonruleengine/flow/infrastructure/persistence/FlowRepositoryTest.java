package com.nhnacademy.insightonruleengine.flow.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.Node;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// (group_id, location_id, name) 부분 유니크 인덱스는 JPA @UniqueConstraint로 표현할 수 없어
// Flow 엔티티에 더 이상 선언돼 있지 않다. create-drop으로 만들어지는 이 스키마에는 실제 운영
// migration과 같은 조건의 인덱스를 테스트 시작 전마다 별도로 만들어 재현한다.
@Sql(scripts = "classpath:sql/flow-name-partial-unique-index.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class FlowRepositoryTest {

    @Autowired
    private FlowRepository flowRepository;

    @Autowired
    private NodeRepository nodeRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Flow를 저장하고 ID로 조회한다")
    void saveAndFindById() {
        Flow flow = createFlow(1L, 2L, "테스트", FlowStatus.ACTIVE);

        Flow savedFlow = flowRepository.saveAndFlush(flow);
        entityManager.clear();

        Flow foundFlow = flowRepository.findById(savedFlow.getId()).orElseThrow();

        Assertions.assertEquals(1L, foundFlow.getGroupId());
        Assertions.assertEquals(2L, foundFlow.getLocationId());
        Assertions.assertEquals("테스트", foundFlow.getName());
        Assertions.assertEquals(FlowStatus.ACTIVE, foundFlow.getStatus());
    }

    @Test
    @DisplayName("ARCHIVED 상태와 관계없이 같은 범위의 이름 중복을 확인한다")
    void findDuplicateNameRegardlessOfStatus() {
        flowRepository.saveAndFlush(createFlow(1L, 1L, "테스트", FlowStatus.ARCHIVED));

        boolean exists = flowRepository.existsByGroupIdAndLocationIdAndName(1L, 1L, "테스트");

        Assertions.assertTrue(exists);
    }

    // archive된 Flow는 부분 유니크 인덱스에서 이름을 점유하지 않으므로, 같은 이름의 새 Flow를
    // 정상적으로 만들 수 있어야 합니다 — AI draft 갱신(archive 후 재생성)이 의존하는 동작입니다.
    @Test
    @DisplayName("ARCHIVED Flow와는 같은 범위에 같은 이름을 저장할 수 있다")
    void allowDuplicateNameWhenOnlyArchivedFlowExists() {
        flowRepository.saveAndFlush(createFlow(1L, 1L, "테스트", FlowStatus.ARCHIVED));

        Flow newFlow = flowRepository.saveAndFlush(createFlow(1L, 1L, "테스트", FlowStatus.ACTIVE));

        Assertions.assertEquals(FlowStatus.ACTIVE, newFlow.getStatus());
    }

    @Test
    @DisplayName("그룹이나 장소가 다르면 같은 이름을 저장할 수 있다")
    void allowSameNameInDifferentGroupOrLocation() {
        flowRepository.save(createFlow(1L, 1L, "테스트", FlowStatus.ACTIVE));
        flowRepository.save(createFlow(2L, 1L, "테스트", FlowStatus.ACTIVE));
        flowRepository.save(createFlow(1L, 2L, "테스트", FlowStatus.ACTIVE));

        flowRepository.flush();

        Assertions.assertEquals(3, flowRepository.findAll().size());
    }

    @Test
    @DisplayName("대소문자가 다르면 같은 범위에 저장할 수 있다")
    void allowNamesWithDifferentCase() {
        flowRepository.save(createFlow(1L, 1L, "Test Flow", FlowStatus.ACTIVE));
        flowRepository.save(createFlow(1L, 1L, "test flow", FlowStatus.ACTIVE));

        flowRepository.flush();

        Assertions.assertEquals(2, flowRepository.findAll().size());
    }

    @Test
    @DisplayName("Flow 이름은 공백을 제거하고 대소문자를 보존한다")
    void trimNameAndPreserveCase() {
        Flow flow = createFlow(1L, 1L, "  Test Flow  ", FlowStatus.ACTIVE);

        Flow savedFlow = flowRepository.saveAndFlush(flow);
        entityManager.clear();

        Flow foundFlow = flowRepository.findById(savedFlow.getId()).orElseThrow();

        Assertions.assertEquals("Test Flow", foundFlow.getName());
    }

    @Test
    @DisplayName("그룹, 장소, 상태가 일치하는 Flow만 조회한다")
    void findAllByGroupAndLocation() {
        Flow activeFlow = flowRepository.save(
                createFlow(1L, 1L, "Active Flow", FlowStatus.ACTIVE)
        );
        flowRepository.save(
                createFlow(1L, 1L, "Archived Flow", FlowStatus.ARCHIVED)
        );
        flowRepository.save(
                createFlow(2L, 1L, "Other Group", FlowStatus.ACTIVE)
        );
        flowRepository.save(
                createFlow(1L, 2L, "Other Location", FlowStatus.ACTIVE)
        );

        entityManager.flush();
        entityManager.clear();

        List<Flow> result =
                flowRepository.findAllByGroupIdAndLocationIdAndStatus(
                        1L,
                        1L,
                        FlowStatus.ACTIVE
                );

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(activeFlow.getId(), result.get(0).getId());
        Assertions.assertEquals(FlowStatus.ACTIVE, result.get(0).getStatus());
    }

    @Test
    @DisplayName("ACTIVE 상태이며 요청한 타입의 노드가 있는 Flow만 조회한다")
    void findAllActiveFlowsByNodeType() {
        Flow activeSchedule = flowRepository.save(
                createFlow(1L, 1L, "Active Schedule", FlowStatus.ACTIVE)
        );
        Flow activeSensor = flowRepository.save(
                createFlow(1L, 2L, "Active Sensor", FlowStatus.ACTIVE)
        );
        Flow inactiveSchedule = flowRepository.save(
                createFlow(1L, 3L, "Inactive Schedule", FlowStatus.INACTIVE)
        );
        nodeRepository.save(new Node(
                activeSchedule.getId(),
                NodeType.SCHEDULE,
                JsonNodeFactory.instance.objectNode().put("cron", "0 0 * * * *")
        ));
        nodeRepository.save(new Node(
                activeSensor.getId(),
                NodeType.SENSOR,
                JsonNodeFactory.instance.objectNode().put("sensorId", 100L)
        ));
        nodeRepository.save(new Node(
                inactiveSchedule.getId(),
                NodeType.SCHEDULE,
                JsonNodeFactory.instance.objectNode().put("cron", "0 0 * * * *")
        ));
        entityManager.flush();
        entityManager.clear();

        List<Flow> result = flowRepository.findAllByStatusAndNodeType(
                FlowStatus.ACTIVE,
                NodeType.SCHEDULE
        );

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(activeSchedule.getId(), result.getFirst().getId());
    }

    @Test
    @DisplayName("생성 시각 테스트")
    void createdAtTest() {
        Flow createdFlow = flowRepository.saveAndFlush(
                createFlow(1L, 1L, "Test Flow", FlowStatus.ACTIVE)
        );
        Long createdFlowId = createdFlow.getId();
        entityManager.clear();

        Flow foundFlow = flowRepository.findById(createdFlowId).orElseThrow();

        Assertions.assertNotNull(foundFlow.getCreatedDate());
    }

    @Test
    @DisplayName("ARCHIVED가 아닌, 그룹·장소·이름이 일치하는 살아있는 Flow를 조회한다")
    void findByGroupIdAndLocationIdAndNameAndStatusNotFindsLiveFlow() {
        Flow inactiveFlow = flowRepository.saveAndFlush(
                createFlow(1L, 1L, "co2 예방 자동화 (AI 제안)", FlowStatus.INACTIVE)
        );
        entityManager.clear();

        Flow found = flowRepository
                .findByGroupIdAndLocationIdAndNameAndStatusNot(1L, 1L, "co2 예방 자동화 (AI 제안)", FlowStatus.ARCHIVED)
                .orElseThrow();

        Assertions.assertEquals(inactiveFlow.getId(), found.getId());
        Assertions.assertEquals(FlowStatus.INACTIVE, found.getStatus());
    }

    // (group_id, location_id, name) 유니크 인덱스가 ARCHIVED를 제외하므로, 같은 이름의 ARCHIVED
    // Flow만 있는 경우는 "살아있는 Flow 없음"과 같게 취급되어야 합니다(그대로 반환하면 안 됨).
    @Test
    @DisplayName("같은 이름의 ARCHIVED Flow만 있으면 빈 값을 반환한다")
    void findByGroupIdAndLocationIdAndNameAndStatusNotIgnoresArchived() {
        flowRepository.saveAndFlush(
                createFlow(1L, 1L, "co2 예방 자동화 (AI 제안)", FlowStatus.ARCHIVED)
        );
        entityManager.clear();

        boolean found = flowRepository
                .findByGroupIdAndLocationIdAndNameAndStatusNot(1L, 1L, "co2 예방 자동화 (AI 제안)", FlowStatus.ARCHIVED)
                .isPresent();

        Assertions.assertEquals(false, found);
    }

    @Test
    @DisplayName("일치하는 Flow가 없으면 빈 값을 반환한다")
    void findByGroupIdAndLocationIdAndNameReturnsEmptyWhenMissing() {
        boolean found = flowRepository
                .findByGroupIdAndLocationIdAndNameAndStatusNot(1L, 1L, "존재하지 않음", FlowStatus.ARCHIVED)
                .isPresent();

        Assertions.assertEquals(false, found);
    }

    // IDENTITY 채번 Entity는 save() 시점에 바로 INSERT가 나가 유니크 제약 위반도 save()에서 즉시 발생합니다.
    @Test
    @DisplayName("ARCHIVED가 아닌 Flow끼리 이름이 충돌하면 save()에서 바로 유니크 제약 위반이 발생한다")
    void duplicateNameFailsImmediatelyOnSave() {
        flowRepository.saveAndFlush(createFlow(1L, 1L, "AI draft", FlowStatus.INACTIVE));
        Flow conflictingFlow = createFlow(1L, 1L, "AI draft", FlowStatus.INACTIVE);

        assertThrows(DataIntegrityViolationException.class, () -> flowRepository.save(conflictingFlow));
    }

    // 이 테스트는 유니크 제약 위반 이후 같은 트랜잭션(영속성 컨텍스트)을 계속 쓰면 안 되는 이유를 남겨둡니다.
    // save()가 실패한 다음 같은 트랜잭션에서 재조회를 시도하면, 실패한 Entity가 다시 flush 대상에 걸려
    // Hibernate가 AssertionFailure를 던집니다. 그래서 FlowService.createAiDraft()는 재조회로 복구하지 않고
    // DuplicateFlowNameException을 던져 트랜잭션을 그대로 롤백시킵니다.
    @Test
    @DisplayName("유니크 제약 위반 이후 같은 트랜잭션에서 재조회하면 안전하지 않다")
    void reusingSessionAfterConstraintViolationIsUnsafe() {
        flowRepository.saveAndFlush(createFlow(1L, 1L, "AI draft", FlowStatus.INACTIVE));
        Flow conflictingFlow = createFlow(1L, 1L, "AI draft", FlowStatus.INACTIVE);

        assertThrows(DataIntegrityViolationException.class, () -> flowRepository.save(conflictingFlow));

        assertThrows(
                RuntimeException.class,
                () -> flowRepository.findByGroupIdAndLocationIdAndNameAndStatusNot(
                        1L, 1L, "AI draft", FlowStatus.ARCHIVED));
    }

    private Flow createFlow(Long groupId, Long locationId, String name, FlowStatus status) {
        return new Flow(groupId, locationId, name, "테스트", status);
    }
}
