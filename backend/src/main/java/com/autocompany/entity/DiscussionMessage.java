package com.autocompany.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.*;



@Entity
@Table(name = "discussion_messages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DiscussionMessage {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    
    @Column(nullable = false)
    private UUID senderEmployeeId;
    
    private UUID targetEmployeeId;
    
    private String content;
    
    @Enumerated(EnumType.STRING)
    private MessageType messageType = MessageType.DISCUSSION;
    
    private LocalDateTime createdAt = LocalDateTime.now();
}

