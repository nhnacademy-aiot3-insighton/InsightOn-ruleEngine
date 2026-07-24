package com.nhnacademy.insightonruleengine.node.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * flows/nodes/links 3테이블 중 nodes
 * Link는 이 Entity의 {@link #id}를 직접 참조
 *
 * <p>Flow와의 관계는 JPA 연관관계(@ManyToOne)로 매핑고민. 우선은 flowId만 저장하는걸로 고민중
 */
@Entity
@Table(name = "nodes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Node {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "node_id", nullable = false)
    private Long id;

    @NotNull
    @Column(name = "flow_id", nullable = false)
    private Long flowId;

    /**
     * 9개 구체 타입 중 하나. DB에는 enum name()이 문자열로 저장된다(예: "THRESHOLD").
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 50)
    private NodeType nodeType;

    @Column(name = "name")
    private String name;

    /**
     * 순수 파라미터만 담긴 JSON 문자열.
     * 실제 타입 있는 객체로의 변환은 이 클래스가 아니라
     * {@code node.parser.NodeParamsParser}가 담당
     */
    @NotBlank
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration", columnDefinition = "jsonb", nullable = false)
    private String configuration;

    public Node(Long flowId, NodeType nodeType, String name, String configuration) {
        this.flowId = flowId;
        this.nodeType = nodeType;
        this.name = name;
        this.configuration = configuration;
    }

    /** DB 컬럼이 아니라 Enum 정의 */
    public NodeType.Category getCategory() {
        return nodeType.getCategory();
    }

    public void rename(String newName) {
        this.name = newName;
    }

    public void updateConfiguration(String newConfiguration) {
        this.configuration = newConfiguration;
    }
    
    
}
