package com.autocompany.repository;
import com.autocompany.entity.Task;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {
    @EntityGraph(attributePaths = {"assignee", "project", "project.user"})
    List<Task> findByAssigneeId(UUID assigneeId);
    
    @EntityGraph(attributePaths = {"assignee", "project", "project.user"})
    List<Task> findByProjectId(UUID projectId);
    
    @EntityGraph(attributePaths = {"assignee", "project", "project.user"})
    Optional<Task> findById(UUID id);
}