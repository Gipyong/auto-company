package com.autocompany.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.*;
import com.autocompany.entity.enums.*;

@Entity
@Table(name = "task_transitions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaskTransition {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false)
    private TaskStatus fromStatus;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private TaskStatus toStatus;
    
    @Column(name = "triggered_by_employee_id")
    private UUID triggeredByEmployeeId;
    
    @Column(name = "triggered_by_user_id")
    private UUID triggeredByUserId;
    
    @Column(columnDefinition = "TEXT")
    private String reason;
    
    @Column(columnDefinition = "TEXT")
    private String metadataJson;
    
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}