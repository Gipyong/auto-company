package com.autocompany.service;

import com.autocompany.entity.Employee;
import com.autocompany.entity.JobGrade;
import com.autocompany.entity.User;
import com.autocompany.entity.enums.Role;
import com.autocompany.repository.EmployeeRepository;
import com.autocompany.repository.JobGradeRepository;
import com.autocompany.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ORG-002 기본 회사 템플릿 import 검증 (§6.1, §6.2)
 */
@SpringBootTest
@Transactional
public class CompanyTemplateServiceTest {

    @Autowired private CompanyTemplateService companyTemplateService;
    @Autowired private JobGradeRepository jobGradeRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private UserRepository userRepository;

    @Test
    public void testImportCreatesFiveTemplateGrades() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        CompanyTemplateService.TemplateImportResult result =
                companyTemplateService.importDefaultCompanyTemplate(organizationId, userId);

        List<JobGrade> grades = jobGradeRepository.findByOrganizationIdOrderByOrdinal(organizationId);
        assertEquals(5, grades.size());
        assertEquals(List.of("L1", "L2", "L3", "L4", "L5"),
                grades.stream().map(JobGrade::getCode).toList());
        assertEquals(List.of(1, 2, 3, 4, 5),
                grades.stream().map(JobGrade::getOrdinal).toList());
        assertTrue(grades.stream().allMatch(JobGrade::isTemplateDefault));
        assertTrue(grades.stream().allMatch(g -> g.getOrganizationId().equals(organizationId)));
        assertTrue(grades.stream().allMatch(g -> g.getResponsibilities() != null && !g.getResponsibilities().isBlank()));
        assertTrue(grades.stream().allMatch(g -> g.getTaskTypes() != null && !g.getTaskTypes().isEmpty()));
        assertTrue(grades.stream().allMatch(g -> g.getModelSelectionMode() == JobGrade.ModelSelectionMode.PRIORITY));
        assertTrue(grades.stream().allMatch(g -> g.getStatus() == JobGrade.Status.ACTIVE));
        assertEquals(5, result.grades().size());
    }

    @Test
    public void testImportCreatesEightEmployeesAssignedToGrades() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        CompanyTemplateService.TemplateImportResult result =
                companyTemplateService.importDefaultCompanyTemplate(organizationId, userId);

        List<Employee> employees = employeeRepository.findByUserId(userId);
        assertEquals(8, employees.size());

        Map<Role, Long> roleCounts = employees.stream()
                .collect(Collectors.groupingBy(Employee::getRole, Collectors.counting()));
        assertEquals(1L, roleCounts.get(Role.PLANNER));
        assertEquals(1L, roleCounts.get(Role.ARCHITECT));
        assertEquals(2L, roleCounts.get(Role.DEVELOPER));
        assertEquals(1L, roleCounts.get(Role.QA_ENGINEER));
        assertEquals(1L, roleCounts.get(Role.SECURITY_DEVOPS));
        assertEquals(1L, roleCounts.get(Role.TECHNICAL_WRITER));
        assertEquals(1L, roleCounts.get(Role.DESIGNER));

        Map<String, UUID> gradeIdByCode = result.grades().stream()
                .collect(Collectors.toMap(JobGrade::getCode, JobGrade::getId));
        Map<String, Employee> employeeByName = employees.stream()
                .collect(Collectors.toMap(Employee::getName, Function.identity()));

        assertEquals(gradeIdByCode.get("L4"), employeeByName.get("프로젝트 리드/분석가").getGradeId());
        assertEquals(gradeIdByCode.get("L5"), employeeByName.get("아키텍트").getGradeId());
        assertEquals(gradeIdByCode.get("L2"), employeeByName.get("개발자 (L2)").getGradeId());
        assertEquals(gradeIdByCode.get("L3"), employeeByName.get("개발자 (L3)").getGradeId());
        assertEquals(gradeIdByCode.get("L3"), employeeByName.get("QA 엔지니어").getGradeId());
        assertEquals(gradeIdByCode.get("L3"), employeeByName.get("보안/DevOps").getGradeId());
        assertEquals(gradeIdByCode.get("L2"), employeeByName.get("기술 문서 담당자").getGradeId());
        assertEquals(gradeIdByCode.get("L2"), employeeByName.get("디자이너/UX").getGradeId());

        assertTrue(employees.stream().allMatch(e -> e.getUser().getId().equals(userId)));
        assertTrue(employees.stream().allMatch(e -> e.getSystemPrompt() != null && !e.getSystemPrompt().isBlank()));
    }

    @Test
    public void testImportRejectsWhenOrganizationAlreadyHasGrades() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        JobGrade existing = new JobGrade();
        existing.setOrganizationId(organizationId);
        existing.setCode("L1");
        existing.setName("Junior");
        existing.setOrdinal(1);
        existing.setModelSelectionMode(JobGrade.ModelSelectionMode.PRIORITY);
        existing.setStatus(JobGrade.Status.ACTIVE);
        jobGradeRepository.save(existing);

        assertThrows(TemplateAlreadyImportedException.class, () ->
                companyTemplateService.importDefaultCompanyTemplate(organizationId, userId));

        assertEquals(1, jobGradeRepository.findByOrganizationIdOrderByOrdinal(organizationId).size());
        assertEquals(0, employeeRepository.findByUserId(userId).size());
    }

    @Test
    public void testImportRejectsWhenUserAlreadyHasEmployees() {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        User user = userRepository.save(User.builder()
                .id(userId)
                .email(userId + "@autocompany.local")
                .name("Local CEO")
                .build());
        employeeRepository.save(Employee.builder()
                .user(user)
                .name("기존 직원")
                .role(Role.DEVELOPER)
                .build());

        assertThrows(TemplateAlreadyImportedException.class, () ->
                companyTemplateService.importDefaultCompanyTemplate(organizationId, userId));

        assertEquals(0, jobGradeRepository.findByOrganizationIdOrderByOrdinal(organizationId).size());
    }

    @Test
    public void testImportRequiresOrganizationAndUserIds() {
        assertThrows(IllegalArgumentException.class, () ->
                companyTemplateService.importDefaultCompanyTemplate(null, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () ->
                companyTemplateService.importDefaultCompanyTemplate(UUID.randomUUID(), null));
    }
}
