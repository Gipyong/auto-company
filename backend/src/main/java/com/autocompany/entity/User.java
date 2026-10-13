package com.autocompany.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.*;



@Entity
@Table(name = "users", uniqueConstraints = {@UniqueConstraint(columnNames = {"email"})})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(nullable = false)
    private String name;
    
    private String companyName;
    
    @Builder.Default
    private boolean isPremium = false;
    
    @Builder.Default
    private String defaultModel = "llama3:8b";
    
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}

