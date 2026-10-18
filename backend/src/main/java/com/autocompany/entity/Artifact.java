package com.autocompany.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Artifact (§5.19) — REQ-004 MVP: 골 첨부 저장.
 * 근거 산출물의 원본을 보존한다(정적 저장만 — 실행 금지, §28.7-5).
 * MVP 저장: 파싱 결과 텍스트는 parsed_text 인라인, 원본은 storage_blob(bytea),
 * storage_uri는 artifact://{id} 논리 URI 기록.
 */
@Entity
@Table(name = "artifacts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Artifact {

    public enum ArtifactType { SOURCE_DIFF, TEST_REPORT, BUILD, LOG, DOCUMENT, IMAGE, OTHER }

    public enum ParseStatus { PARSED, UNSUPPORTED, IMAGE_PENDING_VISION }

    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // ORCH 실행 결과 산출물 연결(§5.19 task_id?) — 첨부 MVP에서는 null
    @Column(name = "task_id")
    private UUID taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_type", nullable = false)
    private ArtifactType artifactType;

    @Column(name = "storage_uri", nullable = false, length = 500)
    private String storageUri;

    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "media_type", nullable = false, length = 100)
    private String mediaType;

    // 첨부 파싱 결과(여기서만 기록됨) — parsed / unsupported / image_pending_vision (§28.7)
    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false)
    private ParseStatus parseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    @Column(name = "parse_evidence", columnDefinition = "jsonb")
    private List<String> parseEvidence = new ArrayList<>();

    @Column(name = "parsed_text", columnDefinition = "text")
    private String parsedText;

    @Column(name = "created_by_employee_id")
    private UUID createdByEmployeeId; // 직원 산출물 연결(§5.19) — 첨부 MVP에서는 null

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "retention_policy", length = 64)
    private String retentionPolicy;

    // 원본 보존 전략(§28.7-3) — 지원 불가·파싱 실패 시에도 원본을 남긴다(실행 대상 아님)
    @Column(name = "storage_blob")
    private byte[] storageBlob;
}
