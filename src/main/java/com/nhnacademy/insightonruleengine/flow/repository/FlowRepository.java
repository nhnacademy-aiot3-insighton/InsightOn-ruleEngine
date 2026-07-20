package com.nhnacademy.insightonruleengine.flow.repository;

import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlowRepository extends JpaRepository<Flow, Long> {
    List<Flow> findAllByGroupId(Long groupId);

    List<Flow> findAllByGroupIdAndStatus(Long groupId, FlowStatus status);

    List<Flow> findAllByGroupIdAndLocationIdAndStatus(Long groupId, Long locationId, FlowStatus status);

    // 플로우는 같은 이름일 수 없지만 아카이브에 들어가 있는 플로우는 같은 이름이 될 수 있음
    boolean existsByGroupIdAndLocationIdAndNameAndStatusNot(
            Long groupId, Long locationId, String name, FlowStatus status
    );

}
