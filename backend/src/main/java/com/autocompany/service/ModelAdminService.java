package com.autocompany.service;

import com.autocompany.entity.ModelProfile;
import com.autocompany.entity.ModelProvider;
import com.autocompany.repository.ModelProfileRepository;
import com.autocompany.repository.ModelProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

/**
 * Model Provider/Profile 관리 (설계 §5.7, §5.8, §17 Models)
 *
 * - provider 생성/조회: 조직 단위, name은 조직 내 유일
 * - profile 생성/조회: provider 단위, name은 provider 내 유일
 * - API 키는 secretRef(참조)만 저장하며 평문을 다루지 않음
 */
@Service
@RequiredArgsConstructor
public class ModelAdminService {

    private final ModelProviderRepository modelProviderRepository;
    private final ModelProfileRepository modelProfileRepository;

    @Transactional
    public ModelProvider createProvider(ModelProvider provider) {
        if (provider.getOrganizationId() == null) {
            throw new IllegalArgumentException("organizationId is required");
        }
        if (provider.getName() == null || provider.getName().isBlank()) {
            throw new IllegalArgumentException("Provider name is required");
        }
        if (provider.getProviderType() == null) {
            throw new IllegalArgumentException("Provider type is required");
        }
        modelProviderRepository.findByOrganizationIdAndName(provider.getOrganizationId(), provider.getName())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Provider with this name already exists: " + provider.getName());
                });

        // health check는 ModelAdapter 계층 구현 전까지 UNKNOWN으로 시작 (설계 §17 Models)
        provider.setHealthStatus(ModelProvider.HealthStatus.UNKNOWN);
        return modelProviderRepository.save(provider);
    }

    @Transactional(readOnly = true)
    public List<ModelProvider> getProvidersByOrganization(UUID organizationId) {
        return modelProviderRepository.findByOrganizationId(organizationId);
    }

    @Transactional
    public ModelProfile createProfile(UUID providerId, ModelProfile profile) {
        ModelProvider provider = modelProviderRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerId));
        if (profile.getName() == null || profile.getName().isBlank()) {
            throw new IllegalArgumentException("Model profile name is required");
        }
        if (profile.getProviderModelId() == null || profile.getProviderModelId().isBlank()) {
            throw new IllegalArgumentException("providerModelId is required");
        }
        modelProfileRepository.findByProviderIdAndName(providerId, profile.getName())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Model profile with this name already exists: " + profile.getName());
                });

        profile.setProvider(provider);
        return modelProfileRepository.save(profile);
    }

    @Transactional(readOnly = true)
    public List<ModelProfile> getProfilesByOrganization(UUID organizationId) {
        return modelProfileRepository.findByProviderOrganizationId(organizationId);
    }
}
