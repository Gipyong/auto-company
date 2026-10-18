package com.autocompany.service;

import com.autocompany.entity.Artifact;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REQ-004 첨부 형식 등급·파싱 단위 테스트(§28.7-3, §23 설정).
 * native(md/txt/csv/json)=UTF-8 자체 파싱 / parser 등급·pdf·기타=원본 보존 unsupported /
 * image=image_pending_vision. 한도(§28.7-5): 기본 10개/50MB.
 */
public class ClarificationAttachmentParserTest {

    private final ClarificationAttachmentParser parser = new ClarificationAttachmentParser();

    @Test
    void nativeMarkdownFileIsParsed() {
        ClarificationAttachmentParser.ParseResult result = parser.parse("회원가입, 로그인", "요구사항.md", "text/markdown");
        assertEquals(Artifact.ParseStatus.PARSED, result.status());
        assertEquals("회원가입, 로그인", result.parsedText());
        assertEquals(ClarificationAttachmentParser.Tier.NATIVE, result.tier());
    }

    @Test
    void pdfBelongsToNativeButIsUnsupportedUntilTextLayerParser() {
        // §28.7-6 MVP: pdf 텍스트 레이어 파서 미구현 → 원본 보존+unsupported
        ClarificationAttachmentParser.ParseResult result = parser.parse("%PDF-1.4 ...", "요구문서.pdf", "application/pdf");
        assertEquals(Artifact.ParseStatus.UNSUPPORTED, result.status());
        assertNull(result.parsedText());
    }

    @Test
    void parserTierFormatsAreUnsupportedInMvp() {
        for (String filename : List.of("요구정리.xlsx", "기획안.pptx", "계약서.docx", "공문.hwp")) {
            ClarificationAttachmentParser.ParseResult result = parser.parse("이진내용", filename, "application/octet-stream");
            assertEquals(Artifact.ParseStatus.UNSUPPORTED, result.status(),
                    filename);
        }
    }

    @Test
    void imagesArePendingVision() {
        ClarificationAttachmentParser.ParseResult png = parser.parse("", "시안.png", "image/png");
        assertEquals(Artifact.ParseStatus.IMAGE_PENDING_VISION, png.status());

        // mediaType이 image/*가 아니어도 확장자로 이미지를 판정한다
        ClarificationAttachmentParser.ParseResult byExt = parser.parse("", "와이어프레임.svg", "application/octet-stream");
        assertEquals(ClarificationAttachmentParser.Tier.IMAGE, byExt.tier());
    }

    @Test
    void unknownFormatIsUnsupported() {
        ClarificationAttachmentParser.ParseResult result = parser.parse("MALLOC MAGIC", "backup.db", "application/x-sqlite3");
        assertEquals(Artifact.ParseStatus.UNSUPPORTED, result.status());
    }

    @Test
    void emptyNativeBodyIsUnsupportedInsteadOfCrash() {
        ClarificationAttachmentParser.ParseResult result = parser.parse("   ", "빈파일.md", "text/markdown");
        assertEquals(Artifact.ParseStatus.UNSUPPORTED, result.status());
        assertTrue(parser.isFollowUpNeeded(result.status().name()));
    }

    @Test
    void limitsUseConfiguredDefaults() {
        assertEquals(10, parser.maxCountPerGoal());
        assertEquals(50L * 1024 * 1024, parser.maxFileSizeBytes()); // §23 기본 10개/50MB

        ReflectionTestUtils.setField(parser, "maxCountPerGoal", 3);
        ReflectionTestUtils.setField(parser, "maxFileSizeMb", 1);
        assertEquals(3, parser.maxCountPerGoal());
        assertEquals(1024L * 1024, parser.maxFileSizeBytes());
    }

    @Test
    void reinforceFollowUpOnlyForUnsupported() {
        assertTrue(parser.isFollowUpNeeded("UNSUPPORTED"));
        assertTrue(!parser.isFollowUpNeeded("PARSED"));
        assertTrue(!parser.isFollowUpNeeded("IMAGE_PENDING_VISION"));
    }
}
