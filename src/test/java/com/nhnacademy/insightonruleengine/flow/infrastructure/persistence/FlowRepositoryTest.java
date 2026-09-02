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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
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

    @Test
    @DisplayName("ARCHIVED Flow와 같은 범위에 같은 이름을 저장할 수 없다")
    void rejectDuplicateNameWhenArchivedFlowExists() {
        flowRepository.saveAndFlush(createFlow(1L, 1L, "테스트", FlowStatus.ARCHIVED));

        Flow duplicatedFlow = createFlow(1L, 1L, "테스트", FlowStatus.ACTIVE);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> flowRepository.saveAndFlush(duplicatedFlow)
        );
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
    @DisplayName("상태와 관계없이 그룹, 장소, 이름이 일치하는 Flow를 조회한다")
    void findByGroupIdAndLocationIdAndNameRegardlessOfStatus() {
        Flow archivedFlow = flowRepository.saveAndFlush(
                createFlow(1L, 1L, "co2 예방 자동화 (AI 제안)", FlowStatus.ARCHIVED)
        );
        entityManager.clear();

        Flow found = flowRepository
                .findByGroupIdAndLocationIdAndName(1L, 1L, "co2 예방 자동화 (AI 제안)")
                .orElseThrow();

        Assertions.assertEquals(archivedFlow.getId(), found.getId());
        Assertions.assertEquals(FlowStatus.ARCHIVED, found.getStatus());
    }

    @Test
    @DisplayName("일치하는 Flow가 없으면 빈 값을 반환한다")
    void findByGroupIdAndLocationIdAndNameReturnsEmptyWhenMissing() {
        boolean found = flowRepository
                .findByGroupIdAndLocationIdAndName(1L, 1L, "존재하지 않음")
                .isPresent();

        Assertions.assertEquals(false, found);
    }

    // IDENTITY 채번 Entity는 save() 시점에 바로 INSERT가 나가 유니크 제약 위반도 save()에서 즉시 발생합니다.
    @Test
    @DisplayName("이름 충돌 시 save()에서 바로 유니크 제약 위반이 발생한다")
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
                () -> flowRepository.findByGroupIdAndLocationIdAndName(1L, 1L, "AI draft"));
    }

    private Flow createFlow(Long groupId, Long locationId, String name, FlowStatus status) {
        return new Flow(groupId, locationId, name, "테스트", status);
    }
}
