-- REQ-002: ClarificationQuestion (§5.24, §28.3)
-- question_type: single_choice|multi_choice|short_text|free_text|number_range
-- answer_status: unanswered|answered|skipped|expired  / skipped은 assumption 기록 시에만(§5.24)

create table clarification_questions (
    display_order integer not null check (display_order > 0),
    question_type varchar(255) not null check (question_type in ('SINGLE_CHOICE','MULTI_CHOICE','SHORT_TEXT','FREE_TEXT','NUMBER_RANGE')),
    answer_status varchar(255) not null check (answer_status in ('UNANSWERED','ANSWERED','SKIPPED','EXPIRED')),
    allow_other boolean not null,
    options jsonb not null default '[]'::jsonb,
    reviewed_by_employee_ids jsonb not null default '[]'::jsonb,
    selected_values jsonb,
    numeric_answer numeric(38,2),
    session_id uuid not null,
    created_by_employee_id uuid not null,
    id uuid not null,
    prompt TEXT not null,
    other_text TEXT,
    assumption_if_skipped TEXT,
    primary key (id),
    constraint uk_clarification_question_order unique (session_id, display_order)
);

alter table if exists clarification_questions add constraint fk_clarification_questions_session foreign key (session_id) references clarification_sessions;
