package com.autocompany.repository;

import com.autocompany.entity.OrchestratorCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrchestratorCycleRepository extends JpaRepository<OrchestratorCycle, UUID> {

    List<OrchestratorCycle> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    long countByProjectIdAndMode(UUID projectId, OrchestratorCycle.CycleMode mode);
}
