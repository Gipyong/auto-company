-- REQ-003: RequirementSet (§5.25, §28.6)
-- status DRAFT|FROZEN|SUPERSEDED / frozen 불변, 진화는 새 세션→새 버전(REQ-005)
-- 항목 source는 client_declared|client_answered|client_relayed|employee_derived (content JSON 내부)

create table requirement_sets (
    version integer not null check (version > 0),
    status varchar(255) not null check (status in ('DRAFT','FROZEN','SUPERSEDED')),
    derived_task_ids jsonb not null default '[]'::jsonb,
    frozen_by_employee_ids jsonb not null default '[]'::jsonb,
    organization_id uuid,
    created_at timestamp(6),
    frozen_at timestamp(6),
    session_id uuid not null,
    content_json jsonb,
    id uuid not null,
    project_id uuid not null,
    primary key (id),
    constraint uk_requirement_set_project_version unique (project_id, version)
);

alter table if exists requirement_sets add constraint fk_requirement_sets_project foreign key (project_id) references projects;
alter table if exists requirement_sets add constraint fk_requirement_sets_session foreign key (session_id) references clarification_sessions;
