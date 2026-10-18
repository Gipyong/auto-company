-- EMP-001: 직원 커스텀 지시(§5.4 custom_instructions, §8.2 컨텍스트 (3) 계층)
-- 사용자(운영자)가 작성하는 개별 지시 — 자유 서식, 최대 4000자(§26 custom_instructions_max_chars)
-- 시스템 안전 정책·도구 제약이 항상 우선하며 이 필드로 덮을 수 없다

alter table if exists employees add column custom_instructions TEXT;
