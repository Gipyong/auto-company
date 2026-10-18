package com.autocompany.repository;

import com.autocompany.entity.ClarificationSession;
import com.autocompany.entity.ClarificationSession.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClarificationSessionRepository extends JpaRepository<ClarificationSession, UUID> {

    Optional<ClarificationSession> findFirstByProjectIdOrderBySessionNumberDesc(UUID projectId);

    List<ClarificationSession> findByProjectIdOrderBySessionNumberDesc(UUID projectId);

    boolean existsByProjectIdAndStatusIn(UUID projectId, List<SessionStatus> statuses);

    List<ClarificationSession> findByProjectIdInAndStatusInOrderBySessionNumberDesc(List<UUID> projectIds, List<SessionStatus> statuses);
}
