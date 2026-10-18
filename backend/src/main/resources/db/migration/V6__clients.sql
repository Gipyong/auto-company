-- ORG-003: Client(외부 고객) 등록부 + 중계 연결(§5.26)
-- 조직 스코프는 organization_id UUID(조직 테이블 없는 MVP — 직급·모델과 동일 패턴)
-- MVP에서 외부 고객은 로그인 없음: 운영자가 등록·중계, 원문 보존+client_relayed 기록

create table if not exists clients (
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    id uuid not null,
    organization_id uuid not null,
    created_by_user_id uuid,
    status varchar(20) not null check (status in ('ACTIVE','ARCHIVED')),
    display_name varchar(200) not null,
    contact varchar(500),
    notes TEXT,
    primary key (id)
);

alter table if exists projects add column client_id uuid;
alter table if exists projects add constraint fk_projects_client foreign key (client_id) references clients;
alter table if exists clarification_sessions add constraint fk_clarification_sessions_client foreign key (client_id) references clients;
