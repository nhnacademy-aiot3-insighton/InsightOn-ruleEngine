package com.nhnacademy.insightonruleengine.flow.domain;

import com.nhnacademy.insightonruleengine.flow.domain.exception.InvalidFlowStatusTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// (group_id, location_id, name) 유니크 제약은 여기서 @UniqueConstraint로 선언하지 않는다.
// ARCHIVED를 제외해야 해서(JPA가 표현할 수 없는 부분 유니크 인덱스) DB에
// project-docs/engine-current-baseline-2026-08-31/db-migrations/2026-09-03-flow-name-partial-unique.sql로
// 직접 만든다. archive된 Flow는 이름을 점유하지 않으므로 같은 이름으로 새 Flow를 만들 수 있다.
@Entity
@Table(
        name = "flows",
        indexes = {
                @Index(name = "idx_flows_group_id", columnList = "group_id, status"),
                @Index(
                        name = "idx_flows_group_location_status",
                        columnList = "group_id, location_id, status"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Flow {

    private static final int MAX_NAME_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "flow_id")
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private FlowStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdDate;

    public Flow(Long groupId, Long locationId, String name, String description, FlowStatus status) {
        if (groupId == null) {
            throw new IllegalArgumentException("그룹아이디는 필수입니다.");
        }
        this.groupId = groupId;
        if (locationId == null) {
            throw new IllegalArgumentException("장소아이디는 필수입니다.");
        }
        this.locationId = locationId;
        this.name = validationName(name);
        this.description = description;
        if (status == null) {
            throw new IllegalArgumentException("상태값은 필수입니다.");
        }
        this.status = status;
        this.createdDate = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void changeActivationStatus(FlowStatus flowStatus) {
        if (flowStatus == null) {
            throw new IllegalArgumentException("상태값은 null이 될 수 없습니다.");
        }
        boolean canChange = status.equals(FlowStatus.ACTIVE) && flowStatus.equals(FlowStatus.INACTIVE)
                || status.equals(FlowStatus.INACTIVE) && flowStatus.equals(FlowStatus.ACTIVE);
        if (!canChange) {
            throw new InvalidFlowStatusTransitionException(status, flowStatus);
        }
        status = flowStatus;
    }

    public void archive() {
        if (!status.equals(FlowStatus.ACTIVE)
                && !status.equals(FlowStatus.INACTIVE)
                && !status.equals(FlowStatus.ERROR)) {
            throw new InvalidFlowStatusTransitionException(status, FlowStatus.ARCHIVED);
        }
        status = FlowStatus.ARCHIVED;
    }

    public void restore() {
        if (!status.equals(FlowStatus.ARCHIVED)) {
            throw new InvalidFlowStatusTransitionException(status, FlowStatus.INACTIVE);
        }
        status = FlowStatus.INACTIVE;
    }

    private String validationName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("이름은 필수입니다.");
        }
        String normalizedName = name.strip();
        if (normalizedName.isBlank()) {
            throw new IllegalArgumentException("이름은 공백일 수 없습니다.");
        }
        if (normalizedName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("이름은 " + MAX_NAME_LENGTH + "자를 초과할 수 없습니다.");
        }
        return normalizedName;
    }
}
