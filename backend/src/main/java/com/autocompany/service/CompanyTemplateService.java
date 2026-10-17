package com.autocompany.service;

import com.autocompany.entity.Employee;
import com.autocompany.entity.JobGrade;
import com.autocompany.entity.User;
import com.autocompany.entity.enums.Role;
import com.autocompany.entity.enums.Status;
import com.autocompany.repository.EmployeeRepository;
import com.autocompany.repository.JobGradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 기본 회사 템플릿 import (ORG-002, 설계 §6.1, §6.2, §17)
 *
 * 조직에 5단계 직급(L1~L5)과 기본 8인 조직 직원을 한 트랜잭션으로 생성한다.
 * 이미 직급이 있거나 해당 사용자에게 이미 직원이 있으면 거부한다(409, 중복/혼합 방지).
 */
@Service
@RequiredArgsConstructor
public class CompanyTemplateService {

    private final JobGradeRepository jobGradeRepository;
    private final EmployeeRepository employeeRepository;
    private final UserService userService;

    private record GradeSpec(String code, String name, int ordinal, String responsibilities, List<String> taskTypes) {}

    private record EmployeeSpec(String name, Role role, String gradeCode, String systemPrompt) {}

    public record TemplateImportResult(UUID organizationId, List<JobGrade> grades, List<Employee> employees) {}

    private static final List<GradeSpec> DEFAULT_GRADES = List.of(
            new GradeSpec("L1", "Junior", 1,
                    "범위가 명확한 작업, 제한된 쓰기 권한, 승인 없는 merge/deploy 불가",
                    List.of("TASK")),
            new GradeSpec("L2", "Mid", 2,
                    "독립적인 기능 구현, 단위 테스트, 일반 PR 리뷰 가능",
                    List.of("TASK", "BUG", "DOCUMENTATION")),
            new GradeSpec("L3", "Senior", 3,
                    "복잡한 구현, 코드 리뷰, 작업 분해 지원. 본인 작성 PR은 승인 불가",
                    List.of("TASK", "BUG", "TEST", "REVIEW", "DOCUMENTATION")),
            new GradeSpec("L4", "Lead", 4,
                    "작업 우선순위 및 모듈 수준 결정 제안, 여러 직원 조정",
                    List.of("EPIC", "STORY", "REVIEW", "SPIKE")),
            new GradeSpec("L5", "Architect", 5,
                    "아키텍처 및 고위험 설계 검토. 운영 배포 권한은 직급과 별도로 부여",
                    List.of("SPIKE", "REVIEW", "EPIC")));

    private static final List<EmployeeSpec> DEFAULT_EMPLOYEES = List.of(
            new EmployeeSpec("프로젝트 리드/분석가", Role.PLANNER, "L4", "요구사항 분석, 백로그, 진행 조정"),
            new EmployeeSpec("아키텍트", Role.ARCHITECT, "L5", "시스템 설계, ADR, 고위험 변경 검토"),
            new EmployeeSpec("개발자 (L2)", Role.DEVELOPER, "L2", "기능 구현, 테스트, PR"),
            new EmployeeSpec("개발자 (L3)", Role.DEVELOPER, "L3", "기능 구현, 테스트, PR"),
            new EmployeeSpec("QA 엔지니어", Role.QA_ENGINEER, "L3", "독립 테스트, 인수 기준 검증"),
            new EmployeeSpec("보안/DevOps", Role.SECURITY_DEVOPS, "L3", "보안 검사, CI, 실행환경 및 배포 준비"),
            new EmployeeSpec("기술 문서 담당자", Role.TECHNICAL_WRITER, "L2", "문서 갱신, 지식 수집, 최신성 검사"),
            new EmployeeSpec("디자이너/UX", Role.DESIGNER, "L2", "사용자 흐름, UI 명세, 접근성 검토"));

    @Transactional
    public TemplateImportResult importDefaultCompanyTemplate(UUID organizationId, UUID userId) {
        if (organizationId == null || userId == null) {
            throw new IllegalArgumentException("organizationId and userId are required");
        }
        if (!jobGradeRepository.findByOrganizationIdOrderByOrdinal(organizationId).isEmpty()) {
            throw new TemplateAlreadyImportedException("Organization already has job grades: " + organizationId);
        }
        if (!employeeRepository.findByUserId(userId).isEmpty()) {
            throw new TemplateAlreadyImportedException("User already has employees: " + userId);
        }

        List<JobGrade> grades = new ArrayList<>();
        for (GradeSpec spec : DEFAULT_GRADES) {
            grades.add(jobGradeRepository.save(newGrade(organizationId, spec)));
        }

        User user = userService.getOrCreateLocalUser(userId);
        List<Employee> employees = new ArrayList<>();
        for (EmployeeSpec spec : DEFAULT_EMPLOYEES) {
            UUID gradeId = grades.stream()
                    .filter(g -> g.getCode().equals(spec.gradeCode()))
                    .map(JobGrade::getId)
                    .findFirst()
                    .orElseThrow();
            employees.add(employeeRepository.save(newEmployee(user, spec, gradeId)));
        }

        return new TemplateImportResult(organizationId, grades, employees);
    }

    private JobGrade newGrade(UUID organizationId, GradeSpec spec) {
        JobGrade grade = new JobGrade();
        grade.setOrganizationId(organizationId);
        grade.setCode(spec.code());
        grade.setName(spec.name());
        grade.setOrdinal(spec.ordinal());
        grade.setResponsibilities(spec.responsibilities());
        grade.setTaskTypes(spec.taskTypes());
        grade.setModelSelectionMode(JobGrade.ModelSelectionMode.PRIORITY);
        grade.setTemplateDefault(true);
        grade.setStatus(JobGrade.Status.ACTIVE);
        return grade;
    }

    private Employee newEmployee(User user, EmployeeSpec spec, UUID gradeId) {
        Employee employee = new Employee();
        employee.setUser(user);
        employee.setName(spec.name());
        employee.setRole(spec.role());
        employee.setGradeId(gradeId);
        employee.setStatus(Status.IDLE);
        employee.setSystemPrompt(spec.systemPrompt());
        return employee;
    }
}
