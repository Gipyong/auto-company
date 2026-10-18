package com.autocompany.repository;

import com.autocompany.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {
    List<Client> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
}
