package com.autocompany.repository;
import com.autocompany.entity.Employee;
import com.autocompany.entity.enums.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    List<Employee> findByRole(Role role);
    
    @EntityGraph(attributePaths = "user")
    List<Employee> findByUserId(UUID userId);
}
