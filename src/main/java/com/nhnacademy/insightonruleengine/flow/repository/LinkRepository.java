package com.nhnacademy.insightonruleengine.flow.repository;

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
}