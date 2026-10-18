package com.autocompany.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** §5.27 OrchestratorCycle (ORCH-001/002) — 동결 요구 분해·배정 사이클의 기록. */
@Entity
@Table(name = "orchestrator_cycles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrchestratorCycle {

    public enum CycleMode { FIRST, REPLAN }

    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "requirement_set_id", nullable = false)
    private UUID requirementSetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CycleMode mode;

    @Column(name = "created_task_count", nullable = false)
    private int createdTaskCount;

    @Column(name = "blocked_task_count", nullable = false)
    private int blockedTaskCount;

    @Column(name = "decisions_json", columnDefinition = "TEXT")
    private String decisionsJson;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
