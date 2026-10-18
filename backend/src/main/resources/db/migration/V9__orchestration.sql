-- TASK-001: Task 의존성(§5.11) — 순환 의존성은 서비스에서 거부
-- 상태 머신 전이 기록은 기존 task_transitions 테이블을 사용한다

create table if not exists task_dependencies (
    created_at timestamp(6) not null,
    id uuid not null,
    task_id uuid not null,
    depends_on_task_id uuid not null,
    dependency_type varchar(20) not null check (dependency_type in ('BLOCKS','RELATES_TO')),
    primary key (id)
);

alter table if exists task_dependencies add constraint fk_task_dependencies_task foreign key (task_id) references tasks;
alter table if exists task_dependencies add constraint fk_task_dependencies_depends_on foreign key (depends_on_task_id) references tasks;

-- ORCH-001/002: 오케스트레이션 사이클 기록(§5.27, §29) — 분해·배정 결정 근거
create table if not exists orchestrator_cycles (
    created_at timestamp(6) not null,
    id uuid not null,
    project_id uuid not null,
    requirement_set_id uuid not null,
    mode varchar(20) not null check (mode in ('FIRST','REPLAN')),
    created_task_count integer not null,
    blocked_task_count integer not null,
    decisions_json TEXT,
    created_by_user_id uuid,
    primary key (id)
);

alter table if exists orchestrator_cycles add constraint fk_orchestrator_cycles_project foreign key (project_id) references projects;
alter table if exists orchestrator_cycles add constraint fk_orchestrator_cycles_requirement_set foreign key (requirement_set_id) references requirement_sets;
