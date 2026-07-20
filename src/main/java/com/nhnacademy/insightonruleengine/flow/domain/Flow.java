package com.nhnacademy.insightonruleengine.flow.domain;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
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
    private Instant createdDate;

    public Flow(Long groupId, Long locationId, String name, String description, FlowStatus status) {
        this.groupId = Objects.requireNonNull(groupId, "그룹 아이디는 꼭 있어햐 합니다.");
        this.locationId = Objects.requireNonNull(locationId, "장소는 꼭 있어야 합니다.");
        this.name = normalizeName(name);
        this.description = description;
        this.status = Objects.requireNonNull(status, "상태는 빈 값이 될 수 없습니다.");
    }

    public void archive() {
        status = FlowStatus.ARCHIVED;
    }

    @PrePersist
    private void onCreate() {
        createdDate = Instant.now();
    }

    private static String normalizeName(String name) {
        String normalizedName = name.trim().toLowerCase();
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("Name cannot be empty");
        }
        if (normalizedName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Name cannot be longer than " + MAX_NAME_LENGTH + " characters");
        }
        return normalizedName;
    }
}
