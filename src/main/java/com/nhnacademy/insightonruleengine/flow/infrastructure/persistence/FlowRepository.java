package com.nhnacademy.insightonruleengine.flow.infrastructure.persistence;

import com.nhnacademy.insightonruleengine.flow.domain.Flow;
import com.nhnacademy.insightonruleengine.flow.domain.FlowStatus;
import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import java.util.List;
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Flow flow where flow.groupId = :groupId")
    int deleteByGroupId(@Param("groupId") Long groupId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Flow flow where flow.locationId = :locationId")
    int deleteByLocationId(@Param("locationId") Long locationId);
}
