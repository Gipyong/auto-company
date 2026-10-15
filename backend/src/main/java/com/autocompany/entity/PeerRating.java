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
    
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}

