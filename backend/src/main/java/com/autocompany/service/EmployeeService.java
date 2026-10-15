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

    public Employee createEmployee(UUID userId, String name, String role) {
        User user = userService.getOrCreateLocalUser(userId);
        Employee e = new Employee();
        e.setId(UUID.randomUUID());
        e.setUser(user);
        e.setName(name);
        e.setRole(Role.valueOf(role.toUpperCase()));
        return employeeRepository.save(e);
    }

    public Optional<Employee> getEmployee(UUID id) { return employeeRepository.findById(id); }

    public List<Employee> getEmployeesByUserId(UUID userId) { return employeeRepository.findByUserId(userId); }

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