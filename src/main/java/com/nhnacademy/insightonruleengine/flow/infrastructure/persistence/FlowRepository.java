package com.nhnacademy.insightonruleengine.flow.infrastructure.persistence;

import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlowRepository extends JpaRepository<Flow, Long> {

    List<Flow> findAllByGroupId(Long groupId);

    List<Flow> findAllByLocationId(Long locationId);

    List<Flow> findAllByGroupIdAndStatusNot(Long groupId, FlowStatus status);

    List<Flow> findAllByGroupIdAndStatus(Long groupId, FlowStatus status);

    List<Flow> findAllByGroupIdAndLocationIdAndStatus(Long groupId, Long locationId, FlowStatus status);

    List<Flow> findAllByStatus(FlowStatus status);

    @Query("""
            select flow
            from Flow flow
            where flow.status = :status
              and exists (
                  select node.id
                  from Node node
                  where node.flowId = flow.id
                    and node.nodeType = :nodeType
              )
            order by flow.id
            """)
    List<Flow> findAllByStatusAndNodeType(
            @Param("status") FlowStatus status,
            @Param("nodeType") NodeType nodeType
    );

    boolean existsByGroupIdAndLocationIdAndName(Long groupId, Long locationId, String name);

    // AI draft 생성 시 같은 위치·같은 이름의 기존 Flow(상태 무관)를 그대로 재사용하기 위해 조회합니다.
    Optional<Flow> findByGroupIdAndLocationIdAndName(Long groupId, Long locationId, String name);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Flow flow where flow.groupId = :groupId")
    int deleteByGroupId(@Param("groupId") Long groupId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Flow flow where flow.locationId = :locationId")
    int deleteByLocationId(@Param("locationId") Long locationId);
}
