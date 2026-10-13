package com.autocompany.repository;
import com.autocompany.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;
@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findByUserId(UUID userId);
}
