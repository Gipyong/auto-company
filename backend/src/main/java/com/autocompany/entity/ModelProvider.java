package com.autocompany.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "model_providers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ModelProvider {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 30)
    private ProviderType providerType;

    @Column(name = "base_url", length = 500)
    private String baseUrl;

    @Column(name = "secret_ref", length = 200)
    private String secretRef;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", nullable = false, length = 20)
    private HealthStatus healthStatus = HealthStatus.UNKNOWN;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rate_limit_policy", columnDefinition = "jsonb")
    private Map<String, Object> rateLimitPolicy;

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

    public enum ProviderType {
        OPENAI_COMPATIBLE,
        ANTHROPIC,
        OLLAMA,
        CUSTOM
    }

    public enum HealthStatus {
        UNKNOWN,
        HEALTHY,
        DEGRADED,
        UNAVAILABLE
    }
}