package com.autocompany.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "requirement_sets", uniqueConstraints = @UniqueConstraint(name = "uk_requirement_set_project_version", columnNames = {"project_id", "version"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RequirementSet {

    public enum SetStatus { DRAFT, FROZEN, SUPERSEDED }

    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    // MVP: 조직 도메인이 사용자 기반(v1.6 §5.10)으로 남아 있어 nullable — Phase 4 조직 다계정화에서 필수화
    @Column(name = "organization_id")
    private UUID organizationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ClarificationSession session;

    @Column(nullable = false)
    private int version;

    // §5.25 content: functional/non_functional/constraints/out_of_scope/open_risks + 항목 source·근거
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String contentJson;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SetStatus status = SetStatus.FROZEN;

    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    @Column(name = "frozen_by_employee_ids", columnDefinition = "jsonb")
    private List<UUID> frozenByEmployeeIds = new ArrayList<>();

    // ORCH-001 분해 결과가 연결됨(§5.25)
    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    @Column(name = "derived_task_ids", columnDefinition = "jsonb")
    private List<UUID> derivedTaskIds = new ArrayList<>();

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "frozen_at")
    private LocalDateTime frozenAt;
}
