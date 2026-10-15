package com.autocompany.service;

import com.autocompany.entity.Employee;
import com.autocompany.entity.JobGrade;
import com.autocompany.entity.PeerRating;
import com.autocompany.repository.EmployeeRepository;
import com.autocompany.repository.JobGradeRepository;
import com.autocompany.repository.PeerRatingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RatingEngineService {

    private final EmployeeRepository employeeRepository;
    private final JobGradeRepository jobGradeRepository;
    private final PeerRatingRepository ratingRepository;

    @Transactional
    public void updateRating(UUID employeeId, float newScore) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow();
        
        List<Float> allRatings = employee.getRatings();
        allRatings.add(newScore);
        float avg = (float) allRatings.stream().mapToDouble(f -> f).average().orElse(0.0);
        
        employee.setAvgRating(avg);
        employee.setTotalTasks(employee.getTotalTasks() + 1);
        
        employeeRepository.save(employee);
    }

    @Transactional(readOnly = true)
    public RatingSummary getRatingSummary(UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow();
        List<PeerRating> ratings = ratingRepository.findByRatedEmployeeId(employeeId);
        
        String gradeName = "";
        if (employee.getGradeId() != null) {
            JobGrade grade = jobGradeRepository.findById(employee.getGradeId()).orElse(null);
            if (grade != null) {
                gradeName = grade.getName();
            }
        }
        
        return new RatingSummary(employeeId, employee.getName(), 
                employee.getRole() != null ? employee.getRole().name() : "",
                gradeName,
                ratings.size(), employee.getAvgRating(),
                employee.getCompletedTasks(), employee.getTotalTasks(),
                ratings.stream().limit(10).toList());
    }

    @lombok.Data
    @lombok.Builder
    public static class RatingSummary {
        private UUID employeeId;
        private String name;
        private String role;
        private String grade;
        private int totalRatings;
        private float avgRating;
        private int completedTasks;
        private int totalTasks;
        private List<PeerRating> recentRatings;

        public RatingSummary(UUID employeeId, String name, String role, String grade,
                             int totalRatings, float avgRating, int completedTasks, int totalTasks,
                             List<PeerRating> recentRatings) {
            this.employeeId = employeeId;
            this.name = name;
            this.role = role;
            this.grade = grade;
            this.totalRatings = totalRatings;
            this.avgRating = avgRating;
            this.completedTasks = completedTasks;
            this.totalTasks = totalTasks;
            this.recentRatings = recentRatings;
        }
    }
}