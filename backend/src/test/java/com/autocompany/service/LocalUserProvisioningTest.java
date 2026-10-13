package com.autocompany.service;

import com.autocompany.entity.Employee;
import com.autocompany.entity.Project;
import com.autocompany.repository.EmployeeRepository;
import com.autocompany.repository.ProjectRepository;
import com.autocompany.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class LocalUserProvisioningTest {

    @Autowired private EmployeeService employeeService;
    @Autowired private ProjectService projectService;
    @Autowired private UserRepository userRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private ProjectRepository projectRepository;

    @Test
    public void sameUserIdReturnsSameUser() {
        UUID userId = UUID.randomUUID();
        Employee e1 = employeeService.createEmployee(userId, "김개발", "DEVELOPER");
        Employee e2 = employeeService.createEmployee(userId, "이개발", "DEVELOPER");

        assertNotNull(e1.getId());
        assertNotNull(e2.getId());
        assertEquals(userId, e1.getUser().getId());
        assertEquals(userId, e2.getUser().getId());
        assertEquals(1, userRepository.count());
        assertEquals(2, employeeRepository.count());
    }

    @Test
    public void differentUserIdCreatesDifferentUsers() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        Employee e1 = employeeService.createEmployee(userId1, "김개발", "DEVELOPER");
        Employee e2 = employeeService.createEmployee(userId2, "이개발", "DEVELOPER");

        assertEquals(userId1, e1.getUser().getId());
        assertEquals(userId2, e2.getUser().getId());
        assertEquals(2, userRepository.count());
        assertEquals(2, employeeRepository.count());
    }

    @Test
    public void projectCreationUsesRequestedUserId() {
        UUID userId = UUID.randomUUID();
        employeeService.createEmployee(userId, "김개발", "DEVELOPER");

        Project project = projectService.createProject(userId, "테스트 프로젝트", "설명");

        assertNotNull(project.getId());
        assertEquals(userId, project.getUser().getId());
        assertEquals(1, userRepository.count());
        assertEquals(1, projectRepository.count());
    }
}