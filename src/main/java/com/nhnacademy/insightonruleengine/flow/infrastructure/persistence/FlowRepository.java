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

    // AI draft 생성 시 같은 위치·같은 이름의 "살아있는"(ARCHIVED가 아닌) 기존 Flow를 찾기 위해
    // 조회합니다. (group_id, location_id, name) 유니크 인덱스가 ARCHIVED를 제외하므로, 같은 이름의
    // ARCHIVED Flow가 여러 개 있을 수 있어 상태 무관 조회는 더 이상 결과가 유일함을 보장하지 않습니다.
    Optional<Flow> findByGroupIdAndLocationIdAndNameAndStatusNot(
            Long groupId, Long locationId, String name, FlowStatus status);

    // 휴지통 복구 시 이름 충돌을 확인하기 위해 사용합니다. archive된 동안 같은 이름의 새 Flow가
    // 만들어졌을 수 있어(위 유니크 인덱스가 ARCHIVED를 제외), DB 제약만으로는 복구를 막아주지 않습니다.
    boolean existsByGroupIdAndLocationIdAndNameAndStatusNot(
            Long groupId, Long locationId, String name, FlowStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Flow flow where flow.groupId = :groupId")
    int deleteByGroupId(@Param("groupId") Long groupId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Flow flow where flow.locationId = :locationId")
    int deleteByLocationId(@Param("locationId") Long locationId);
}
