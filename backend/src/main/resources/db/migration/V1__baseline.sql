-- AutoCompany baseline schema (PLAT-004)
-- Hibernate 매핑 기준으로 생성한 베이스라인 마이그레이션.
-- enum 열은 CHECK 제약으로 고정된다. 이후 스키마 변경은 V2부터 마이그레이션으로 반영한다.

create table users (is_premium boolean not null, created_at timestamp(6), id uuid not null, company_name varchar(255), default_model varchar(255), email varchar(255) not null unique, name varchar(255) not null, primary key (id));

create table employees (avg_rating float4 not null, completed_tasks integer not null, total_tasks integer not null, created_at timestamp(6), grade_id uuid, id uuid not null, user_id uuid not null, docker_container_id varchar(255), name varchar(255) not null, role varchar(255) not null check (role in ('PLANNER','DEVELOPER','DESIGNER','PROJECT_MANAGER','TECHNICAL_WRITER','QA_ENGINEER','ARCHITECT','SECURITY_DEVOPS')), status varchar(255) not null check (status in ('IDLE','WORKING','DISCUSSING','TESTING','OFFLINE')), system_prompt varchar(255), primary key (id));
create table employee_ratings (rating float4, employee_id uuid not null);
create table employee_feedback (employee_id uuid not null, feedback_comments varchar(255));

create table model_providers (enabled boolean not null, created_at timestamp(6) not null, updated_at timestamp(6) not null, id uuid not null, organization_id uuid not null, health_status varchar(20) not null check (health_status in ('UNKNOWN','HEALTHY','DEGRADED','UNAVAILABLE')), provider_type varchar(30) not null check (provider_type in ('OPENAI_COMPATIBLE','ANTHROPIC','OLLAMA','CUSTOM')), name varchar(100) not null, secret_ref varchar(200), base_url varchar(500), rate_limit_policy jsonb, primary key (id));

create table model_profiles (context_window integer, enabled boolean not null, input_cost_per_million numeric(19,6), local_only boolean not null, max_output_tokens integer, output_cost_per_million numeric(19,6), quality_tier integer, supports_json_schema boolean not null, supports_streaming boolean not null, supports_tool_calling boolean not null, created_at timestamp(6) not null, updated_at timestamp(6) not null, id uuid not null, provider_id uuid not null, latency_class varchar(20) check (latency_class in ('LOW','MEDIUM','HIGH','UNKNOWN')), name varchar(100) not null, provider_model_id varchar(100) not null, capabilities jsonb, metadata jsonb, primary key (id));

create table job_grades (is_template_default boolean not null, ordinal integer not null, created_at timestamp(6) not null, updated_at timestamp(6) not null, id uuid not null, organization_id uuid not null, status varchar(20) not null check (status in ('ACTIVE','ARCHIVED')), model_selection_mode varchar(30) not null check (model_selection_mode in ('PRIORITY','TASK_BASED','BUDGET_OPTIMIZED')), code varchar(50) not null, name varchar(100) not null, responsibilities TEXT, task_types jsonb, primary key (id), constraint uk_grade_ordinal_org unique (organization_id, ordinal));

create table grade_models (enabled boolean not null, priority integer not null, created_at timestamp(6) not null, updated_at timestamp(6) not null, grade_id uuid not null, id uuid not null, model_profile_id uuid not null, primary key (id), constraint uk_grade_model unique (grade_id, model_profile_id));

create table projects (created_at timestamp(6), id uuid not null, user_id uuid not null, description TEXT, name varchar(255) not null, status varchar(255), primary key (id));
create table project_employees (employees uuid, project_id uuid not null);

create table tasks (attempt_count integer not null, complexity integer not null, escalation_count integer not null, estimated_cost float4 not null, estimated_effort integer, max_attempts integer not null, max_cost numeric(38,2), success boolean not null, total_cost float4 not null, completed_at timestamp(6), created_at timestamp(6), due_at timestamp(6), assignee_id uuid, created_by_employee_id uuid, created_by_user_id uuid, id uuid not null, parent_task_id uuid, project_id uuid not null, blocked_reason varchar(255), description TEXT, priority varchar(255) not null check (priority in ('CRITICAL','HIGH','NORMAL','LOW')), result_summary TEXT, risk_level varchar(255) not null check (risk_level in ('LOW','MEDIUM','HIGH','CRITICAL')), status varchar(255) not null check (status in ('DRAFT','READY','ASSIGNED','IN_PROGRESS','REVIEW','BLOCKED','WAITING_FOR_QUOTA','DONE','FAILED','CANCELLED')), title varchar(255) not null, type varchar(255) not null check (type in ('EPIC','STORY','TASK','BUG','SPIKE','DOCUMENTATION','REVIEW','TEST')), acceptance_criteria jsonb, output_artifact_ids jsonb, related_commit_shas jsonb, reviewer_employee_ids jsonb, primary key (id));

create table task_transitions (created_at timestamp(6), id uuid not null, task_id uuid not null, triggered_by_employee_id uuid, triggered_by_user_id uuid, from_status varchar(255) not null check (from_status in ('DRAFT','READY','ASSIGNED','IN_PROGRESS','REVIEW','BLOCKED','WAITING_FOR_QUOTA','DONE','FAILED','CANCELLED')), metadata_json TEXT, reason TEXT, to_status varchar(255) not null check (to_status in ('DRAFT','READY','ASSIGNED','IN_PROGRESS','REVIEW','BLOCKED','WAITING_FOR_QUOTA','DONE','FAILED','CANCELLED')), primary key (id));

create table peer_ratings (context_weight float4, score float4 not null, archived_at timestamp(6), created_at timestamp(6), id uuid not null, rated_employee_id uuid not null, rater_employee_id uuid not null, task_id uuid not null, category varchar(255) not null check (category in ('CODE_QUALITY','LOGIC','COOPERATION','DOC_COMPLETENESS')), comment TEXT, feedback_summary TEXT, evidence_refs jsonb, primary key (id));

create table discussion_messages (created_at timestamp(6), id uuid not null, project_id uuid not null, sender_employee_id uuid not null, target_employee_id uuid, content varchar(255), message_type varchar(255) check (message_type in ('DISCUSSION','FEEDBACK','CODE_PROPOSAL','INTERVIEW_Q','INTERVIEW_A')), primary key (id));

create table knowledge_documents (created_at timestamp(6), updated_at timestamp(6), author_employee_id uuid, id uuid not null, project_id uuid not null, content TEXT, embedding_blob TEXT, source_file_path varchar(255), title TEXT, primary key (id));

alter table if exists employees add constraint fk_employees_user foreign key (user_id) references users;
alter table if exists employee_feedback add constraint fk_employee_feedback_employee foreign key (employee_id) references employees;
alter table if exists employee_ratings add constraint fk_employee_ratings_employee foreign key (employee_id) references employees;
alter table if exists projects add constraint fk_projects_user foreign key (user_id) references users;
alter table if exists project_employees add constraint fk_project_employees_project foreign key (project_id) references projects;
alter table if exists model_profiles add constraint fk_model_profiles_provider foreign key (provider_id) references model_providers;
alter table if exists tasks add constraint fk_tasks_project foreign key (project_id) references projects;
alter table if exists tasks add constraint fk_tasks_assignee foreign key (assignee_id) references employees;
alter table if exists tasks add constraint fk_tasks_parent_task foreign key (parent_task_id) references tasks;
alter table if exists task_transitions add constraint fk_task_transitions_task foreign key (task_id) references tasks;
alter table if exists peer_ratings add constraint fk_peer_ratings_task foreign key (task_id) references tasks;
alter table if exists discussion_messages add constraint fk_discussion_messages_project foreign key (project_id) references projects;
alter table if exists knowledge_documents add constraint fk_knowledge_documents_project foreign key (project_id) references projects;
