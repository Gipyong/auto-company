package com.autocompany.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.autocompany.entity.enums.*;


@Entity
@Table(name = "tasks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Task {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_task_id")
    private Task parentTask;
    
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskType type = TaskType.TASK;
    
    @Column(nullable = false)
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String acceptanceCriteria;
    
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.DRAFT;
    
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority = Priority.NORMAL;
    
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskLevel riskLevel = RiskLevel.LOW;
    
    @Builder.Default
    private int complexity = 1;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private Employee assignee;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String reviewerEmployeeIds;
    
    @Column(name = "created_by_user_id")
    private UUID createdByUserId;
    
    @Column(name = "created_by_employee_id")
    private UUID createdByEmployeeId;
    
    private Integer estimatedEffort;
    
    private LocalDateTime dueAt;
    
    private String blockedReason;
    
    @Builder.Default
    private int attemptCount = 0;
    
    @Builder.Default
    private int maxAttempts = 3;
    
    private BigDecimal maxCost;
    
    @Column(columnDefinition = "TEXT")
    private String resultSummary;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String outputArtifactIds;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String relatedCommitShas;
    
    private LocalDateTime completedAt;
    
    @Builder.Default
    private float estimatedCost = 0.0f;
    
    @Builder.Default
    private float totalCost = 0.0f;
    
    @Builder.Default
    private int escalationCount = 0;
    
    @Builder.Default
    private boolean success = false;
    
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}