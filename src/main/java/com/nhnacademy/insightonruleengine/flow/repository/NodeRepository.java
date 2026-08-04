package com.nhnacademy.insightonruleengine.flow.repository;

import com.nhnacademy.insightonruleengine.flow.domain.Node;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NodeRepository extends JpaRepository<Node, Long> {

    List<Node> findByFlowId(Long flowId);

    /**
     * Flow 전체 교체(delete+insert) 방식에서 사용하는 delete
     * Node가 Flow와 JPA 연관관계(@OneToMany)를 갖지 않으므로 orphanRemoval로 지울 수 없음
     */
    @Modifying
    @Query("delete from Node n where n.flowId = :flowId")
    void deleteByFlowId(@Param("flowId") Long flowId);
}
