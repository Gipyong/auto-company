package com.autocompany.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "job_grades", uniqueConstraints = @UniqueConstraint(name = "uk_grade_ordinal_org", columnNames = {"organization_id", "ordinal"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JobGrade {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int ordinal;

    @Column(columnDefinition = "TEXT")
    private String responsibilities;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "task_types", columnDefinition = "jsonb")
    private List<String> taskTypes;

    @Enumerated(EnumType.STRING)
    @Column(name = "model_selection_mode", nullable = false, length = 30)
    private ModelSelectionMode modelSelectionMode = ModelSelectionMode.PRIORITY;

    @Builder.Default
    @Column(name = "is_template_default", nullable = false)
    private boolean templateDefault = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum ModelSelectionMode {
        PRIORITY,
        TASK_BASED,
        BUDGET_OPTIMIZED
    }

    public enum Status {
        ACTIVE,
        ARCHIVED
    }
}