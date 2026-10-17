package com.autocompany.repository;

import com.autocompany.entity.ModelProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ModelProfileRepository extends JpaRepository<ModelProfile, UUID> {
    List<ModelProfile> findByProviderId(UUID providerId);
    List<ModelProfile> findByEnabledTrue();
    Optional<ModelProfile> findByProviderIdAndName(UUID providerId, String name);
    List<ModelProfile> findByProviderOrganizationId(UUID organizationId);
}