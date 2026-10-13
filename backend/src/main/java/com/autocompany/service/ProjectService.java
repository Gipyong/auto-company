package com.autocompany.service;

import com.autocompany.entity.*;
import com.autocompany.entity.enums.*;
import com.autocompany.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final EmployeeRepository employeeRepository;
    private final UserService userService;

    @Transactional
    public Project createProject(UUID userId, String name, String description) {
        User user = userService.getOrCreateLocalUser(userId);
        Project project = Project.builder()
                .user(user)
                .name(name)
                .description(description)
                .status("PLANNING")
                .build();
        return projectRepository.save(project);
    }

    @Transactional(readOnly = true)
    public Optional<Project> getProject(UUID projectId) {
        return projectRepository.findById(projectId);
    }

    @Transactional(readOnly = true)
    public List<Project> getProjectsByUser(UUID userId) {
        return projectRepository.findByUserId(userId);
    }

    @Transactional
    public void addEmployeeToProject(UUID projectId, UUID employeeId) {
        Project project = projectRepository.findById(projectId).orElseThrow();
        project.getEmployees().add(employeeId);
        projectRepository.save(project);
    }

    @Transactional
    public void removeEmployeeFromProject(UUID projectId, UUID employeeId) {
        Project project = projectRepository.findById(projectId).orElseThrow();
        project.getEmployees().remove(employeeId);
        projectRepository.save(project);
    }
}
