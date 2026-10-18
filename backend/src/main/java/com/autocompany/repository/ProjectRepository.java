package com.autocompany.repository;
import com.autocompany.entity.Project;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    @EntityGraph(attributePaths = "user")
    List<Project> findByUserId(UUID userId);

    @EntityGraph(attributePaths = "user")
    List<Project> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
