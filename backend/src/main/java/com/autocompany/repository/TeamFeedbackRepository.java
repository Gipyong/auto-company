package com.autocompany.repository;

import com.autocompany.entity.TeamFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TeamFeedbackRepository extends JpaRepository<TeamFeedback, UUID> {

    List<TeamFeedback> findBySubjectTeamIdOrderByCreatedAtDesc(UUID subjectTeamId);

    List<TeamFeedback> findByAuthorEmployeeIdOrderByCreatedAtDesc(UUID authorEmployeeId);

    List<TeamFeedback> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
}