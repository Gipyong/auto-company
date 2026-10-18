package com.autocompany.service;

import com.autocompany.entity.Employee;
import com.autocompany.entity.JobGrade;
import com.autocompany.entity.Team;
import com.autocompany.entity.TeamFeedback;
import com.autocompany.entity.TeamFeedback.Category;
import com.autocompany.repository.EmployeeRepository;
import com.autocompany.repository.JobGradeRepository;
import com.autocompany.repository.TeamFeedbackRepository;
import com.autocompany.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * 팀 단위 피드백 서비스(§5.20.1 v1.9) — 팀장이 상대 팀에 제출, 대상은 팀 전체.
 * 자기 팀 지목 금지, 팀장만 작성 가능.
 */
@Service
@RequiredArgsConstructor
public class TeamFeedbackService {

    private final TeamFeedbackRepository teamFeedbackRepository;
    private final TeamRepository teamRepository;
    private final EmployeeRepository employeeRepository;
    private final JobGradeRepository jobGradeRepository;

    @Transactional
    public TeamFeedback createFeedback(UUID organizationId, UUID subjectTeamId, UUID authorEmployeeId,
                                       UUID authorTeamId, Category category, Float score, String summary,
                                       List<String> evidenceRefs, Float contextWeight) {
        // 작성자 검증: authorEmployeeId가 authorTeamId의 팀장(또는 임시 대표)인지 확인
        Employee author = employeeRepository.findById(authorEmployeeId)
                .orElseThrow(() -> new NoSuchElementException("Author employee not found: " + authorEmployeeId));
        Team authorTeam = teamRepository.findById(authorTeamId)
                .orElseThrow(() -> new NoSuchElementException("Author team not found: " + authorTeamId));

        Employee effectiveLead = getEffectiveTeamLead(authorTeamId);
        if (!effectiveLead.getId().equals(authorEmployeeId)) {
            throw new IllegalStateException("Only team lead (or acting lead) can submit team feedback");
        }

        // 자기 팀 지목 금지
        if (subjectTeamId != null && subjectTeamId.equals(authorTeamId)) {
            throw new IllegalStateException("Cannot submit feedback to own team");
        }

        // 대상 팀 존재 확인 (null=기본 팀은 허용)
        if (subjectTeamId != null) {
            teamRepository.findById(subjectTeamId)
                    .orElseThrow(() -> new NoSuchElementException("Subject team not found: " + subjectTeamId));
        }

        TeamFeedback feedback = TeamFeedback.builder()
                .organizationId(organizationId)
                .subjectTeamId(subjectTeamId)
                .authorEmployeeId(authorEmployeeId)
                .authorTeamId(authorTeamId)
                .category(category)
                .score(score != null ? BigDecimal.valueOf(score) : null)
                .summary(summary)
                .evidenceRefs(evidenceRefs != null ? evidenceRefs : List.of())
                .contextWeight(contextWeight != null ? BigDecimal.valueOf(contextWeight) : BigDecimal.ONE)
                .build();

        return teamFeedbackRepository.save(feedback);
    }

    @Transactional(readOnly = true)
    public List<TeamFeedback> getFeedbackForTeam(UUID teamId) {
        return teamFeedbackRepository.findBySubjectTeamIdOrderByCreatedAtDesc(teamId);
    }

    @Transactional(readOnly = true)
    public TeamFeedbackSummary getFeedbackSummary(UUID teamId) {
        List<TeamFeedback> feedbacks = teamFeedbackRepository.findBySubjectTeamIdOrderByCreatedAtDesc(teamId);
        if (feedbacks.isEmpty()) {
            return new TeamFeedbackSummary(0, 0.0, 0, 0.0);
        }
        int totalCount = feedbacks.size();
        double avgScore = feedbacks.stream()
                .filter(f -> f.getScore() != null)
                .mapToDouble(f -> f.getScore().doubleValue())
                .average()
                .orElse(0.0);
        int byCategoryCount = (int) feedbacks.stream().map(TeamFeedback::getCategory).distinct().count();
        double avgContextWeight = feedbacks.stream()
                .mapToDouble(f -> f.getContextWeight().doubleValue())
                .average()
                .orElse(0.0);
        return new TeamFeedbackSummary(totalCount, avgScore, byCategoryCount, avgContextWeight);
    }

    public record TeamFeedbackSummary(
            int totalCount,
            double avgScore,
            int byCategoryCount,
            double avgContextWeight
    ) {
    }

    /** 팀의 실질 리드 반환 — 지정 리드 있으면 반환, 없으면 최고 직급(ordinal desc, 동률 이름순) 임시 대표 */
    private Employee getEffectiveTeamLead(UUID teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NoSuchElementException("Team not found: " + teamId));
        if (team.getTeamLeadEmployeeId() != null) {
            return employeeRepository.findById(team.getTeamLeadEmployeeId())
                    .orElseThrow(() -> new NoSuchElementException("Team lead employee not found: " + team.getTeamLeadEmployeeId()));
        }
        List<Employee> members = employeeRepository.findByTeamId(teamId);
        if (members.isEmpty()) {
            throw new IllegalStateException("Team has no members to act as lead: " + teamId);
        }
        return members.stream()
                .filter(e -> e.getGradeId() != null)
                .max((e1, e2) -> {
                    JobGrade g1 = jobGradeRepository.findById(e1.getGradeId()).orElse(null);
                    JobGrade g2 = jobGradeRepository.findById(e2.getGradeId()).orElse(null);
                    int o1 = g1 != null ? g1.getOrdinal() : -1;
                    int o2 = g2 != null ? g2.getOrdinal() : -1;
                    if (o1 != o2) return Integer.compare(o2, o1);
                    return e1.getName().compareTo(e2.getName());
                })
                .orElse(members.stream().min((e1, e2) -> e1.getName().compareTo(e2.getName())).orElseThrow());
    }
}