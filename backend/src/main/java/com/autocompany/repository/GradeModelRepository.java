package com.autocompany.repository;

import com.autocompany.entity.GradeModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GradeModelRepository extends JpaRepository<GradeModel, UUID> {
    List<GradeModel> findByGradeIdOrderByPriority(UUID gradeId);
    Optional<GradeModel> findByGradeIdAndModelProfileId(UUID gradeId, UUID modelProfileId);
    List<GradeModel> findByGradeIdAndEnabledTrueOrderByPriority(UUID gradeId);
    void deleteByGradeId(UUID gradeId);
}