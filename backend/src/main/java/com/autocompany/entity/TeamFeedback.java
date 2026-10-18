package com.autocompany.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 팀 단위 피드백(§5.20.1 v1.9) — 팀장이 상대 팀에 제출, 대상은 팀 전체.
 * 개인 평가로 분해하지 않음, 팀 컨텍스트 주입용(구성원 AgentRun 참고·팀 요약 리포트).
 */
@Entity
@Table(name = "team_feedbacks", indexes = {
        @Index(name = "idx_team_feedbacks_subject", columnList = "subject_team_id"),
        @Index(name = "idx_team_feedbacks_author", columnList = "author_employee_id"),
        @Index(name = "idx_team_feedbacks_org", columnList = "organization_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TeamFeedback {

    public enum Category { CODE_QUALITY, LOGIC, COOPERATION, DOC_COMPLETENESS, PROCESS, OTHER }

    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "subject_team_id")
    private UUID subjectTeamId;

    @Column(name = "author_employee_id", nullable = false)
    private UUID authorEmployeeId;

    @Column(name = "author_team_id")
    private UUID authorTeamId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Category category;

    @Column(name = "score", precision = 2, scale = 1)
    private java.math.BigDecimal score;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    @Column(name = "evidence_refs", columnDefinition = "jsonb")
    private java.util.List<String> evidenceRefs = new java.util.ArrayList<>();

    @Column(name = "context_weight", precision = 3, scale = 2)
    @Builder.Default
    private java.math.BigDecimal contextWeight = java.math.BigDecimal.ONE;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}