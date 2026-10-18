package com.autocompany.repository;

import com.autocompany.entity.ClarificationQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ClarificationQuestionRepository extends JpaRepository<ClarificationQuestion, UUID> {

    List<ClarificationQuestion> findBySessionIdOrderByDisplayOrderAsc(UUID sessionId);

    boolean existsBySessionId(UUID sessionId);

    long deleteBySessionId(UUID sessionId);
}
