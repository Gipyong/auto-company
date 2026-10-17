package com.autocompany.service;

import com.autocompany.entity.*;
import com.autocompany.entity.enums.*;
import com.autocompany.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
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
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private ProjectRepository projectRepository;

    private Task testTask;
    private UUID testEmployeeId;
    private UUID testGradeId;
    private UUID raterId;
    private UUID testTaskId;

    @BeforeEach
    public void setUp() {
        User user = userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email("ceo@test.com")
                .name("CEO")
                .build());

        User raterUser = userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email("rater@test.com")
                .name("Rater")
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

        Employee rater = employeeRepository.save(Employee.builder()
                .user(raterUser)
                .name("Rater Employee")
                .role(Role.DEVELOPER)
                .gradeId(testGradeId)
                .status(Status.IDLE)
                .build());
        raterId = rater.getId();

        Project project = projectRepository.save(Project.builder()
                .user(user)
                .name("Test Project")
                .status("PLANNING")
                .build());

        testTask = taskRepository.save(Task.builder()
                .project(project)
                .assignee(employee)
                .type(TaskType.TASK)
                .title("Test Task")
                .description("Test Description")
                .build());
        testTaskId = testTask.getId();
    }

    @Test
    public void testSubmitFeedback() {
        PeerRating feedback = new PeerRating();
        feedback.setRatedEmployeeId(testEmployeeId);
        feedback.setRaterEmployeeId(raterId);
        feedback.setTask(testTask);
        feedback.setCategory(Category.CODE_QUALITY);
        feedback.setScore(4.5f);
        feedback.setComment("좋은 코드 구조입니다. 테스트 커버리지를 더 높이면 좋겠습니다.");
        feedback.setEvidenceRefs(List.of("PR-123", "commit-abc"));

        PeerRating saved = ratingEngineService.submitFeedback(feedback);

        assertNotNull(saved.getId());
        assertEquals(testEmployeeId, saved.getRatedEmployeeId());
        assertEquals(raterId, saved.getRaterEmployeeId());
        assertEquals(1.0f, saved.getContextWeight()); // 초기 가중치
        assertNull(saved.getArchivedAt());
    }

    @Test
    public void testFeedbackContextRetrieval() {
        // 여러 피드백 제출
        for (int i = 0; i < 5; i++) {
            PeerRating fb = new PeerRating();
            fb.setRatedEmployeeId(testEmployeeId);
            fb.setRaterEmployeeId(raterId);
            fb.setTask(testTask);
            fb.setCategory(Category.values()[i % Category.values().length]);
            fb.setScore(4.0f + (i * 0.2f));
            fb.setComment("피드백 " + i);
            fb.setEvidenceRefs(List.of("PR-" + i));
            ratingEngineService.submitFeedback(fb);
        }

        RatingEngineService.FeedbackContext context = ratingEngineService.getFeedbackContext(testEmployeeId);

        assertNotNull(context);
        assertEquals(5, context.getTotalFeedbackCount());
        assertEquals(5, context.getSelectedCount()); // 5개 모두 10KB 이내
        assertTrue(context.getContextBytes() > 0);
        assertTrue(context.getFormattedContext().contains("CODE_QUALITY"));
        assertTrue(context.getFormattedContext().contains("PR-"));
    }

    @Test
    public void testFeedbackContextSizeLimit() {
        // 큰 피드백 여러 개로 10KB 초과 테스트
        String largeComment = "x".repeat(3000); // ~3KB each
        for (int i = 0; i < 5; i++) {
            PeerRating fb = new PeerRating();
            fb.setRatedEmployeeId(testEmployeeId);
            fb.setRaterEmployeeId(raterId);
            fb.setTask(testTask);
            fb.setCategory(Category.CODE_QUALITY);
            fb.setScore(4.0f);
            fb.setComment(largeComment);
            ratingEngineService.submitFeedback(fb);
        }

        RatingEngineService.FeedbackContext context = ratingEngineService.getFeedbackContext(testEmployeeId);

        // 10KB 제한으로 일부만 선택되어야 함
        assertTrue(context.getSelectedCount() < context.getTotalFeedbackCount());
        assertTrue(context.getContextBytes() <= RatingEngineService.MAX_CONTEXT_BYTES);
    }

    @Test
    public void testNoAutoPromotionStillWorks() {
        Employee before = employeeRepository.findById(testEmployeeId).orElseThrow();
        UUID originalGradeId = before.getGradeId();

        // 구식 updateRating 호출해도 직급 변경 없음
        for (int i = 0; i < 20; i++) {
            ratingEngineService.updateRating(testEmployeeId, 5.0f);
        }

        Employee after = employeeRepository.findById(testEmployeeId).orElseThrow();
        assertEquals(originalGradeId, after.getGradeId());
    }

    @Test
    public void testFeedbackSummaryAndArchive() {
        PeerRating fb = new PeerRating();
        fb.setRatedEmployeeId(testEmployeeId);
        fb.setRaterEmployeeId(raterId);
        fb.setTask(testTask);
        fb.setCategory(Category.CODE_QUALITY);
        fb.setScore(4.5f);
        fb.setComment("매우 상세한 피드백 내용입니다. ".repeat(20));
        ratingEngineService.submitFeedback(fb);

        // 요약/아카이브 실행
        ratingEngineService.manageFeedbackContext(testEmployeeId);

        // 바로 아카이브되지는 않음 (보존 기간 6개월)
        PeerRating saved = peerRatingRepository.findByRatedEmployeeIdOrderByCreatedAtDesc(testEmployeeId).get(0);
        assertNull(saved.getArchivedAt());
        assertEquals(1.0f, saved.getContextWeight());
    }

    @Test
    public void testFeedbackWeightDecay() {
        PeerRating fb = new PeerRating();
        fb.setRatedEmployeeId(testEmployeeId);
        fb.setRaterEmployeeId(raterId);
        fb.setTask(testTask);
        fb.setCategory(Category.CODE_QUALITY);
        fb.setScore(4.5f);
        fb.setComment("테스트 피드백");
        ratingEngineService.submitFeedback(fb);

        // 가중치 감쇠 실행
        ratingEngineService.decayWeights(testEmployeeId);

        PeerRating saved = peerRatingRepository.findByRatedEmployeeIdOrderByCreatedAtDesc(testEmployeeId).get(0);
        // 당월 생성된 것은 감쇠 없음 (0개월)
        assertEquals(1.0f, saved.getContextWeight());
    }

    @Test
    public void testGetEvaluationReportAggregates() {
        // 두 번째 평가자 추가
        User raterUser2 = userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email("rater2@test.com")
                .name("Rater2")
                .build());
        Employee rater2 = employeeRepository.save(Employee.builder()
                .user(raterUser2)
                .name("Rater2 Employee")
                .role(Role.DEVELOPER)
                .gradeId(testGradeId)
                .status(Status.IDLE)
                .build());

        submitOne(raterId, Category.CODE_QUALITY, 4.0f, List.of("PR-1"));
        submitOne(raterId, Category.CODE_QUALITY, 5.0f, null);
        submitOne(rater2.getId(), Category.LOGIC, 3.0f, List.of("commit-2"));

        RatingEngineService.EvaluationReport report =
                ratingEngineService.getEvaluationReport(testEmployeeId).orElseThrow();

        assertEquals(testEmployeeId, report.getEmployeeId());
        assertEquals("Test Employee", report.getName());
        assertEquals("DEVELOPER", report.getRole());
        assertEquals("Mid", report.getGrade());
        assertEquals(3, report.getTotalFeedbackCount());
        assertEquals(3, report.getActiveFeedbackCount());
        assertEquals(2, report.getDistinctRaterCount());
        assertEquals(2, report.getEvidenceBackedCount());
        assertEquals(4.0, report.getOverallAvgScore(), 0.001);
        assertEquals(3, report.getRecent30dCount());
        assertEquals(4.0, report.getRecent30dAvgScore(), 0.001);
        assertEquals(3, report.getRecentFeedback().size());
        assertEquals(2, report.getCategoryAverages().size());
        // 카테고리는 이름순 정렬
        assertEquals(Category.CODE_QUALITY, report.getCategoryAverages().get(0).getCategory());
        assertEquals(2, report.getCategoryAverages().get(0).getCount());
        assertEquals(4.5, report.getCategoryAverages().get(0).getAvgScore(), 0.001);
        assertEquals(Category.LOGIC, report.getCategoryAverages().get(1).getCategory());
        // FeedbackEntry는 엔티티 참조 대신 taskId를 포함
        assertTrue(report.getRecentFeedback().stream().allMatch(f -> testTaskId.equals(f.getTaskId())));
    }

    @Test
    public void testGetEvaluationReportUnknownEmployee() {
        assertTrue(ratingEngineService.getEvaluationReport(UUID.randomUUID()).isEmpty());
    }

    @Test
    public void testGetEvaluationReportExcludesArchivedFromActiveStats() {
        submitOne(raterId, Category.CODE_QUALITY, 4.5f, List.of("PR-9"));

        // 아카이브 처리
        PeerRating saved = peerRatingRepository.findByRatedEmployeeIdOrderByCreatedAtDesc(testEmployeeId).get(0);
        saved.setArchivedAt(java.time.LocalDateTime.now());
        saved.setContextWeight(0.0f);
        peerRatingRepository.save(saved);

        RatingEngineService.EvaluationReport report =
                ratingEngineService.getEvaluationReport(testEmployeeId).orElseThrow();

        assertEquals(1, report.getTotalFeedbackCount());
        assertEquals(0, report.getActiveFeedbackCount());
        assertEquals(0, report.getRecent30dCount());
        assertNull(report.getRecent30dAvgScore());
        assertTrue(report.getRecentFeedback().isEmpty());
        assertTrue(report.getCategoryAverages().isEmpty());
        // overallAvgScore는 전체(아카이브 포함) 기준
        assertEquals(4.5, report.getOverallAvgScore(), 0.001);
    }

    private void submitOne(UUID rater, Category category, float score, List<String> evidenceRefs) {
        PeerRating fb = new PeerRating();
        fb.setRatedEmployeeId(testEmployeeId);
        fb.setRaterEmployeeId(rater);
        fb.setTask(testTask);
        fb.setCategory(category);
        fb.setScore(score);
        fb.setComment(category + " 피드백");
        fb.setEvidenceRefs(evidenceRefs);
        ratingEngineService.submitFeedback(fb);
    }
}