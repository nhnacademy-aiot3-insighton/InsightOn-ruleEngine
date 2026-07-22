package com.nhnacademy.insightonruleengine.flow.repository;

import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlowRepository extends JpaRepository<Flow, Long> {
    List<Flow> findAllByGroupId(Long groupId);

    List<Flow> findAllByGroupIdAndStatus(Long groupId, FlowStatus status);

    List<Flow> findAllByGroupIdAndLocationIdAndStatus(Long groupId, Long locationId, FlowStatus status);

    boolean existsByGroupIdAndLocationIdAndName(Long groupId, Long locationId, String name);
}
