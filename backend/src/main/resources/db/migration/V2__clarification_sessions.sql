-- REQ-001: ClarificationSession (§5.23, §28)
-- 라이프사이클 OPEN → WAITING_CLIENT → REVIEW → FROZEN / CANCELLED (§28.5)
-- 동결 이후 수정은 409 (불변성, REQ-003에서 앱 레벨 전이 규칙으로 강제)

create table clarification_sessions (
    status varchar(255) not null check (status in ('OPEN','WAITING_CLIENT','REVIEW','FROZEN','CANCELLED')),
    session_number integer not null check (session_number > 0),
    max_questions integer not null check (max_questions > 0),
    client_notice_read boolean not null,
    attachments jsonb not null default '[]'::jsonb,
    created_at timestamp(6),
    closed_at timestamp(6),
    frozen_at timestamp(6),
    parent_session_id uuid,
    client_id uuid,
    opened_by_employee_id uuid,
    opened_by_user_id uuid,
    id uuid not null,
    project_id uuid not null,
    client_extra_note TEXT,
    goal_text TEXT not null,
    primary key (id),
    constraint uk_clarification_session_project_number unique (project_id, session_number)
);

alter table if exists clarification_sessions add constraint fk_clarification_sessions_project foreign key (project_id) references projects;
alter table if exists clarification_sessions add constraint fk_clarification_sessions_parent_session foreign key (parent_session_id) references clarification_sessions;
