-- REQ-004: Artifact (§5.19 MVP) + 골 첨부 흐름(§28.7)
-- MVP 저장: 파싱 결과 parsed_text 인라인, 원본 storage_blob(bytea), storage_uri = artifact://{id} 논리 URI
-- 첨부는 정적 저장만(실행 금지 §28.7-5); parse_status: PARSED|UNSUPPORTED|IMAGE_PENDING_VISION

alter table clarification_sessions alter column goal_text drop not null; -- §28.7(1) 첨부 단독 접수 허용

create table artifacts (
    created_at timestamp(6),
    created_by_employee_id uuid,
    size_bytes bigint not null,
    task_id uuid,
    artifact_type varchar(255) not null check (artifact_type in ('SOURCE_DIFF','TEST_REPORT','BUILD','LOG','DOCUMENT','IMAGE','OTHER')),
    storage_uri varchar(500) not null default 'artifact://pending',
    content_hash varchar(255) not null,
    media_type varchar(100) not null,
    parse_status varchar(255) not null check (parse_status in ('PARSED','UNSUPPORTED','IMAGE_PENDING_VISION')),
    parse_evidence jsonb not null default '[]'::jsonb,
    retention_policy varchar(64),
    parsed_text text,
    id uuid not null,
    storage_blob bytea,
    project_id uuid not null,
    primary key (id)
);

alter table if exists artifacts add constraint fk_artifacts_project foreign key (project_id) references projects;
