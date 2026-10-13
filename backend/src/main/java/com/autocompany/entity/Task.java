package com.autocompany.entity;

import jakarta.persistence.*;
import lombok.*;
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
    @JoinColumn(name = "assignee_id", nullable = false)
    private Employee assignee;
    
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskType type = TaskType.CODING;
    
    private String title;
    @Column(columnDefinition = "TEXT")
    private String prompt;
    
    @Builder.Default
    private String status = "PENDING"; // PENDING, IN_PROGRESS, CODE_REVIEW, TESTING, COMPLETED, FAILED
    
    @Column(columnDefinition = "TEXT")
    private String aiOutput;
    
    @Column(columnDefinition = "TEXT")
    private String testResults;
    
    @Column(columnDefinition = "TEXT")
    private String dockerExecutionResult;
    
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    private LocalDateTime completedAt;
    
    @Builder.Default
    private float estimatedCost = 0.0f;
    
    @Builder.Default
    private float totalCost = 0.0f;
    
    @Builder.Default
    private int escalationCount = 0;
    
    @Builder.Default
    private boolean success = false;
}

