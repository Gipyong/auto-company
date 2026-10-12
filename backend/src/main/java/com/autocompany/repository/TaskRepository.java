package com.autocompany.repository;
import com.autocompany.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByAssigneeId(UUID assigneeId);
    List<Task> findByProjectId(UUID projectId);
}
