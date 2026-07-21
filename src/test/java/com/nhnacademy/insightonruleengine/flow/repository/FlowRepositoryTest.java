package com.nhnacademy.insightonruleengine.flow.repository;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
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

    private Flow createFlow(Long groupId, Long locationId, String name, FlowStatus status) {
        return new Flow(groupId, locationId, name, "테스트", status);
    }
}
