package com.autocompany.repository;

import com.autocompany.entity.ModelProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ModelProviderRepository extends JpaRepository<ModelProvider, UUID> {
    List<ModelProvider> findByOrganizationId(UUID organizationId);
    List<ModelProvider> findByOrganizationIdAndEnabledTrue(UUID organizationId);
    Optional<ModelProvider> findByOrganizationIdAndName(UUID organizationId, String name);
}