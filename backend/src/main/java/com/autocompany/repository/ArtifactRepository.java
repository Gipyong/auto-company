package com.autocompany.repository;

import com.autocompany.entity.Artifact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ArtifactRepository extends JpaRepository<Artifact, UUID> {

    java.util.List<Artifact> findByProjectIdOrderByCreatedAtAsc(UUID projectId);
}
