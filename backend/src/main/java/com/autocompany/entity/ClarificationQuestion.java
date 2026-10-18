package com.autocompany.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "clarification_questions", uniqueConstraints = @UniqueConstraint(name = "uk_clarification_question_order", columnNames = {"session_id", "display_order"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClarificationQuestion {

    public enum QuestionType { SINGLE_CHOICE, MULTI_CHOICE, SHORT_TEXT, FREE_TEXT, NUMBER_RANGE }

    public enum AnswerStatus { UNANSWERED, ANSWERED, SKIPPED, EXPIRED }

    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ClarificationSession session;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType questionType;

    // §28.2 산업 용어만 — 내부 기술용어 금지
    @Column(columnDefinition = "TEXT", nullable = false)
    private String prompt;

    // §28.3: [{value,label,description?}] — 선택형 필수
    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    @Column(columnDefinition = "jsonb")
    private List<QuestionOption> options = new ArrayList<>();

    @Builder.Default
    @Column(name = "allow_other", nullable = false)
    private boolean allowOther = true; // §28.3 기타(Other) 항상 제공

    @Column(name = "other_text", columnDefinition = "TEXT")
    private String otherText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_values", columnDefinition = "jsonb")
    private List<String> selectedValues;

    @Column(name = "numeric_answer")
    private BigDecimal numericAnswer;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "answer_status", nullable = false)
    private AnswerStatus answerStatus = AnswerStatus.UNANSWERED;

    @Column(name = "created_by_employee_id", nullable = false)
    private UUID createdByEmployeeId;

    // §5.24 검토 체인 이력 (REQ-003에서 채움)
    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    @Column(name = "reviewed_by_employee_ids", columnDefinition = "jsonb")
    private List<UUID> reviewedByEmployeeIds = new ArrayList<>();

    @Column(name = "assumption_if_skipped", columnDefinition = "TEXT")
    private String assumptionIfSkipped;

    public record QuestionOption(String value, String label, String description) {
    }
}
