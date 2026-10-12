package com.autocompany.repository;
import com.autocompany.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    List<Employee> findByRole(String role);
}
