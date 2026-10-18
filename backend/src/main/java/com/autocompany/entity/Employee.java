package com.autocompany.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.*;
import com.autocompany.entity.enums.*;

@Entity
@Table(name = "employees")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Employee {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false)
    private String name;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;
    
    @Column(name = "grade_id")
    private UUID gradeId;
    
    private String dockerContainerId;
    
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.IDLE;
    
    @Builder.Default
    private int totalTasks = 0;
    
    @Builder.Default
    private int completedTasks = 0;
    
    @Builder.Default
    private float avgRating = 0.0f;
    
    private String systemPrompt;

    // §5.4 EMP-001 — 사용자가 작성한 개별 지시(자유 서식, 최대 4000자, 안전 정책 우선 §8.2)
    @Column(name = "custom_instructions", columnDefinition = "TEXT")
    private String customInstructions;
    
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "employee_ratings", joinColumns = @JoinColumn(name = "employee_id"))
    @Column(name = "rating")
    private List<Float> ratings = new ArrayList<>();
    
    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "employee_feedback", joinColumns = @JoinColumn(name = "employee_id"))
    private List<String> feedbackComments = new ArrayList<>();
    
    // Lombok generates getters, but explicit for compile safety
    public float getAvgRating() { return avgRating; }
    public int getCompletedTasks() { return completedTasks; }
    public int getTotalTasks() { return totalTasks; }
}