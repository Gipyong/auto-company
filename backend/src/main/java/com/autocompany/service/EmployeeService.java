package com.autocompany.service;
import com.autocompany.entity.Employee;
import com.autocompany.entity.User;
import com.autocompany.entity.enums.Role;
import com.autocompany.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
@RequiredArgsConstructor
public class EmployeeService {
    private final EmployeeRepository employeeRepository;
    public Employee createEmployee(User user, String name, String role) {
        Employee e = new Employee();
        e.setId(UUID.randomUUID());
        e.setUser(user);
        e.setName(name);
        e.setRole(role);
        return employeeRepository.save(e);
    }
    public Optional<Employee> getEmployee(UUID id) { return employeeRepository.findById(id); }
    public List<Employee> getEmployeesByUserId(UUID userId) { return employeeRepository.findByUserId(userId); }
    public void updateEmployeeModel(UUID id, String aiModel) { employeeRepository.findById(id).ifPresent(e -> { e.setAiModel(aiModel); employeeRepository.save(e); }); }
}
