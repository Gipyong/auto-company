package com.autocompany.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "model_profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ModelProfile {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private ModelProvider provider;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "provider_model_id", nullable = false, length = 100)
    private String providerModelId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "capabilities", columnDefinition = "jsonb")
    private List<String> capabilities;

    @Column(name = "quality_tier")
    private Integer qualityTier;

    @Column(name = "input_cost_per_million", precision = 19, scale = 6)
    private BigDecimal inputCostPerMillion;

    @Column(name = "output_cost_per_million", precision = 19, scale = 6)
    private BigDecimal outputCostPerMillion;

    @Column(name = "context_window")
    private Integer contextWindow;

    @Column(name = "max_output_tokens")
    private Integer maxOutputTokens;

    @Builder.Default
    @Column(name = "supports_streaming", nullable = false)
    private boolean supportsStreaming = false;

    @Builder.Default
    @Column(name = "supports_tool_calling", nullable = false)
    private boolean supportsToolCalling = false;

    @Builder.Default
    @Column(name = "supports_json_schema", nullable = false)
    private boolean supportsJsonSchema = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "latency_class", length = 20)
    private LatencyClass latencyClass = LatencyClass.UNKNOWN;

    @Builder.Default
    @Column(name = "local_only", nullable = false)
    private boolean localOnly = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private java.util.Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum LatencyClass {
        LOW, MEDIUM, HIGH, UNKNOWN
    }
}