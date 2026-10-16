package com.autocompany.repository;

import com.autocompany.entity.JobGrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobGradeRepository extends JpaRepository<JobGrade, UUID> {
    List<JobGrade> findByOrganizationIdOrderByOrdinal(UUID organizationId);
    Optional<JobGrade> findByOrganizationIdAndCode(UUID organizationId, String code);
    List<JobGrade> findByOrganizationIdAndStatus(UUID organizationId, JobGrade.Status status);
    boolean existsByOrganizationIdAndOrdinal(UUID organizationId, int ordinal);
}