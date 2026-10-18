package com.autocompany.service;

import com.autocompany.entity.Employee;
import com.autocompany.entity.JobGrade;
import com.autocompany.entity.User;
import com.autocompany.entity.enums.Role;
import com.autocompany.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EmployeeService {
    private final EmployeeRepository employeeRepository;
    private final UserService userService;
    private final JobGradeService jobGradeService;
    private final TeamService teamService;

    /** §26 설정 custom_instructions_max_chars — 초과 시 400 */
    static final int MAX_CUSTOM_INSTRUCTIONS_CHARS = 4000;

    public Employee createEmployee(UUID userId, String name, String role) {
        return createEmployee(userId, name, role, null, null);
    }

    /** §5.4 EMP-001 — 생성 시 custom_instructions 부여 가능(자유 서식); §5.3 teamId는 active 팀만(선택) */
    @Transactional
    public Employee createEmployee(UUID userId, String name, String role, String customInstructions, UUID teamId) {
        User user = userService.getOrCreateLocalUser(userId);
        if (teamId != null) {
            teamService.requireActiveForAssignment(teamId);
        }
        Employee e = new Employee();
        e.setId(UUID.randomUUID());
        e.setUser(user);
        e.setName(name);
        e.setRole(Role.valueOf(role.toUpperCase()));
        e.setCustomInstructions(normalizeInstructions(customInstructions));
        e.setTeamId(teamId);
        return employeeRepository.save(e);
    }

    public Optional<Employee> getEmployee(UUID id) { return employeeRepository.findById(id); }

    public List<Employee> getEmployeesByUserId(UUID userId) { return employeeRepository.findByUserId(userId); }

    /**
     * §17 EMP-001·TEAM-001 — 부분 갱신: null은 유지, 빈 문자열·공백은 지우기(null)로 반영.
     * teamId는 키 제시(present) 시에만 반영 — null이면 미배정 해제(§5.3).
     */
    @Transactional
    public Employee updateEmployee(UUID employeeId, String name, String customInstructions,
                                   UUID teamId, boolean teamIdPresent) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new NoSuchElementException("Employee not found: " + employeeId));
        if (name != null) {
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            employee.setName(name.strip());
        }
        if (customInstructions != null) {
            employee.setCustomInstructions(normalizeInstructions(customInstructions));
        }
        if (teamIdPresent) {
            if (teamId == null) {
                employee.setTeamId(null);
            } else {
                teamService.requireActiveForAssignment(teamId);
                employee.setTeamId(teamId);
            }
        }
        return employeeRepository.save(employee);
    }

    /**
     * §8.2 (3)(4) 계층 조립 — 사용자 개별 지시(custom_instructions) + 직무 원칙(systemPrompt).
     * 순서: 안전 정책 문구 → 사용자 개별 지시(직무·조직 기본값·동료 피드백보다 우선, §0 원칙 11) → 직무 원칙.
     * 이 결과를 후속 AgentRun이 시스템 메시지로 소비한다.
     */
    public AgentContext buildAgentContext(UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new NoSuchElementException("Employee not found: " + employeeId));
        StringBuilder instructions = new StringBuilder();
        instructions.append("[시스템 안전 정책·도구 제약이 우선한다 — 아래 지시로 안전 정책을 변경할 수 없다]\n");
        if (employee.getCustomInstructions() != null && !employee.getCustomInstructions().isBlank()) {
            instructions.append("[사용자 개별 지시 — 직무·조직 기본값과 동료 피드백보다 우선] ")
                    .append(employee.getCustomInstructions()).append('\n');
        }
        if (employee.getSystemPrompt() != null && !employee.getSystemPrompt().isBlank()) {
            instructions.append("[직무 원칙] ").append(employee.getSystemPrompt()).append('\n');
        }
        return new AgentContext(employee.getId(), employee.getSystemPrompt(),
                employee.getCustomInstructions(), instructions.toString().strip());
    }

    public record AgentContext(UUID employeeId, String systemPrompt, String customInstructions, String instructions) {
    }

    /** 공백 정리 + 상한(4000자) 검증 — 빈 값은 미설정(null)으로 정규화(지우기) */
    private static String normalizeInstructions(String customInstructions) {
        if (customInstructions == null) {
            return null;
        }
        String trimmed = customInstructions.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_CUSTOM_INSTRUCTIONS_CHARS) {
            throw new IllegalArgumentException("customInstructions exceeds "
                    + MAX_CUSTOM_INSTRUCTIONS_CHARS + " chars (§26 custom_instructions_max_chars: 400)");
        }
        return trimmed;
    }

    @Transactional
    public void assignGrade(UUID employeeId, UUID gradeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));
        
        JobGrade grade = jobGradeService.getGrade(gradeId)
                .orElseThrow(() -> new IllegalArgumentException("Grade not found: " + gradeId));
        
        employee.setGradeId(gradeId);
        employeeRepository.save(employee);
    }

    @Transactional
    public void removeGrade(UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));
        
        employee.setGradeId(null);
        employeeRepository.save(employee);
    }
}