package com.autocompany.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** §5.11 TaskDependency (TASK-001) — task_id가 depends_on_task_id에 의존한다. 순환은 서비스에서 거부. */
@Entity
@Table(name = "task_dependencies")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaskDependency {

    public enum DependencyType { BLOCKS, RELATES_TO }

    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "depends_on_task_id", nullable = false)
    private UUID dependsOnTaskId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "dependency_type", nullable = false, length = 20)
    private DependencyType dependencyType = DependencyType.BLOCKS;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
