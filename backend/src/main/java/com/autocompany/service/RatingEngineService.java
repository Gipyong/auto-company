package com.autocompany.service;
import com.autocompany.entity.enums.Rank;

import com.autocompany.entity.Employee;
import com.autocompany.entity.PeerRating;
import com.autocompany.entity.enums.Rank;
import com.autocompany.repository.EmployeeRepository;
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
    private final PeerRatingRepository ratingRepository;

    @Transactional
    public void updateRating(UUID employeeId, float newScore) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow();
        
        // 기존 평점들에 새 평점 추가 후 평균 계산
        List<Float> allRatings = employee.getRatings();
        allRatings.add(newScore);
        float avg = (float) allRatings.stream().mapToDouble(f -> f).average().orElse(0.0);
        
        employee.setAvgRating(avg);
        employee.setTotalTasks(employee.getTotalTasks() + 1);
        
        // 레벨업 체크
        checkAndPromote(employee);
        
        employeeRepository.save(employee);
    }

    private void checkAndPromote(Employee employee) {
        int newLevel = calculateLevel(employee.getCompletedTasks(), employee.getAvgRating());
        
        if (newLevel > employee.getLevel()) {
            employee.setLevel(newLevel);
            
            // 직급(Rank) 업데이트
            Rank newRank = calculateRank(newLevel);
            if (newRank != employee.getRank()) {
                employee.setRank(newRank);
            }
        }
    }

    private int calculateLevel(int completedTasks, float avgRating) {
        // Score = completedTasks * 0.4 + avgRating * 20 * 0.4 + contribution * 0.2
        // contribution은 문서화 작업 등으로 가정
        double score = completedTasks * 0.4 + avgRating * 20 * 0.4;
        
        if (score >= 200) return 10;
        if (score >= 150) return 9;
        if (score >= 120) return 8;
        if (score >= 90) return 7;
        if (score >= 60) return 6;
        if (score >= 40) return 5;
        if (score >= 25) return 4;
        if (score >= 15) return 3;
        if (score >= 5) return 2;
        return 1;
    }

    private Rank calculateRank(int level) {
        if (level >= 9) return Rank.LEAD;
        if (level >= 7) return Rank.SENIOR;
        if (level >= 5) return Rank.MID;
        if (level >= 3) return Rank.JUNIOR;
        return Rank.INTERN;
    }

    @Transactional(readOnly = true)
    public RatingSummary getRatingSummary(UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow();
        List<PeerRating> ratings = ratingRepository.findByRatedEmployeeId(employeeId);
        
        return new RatingSummary(employeeId, employee.getName(), 
                (employee.getRole() != null ? employee.getRole().name() : ""),
                (employee.getRank() != null ? employee.getRank().name() : "INTERN"),
                employee.getLevel(), ratings.size(), employee.getAvgRating(),
                employee.getCompletedTasks(), employee.getTotalTasks(),
                ratings.stream().limit(10).toList());
    }

    @lombok.Data
    @lombok.Builder
    public static class RatingSummary {
        private UUID employeeId;
        private String name;
        private String role;
        private String rank;
        private int level;
        private int totalRatings;
        private float avgRating;
        private int completedTasks;
        private int totalTasks;
        private List<PeerRating> recentRatings;

        public RatingSummary(UUID employeeId, String name, String role, String rank, int level,
                             int totalRatings, float avgRating, int completedTasks, int totalTasks,
                             List<PeerRating> recentRatings) {
            this.employeeId = employeeId;
            this.name = name;
            this.role = role;
            this.rank = rank;
            this.level = level;
            this.totalRatings = totalRatings;
            this.avgRating = avgRating;
            this.completedTasks = completedTasks;
            this.totalTasks = totalTasks;
            this.recentRatings = recentRatings;
        }
    }
}