package com.autocompany.service;

import com.autocompany.entity.Employee;
import com.autocompany.entity.JobGrade;
import com.autocompany.repository.EmployeeRepository;
import com.autocompany.repository.JobGradeRepository;
import com.autocompany.repository.GradeModelRepository;
import com.autocompany.entity.GradeModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobGradeService {
    private final JobGradeRepository jobGradeRepository;
    private final GradeModelRepository gradeModelRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional
    public JobGrade createGrade(JobGrade grade) {
        if (jobGradeRepository.existsByOrganizationIdAndOrdinal(grade.getOrganizationId(), grade.getOrdinal())) {
            throw new IllegalArgumentException("Grade with this ordinal already exists: " + grade.getOrdinal());
        }
        return jobGradeRepository.save(grade);
    }

    @Transactional(readOnly = true)
    public List<JobGrade> getGradesByOrganization(UUID organizationId) {
        return jobGradeRepository.findByOrganizationIdOrderByOrdinal(organizationId);
    }

    @Transactional(readOnly = true)
    public Optional<JobGrade> getGrade(UUID gradeId) {
        return jobGradeRepository.findById(gradeId);
    }

    @Transactional(readOnly = true)
    public Optional<JobGrade> getGradeByCode(UUID organizationId, String code) {
        return jobGradeRepository.findByOrganizationIdAndCode(organizationId, code);
    }

    @Transactional
    public JobGrade updateGrade(UUID gradeId, JobGrade updated) {
        JobGrade existing = jobGradeRepository.findById(gradeId)
                .orElseThrow(() -> new IllegalArgumentException("Grade not found: " + gradeId));
        
        if (!existing.getOrdinal().equals(updated.getOrdinal()) 
                && jobGradeRepository.existsByOrganizationIdAndOrdinal(existing.getOrganizationId(), updated.getOrdinal())) {
            throw new IllegalArgumentException("Grade with this ordinal already exists: " + updated.getOrdinal());
        }
        
        existing.setCode(updated.getCode());
        existing.setName(updated.getName());
        existing.setOrdinal(updated.getOrdinal());
        existing.setResponsibilities(updated.getResponsibilities());
        existing.setTaskTypes(updated.getTaskTypes());
        existing.setModelSelectionMode(updated.getModelSelectionMode());
        existing.setStatus(updated.getStatus());
        return jobGradeRepository.save(existing);
    }

    @Transactional
    public void deleteGrade(UUID gradeId) {
        JobGrade grade = jobGradeRepository.findById(gradeId)
                .orElseThrow(() -> new IllegalArgumentException("Grade not found: " + gradeId));
        
        // Check if any employees reference this grade
        if (!employeeRepository.findByGradeId(gradeId).isEmpty()) {
            throw new IllegalStateException("Cannot delete grade with assigned employees. Reassign employees first.");
        }
        
        // Check if any grade models reference this grade
        if (!gradeModelRepository.findByGradeIdOrderByPriority(gradeId).isEmpty()) {
            throw new IllegalStateException("Cannot delete grade with assigned models. Remove grade models first.");
        }
        
        jobGradeRepository.delete(grade);
    }

    @Transactional
    public void setGradeModels(UUID gradeId, List<GradeModel> gradeModels) {
        JobGrade grade = jobGradeRepository.findById(gradeId)
                .orElseThrow(() -> new IllegalArgumentException("Grade not found: " + gradeId));
        
        // Remove existing grade models
        gradeModelRepository.deleteByGradeId(gradeId);
        
        // Add new grade models
        for (int i = 0; i < gradeModels.size(); i++) {
            GradeModel gm = gradeModels.get(i);
            gm.setGradeId(gradeId);
            gm.setPriority(i + 1);
            gradeModelRepository.save(gm);
        }
    }

    @Transactional(readOnly = true)
    public List<GradeModel> getGradeModels(UUID gradeId) {
        return gradeModelRepository.findByGradeIdAndEnabledTrueOrderByPriority(gradeId);
    }
}