package com.autocompany.service;

import com.autocompany.entity.Artifact;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 첨부 형식 등급 판정·파싱 (§28.7-3, REQ-004).
 * native(md/txt/csv/json)=자체 UTF-8 파싱 / parser 등급·pdf·기타=원본 보존+unsupported /
 * image=vision 모델 배정 전까지 image_pending_vision. MVP: 실제 파싱은 UTF-8 텍스트만.
 */
@Component
public class ClarificationAttachmentParser {

    public enum Tier { NATIVE, PARSER, IMAGE, UNSUPPORTED }

    public record ParseResult(Artifact.ParseStatus status, String parsedText, Tier tier, String note) {
    }

    // §23 기본 한도 10개/50MB — 단위테스트(new 인스턴스)에서도 기본값 유지, Spring은 setter로 주입
    private int maxCountPerGoal = 10;
    private int maxFileSizeMb = 50;

    @Value("${clarification.attachments.max_count_per_goal:10}")
    public void setMaxCountPerGoal(int maxCountPerGoal) {
        this.maxCountPerGoal = maxCountPerGoal;
    }

    @Value("${clarification.attachments.max_file_size_mb:50}")
    public void setMaxFileSizeMb(int maxFileSizeMb) {
        this.maxFileSizeMb = maxFileSizeMb;
    }

    private static final List<String> NATIVE = List.of("md", "txt", "csv", "json");
    private static final List<String> PARSER = List.of("xlsx", "pptx", "docx", "hwp");
    private static final List<String> IMAGE_EXT = List.of("png", "jpg", "jpeg", "gif", "webp", "svg");

    public int maxCountPerGoal() {
        return maxCountPerGoal;
    }

    public long maxFileSizeBytes() {
        return maxFileSizeMb * 1024L * 1024L;
    }

    public Tier classify(String filename, String mediaType) {
        String ext = extOf(filename);
        boolean imageType = mediaType != null && mediaType.toLowerCase(Locale.ROOT).startsWith("image/");
        if (imageType || IMAGE_EXT.contains(ext)) {
            return Tier.IMAGE; // §28.7-3: vision capability 모델 배정 시에만 내용 해석
        }
        if (NATIVE.contains(ext)) {
            return Tier.NATIVE;
        }
        if (PARSER.contains(ext)) {
            return Tier.PARSER; // 형식별 파서 모듈은 이후 단계(DESIGN §28.7-6)
        }
        return Tier.UNSUPPORTED;
    }

    /** native 형식만 실제 파싱한다 — 파싱 불가는 항상 원본 보존(unsupported), 절대 요청 실패로 터지지 않는다 */
    public ParseResult parse(String decodedText, String filename, String mediaType) {
        Tier tier = classify(filename, mediaType);
        return switch (tier) {
            case NATIVE -> decodedText == null || decodedText.isBlank()
                    ? new ParseResult(Artifact.ParseStatus.UNSUPPORTED, null, tier, "본문 비어 있음 — 원본 보존")
                    : new ParseResult(Artifact.ParseStatus.PARSED, decodedText, tier, "UTF-8 텍스트 추출");
            case PARSER -> new ParseResult(Artifact.ParseStatus.UNSUPPORTED, null, tier,
                    "형식별 파서 미구현(DB의 파서 등급) — 텍스트 보강 요청 대상");
            case IMAGE -> new ParseResult(Artifact.ParseStatus.IMAGE_PENDING_VISION, null, tier,
                    "vision 모델 배정 전 — image_pending_vision");
            case UNSUPPORTED -> new ParseResult(Artifact.ParseStatus.UNSUPPORTED, null, tier,
                    "지원하지 않는 형식 — 원본 보존+텍스트 보강 요청");
        };
    }

    public boolean isFollowUpNeeded(String parseStatus) {
        return Artifact.ParseStatus.UNSUPPORTED.name().equals(parseStatus);
    }

    private String extOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
