package com.autocompany.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.*;
import com.autocompany.entity.enums.*;

@Entity
@Table(name = "peer_ratings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PeerRating {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;
    
    @Column(name = "rater_employee_id", nullable = false)
    private UUID raterEmployeeId;
    
    @Column(name = "rated_employee_id", nullable = false)
    private UUID ratedEmployeeId;
    
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category = Category.CODE_QUALITY;
    
    @Column(nullable = false)
    private float score; // 1.0 - 5.0
    
    @Column(columnDefinition = "TEXT")
    private String comment;
    
    // Feedback context management (§15.3)
    @Column(name = "feedback_summary", columnDefinition = "TEXT")
    private String feedbackSummary; // LLM-generated summary of key improvement points
    
    @Builder.Default
    @Column(name = "context_weight")
    private float contextWeight = 1.0f; // Time-decay weight for prompt injection
    
    @Column(name = "evidence_refs", columnDefinition = "jsonb")
    private List<String> evidenceRefs; // PR, commit, test, doc references
    
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "archived_at")
    private LocalDateTime archivedAt; // When compressed to summary
}