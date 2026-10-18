-- TEAM-001: 팀(부서) 도메인(§5.3) + 직원 팀 배치
-- 조직 스코프는 organization_id UUID(조직 테이블 없는 MVP — 직급·모델·고객과 동일 패턴)
-- 팀 아카이브는 소속 직원 0명일 때만 허용(409) — 직급 삭제와 동일 원칙
-- 직원 배치는 employees.team_id(nullable, null=미배정)

create table if not exists teams (
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    id uuid not null,
    organization_id uuid not null,
    name varchar(200) not null,
    description TEXT,
    status varchar(20) not null check (status in ('ACTIVE','ARCHIVED')),
    primary key (id)
);

alter table if exists employees add column team_id uuid;
alter table if exists employees add constraint fk_employees_team foreign key (team_id) references teams;
