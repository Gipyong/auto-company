package com.autocompany.repository;

import com.autocompany.entity.TaskTransition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.util.UUID;

@Repository
public interface TaskTransitionRepository extends JpaRepository<TaskTransition, UUID> {
    List<TaskTransition> findByTaskIdOrderByCreatedAtAsc(UUID taskId);
}