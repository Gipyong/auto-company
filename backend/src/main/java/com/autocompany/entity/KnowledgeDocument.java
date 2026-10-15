package com.autocompany.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.*;



@Entity
@Table(name = "knowledge_documents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class KnowledgeDocument {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    
    private UUID authorEmployeeId;
    
    @Column(columnDefinition = "TEXT")
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    private String sourceFilePath;
    
    @Column(name = "embedding_blob", columnDefinition = "TEXT")
    private String embedding; // pgvector용
    
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}