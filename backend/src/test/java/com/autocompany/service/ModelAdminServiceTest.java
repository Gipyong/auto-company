package com.autocompany.service;

import com.autocompany.entity.*;
import com.autocompany.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class ModelAdminServiceTest {

    @Autowired
    private ModelAdminService modelAdminService;
    @Autowired
    private ModelProviderRepository modelProviderRepository;
    @Autowired
    private ModelProfileRepository modelProfileRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private UUID organizationId;
    private UUID otherOrganizationId;

    @BeforeEach
    public void setUp() {
        organizationId = UUID.randomUUID();
        otherOrganizationId = UUID.randomUUID();
    }

    @Test
    public void testCreateProvider() {
        ModelProvider provider = newProvider("openai", organizationId);

        ModelProvider created = modelAdminService.createProvider(provider);

        assertNotNull(created.getId());
        assertEquals("openai", created.getName());
        assertEquals(ModelProvider.HealthStatus.UNKNOWN, created.getHealthStatus(),
                "생성 직후 healthStatus는 UNKNOWN이어야 함");
        assertTrue(created.isEnabled());
    }

    @Test
    public void testCreateProviderDuplicateNameInSameOrgFails() {
        modelAdminService.createProvider(newProvider("openai", organizationId));

        assertThrows(IllegalArgumentException.class,
                () -> modelAdminService.createProvider(newProvider("openai", organizationId)));
    }

    @Test
    public void testCreateProviderSameNameInDifferentOrgOk() {
        modelAdminService.createProvider(newProvider("openai", organizationId));
        ModelProvider created = modelAdminService.createProvider(newProvider("openai", otherOrganizationId));

        assertNotNull(created.getId());
    }

    @Test
    public void testCreateProviderRequiresNameAndType() {
        ModelProvider noName = newProvider(null, organizationId);
        assertThrows(IllegalArgumentException.class, () -> modelAdminService.createProvider(noName));

        ModelProvider noType = newProvider("openai", organizationId);
        noType.setProviderType(null);
        assertThrows(IllegalArgumentException.class, () -> modelAdminService.createProvider(noType));
    }

    @Test
    public void testGetProvidersByOrganization() {
        modelAdminService.createProvider(newProvider("openai", organizationId));
        modelAdminService.createProvider(newProvider("ollama", organizationId));
        modelAdminService.createProvider(newProvider("anthropic", otherOrganizationId));

        List<ModelProvider> providers = modelAdminService.getProvidersByOrganization(organizationId);

        assertEquals(2, providers.size());
        assertTrue(providers.stream().allMatch(p -> p.getOrganizationId().equals(organizationId)));
    }

    @Test
    public void testCreateProfile() {
        ModelProvider provider = modelAdminService.createProvider(newProvider("openai", organizationId));

        ModelProfile profile = newProfile("gpt-4o-mini", new BigDecimal("0.15"), new BigDecimal("0.60"));
        ModelProfile created = modelAdminService.createProfile(provider.getId(), profile);

        assertNotNull(created.getId());
        assertEquals(provider.getId(), created.getProvider().getId());
        assertEquals("gpt-4o-mini", created.getProviderModelId());
        assertEquals(new BigDecimal("0.15"), created.getInputCostPerMillion());
    }

    @Test
    public void testCreateProfileUnknownProviderFails() {
        ModelProfile profile = newProfile("gpt-4o-mini", null, null);

        assertThrows(IllegalArgumentException.class,
                () -> modelAdminService.createProfile(UUID.randomUUID(), profile));
    }

    @Test
    public void testCreateProfileDuplicateNameInSameProviderFails() {
        ModelProvider provider = modelAdminService.createProvider(newProvider("openai", organizationId));
        modelAdminService.createProfile(provider.getId(), newProfile("gpt-4o-mini", null, null));

        assertThrows(IllegalArgumentException.class,
                () -> modelAdminService.createProfile(provider.getId(), newProfile("gpt-4o-mini", null, null)));
    }

    @Test
    public void testCreateProfileRequiresNameAndModelId() {
        ModelProvider provider = modelAdminService.createProvider(newProvider("openai", organizationId));

        ModelProfile noName = newProfile(null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> modelAdminService.createProfile(provider.getId(), noName));

        ModelProfile noModelId = newProfile("gpt-4o-mini", null, null);
        noModelId.setProviderModelId(null);
        assertThrows(IllegalArgumentException.class,
                () -> modelAdminService.createProfile(provider.getId(), noModelId));
    }

    @Test
    public void testGetProfilesByOrganization() {
        ModelProvider provider = modelAdminService.createProvider(newProvider("openai", organizationId));
        ModelProvider otherProvider = modelAdminService.createProvider(newProvider("anthropic", otherOrganizationId));

        modelAdminService.createProfile(provider.getId(), newProfile("gpt-4o-mini", null, null));
        modelAdminService.createProfile(provider.getId(), newProfile("gpt-4o", null, null));
        modelAdminService.createProfile(otherProvider.getId(), newProfile("claude-3", null, null));

        List<ModelProfile> profiles = modelAdminService.getProfilesByOrganization(organizationId);

        assertEquals(2, profiles.size());
        assertTrue(profiles.stream().allMatch(p -> p.getProvider().getOrganizationId().equals(organizationId)));
    }

    @Test
    public void testProfilesAreSerializableWithLazyProviderProxy() throws Exception {
        // GET /model-profiles 응답은 provider(LAZY)를 포함해 직렬화되므로
        // Hibernate 프록시 직렬화가 실패하지 않아야 한다
        ModelProvider provider = modelAdminService.createProvider(newProvider("openai", organizationId));
        modelAdminService.createProfile(provider.getId(), newProfile("gpt-4o-mini", null, null));

        // 영속성 컨텍스트를 비워 provider가 실제로 LAZY 프록시로 로딩되게 한다
        entityManager.flush();
        entityManager.clear();

        List<ModelProfile> profiles = modelAdminService.getProfilesByOrganization(organizationId);
        String json = objectMapper.writeValueAsString(profiles);

        assertTrue(json.contains("\"name\":\"gpt-4o-mini\""));
        assertTrue(json.contains("\"provider\""));
        assertTrue(json.contains("\"name\":\"openai\""));
    }

    // ===== Helpers =====

    private ModelProvider newProvider(String name, UUID orgId) {
        ModelProvider provider = new ModelProvider();
        provider.setOrganizationId(orgId);
        provider.setName(name);
        provider.setProviderType(ModelProvider.ProviderType.OPENAI_COMPATIBLE);
        provider.setBaseUrl("http://localhost:8000");
        provider.setEnabled(true);
        return provider;
    }

    private ModelProfile newProfile(String name, BigDecimal inputCost, BigDecimal outputCost) {
        ModelProfile profile = new ModelProfile();
        profile.setName(name);
        profile.setProviderModelId(name);
        profile.setQualityTier(2);
        profile.setInputCostPerMillion(inputCost);
        profile.setOutputCostPerMillion(outputCost);
        profile.setEnabled(true);
        return profile;
    }
}
