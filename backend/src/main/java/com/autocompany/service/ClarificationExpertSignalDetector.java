package com.autocompany.service;

import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * §28.4 [0단계] 전문성 신호 선식별 (§28.2).
 * goal/기타 텍스트에서 전문적 내용을 도메인별 신호로 세분 기록한다 — "전문가" 단정 금지.
 * 이미 결정·제시된 영역은 해당 질문 면제(사유+근거를 협의 기록으로 남긴다).
 */
@Component
public class ClarificationExpertSignalDetector {

    public enum Domain { TECH_STACK, SECURITY, DESIGN, UX, INTEGRATION, FEATURES, BUDGET }

    public record ExpertSignal(Domain domain, String evidence, int matchCount) {
    }

    private static final Map<Domain, List<String>> KEYWORDS = Map.of(
            Domain.TECH_STACK, List.of("spring", "boot", "postgres", "mysql", "react", "vue", "next.js", "nextjs",
                    "node", "python", "django", "docker", "kubernetes", "aws", "cloud", "api", "백엔드", "프론트엔드", "서버"),
            Domain.SECURITY, List.of("암호화", "개인정보", "보안", "인증서", "gdpr", "isms", "컴플라이언스"),
            Domain.DESIGN, List.of("시안", "디자인", "색", "컬러", "폰트", "레이아웃", "와이어프레임", "로고"),
            Domain.UX, List.of("반응형", "모바일", "접근성", "사용성", "ae대응", "다국어"),
            Domain.INTEGRATION, List.of("연동", "sso", "외부 시스템", "미리캔버스", "노션", "slack", "이메일 서비스", "카카오", "네이버 소셜"),
            Domain.FEATURES, List.of("회원가입", "로그인", "댓글", "좋아요", "글쓰기", "검색", "업로드",
                    "구독", "팔로우", "게시판", "채팅", "결제"),
            Domain.BUDGET, List.of("예산", "비용", "비용 한도", "천만원", "만원")
    );

    private static final int FEATURES_MIN_TO_EXEMPT = 2; // 기능을 스스로 나열했으면 기능 질문 면제(§28.2.1)

    public List<ExpertSignal> detect(String text) {
        List<ExpertSignal> signals = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return signals;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (Map.Entry<Domain, List<String>> entry : KEYWORDS.entrySet()) {
            int count = 0;
            String firstHit = null;
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                    count++;
                    if (firstHit == null) {
                        firstHit = keyword;
                    }
                }
            }
            if (count > 0) {
                String evidence = count > 1 ? firstHit + " 등 " + count + "개 신호" : firstHit;
                signals.add(new ExpertSignal(entry.getKey(), evidence, count));
            }
        }
        return signals;
    }

    public boolean shouldExemptFeatureQuestion(List<ExpertSignal> signals) {
        return signals.stream()
                .filter(s -> s.domain() == Domain.FEATURES)
                .anyMatch(s -> s.matchCount() >= FEATURES_MIN_TO_EXEMPT);
    }
}
