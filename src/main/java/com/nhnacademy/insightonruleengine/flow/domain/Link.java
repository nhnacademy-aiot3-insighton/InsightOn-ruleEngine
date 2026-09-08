package com.nhnacademy.insightonruleengine.flow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.Hibernate;

/**
 */
@Entity
@Table(
        name = "links",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_links_flow_source_port_target",
                columnNames = {
                        "flow_id", "source_node_id", "source_port", "target_node_id", "target_port"
                }
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Link {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "link_id")
    private Long id;

    @Column(name = "flow_id", nullable = false)
    private Long flowId;

    @Column(name = "source_node_id", nullable = false)
    private Long sourceNodeId;

    @Column(name = "target_node_id", nullable = false)
    private Long targetNodeId;

    @Column(name = "source_port", nullable = false, length = 50)
    private String sourcePort;

    @Column(name = "target_port", nullable = false, length = 50)
    private String targetPort;

    public Link(Long flowId, Long sourceNodeId, String sourcePort, Long targetNodeId, String targetPort) {
        this.flowId = flowId;
        this.sourceNodeId = sourceNodeId;
        this.sourcePort = sourcePort;
        this.targetNodeId = targetNodeId;
        this.targetPort = targetPort;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;

        Link other = (Link) o;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
