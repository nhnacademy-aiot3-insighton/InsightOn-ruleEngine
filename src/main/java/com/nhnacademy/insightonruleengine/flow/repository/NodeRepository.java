package com.nhnacademy.insightonruleengine.flow.repository;

import com.nhnacademy.insightonruleengine.flow.domain.Node;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NodeRepository extends JpaRepository<Node, Long> {

    List<Node> findByFlowId(Long flowId);

    List<Node> findByFlowIdIn(List<Long> flowIds);

    /**
     * Flow 전체 교체(delete+insert) 방식에서 사용하는 delete Node가 Flow와 JPA 연관관계(@OneToMany)를 갖지 않으므로 orphanRemoval로 지울 수 없음
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Node n where n.flowId = :flowId")
    int deleteByFlowId(@Param("flowId") Long flowId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from Node node
            where node.flowId in (
                select flow.id from Flow flow where flow.groupId = :groupId
            )
            """)
    int deleteByGroupId(@Param("groupId") Long groupId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from Node node
            where node.flowId in (
                select flow.id from Flow flow where flow.locationId = :locationId
            )
            """)
    int deleteByLocationId(@Param("locationId") Long locationId);
}
