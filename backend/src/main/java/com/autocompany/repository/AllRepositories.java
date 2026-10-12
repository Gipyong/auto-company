package com.autocompany.repository;

import com.autocompany.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {}

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    List<Employee> findByRole(String role);
}

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {}

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByAssigneeId(UUID assigneeId);
    List<Task> findByProjectId(UUID projectId);
}

@Repository
public interface PeerRatingRepository extends JpaRepository<PeerRating, UUID> {}

@Repository
public interface DiscussionMessageRepository extends JpaRepository<DiscussionMessage, UUID> {}

@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {}