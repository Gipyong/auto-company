package com.autocompany.service;

import com.autocompany.entity.Employee;
import com.autocompany.entity.JobGrade;
import com.autocompany.entity.PeerRating;
import com.autocompany.entity.enums.Category;
import com.autocompany.repository.EmployeeRepository;
import com.autocompany.repository.JobGradeRepository;
import com.autocompany.repository.PeerRatingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RatingEngineService {

    private final EmployeeRepository employeeRepository;
    private final JobGradeRepository jobGradeRepository;
    private final PeerRatingRepository ratingRepository;

    // Context management constants (§15.3)
    static final int MAX_CONTEXT_BYTES = 10 * 1024; // 10KB per employee
    static final int MAX_RECENT_RATINGS = 50;
    static final int SUMMARY_TRIGGER_COUNT = 20;
    static final int RETENTION_MONTHS = 6;

    // Evaluations endpoint constants (§15.4)
    static final int RECENT_FEEDBACK_LIMIT = 20;
    static final int RECENT_PERFORMANCE_DAYS = 30;

    /**
     * 피드백 제출 - 상호 피드백 루프 형성
     * 평가자도 피드백을 받는 구조로 양방향 개선 유도
     */
    @Transactional
    public PeerRating submitFeedback(PeerRating feedback) {
        // 증거 레퍼런스 검증 (PR, 커밋, 테스트 등)
        validateEvidenceRefs(feedback.getEvidenceRefs());
        
        // 초기 가중치 1.0
        feedback.setContextWeight(1.0f);
        feedback.setCreatedAt(LocalDateTime.now());
        
        PeerRating saved = ratingRepository.save(feedback);
        
        // 피드백 컨텍스트 크기 관리
        manageFeedbackContext(feedback.getRatedEmployeeId());
        
        return saved;
    }

    /**
     * 직원에게 주어진 피드백들을 컨텍스트용으로 조회 (프롬프트 주입용)
     * - 최신순, 가중치 높은 순 정렬
     * - 컨텍스트 크기 제한 내에서 선별
     * - 요약본 우선 사용
     */
    @Transactional(readOnly = true)
    public FeedbackContext getFeedbackContext(UUID employeeId) {
        List<PeerRating> allRatings = ratingRepository.findByRatedEmployeeIdOrderByCreatedAtDesc(employeeId);
        
        // 가중치 적용 및 필터링 (아카이브된 것 제외)
        List<PeerRating> activeRatings = allRatings.stream()
                .filter(r -> r.getArchivedAt() == null)
                .sorted(Comparator.comparing(PeerRating::getContextWeight).reversed()
                        .thenComparing(PeerRating::getCreatedAt).reversed())
                .toList();
        
        // 컨텍스트 크기 계산 및 선별
        StringBuilder contextBuilder = new StringBuilder();
        int bytesUsed = 0;
        List<PeerRating> selected = new ArrayList<>();
        
        for (PeerRating r : activeRatings) {
            String entry = formatFeedbackEntry(r);
            int entryBytes = entry.getBytes().length;
            
            if (bytesUsed + entryBytes > MAX_CONTEXT_BYTES) {
                break;
            }
            
            contextBuilder.append(entry).append("\n---\n");
            bytesUsed += entryBytes;
            selected.add(r);
        }
        
        // 통계
        double avgScore = activeRatings.isEmpty() ? 0 : 
                activeRatings.stream().mapToDouble(PeerRating::getScore).average().orElse(0);
        
        return new FeedbackContext(
                employeeId,
                activeRatings.size(),
                selected.size(),
                bytesUsed,
                avgScore,
                contextBuilder.toString(),
                selected
        );
    }

    /**
     * 피드백 컨텍스트 크기 관리 - 초과 시 요약/압축
     */
    @Transactional
    public void manageFeedbackContext(UUID employeeId) {
        List<PeerRating> allRatings = ratingRepository.findByRatedEmployeeIdOrderByCreatedAtDesc(employeeId);
        
        // 1. 보존 기간 초과 아카이브
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(RETENTION_MONTHS);
        for (PeerRating r : allRatings) {
            if (r.getCreatedAt().isBefore(cutoff) && r.getArchivedAt() == null) {
                archiveRating(r);
            }
        }
        
        // 2. 개수 초과 시 오래된 것부터 요약
        List<PeerRating> active = allRatings.stream()
                .filter(r -> r.getArchivedAt() == null)
                .toList();
        
        if (active.size() > MAX_RECENT_RATINGS) {
            List<PeerRating> toSummarize = active.stream()
                    .skip(MAX_RECENT_RATINGS)
                    .toList();
            
            for (PeerRating r : toSummarize) {
                summarizeAndArchive(r);
            }
        }
        
        // 3. 가중치 시간 감쇠 적용 (월 1회 배치에서 호출 권장)
        decayWeights(employeeId);
    }

    /**
     * 가중치 시간 감쇠 - 오래된 피드백의 영향력 감소
     */
    @Transactional
    public void decayWeights(UUID employeeId) {
        List<PeerRating> active = ratingRepository.findByRatedEmployeeIdOrderByCreatedAtDesc(employeeId)
                .stream()
                .filter(r -> r.getArchivedAt() == null)
                .toList();
        
        LocalDateTime now = LocalDateTime.now();
        for (PeerRating r : active) {
            long monthsOld = java.time.temporal.ChronoUnit.MONTHS.between(r.getCreatedAt(), now);
            // 월 10% 감쇠, 최소 0.1
            float newWeight = Math.max(0.1f, 1.0f - (monthsOld * 0.1f));
            r.setContextWeight(newWeight);
            ratingRepository.save(r);
        }
    }

    private void archiveRating(PeerRating rating) {
        rating.setArchivedAt(LocalDateTime.now());
        rating.setContextWeight(0.0f);
        ratingRepository.save(rating);
    }

    private void summarizeAndArchive(PeerRating rating) {
        // TODO: LLM 기반 요약 호출 (Agent Runtime 연동 시 구현)
        // 현재는 comment를 요약본으로 사용
        String summary = rating.getComment() != null ? 
                rating.getComment().substring(0, Math.min(200, rating.getComment().length())) 
                : "자동 요약: " + rating.getCategory().name() + " " + rating.getScore();
        
        rating.setFeedbackSummary(summary);
        rating.setArchivedAt(LocalDateTime.now());
        rating.setContextWeight(0.0f);
        ratingRepository.save(rating);
    }

    private void validateEvidenceRefs(List<String> refs) {
        if (refs != null && refs.size() > 10) {
            throw new IllegalArgumentException("증거 레퍼런스는 최대 10개까지 허용");
        }
    }

    private String formatFeedbackEntry(PeerRating r) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(r.getCategory().name()).append("] ")
          .append("Score: ").append(r.getScore()).append("/5.0");
        if (r.getFeedbackSummary() != null) {
            sb.append(" | Summary: ").append(r.getFeedbackSummary());
        } else if (r.getComment() != null) {
            sb.append(" | ").append(r.getComment());
        }
        if (r.getEvidenceRefs() != null && !r.getEvidenceRefs().isEmpty()) {
            sb.append(" | Refs: ").append(String.join(", ", r.getEvidenceRefs()));
        }
        sb.append(" | Weight: ").append(String.format("%.2f", r.getContextWeight()));
        sb.append(" | Date: ").append(r.getCreatedAt().toLocalDate());
        return sb.toString();
    }

    /**
     * 직급 변경 판단 참고 자료 (§15.4, GET /employees/{id}/evaluations)
     * 피드백 이력과 집계 수치를 사실 기반으로 제공하며, 자동 직급 변경에 사용하지 않는다.
     */
    @Transactional(readOnly = true)
    public Optional<EvaluationReport> getEvaluationReport(UUID employeeId) {
        return employeeRepository.findById(employeeId).map(employee -> {
            List<PeerRating> all = ratingRepository.findByRatedEmployeeIdOrderByCreatedAtDesc(employeeId);
            List<PeerRating> active = all.stream()
                    .filter(r -> r.getArchivedAt() == null)
                    .toList();

            LocalDateTime recentCutoff = LocalDateTime.now().minusDays(RECENT_PERFORMANCE_DAYS);
            List<PeerRating> recent30d = active.stream()
                    .filter(r -> r.getCreatedAt().isAfter(recentCutoff))
                    .toList();

            int distinctRaters = (int) active.stream()
                    .map(PeerRating::getRaterEmployeeId)
                    .distinct()
                    .count();
            int evidenceBacked = (int) active.stream()
                    .filter(r -> r.getEvidenceRefs() != null && !r.getEvidenceRefs().isEmpty())
                    .count();

            List<CategoryAverage> categoryAverages = active.stream()
                    .collect(Collectors.groupingBy(PeerRating::getCategory))
                    .entrySet().stream()
                    .map(e -> new CategoryAverage(
                            e.getKey(),
                            e.getValue().size(),
                            e.getValue().stream().mapToDouble(PeerRating::getScore).average().orElse(0)))
                    .sorted(Comparator.comparing(ca -> ca.getCategory().name()))
                    .toList();

            List<FeedbackEntry> recentFeedback = active.stream()
                    .limit(RECENT_FEEDBACK_LIMIT)
                    .map(FeedbackEntry::from)
                    .toList();

            String gradeName = "";
            if (employee.getGradeId() != null) {
                JobGrade grade = jobGradeRepository.findById(employee.getGradeId()).orElse(null);
                if (grade != null) gradeName = grade.getName();
            }

            return EvaluationReport.builder()
                    .employeeId(employeeId)
                    .name(employee.getName())
                    .role(employee.getRole() != null ? employee.getRole().name() : "")
                    .grade(gradeName)
                    .totalFeedbackCount(all.size())
                    .activeFeedbackCount(active.size())
                    .distinctRaterCount(distinctRaters)
                    .evidenceBackedCount(evidenceBacked)
                    .overallAvgScore(all.isEmpty() ? null :
                            all.stream().mapToDouble(PeerRating::getScore).average().orElse(0))
                    .recent30dCount(recent30d.size())
                    .recent30dAvgScore(recent30d.isEmpty() ? null :
                            recent30d.stream().mapToDouble(PeerRating::getScore).average().orElse(0))
                    .categoryAverages(categoryAverages)
                    .recentFeedback(recentFeedback)
                    .build();
        });
    }

    // ===== 기존 호환용 메서드 =====

    @Deprecated(forRemoval = true, since = "v1.2 - use submitFeedback")
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
            if (grade != null) gradeName = grade.getName();
        }
        
        return new RatingSummary(employeeId, employee.getName(), 
                employee.getRole() != null ? employee.getRole().name() : "",
                gradeName,
                ratings.size(), employee.getAvgRating(),
                employee.getCompletedTasks(), employee.getTotalTasks(),
                ratings.stream().limit(10).toList());
    }

    // ===== DTOs =====

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

    @lombok.Data
    @lombok.Builder
    public static class FeedbackContext {
        private UUID employeeId;
        private int totalFeedbackCount;
        private int selectedCount;
        private int contextBytes;
        private double avgScore;
        private String formattedContext; // Ready for prompt injection
        private List<PeerRating> selectedRatings;

        public FeedbackContext(UUID employeeId, int totalFeedbackCount, int selectedCount,
                               int contextBytes, double avgScore, String formattedContext,
                               List<PeerRating> selectedRatings) {
            this.employeeId = employeeId;
            this.totalFeedbackCount = totalFeedbackCount;
            this.selectedCount = selectedCount;
            this.contextBytes = contextBytes;
            this.avgScore = avgScore;
            this.formattedContext = formattedContext;
            this.selectedRatings = selectedRatings;
        }
    }

    /**
     * 직급 변경 판단 참고 자료 (§15.4). 점수로 자동 변경을 수행하지 않는다.
     */
    @lombok.Data
    @lombok.Builder
    public static class EvaluationReport {
        private UUID employeeId;
        private String name;
        private String role;
        private String grade;
        private int totalFeedbackCount;    // 전체 피드백 수 (아카이브 포함)
        private int activeFeedbackCount;   // 활성 피드백 수 (아카이브 제외)
        private int distinctRaterCount;    // 서로 다른 평가자 수 (신뢰도 참고)
        private int evidenceBackedCount;   // evidenceRefs 포함 피드백 수 (리뷰 품질 참고)
        private Double overallAvgScore;    // 전체 평균 점수 (데이터 없으면 null)
        private int recent30dCount;        // 최근 30일 피드백 수
        private Double recent30dAvgScore;  // 최근 30일 평균 점수 (데이터 없으면 null)
        private List<CategoryAverage> categoryAverages;
        private List<FeedbackEntry> recentFeedback; // 최근순, 아카이브 제외
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class CategoryAverage {
        private Category category;
        private int count;
        private double avgScore;
    }

    /**
     * API 응답용 피드백 항목 (엔티티 참조 없는 평면 구조)
     */
    @lombok.Data
    @lombok.Builder
    public static class FeedbackEntry {
        private UUID id;
        private UUID taskId;
        private UUID raterEmployeeId;
        private Category category;
        private float score;
        private String comment;
        private String feedbackSummary;
        private float contextWeight;
        private List<String> evidenceRefs;
        private LocalDateTime createdAt;
        private LocalDateTime archivedAt;

        public static FeedbackEntry from(PeerRating r) {
            return FeedbackEntry.builder()
                    .id(r.getId())
                    .taskId(r.getTask() != null ? r.getTask().getId() : null)
                    .raterEmployeeId(r.getRaterEmployeeId())
                    .category(r.getCategory())
                    .score(r.getScore())
                    .comment(r.getComment())
                    .feedbackSummary(r.getFeedbackSummary())
                    .contextWeight(r.getContextWeight())
                    .evidenceRefs(r.getEvidenceRefs())
                    .createdAt(r.getCreatedAt())
                    .archivedAt(r.getArchivedAt())
                    .build();
        }
    }
}