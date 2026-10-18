package com.autocompany.repository;

import com.autocompany.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TeamRepository extends JpaRepository<Team, UUID> {

    List<Team> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    boolean existsByOrganizationIdAndNameIgnoreCase(UUID organizationId, String name);
}
