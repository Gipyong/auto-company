package com.autocompany.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.*;



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
    private Role role; // PLANNER, DEVELOPER, DESIGNER, PROJECT_MANAGER, TECHNICAL_WRITER, QA_ENGINEER
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rank rank = Rank.INTERN; // INTERN, JUNIOR, MID, SENIOR, LEAD
    
    @Column(nullable = false)
    private int level = 1;
    
    private String aiModel = "llama3:8b";
    
    private String dockerContainerId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.IDLE;
    
    private int totalTasks = 0;
    
    private int completedTasks = 0;
    
    private float avgRating = 0.0f;
    
    private String systemPrompt;
    
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @ElementCollection
    @CollectionTable(name = "employee_ratings", joinColumns = @JoinColumn(name = "employee_id"))
    @Column(name = "rating")
    private List<Float> ratings = new ArrayList<>();
    
    @ElementCollection
    @CollectionTable(name = "employee_feedback", joinColumns = @JoinColumn(name = "employee_id"))
    private List<String> feedbackComments = new ArrayList<>();
}

