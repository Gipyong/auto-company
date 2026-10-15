package com.autocompany.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "grade_models", uniqueConstraints = @UniqueConstraint(name = "uk_grade_model", columnNames = {"grade_id", "model_profile_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GradeModel {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "grade_id", nullable = false)
    private UUID gradeId;

    @Column(name = "model_profile_id", nullable = false)
    private UUID modelProfileId;

    @Column(nullable = false)
    private int priority;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

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
}