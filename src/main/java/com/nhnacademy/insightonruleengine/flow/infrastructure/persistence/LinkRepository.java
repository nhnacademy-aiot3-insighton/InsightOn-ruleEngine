package com.nhnacademy.insightonruleengine.flow.infrastructure;

import com.nhnacademy.insightonruleengine.flow.domain.Link;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LinkRepository extends JpaRepository<Link, Long> {

    List<Link> findByFlowId(Long flowId);

    /**
     * Flow 전체 교체 방식에서 사용하는 delete.
     */
    @Modifying
    @Query("delete from Link l where l.flowId = :flowId")
    void deleteByFlowId(@Param("flowId") Long flowId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from Link link
            where link.flowId in (
                select flow.id from Flow flow where flow.groupId = :groupId
            )
            """)
    int deleteByGroupId(@Param("groupId") Long groupId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from Link link
            where link.flowId in (
                select flow.id from Flow flow where flow.locationId = :locationId
            )
            """)
    int deleteByLocationId(@Param("locationId") Long locationId);

}
