package com.autocompany.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "projects")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Project {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;

    // §5.26 외부 고객 연결 (null = 사용자 자신이 발주 고객 포지션); 세션 client_id가 상속
    @Column(name = "client_id")
    private UUID clientId;
    
    @Builder.Default
    private String status = "PLANNING"; // PLANNING, ACTIVE, TESTING, COMPLETED
    
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Builder.Default
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    @JsonManagedReference
    private List<Task> tasks = new ArrayList<>();
    
    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "project_employees", joinColumns = @JoinColumn(name = "project_id"))
    private List<UUID> employees = new ArrayList<>();
}
