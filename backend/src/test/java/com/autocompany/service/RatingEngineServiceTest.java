package com.autocompany.service;

import com.autocompany.entity.*;
import com.autocompany.entity.enums.*;
import com.autocompany.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class RatingEngineServiceTest {

    @Autowired
    private RatingEngineService ratingEngineService;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JobGradeRepository jobGradeRepository;
    @Autowired
    private PeerRatingRepository peerRatingRepository;

    private UUID testEmployeeId;
    private UUID testGradeId;

    @BeforeEach
    public void setUp() {
        User user = userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email("ceo@test.com")
                .name("CEO")
                .build());

        JobGrade grade = new JobGrade();
        grade.setOrganizationId(UUID.randomUUID());
        grade.setCode("L2");
        grade.setName("Mid");
        grade.setOrdinal(2);
        grade.setModelSelectionMode(JobGrade.ModelSelectionMode.PRIORITY);
        grade.setStatus(JobGrade.Status.ACTIVE);
        grade = jobGradeRepository.save(grade);
        testGradeId = grade.getId();

        Employee employee = employeeRepository.save(Employee.builder()
                .user(user)
                .name("Test Employee")
                .role(Role.DEVELOPER)
                .gradeId(testGradeId)
                .status(Status.IDLE)
                .build());
        testEmployeeId = employee.getId();
    }

    @Test
    public void testUpdateRatingRecordsScore() {
        // 초기 상태 확인
        Employee before = employeeRepository.findById(testEmployeeId).orElseThrow();
        assertEquals(0, before.getTotalTasks());
        assertEquals(0.0f, before.getAvgRating());

        // 평가 추가
        ratingEngineService.updateRating(testEmployeeId, 4.5f);

        Employee after = employeeRepository.findById(testEmployeeId).orElseThrow();
        assertEquals(1, after.getTotalTasks());
        assertEquals(4.5f, after.getAvgRating());
    }

    @Test
    public void testUpdateRatingMultipleTimes() {
        ratingEngineService.updateRating(testEmployeeId, 4.0f);
        ratingEngineService.updateRating(testEmployeeId, 5.0f);
        ratingEngineService.updateRating(testEmployeeId, 3.0f);

        Employee employee = employeeRepository.findById(testEmployeeId).orElseThrow();
        assertEquals(3, employee.getTotalTasks());
        assertEquals((4.0f + 5.0f + 3.0f) / 3.0f, employee.getAvgRating(), 0.01f);
    }

    @Test
    public void testGetRatingSummary() {
        ratingEngineService.updateRating(testEmployeeId, 4.5f);
        
        RatingEngineService.RatingSummary summary = ratingEngineService.getRatingSummary(testEmployeeId);
        
        assertNotNull(summary);
        assertEquals(testEmployeeId, summary.getEmployeeId());
        assertEquals("Test Employee", summary.getName());
        assertEquals(Role.DEVELOPER.name(), summary.getRole());
        assertEquals("Mid", summary.getGrade()); // 직급명 반환
        assertEquals(1, summary.getTotalRatings());
        assertEquals(4.5f, summary.getAvgRating());
    }

    @Test
    public void testNoAutoPromotion() {
        // 여러 번 높은 평가를 받아도 직급이 자동 변경되지 않아야 함
        Employee before = employeeRepository.findById(testEmployeeId).orElseThrow();
        UUID originalGradeId = before.getGradeId();

        for (int i = 0; i < 20; i++) {
            ratingEngineService.updateRating(testEmployeeId, 5.0f);
        }

        Employee after = employeeRepository.findById(testEmployeeId).orElseThrow();
        // 직급은 여전히 원래대로 유지되어야 함 (자동 승급 없음)
        assertEquals(originalGradeId, after.getGradeId());
        assertEquals(testGradeId, after.getGradeId());
    }
}