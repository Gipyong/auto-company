package com.autocompany.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "clarification_sessions", uniqueConstraints = @UniqueConstraint(name = "uk_clarification_session_project_number", columnNames = {"project_id", "session_number"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClarificationSession {

    public enum SessionStatus { OPEN, WAITING_CLIENT, REVIEW, FROZEN, CANCELLED }

    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // §5.23 불변 감사용 원문 — 초기 골 또는 세션 개설 취지/변경 요청 원문
    @Column(name = "goal_text", columnDefinition = "TEXT", nullable = false, updatable = false)
    private String goalText;

    @Column(name = "session_number", nullable = false)
    private int sessionNumber;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status = SessionStatus.OPEN;

    // 파이프라인 개설 or 사용자 개설(§28.6) — 둘 중 하나
    @Column(name = "opened_by_employee_id")
    private UUID openedByEmployeeId;
    @Column(name = "opened_by_user_id")
    private UUID openedByUserId;

    // §5.26 외부 고객 (null = 사용자 자신이 발주 고객 포지션); Project.client_id 상속(REQ-004에서 연결)
    @Column(name = "client_id")
    private UUID clientId;

    // §28.7 첨부 참조 목록 (REQ-004에서 데이터 구성)
    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    @Column(name = "attachments", columnDefinition = "jsonb")
    private String attachments = "[]";

    @Column(name = "closed_at")
    private LocalDateTime closedAt;
    @Column(name = "frozen_at")
    private LocalDateTime frozenAt;

    @Builder.Default
    @Column(name = "client_notice_read", nullable = false)
    private boolean clientNoticeRead = false;

    // §28.2.4 상시 열린 자유 입력란(전문 요구)
    @Column(name = "client_extra_note", columnDefinition = "TEXT")
    private String clientExtraNote;

    @Builder.Default
    @Column(name = "max_questions", nullable = false)
    private int maxQuestions = 5;

    @Column(name = "parent_session_id")
    private UUID parentSessionId;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
