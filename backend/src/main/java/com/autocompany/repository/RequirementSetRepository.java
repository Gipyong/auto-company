package com.autocompany.repository;

import com.autocompany.entity.RequirementSet;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RequirementSetRepository extends JpaRepository<RequirementSet, UUID> {

    List<RequirementSet> findByProjectIdOrderByVersionDesc(UUID projectId);
}
