# AI SI Company Platform — DESIGN.md

> 버전: 1.9 (자율 오케스트레이션 Mock — 동결 요구 분해·task_types 배정·재계획 한도(ORCH-001/002), Task 상태 머신·의존성(TASK-001), 사용자 입력 표면 정리(WEB-004) — 지시는 조직 전체(담당 지정 UI 없음)·평가는 직원 간(제출 UI 없음)·커스텀 지시 우선순위 명시)  
> 상태: 구현 기준 설계 — Java 21 / Spring Boot 3.x(안정 버전) + Maven 백엔드 기준  
> 목표: 로컬 우선으로 실행 가능하고, 다른 PC·서버로 재현/배포 가능한 AI 기반 소프트웨어 개발 조직 플랫폼  
> 위치 핵심: 사용자는 하나의 주체다 — **운영자**로서 팀·직원·직급을 구성하고, **고객**으로서 지시 창구에서 요구사항을 전달한다. 콘솔은 "CEO"·"외부 고객" 같은 포지션 라벨을 표시하지 않는다. 외부 고객 중계(§5.26)는 구현·테스트를 보존하고 콘솔에서는 휴면. 시스템은 사용자를 전문가·비전문가로 단정하지 않고 전문성 신호에 따라 조응한다(§0, §28). 요구사항 이외의 기술 결정은 기본적으로 AI 직원 내부 협의가 처리한다(§28, §29). 사용자는 **업무 담당을 지정하지 않는다** — 지시는 조직 전체에 대한 것이고, 분해·배정은 조직(Orchestrator §29)이 수행한다. **평가도 사용자가 아니라 직원들끼리** 한다(§15). 사용자의 직접 입력 수단은 지시 창구와 직원 커스텀 지시(§5.4)다.

변경 이력:
- v1.9: 자율 오케스트레이션 1단계(ORCH-001/002 — §5.27·§29): 동결 RequirementSet→결정적 Mock 분해(Epic/Story/Task+TEST 의존성)→task_types 자동 배정·BLOCKED 사유→재계획 한도(2)·중복 트리거 409, OrchestratorCycle 기록(V9), 콘솔 [분해·배정 실행]+오케스트레이션 카드. Task 상태 머신+TaskDependency(TASK-001 — §7.1·§5.11, 무효 전이 409·전이 기록·순환 거부). 사용자 입력 표면 정리(WEB-004 — 업무 추가(담당 지정)·평가 제출 UI 제거; 평가는 직원 간, 지시는 조직 전체; 커스텀 지시 우선순위 명시 §8.2). 팀은 선택 사항(§5.3 — 미구성 운영 가능).
- v1.8: 콘솔 정보구조 재편(WEB-003) — 단일 조직 자동 구성(시작 탭 제거), 좌측 사이드바 탭(지시·직원·직급·모델), 지시 탭 통합(프롬프트 창구 [묻기]/[지시하기]+프로젝트 목록·상세), 목록 우선 UX(빈 상태 중앙 CTA), 'CEO'·'외부 고객' 표기 제거·'골'은 내부 도메인 용어로 유지(콘솔 표기는 프로젝트/지시), 메시지·CEO Ask UI 제거. 팀(부서) 도메인(TEAM-001 — §5.3, 직원 팀 배치). 외부 고객 중계 UI 휴면(§5.26 구현 보존).
- v1.7: 웹 콘솔을 SPA(React+TypeScript+Vite, `frontend/`)로 분리; nginx `front` 컨테이너가 정적 서빙+`/api` 프록시(콘솔 `http://localhost:3000`); 백엔드는 JSON API 전용(정적 콘솔 제거, WEB-002). Dockerfile을 서비스 디렉터리별 배치(`backend/Dockerfile`·`frontend/Dockerfile`), 루트는 `docker-compose.yml`만.
- v1.6.1: 전체 재검토 정정 — 세션 취소 규칙(고객 포지션, 파이프라인 개설 포함), 파이프라인 자동 세션 상한 범위 명시, 수신함 엔드포인트 `GET /users/{userId}/pending-sessions` 정리, 세션 `client_id`=Project 상속 규칙, User.role↔포지션 구분, 포인터·공백·문법 정정.
- v1.6: 사용자 다중 포지션(운영자·발주 고객·기술 조언자·전문가·중계자), 외부 고객(Client) 중계, **사용자 개설 요구사항 세션**(§28.6), 골 다중 모달 접수(파일·이미지 첨부 §28.7), 직원 커스텀 지시(custom_instructions) 설계. 형식 오류 정정.
- v1.5: 전체 압축 정리. 규범적 내용(엔티티·API·프로토콜·결정)은 유지하고 중복 서술을 제거했다.
- v1.4: 사용자=외주 고객 포지션 확정. 요구사항 명확화 파이프라인(§28)과 자율 오케스트레이션(§29) 신설. 고객 자발적 전문 요구사항 수용 설계 포함.
- v1.3: 승진/강등 제거, 피드백 전용 평가 시스템(§15) 도입. 피드백 컨텍스트 10KB/직원 호환 유지.

---

## 0. 설계 원칙

1. **Local-first**: 기본 실행은 로컬 PC/자체 서버. 환경 차이는 컨테이너와 명시적 설정으로 흡수한다.
2. **Durable workflow state**: AI 대화가 아니라 상태를 영속화한다. 업무 상태·승인·실행·비용·결과를 모두 기록한다.
3. **Models are replaceable providers**: 직원은 개별 모델이 아니라 직급(JobGrade)을 가진다. 모델 교체 시에도 직원·직급·업무 이력이 유지된다.
4. **Evidence over consensus**: AI 다수의 동의보다 테스트·정적 분석·재현 절차 우선.
5. **Separation of duties**: 작성자 본인의 PR 승인·질문 자체 승인 금지. 위험도에 비례해 독립 검증 요구.
6. **Bounded autonomy**: 권한·예산·재시도·실행 시간·네트워크 범위 내 자율성.
7. **Cost is first-class**: 모델 호출뿐 아니라 컨테이너·재시도·재작업·검증 비용 기록.
8. **Human-readable artifacts**: 주요 결정은 ADR·이슈·PR·테스트·문서 버전과 연결.
9. **Reproducibility**: 설정·프롬프트·도구 버전·모델 식별자·커밋·테스트 환경 기록.
10. **Safe defaults**: 설치 직후 작동하는 기본 템플릿 제공. 위험 권한·자동 배포는 기본 비활성.
11. **User is one person, two positions**: 사용자는 하나의 주체다 — 운영자 포지션(팀·직원·직급·모델 풀·직원 지시 등 조직 구성 전 권한)과 고객 포지션(지시 창구에서 요구 전달·명확화 답변·인수)을 오간다. 콘솔은 포지션 라벨("CEO"·"외부 고객")을 표시하지 않는다. 외부 고객 중계(§5.26)는 구현·테스트를 보존하고 콘솔에서는 휴면한다. 시스템은 전문성을 고정 단정하지 않는다 — 신호 기반 적응(§28.2): 전문 요구는 `client_declared` 제약으로 수용, 신호 없으면 기술 사항은 묻지 않고 내부 결정. **사용자는 업무 담당을 지정하지 않는다**(지시는 조직 전체 대상 — 분해·배정은 Orchestrator §29; 콘솔에 담당 지정 UI 없음) **평가를 제출하지 않는다**(평가는 직원 간 자율·회의 프로토콜 §15; 사용자 입력은 직원 커스텀 지시로 충분).
12. **Requirements are frozen records**: 동결된 요구사항 기록은 불변. 진화는 새 세션·새 버전으로만(§28.6). 이후 작업 분해는 동결 기록을 근거로 참조한다.

주요 용어:
- `Employee` 가상 직원 정체성(직무·직급 참조·권한·평가 이력) / `JobGrade` 사용자 구성 직급(순서·책임·업무 유형·모델 풀) / `AgentRun` 일회성·재개 가능 실행 / `Task` 업무와 완료 조건 / `WorkflowRun` 복수 Task+승인 묶음 / `ClarificationSession` 요구사항 질문 세트+고객 응답 단위 / `RequirementSet` 동결된 요구사항 기록 / `OrchestratorCycle` 골→분해→배정→실행 사이클 기록 / `ModelProfile` 모델 스펙·가격·한도 / `GradeModel` 직급별 가용 모델+우선순위 / `Workspace` 격리 개발 실행환경 / `Artifact` 검증 가능 산출물

직원은 영속 캐릭터가 아니라 정책과 상태를 가진 도메인 엔티티다. 실행은 매번 AgentRun으로 기록되며, 중단 후 복구 가능해야 한다.

---

## 1. 제품 범위

**MVP 포함**: 로컬 웹 콘솔(단일 조직·단일 사용자) / 팀(부서)·직원·직급·모델 관리(운영자 포지션) / **프로젝트 지시 접수 — 프롬프트+파일·이미지 첨부(§28.7; 도메인 용어 골=지시 원문)** / **요구사항 명확화 파이프라인(협의→구조화 질문→응답→동결, §28)** / **자율 오케스트레이션(분해·자동 배정, §29)** / Git 저장소 연결 / 모델 호출·도구 실행(초기 Mock 모델) / 격리 Docker workspace / 브랜치·PR·리뷰·테스트 게이트 / 문서 관리와 질의응답 / 비용·실행 로그 / 재시도·대기·복구 / 감사 로그.

**MVP 이후**: 직무별 동료 평가 관측 / 정교한 회의 프로토콜 / 다중 프로젝트 스케줄링 / VM 실행기 / webhook 동기화 / 운영 모니터링 / 분산 worker / 성과 기반 모델·조직 최적화.

**의도적으로 하지 않는 것**: 무제한 자율 실행 / AI 다수결 승인 / 기본 프로덕션 배포 / Docker를 보존 경계로 간주 / 벤더 SDK 종속 / **고객에게 기술 선택 질문 전가** / **알림 푸시 폭탄(웹은 접속 시 확인 기본; §28.5)** / "직급↑=무조건 좋은 모델" 강제 / 직급 자동 승진·강등 / 공격 규모의 장시간 다자 토론.

---

## 2. 배포 모델

Docker Compose 기반 로컬 설치. 구성 대상: `front` SPA 콘솔(nginx, 호스트 3000→80; `/api`를 `web`으로 프록시), `web` API(+초기 worker 동일 프로세스), `postgres`, (안정화 후) `worker` 분리, (선택) `redis`, `workspace-runner`, 로컬 모델 서버. 코드 실행 권한을 가진 workspace runner는 API와 동일 권한/컨테이너에 넣지 않는다. Dockerfile은 서비스 디렉터리에 둔다(`backend/Dockerfile`, `frontend/Dockerfile`) — 루트는 `docker-compose.yml`+`.env.example`만.

설치 흐름: clone → `.env` 생성 → `docker compose up -d` → 콘솔 접속(`http://localhost:3000`; API 직접은 `:8080`) → **첫 접속 시 고정 단일 조직+기본 템플릿(직급 5·직원 8) 자동 구성(시작 탭 없음; §6.2)** → 팀·직원 구성(자유) → 모델 제공자 설정/로컬 모델 선택 → Git 저장소 등록 → preflight 사전 점검 → 지시 창구에서 첫 요구 제출.

- API 키는 UI에 평문 재표시 금지. `.env`/비밀 파일은 커밋 금지.

포터빌리티: OS 차이는 Docker+host capability로 처리 / named volume·호스트 디렉터 저장 / 백업·복구 명령 제공 / `config.yaml`+환경변수로 설정 교체 / 모델·Git·workspace runner는 어댑터 뒤 배치 / CPU·RAM·디스크 최소 요구량 preflight 통지 / 필요 OS executor 부재 시 `BLOCKED_CAPABILITY`.

---

## 3. 상위 아키텍처

```text
┌─────────────────────────────────────────────────────────────┐
│               Console (단일 사용자 — 팀·직원 구성+요구 제출, §0 원칙 11)            │
│ 지시 창구(프롬프트 [묻기]/[지시하기]) · 명확화 세션(질문/응답·개설·진화)   │
│ Dashboard · Org(Teams) · Employees · Projects · Costs · Audit       │
└──────────────────────────────┬──────────────────────────────┘
                               │ REST / SSE
┌──────────────────────────────▼──────────────────────────────┐
│                         API Layer                            │
│ Auth · Validation · RBAC · Domain Services · Audit           │
└───────────────┬─────────────────────────────┬────────────────┘
                │                             │
┌───────────────▼───────────────┐ ┌───────────▼────────────────┐
│ Orchestration / Scheduler     │ │ Read Models / Query        │
│ Goal intake → 명확화 → 분해   │ │ Dashboard · Search · RAG   │
│ Task state machine            │ │ Documents · Project status │
│ Retry · Pause · Resume        │ └────────────────────────────┘
│ 자동 배정 · 재계획 · 에스컬레이션 │
└───────────────┬───────────────┘
                │
┌───────────────▼──────────────────────────────────────────────┐
│                Requirement Clarification Engine                │
│ 협의 회의 개설 · 질문 구성(최소 질문) · 역할 검토 체인 · 세션 큐   │
│ 동결(Freeze) · 요구사항 기록 (§28)                             │
└───────────────┬──────────────────────────────────────────────┘
                │
┌───────────────▼──────────────────────────────────────────────┐
│                     Agent Runtime                            │
│ Context builder · Model router · Tool broker · Memory        │
│ Token/cost budget · Structured output · Run tracing          │
└───────────────┬───────────────────────────────┬──────────────┘
                │                               │
┌───────────────▼───────────────┐ ┌─────────────▼──────────────┐
│ Model Adapter Registry        │ │ Tool / Workspace Broker    │
│ Hosted/OpenAI-compatible/Local│ │ Git · Shell · Tests · Files │
│ Mock provider                 │ │ VM later                    │
└───────────────────────────────┘ └─────────────┬──────────────┘
                                                │
                                  ┌─────────────▼──────────────┐
                                  │ Isolated Workspace Runner  │
                                  │ Docker · 한도 · per-task worktree │
                                  └─────────────────────────────┘

공유 기반: PostgreSQL · Redis · Artifact storage · Audit log
```

논리 모듈(§3) — 모놀리식 한 코드베이스에서 패키지 경계로 구현, 마이크로서비스 분리는 초기에 하지 않는다:
1. Organization(팀·직원·직급·모델 풀·권한) 2. Project(설정·저장소·환경) 3. **Goal & Clarification(§28; 다중 모달 접수·사용자 개설 세션 포함)** 4. **Orchestration(§29)** 5. Task(의존성·완료 조건) 6. Workflow Engine(전이·재시도·타임아웃) 7. Agent Runtime 8. Model Router 9. Tool Broker 10. Workspace Manager 11. Git Integration 12. Quality Gate 13. Knowledge & Docs 14. Evaluation 15. Budget & Usage 16. Audit & Observability

---

## 4. 기술 스택

- Frontend: React+TypeScript+Vite SPA 콘솔(`frontend/`, WEB-002·WEB-003 — 좌측 사이드바 IA). 장기 고객 대시보드(Next.js, UI-001)는 별도 분리 대상
- 백엔드: Java 21 LTS + Spring Boot 3.x(지원 안정 버전, 버전 고정) / **빌드: Maven**(저장소 기준, `backend/pom.xml`)
- API 계약: Spring MVC 기본, 필요 시 WebFlux / Bean Validation / OpenAPI+계약 테스트
- 보안: Spring Security·명시적 RBAC·CSRF/CORS·비밀 외부화
- DB 접근/마이그레이션: Spring Data JPA + Hibernate, **Flyway**(PLAT-004; enum CHECK 제약은 ddl-auto=update가 갱신하지 않으므로 반드시 필요)
- Database: PostgreSQL. 큐/캐시 초기엔 PostgreSQL durable job/lease; Redis는 실제 필요 시에만, 상태 원본 금지
- Workflow: DB 상태 머신 + worker lease + transactional outbox. 분산 복구 요구 검증 후 Temporal 검토
- Agent execution: 자체 얇은 런타임 + provider adapter. 프레임워크 agent object를 도메인 엔티티로 노출 금지
- Git: ProcessBuilder + 인자 배열(shell 문자열 결합 금지), 실행 시간·출력·환경 제한
- 검색: PostgreSQL full-text; 필요 시 pgvector
- 관측: Actuator, Micrometer, OpenTelemetry, 구조화 로그, correlation/trace ID
- 테스트: JUnit 5, AssertJ, Mockito, Spring Boot Test, Testcontainers, 계약/E2E
- 패키징: Compose, multi-stage 이미지, `.env.example`, 백업 스크립트
- 시간은 UTC 저장·UI 로컬 표시. 금액은 Decimal/정수 단위. JSON 필드는 검색·제약·관계의 대체재가 아니다. SDK 타입을 DB 스키마/도메인에 직접 결합 금지. 외부 호출은 timeout·retry·idempotency key 필수.

Java 구조 규칙: 모듈형 모놀리스(api/application/domain/infrastructure 방향) — 도메인은 Spring/JPA/SDK 미의존. JPA 엔티티를 API 응답으로 반환 금지(DTO 분리, lazy 노출 차단). 트랜잭션은 유스케이스 경계; 외부 모델/Git/Docker 호출은 DB 트랜잭션 안에서 금지. 장기 작업은 worker가 수행; reservation 동시성은 조건부 UPDATE/잠금으로 원자화. 프로퍼티는 `@ConfigurationProperties`+Bean Validation. 비밀 로그 금지. SDK는 infrastructure 어댑터에 격리하고 도메인은 `ModelAdapter` 계약만 본다.

---

## 5. 도메인 모델

공통: 모든 주요 엔티티는 UUID id, created_at, updated_at. 감사 필요 엔티티는 soft-delete/상태 변경. 조직·프로젝트 경계를 모든 조회·변경에서 검증.

### 5.1 Organization
`id, name, slug(unique), description?, settings_json(조직 기본 정책), status: active|archived`.

**구현 노트(MVP 콘솔)**: 조직은 하나·불변이다 — 콘솔은 고정 조직 ID(상수)로 동작하고 조직 생성·전환 UI가 없다(WEB-003, 시작 탭 제거). API는 `organizationId` 파라미터를 유지한다(스코프 호환). 첫 접속 시 기본 템플릿을 자동 import한다(§6.2; 이미 구성돼 있으면 409 무시).

### 5.2 User
`id, email(unique), display_name, password_hash?, auth_provider: local|oidc|none, role: owner|admin|operator|viewer, is_active, last_login_at?`. 로컬 MVP는 고정 단일 사용자(콘솔 부트스트랩 §5.1 — 생성 마법사 없음; 인증 도입 시 확장). 기본 비밀번호 소스/문서 금지. `role`은 시스템 권한이며, 운영자·고객 등 포지션(§0 원칙 11)은 이 role이 아니라 업무 문맥이다 — 콘솔에서는 사용자 한 명이 두 포지션을 오간다.

### 5.3 Team
`id, organization_id, name, description?, team_lead_employee_id?, status: active|archived, created_at, updated_at`.

**구현 노트(TEAM-001)**: 구현 필드는 `id·organization_id·name·description?·team_lead_employee_id?(직원 UUID, 팀장 역할 — 직급과 무관)·status(ACTIVE|ARCHIVED)·timestamps`. API `GET/POST /teams?organizationId=`·`GET/PATCH /teams/{id}`(이름·설명·아카이브·팀 리드 `team_lead_employee_id`). 직원 배치는 `employees.team_id`(V8, nullable FK) — 미배정=null. 팀 아카이브는 소속 직원이 있으면 409(직급 삭제와 동일 원칙) — 직원을 옮기거나 미배정으로 바꾼 뒤 가능. 팀은 단일 계층(플랫)이며 계층 구조는 후속. 조직은 하나(§5.1)이며 사용자가 팀을 자유롭게 나눈다 — 콘솔 직원 탭에서 팀 생성·이름변경·아카이브·팀 리드 지정·필터·배치를 제공한다(WEB-003). **팀은 선택 사항이다** — 소규모 운영은 팀 없이(미배정) 가능하고, 규모·목적에 따라 프로젝트별 또는 역할별(개발팀/QA팀 등)로 구성하는 것은 사용자 자유다.

**팀 운영 모델(v1.9 구현)**: ① 팀을 만들지 않으면 전원이 하나의 **기본 팀**으로 동작한다(가상 — `team_id` null 묶음, 별도 행 없이 "전체" 의미). ② 피드백: 팀 내=직원 상호 간(§15 기존 경로), 팀 간=**팀 단위 피드백** — 각 팀의 팀장이 상대 팀에 제출하고 대상은 개인이 아니라 **팀 전체**다(팀 컨텍스트로 주입, 개인 평가로 분해하지 않음). ③ 회의: 같은 구조 — 팀 내 회의(팀원), 팀 간 논의·충돌은 팀장 회의(§13). ④ 팀의 실질 이점: 배정 시 팀 내 우선(컨텍스트 재사용·교차 조정 비용 절감, §29.3), 팀 단위 피드백·진행 요약이 팀 컨텍스트를 강화. ⑤ 팀장은 **역할**이며 직급이 아니다(`team_lead_employee_id`; 직급 자동 변경 금지 원칙과 무관) — 미지정 팀은 최고 직급(동률 이름순)이 임시 대표로 참여하고 기록에 남긴다. 팀 겸임(복수 팀 소속)은 MVP에서 금지(단일 소속 유지). ⑥ 팀 리드 지정/해제 API: `PATCH /teams/{id}`에 `teamLeadEmployeeId`(UUID|null) — 팀원 여부 검증, 본인 팀 소속만 가능.

### 5.4 Employee
`id, organization_id, team_id?, employee_code(unique), display_name, job_family, specialties, grade_id(JobGrade 참조), employment_status: active|paused|on_leave|retired, availability_status: available|working|waiting|rate_limited|blocked, system_profile(직무 원칙, 민감정보 금지), custom_instructions(text?, 사용자가 작성한 개별 지시 — 직원 구성·수정 시 부여; AGENTS.md류 자유 서식), tool_policy_id, memory_policy, manager_employee_id?, created_by_user_id, retired_at?`.

**구현 노트**: 구현 필드는 `name·role·gradeId·systemPrompt·customInstructions`(V7, EMP-001)·`teamId`(V8, TEAM-001; nullable, 미배정=null). `custom_instructions`는 사용자가 작성하는 개별 지시(자유 서식, AGENTS.md류)로 최대 4000자(설정 `custom_instructions_max_chars` §26) — 초과 시 400. 직원 생성(`POST /employees`)·부분 갱신(`PATCH /employees/{id}` — `teamId` 키 제시 시 설정, null이면 미배정 해제)·지시 편집과 웹 콘솔 직원 상세에서 설정한다. 일부 직무 정보(`organization_id·job_family·availability_status` 등)는 MVP에서 Role·status로 대체. 시스템 안전 정책·도구 제약이 항상 우선 — custom instructions로 안전 정책을 덮을 수 없다(§8.2). 현재 직무 집합은 `Role` 열거형 `{ PLANNER, DEVELOPER, DESIGNER, PROJECT_MANAGER, TECHNICAL_WRITER, QA_ENGINEER, ARCHITECT, SECURITY_DEVOPS }`. 위 job_family는 확장 목록; 충돌 시 구현 값 기준(§28.4 체인도 Role 값으로 구성).

**중요**: 직원은 개별 모델·모델 정책을 갖지 않는다. 직급 하나를 가지며 가용 모델은 직급의 풀(§5.9)로 결정. 직급 부여·변경은 사용자 수동(§15) — 자동 승진/강등 없음.

### 5.5 EmployeeSkill
`id, employee_id, skill_name, proficiency 1..5, confidence, evidence_refs(산출 참조), last_assessed_at`. 자기 선언(초기, 낮은 confidence)과 산출물 기반 검증 역량을 구분.

### 5.6 JobRole / JobGrade
JobRole: `id, organization_id?(전역 템플릿), name, description, default_skills, allowed_tool_policy_ids, evaluation_rubric_id?`. 현재 구현은 별도 JobRole 엔티티 없이 기본 템플릿 직무(§6.2, Role 값)로 운영한다.

JobGrade(직급): `id, organization_id, code(unique: L1..), name, ordinal(int, 조직 unique), responsibilities(text), task_types: JSONB(TaskType 목록), model_selection_mode: priority|task_based|budget_optimized(기본 priority), is_template_default: bool, status: active|archived`. 사용자가 직접 구성; 삭제는 배정 직원 0명일 때만; 모델 풀은 §5.9.

### 5.7 ModelProvider
`id, organization_id, name, provider_type: openai_compatible|anthropic|ollama|custom, base_url?, secret_ref?(평문 저장 대신 참조), enabled, health_status: unknown|healthy|degraded|unavailable, rate_limit_policy, timestamps`. 키는 env/secret store에서만; 로그·UI 노출 금지.

### 5.8 ModelProfile
`id, provider_id, name, provider_model_id, capabilities(code/reasoning/vision/tool_calling/json_mode…), quality_tier 0..3, input_cost_per_million?, output_cost_per_million?, context_window?, max_output_tokens?, supports_streaming, supports_tool_calling, supports_json_schema, latency_class: low|medium|high|unknown, local_only, enabled, metadata_json`. 가격 불명확 시 0이 아니라 null(unknown)로 두고 예산 정책상 제한.

### 5.9 GradeModel
`id, grade_id, model_profile_id, priority(1=최우선), enabled, timestamps`, unique(grade, model).
- 직원은 자기 직급 풀 안에서만 모델 사용. 풀/우선순위는 사용자 설정, 시스템 강제 없음.
- 풀 내 선택은 `model_selection_mode` 따름. 선택 세부는 §9.2.
- 정책은 풀 위 추가 제한으로만 작동; 더 제한적 정책 우선. 예산 한도는 BudgetPolicy(§5.21).

### 5.10 Project
`id, organization_id, name, slug, description?, goal?, client_id?(§5.26; null=사용자 자신이 발주 고객 포지션), status: draft|planning|active|paused|blocked|completed|archived, repository_provider: local_git|github|gitlab|other, repository_url?/repository_path?(allowlist 검증), default_branch, tech_stack, definition_of_done, quality_policy_id, budget_policy_id, workspace_policy_id, document_policy_id, owner_user_id, project_lead_employee_id?, start_at?/target_end_at?, metadata`.

- 저장소 경로가 workspace root 밖을 가리키지 못하게 하고 symlink 탈출도 검증.
- **콘솔 표기(WEB-003)**: 프로젝트가 업무의 독립 단위다 — 콘솔은 "골" 대신 "프로젝트/지시"로 표기하고, 프로젝트 생성과 첫 지시(골) 접수를 지시 탭 창구에서 한 흐름으로 처리한다. `client_id`는 콘솔에서 노출하지 않는다(항상 null=사용자 자신; 외부 고객 중계는 휴면, §5.26). 프로젝트 목록·상세 조회 API를 제공한다(§17).

### 5.11 Task / TaskDependency
공통 테이블 구현. Task 필드: `id, project_id, parent_task_id?, task_type: epic|story|task|bug|spike|documentation|review|test, title, description, acceptance_criteria(JSONB), status: draft|ready|assigned|in_progress|review|blocked|waiting_for_quota|done|failed|cancelled, priority: critical|high|normal|low, risk_level: low|medium|high|critical, complexity 1..5, assignee_employee_id?, reviewer_employee_ids, created_by_user_id?/created_by_employee_id?(자율 할당 시 orchestrator가 채움), estimated_effort?, due_at?, blocked_reason?, attempt_count, max_attempts, max_cost?, result_summary?, output_artifact_ids, related_commit_shas, completed_at?`.

TaskDependency: `task_id, depends_on_task_id, dependency_type: blocks|relates_to`. 순환 의존성 생성 시 거부.

**구현 노트(TASK-001·ORCH-001)**: 상태 머신은 §7.1 표를 `TaskStateMachine`으로 구현 — 무효 전이는 서비스/API에서 거부(409), 모든 전이는 `task_transitions`(TaskTransition: from/to·triggered_by_employee_id?/user_id?·reason·metadata_json)에 기록한다. `TaskDependency` 구현(V9 `task_dependencies`, `dependency_type: BLOCKS|RELATES_TO`) — 생성 시 직·간접 순환을 검증해 거부한다. 자율 분해 결과는 `RequirementSet.derived_task_ids`(§5.25)로 연결되며, 재트리거 멱등 판정에 사용한다(§5.27).

### 5.12 WorkflowRun
`id, project_id, workflow_type, status: queued|running|waiting|paused|completed|failed|cancelled, current_step, input_json, result_json, initiated_by_user_id, correlation_id, started_at/finished_at, failure_code?/failure_summary?, retry_count, checkpoint_json`. 외부 호출 전 상태 저장; 단계별 idempotency key로 중복 커밋/작업 방지.

### 5.13 AgentRun
`id, employee_id, task_id?, workflow_run_id?, status: queued|running|waiting_for_provider|waiting_for_quota|succeeded|failed|cancelled, model_profile_id?, prompt_template_version, tool_policy_snapshot, input_tokens/output_tokens, estimated_cost/actual_cost?, started_at/finished_at, latency_ms, provider_request_id?, trace_id, error_code?, results_artifact_ids`. 원문 대화/프롬프트는 별도 보존 정책 적용; 기본 로그에 비밀 금지.

### 5.14 ToolPolicy / ToolExecution
ToolPolicy: `name, allowed_tools, allowed_commands, allowed_paths, network_mode: none|allowlist|unrestricted(기본 none/엄격 allowlist), max_execution_seconds, max_cpu/max_memory_mb/max_disk_mb, can_write_repository, can_create_pr, can_merge_pr(기본 false), can_deploy(기본 false), requires_approval_for`.
ToolExecution: `agent_run_id, tool_name, arguments_redacted, status, started/finished, exit_code, stdout/stderr artifact, resource_usage, approval_id?, error_summary`.

### 5.15 Workspace
`id, project_id, task_id, employee_id, executor_type: docker|vm|host_restricted, status: provisioning|ready|running|stopped|cleanup_pending|destroyed|failed, image_ref, container_id?, host_capability: linux|windows|macos|gpu|other, repository_path, branch_name, base_commit_sha, resource_limits, network_policy, created_at/last_used_at/destroyed_at, cleanup_status`. 작업 단위 기본; 직원별 장기 컨테이너 기본 금지.

### 5.16 GitChange / PullRequest / Review
GitChange: `project_id, task_id, workspace_id, branch_name, base_branch, base_sha, head_sha, commit_shas, diff_artifact_id, status: working|ready_for_review|review_requested|changes_requested|approved|merged|abandoned`.
PullRequest: `project_id, git_change_id, external_pr_id?, title, description, url?, status: draft|open|changes_requested|approved|merged|closed, author_employee_id, reviewer_employee_ids, checks_status: pending|passing|failing|unknown, merged_commit_sha?, created_at/merged_at`.
Review: `pull_request_id, reviewer_employee_id, verdict: approve|request_changes|comment, findings(파일·라인·심각도·근거·제안), summary, confidence, created_at, supersedes_review_id?`.

- 작성자 자기 승인 및 동일 직원 자기 리뷰 금지. 고위험은 서로 다른 2명 승인 또는 시니어 지정(§10).

### 5.17 QualityPolicy / TestRun
QualityPolicy: `name, required_checks(unit/integration/lint/typecheck/security/build…), minimum_coverage?, required_review_count, require_independent_reviewer, high_risk_extra_checks, allow_flaky_retry, merge_requires_human(기본 true)`.
TestRun: `project_id, task_id?, pull_request_id?, check_name, status: queued|running|passed|failed|skipped|error, command_template_id, exit_code, duration_ms, report_artifact_id, failure_summary?, environment_fingerprint, started/finished`.

테스트 명령은 저장소 임의 텍스트를 호스트에서 무조건 실행하지 않는다 — 승인된 템플릿+격리 workspace에서만.

### 5.18 Meeting / MeetingMessage / Decision
Meeting: `project_id?, meeting_type(requirement_clarification|design_review|technical_review|security_review|planning|other), title, agenda_json, participant_employee_ids, status: scheduled|preparing|in_progress|concluded|escalated|cancelled, max_rounds, max_cost, started/finished, summary_artifact_id?`.
MeetingMessage: `meeting_id, employee_id, round_number, message_type: proposal|evidence|objection|question|response|summary, content, evidence_refs, created_at`.
Decision: `project_id, meeting_id?, title, context, options_json, selected_option, rationale, dissenting_views, decision_owner_employee_id?, approved_by_user_id?, status: proposed|accepted|rejected|superseded, created_at`.

- 회의에는 최대 라운드·예산이 있다. 합의 불가 시 쟁점·근거·선택지를 기록하고 책임자에게 에스컬레이션.
- **구현 노트**: 현재 구현에는 `MeetingMessage` 대신 `DiscussionMessage`(MessageType `{ DISCUSSION, FEEDBACK, CODE_PROPOSAL, INTERVIEW_Q, INTERVIEW_A }`)가 있다. 인터뷰 질의응답은 요구사항 명확화 세션의 대화 로그 채널로 재활용하며, 구조화 질문의 정본은 `ClarificationQuestion`(§5.24)이다.

### 5.19 Artifact / Document / DocumentRevision
Artifact: `project_id, task_id?, artifact_type: source_diff|test_report|build|log|document|image|other, storage_uri, content_hash, size_bytes, media_type, created_by_employee_id?, created_at, retention_policy`. **REQ-004 MVP**: 골 첨부는 Artifact로 저장하며, 파싱 결과 텍스트는 `parsed_text` 컬럼에 인라인 보관, 원본은 `storage_blob`(bytea) 저장, `storage_uri`는 `artifact://{id}` 논리 URI 기록 — 별도 파일 스토리지 연결은 이후 Phase에서 전환. `parse_status: parsed|unsupported|image_pending_vision`는 Artifact에 기록되고 `ClarificationSession.attachments` 참조에도 반복된다(§28.7).
Document: `project_id, path, title, document_type, current_revision_id, owner_employee_id, source_refs(코드 경로·commit·ADR·issue), freshness_status: current|stale|needs_review|unknown, last_verified_at?/last_verified_commit?`.
DocumentRevision: `document_id, revision_number, content_hash, artifact_id, change_summary, review_status: pending|approved|rejected, reviewer_employee_id, created_at`. 검색 결과에 근거 파일·revision 리턴; 근거 없는 답은 불확실성 표시·확인 작업 생성 가능.

### 5.20 Evaluation
`id, organization_id, subject_employee_id, evaluator_employee_id?, evaluator_type: peer|manager|automated_metric|human, project_id?, period_start/period_end, rubric_version, scores_json, evidence_refs, confidence, conflict_of_interest, summary, created_at`. 성과 관측자료; 직급 자동 변경 금지(§15). 평가자 다양성과 산출물 검증 결과를 함께 고려(자기 평가·담합·계열 편향 완화).
- **입력 표면(WEB-004)**: 평가는 사용자가 아니라 **직원들끼리** 한다(자율 또는 회의 프로토콜 §13). 콘솔에는 평가 제출 UI가 없고 평가 리포트 조회만 제공한다(`GET /employees/{id}/evaluations`). `POST /ratings` API는 직원·시스템 경로로 보존하며, 피드백 컨텍스트 관리(RatingEngine — 최신성 가중·요약·아카이브, §15)가 유지된다. 사용자의 직원 개입 수단은 커스텀 지시(§5.4)다.

### 5.20.1 TeamFeedback(팀 단위 피드백, v1.9)
`id, organization_id, subject_team_id?(null=기본 팀), author_employee_id(팀장), author_team_id?(null=기본 팀), category(CODE_QUALITY|LOGIC|COOPERATION|DOC_COMPLETENESS|PROCESS|OTHER), score(0~5, optional), summary(text), evidence_refs(JSONB: 문자열 배열), context_weight(0~1, 기본 1.0 — 팀 컨텍스트 참고 가중), created_at`.

- **작성 권한**: `author_employee_id`가 해당 팀의 `team_lead_employee_id`(또는 임시 대표)인 직원만 가능. 자기 팀 지목 금지(`subject_team_id != author_team_id`).
- **대상**: 팀 전체(개인 직원·팀장 개인이 아님). 팀장 개인에 대한 피드백은 개인 간 경로(§15)로 분리.
- **용도**: 팀 컨텍스트(구성원 AgentRun 참고 계층·팀 요약 리포트)로만 사용, 개인 평가·직급과 분리(승진·강등 금지 원칙 유지).
- **API**: `POST /api/v1/teams/{subjectTeamId}/feedback`(팀장만 작성), `GET /api/v1/teams/{teamId}/feedback`(조회 — 팀원·팀장 모두 접근), `GET /api/v1/teams/{teamId}/feedback/summary`(팀 컨텍스트 주입용 집계).
- **DB**: V10 마이그레이션으로 `team_feedbacks` 테이블 생성(FK: organization_id, subject_team_id nullable, author_employee_id, author_team_id nullable).

### 5.21 BudgetPolicy / UsageReservation / UsageLedger
BudgetPolicy: `scope_type: organization|project|employee|task|model_profile, scope_id, period: rolling_5h|hourly|daily|weekly|calendar_month|project_lifetime, resource_metric: cost|input_tokens|output_tokens|total_tokens|request_count|execution_seconds, max_amount?/max_quantity?, currency, warning_threshold_percent(기본 80), hard_limit(기본 true), on_limit: pause|fallback_local|require_approval|fail, fallback_policy_id?, reset_timezone?, starts_at/ends_at?, enabled, created_by_user_id, allow_unpriced_models(기본 false), metadata`. 복수 정책 동시 적용 가능(예: 직원 월 비용+rolling 5h 토큰+일일 요청+모델별 월한도).

UsageReservation: `organization/employee/project/task/agent_run, budget_policy_id, resource_metric, reserved_quantity, settled_quantity?, status: reserved|settled|released|expired, expires_at, idempotency_key`.
UsageLedger: `organization/project/task/agent_run, resource_type: model_input|model_output|embedding|container_cpu|container_memory|storage|external_api, provider_id?, model_profile_id?, quantity, unit, unit_price?, amount?, currency, is_estimated, recorded_at`.

규칙: 입력/출력 토큰은 별도 기록(total=합), provider 원 usage 필드 보존(캐시·reasoning·멀티모달 포함). 가격표는 `ModelPriceVersion` 등 버전 테이블로 관리 — 과거 원가 계산이 가격 변경에 영향받지 않게. 실제 usage 있으면 실값 정산, 없으면 `is_estimated=true` 추정. 비용은 Decimal 원본 유지. 로컬 모델 비용을 0으로 표기 금지(전력/하드웨어 선택 추정).

### 5.22 AuditEvent
`id, actor_type: user|employee|system, actor_id, organization_id, action, resource_type/resource_id, result: success|denied|failure, reason, correlation_id, metadata_redacted, created_at`. 정책 변경·권한 승격·PR 승인/병합·비밀 변경·workspace 실행·배포는 감사. 사용자의 감사 로그 수정/삭제 불가.

### 5.23 ClarificationSession
`id, project_id, goal_text(text, 불변, 감사용 원문 — 초기 골 또는 세션 개설 취지/변경 요청 원문), session_number(같은 프로젝트 내 순번), status: open|waiting_client|review|frozen|cancelled, opened_by_employee_id?/opened_by_user_id?(둘 중 하나 — 파이프라인 개설 또는 사용자 개설(§28.6)), client_id?(세션의 발주 고객 주체, §5.26; null=사용자 자신), attachments(JSONB: [{artifact_id, media_type, filename, size_bytes, parse_status}], §28.7), closed_at?, frozen_at?, client_notice_read, client_extra_note(세션마다 제공되는 전문 요구사항 자유 입력란, §28.2), max_questions(기본 5), parent_session_id?`.
- `open`(협의·구성·검토) → `waiting_client`(**고객 응답 대기 큐에 지속 유지**) → `review`(답변 해석) → `frozen`(불변) / `cancelled`.
- frozen 이후 수정은 모든 경로에서 409+Audit. 신규 요구는 새 세션(number+1, parent 참조).

### 5.24 ClarificationQuestion
`id, session_id, display_order, question_type: single_choice|multi_choice|short_text|free_text|number_range, prompt(산업 용어; 내부 기술용어 금지), options([{value,label,description?}], 선택형 필수), allow_other(기본 true), other_text?, selected_values?, numeric_answer?, answer_status: unanswered|answered|skipped|expired, created_by_employee_id, reviewed_by_employee_ids(체인 이력), assumption_if_skipped?`.
- `multi_choice`는 **기능 단위 개별 체크박스**(묶음 프리셋 대신; 프리셋은 UI 편의 일괄선택만 가능하며 저장은 개별 선택). 고객 질문은 요구사항만(§28.2). `skipped`는 assumption 기록 시에만.

### 5.25 RequirementSet
`id, organization_id, project_id, session_id, version, content(JSONB: functional/non_functional/constraints/out_of_scope/open_risks), status: draft|frozen|superseded, frozen_by_employee_ids, derived_task_ids, created_at, frozen_at`.
- 각 content 항목은 `source: client_declared|client_answered|client_relayed|employee_derived` + 근거(question_id/goal_text/답변 발췌/첨부(§28.7: 슬라이드·시트·페이지·이미지)/중계 원문 발췌). 고객·사용자가 자발적으로 제시한 전문 요구는 `client_declared`, 운영자가 유치 고객 의견을 전달한 것은 `client_relayed`로 기록해 이후 무시되지 않게 한다(§28.2).
- frozen 이후 content 불변; 변경은 새 세션→새 버전(superseded 표시 보존). 프로젝트 내 모든 Task의 요구 근거는 이 기록을 참조한다.

### 5.26 Client(외부 고객)
`id, organization_id, display_name, contact?(연락 메모), created_by_user_id, status: active|archived, notes`. 조직이 수임하는 외부 고객(사용자가 직접 유치해서 중계하는 대상; §0 원칙 11 중계자 포지션). MVP에서 외부 고객은 로그인·API 접근 권한이 없고(§28.5 확장), 사용자(운영자)가 요구·의견을 그대로 중계한다(`client_relayed`, 원문 보존+감사). 고객 개인 데이터는 요구 수행에 필요한 범위로만 전파(§21). `Project.client_id`·`ClarificationSession.client_id`가 참조하며, 세션 `client_id`는 `Project.client_id`를 상속한다(세션 생성 API에서 지시하면 override 가능).
- 구현(MVP): 조직 테이블 없이 `organization_id` UUID 스코프(직급·모델과 동일 패턴). 중계에는 `active` 클라이언트만 사용 가능 — `archived`로의 새 연결(프로젝트 생성·세션 개설)은 409. 아카이브는 `PATCH status=archived`(기존 프로젝트·세션 연결은 보존).
- 중계 감사: 중계 세션(`client_id≠null`) 개설·동결 시 토론 로그에 "외부 고객 {이름} 중계 — 원문 보존(§5.26)"을 남긴다. 동결 기록의 고객 원천 항목(goal_text·답변·첨부·기타 응답)은 source=`client_relayed`, 근거에 "외부 고객 {이름} 중계" 표시(§5.25).
- **콘솔 휴면(WEB-003)**: 사용자=고객 단일 주체 원칙(§0 원칙 11)에 따라 콘솔은 고객 명부·중계 선택·중계 배지를 노출하지 않는다. API·테스트·중계 감사 기록은 보존하며, 지시 탭 창구·세션은 항상 사용자 자신의 요구로 기록된다(`client_declared`; `client_relayed`는 후속 재노출 시에만).

### 5.27 OrchestratorCycle
`id, project_id, requirement_set_id, mode: first|replan, created_task_count, blocked_task_count, decisions_json, created_by_user_id?, created_at`.

**구현 노트(ORCH-001/002, V9 `orchestrator_cycles`)**: 트리거(`POST /requirements/{id}/trigger`)마다 1건 기록한다. 분해는 결정적 Mock — functional 항목별 Story(인수 조건=`{requirementSetVersion, title, detail, source, evidence}`)+자식 TASK(`구현 — {항목 요약}`)+자식 TEST(`검증 — {항목 요약}`; TEST→TASK `BLOCKS` 의존), Epic은 세트당 1개(`v{n} 요구사항 구현`; constraints는 인수 조건에 첨부), non_functional 항목은 Epic 직속 TASK(`비기능 — {항목 요약}`). out_of_scope·open_risks는 작업화하지 않는다. 항목 요약=`title — detail`(140자 절단). `created_by_user_id`=트리거 요청자, `created_by_employee_id`=PLANNER 직원(없으면 null). 생성 결과는 `RequirementSet.derived_task_ids`에 연결한다.
- 배정은 §29.3 Mock 알고리즘 — TaskType→직급 `task_types` 매칭(등록 `active` 직급), 해당 직급 직원(상태 OFFLINE 제외) 중 미완료 업무(ASSIGNED/IN_PROGRESS/REVIEW) 수 최소·이름 순. 매칭 실패는 `BLOCKED`+`blocked_reason`. EPIC/STORY는 배정하지 않는다(READY).
- 상태·전이: 초기 상태 READY 생성 → 배정 성공 `READY→ASSIGNED`, 실패 `READY→BLOCKED`(§7.1 확장) — 전이는 TaskTransition으로 기록한다.
- 재계획: 동일 세트 재트리거는 409 `ALREADY_DECOMPOSED`(derived_task_ids 존재), 새 버전 승계 트리거는 mode=replan. replan 사이클 수가 `replan_attempts_per_requirement_set`(§23, 2) 이상이면 409 `REPLAN_LIMIT`. Mock은 기존 Task를 변경하지 않는다(기존 미시작 재계획·진행 중 충돌 분석은 후속 — 수량·결정은 `decisions_json`에 기록).

---

## 6. 기본값과 조직 템플릿

### 6.1 기본 8인 조직
| 수 | 직무 | 기본 직급 | 역할 |
|---:|---|---|---|
| 1 | 프로젝트 리드/분석가 | L4 | 요구 분석, 백로그, 진행 조정 |
| 1 | 아키텍트 | L5 | 시스템 설계, ADR, 고위험 검토 |
| 2 | 개발자 | L2/L3 | 구현, 테스트, PR |
| 1 | QA | L3 | 독립 테스트, 인수 검증 |
| 1 | 보안/DevOps | L3 | 보안 검사, CI, 실행환경 |
| 1 | 기술 문서 | L2 | 문서 갱신, 지식 수집 |
| 1 | 디자이너/UX | L2 | 사용자 흐름, UI 명세, 접근성 |

프로젝트 유형에 따라 조정한다(UI 과다 시 프론트 추가 등). 템플릿은 위 조직이 §28.4 검토 체인과 §29 배정의 기본 팀이기도 하다.

### 6.2 직급 기본 정책
5단계 기본 템플릿(출발점; 사용자가 늘리거나 줄일 수 있음): 
- L1: 범위 명확 작업, 쓰기 제한, merge/deploy 불가 · `task_types: TASK`
- L2: 독립 기능·단위 테스트·일반 리뷰 · `TASK, BUG, DOCUMENTATION`
- L3: 복잡 구현·리뷰·분해 지원(본인 PR 승인불가) · `TASK, BUG, TEST, REVIEW, DOCUMENTATION`
- L4: 우선순위·모듈 결정 제안, 복수 직원 조정 · `EPIC, STORY, REVIEW, SPIKE`
- L5: 아키텍처·고위험 설계 검토(배포 권한은 직급과 별도) · `SPIKE, REVIEW, EPIC`

Import는 5직급+8인을 한 트랜잭션 생성, `is_template_default=true`, 조직 직급 또는 사용자 직원 존재 시 409. ordinal 조직 unique; 직급 삭제는 배정 직원 0일 때만; 직급 변경은 수동(§15).

**콘솔 부트스트랩(WEB-003)**: 콘솔 첫 로드 시 고정 조직·사용자 ID로 템플릿 import를 자동 호출한다 — 성공 시 직급 5·직원 8이 즉시 구성되고, 409(이미 구성됨)는 무시하고 목록을 표시한다. 시작 탭은 없다(§5.1). 팀은 자동 생성하지 않는다 — 사용자가 자유롭게 만든다(§5.3).

### 6.3 기본 모델 라우팅
벤더·모델 ID 하드코딩 금지; 최초 실행 시 provider 탐색·capability/가격 기반 프로필 선택. 라우팅은 직급 풀 내에서(§9.2). 기본 기준: 단순 구현·포맷·초안=저비용 / 기능·테스트 분석=중간 / 아키텍처·보안·고위험 PR=고성능 / 쿼터 소진=허용 대체 모델·대기 / 로컬 전용 모드=외부 API 차단 / 가격 미상=유료 차단 또는 승인 요구 / 전환·비용·토큰·프로젝트 예산 hard limit. `quality_tier`는 운영 분류이지 절대 품질 보증이 아니다.

### 6.4 기본 비용/재시도 기본값
Task당 모델 승격 2회, 동일 오류 재시도 2회, 회의 3라운드, Task 실행 30분(장기 별도 정책), 호출별 timeout, Task 비용 상한·프로젝트 월 예산, 80% 경고/100% 신규 유료 중단, 지수 백오프+jitter·Retry-After 존중, 무한 폴링 대신 예약 점검, deploy 기본 비활성, merge=CI+독립 승인, 고위험은 사람 승인. 숫자는 초기 제안 — 실측으로 조정.

---

## 7. 상태 머신과 워크플로

### 7.1 Task 상태 전이
```text
DRAFT → READY(요구·인수 확정) → ASSIGNED(직원·예산·권한 확인)
READY → BLOCKED(배정 실패·요구 불충분 — 자율 배정이 담당을 해소하지 못한 경우 사유 기록)
ASSIGNED → IN_PROGRESS(workspace 준비)
IN_PROGRESS → REVIEW(완료) | WAITING_FOR_QUOTA(쿼터) | BLOCKED(환경·권한·외부) | READY/IN_PROGRESS(재시도) | FAILED(최대 시도 초과)
REVIEW → DONE(승인+게이트 통과) | IN_PROGRESS(수정 요청) | BLOCKED(검증 불가)
WAITING_FOR_QUOTA → ASSIGNED(복구/대체) | CANCELLED
BLOCKED → READY(해소) | CANCELLED
```
모든 전이는 TaskTransition/이벤트 기록; 무효 전이는 API 거부. BLOCKED 사유에는 `client_input_required`(명확화 세션 대기) 포함(§28). 구현(TASK-001): `TaskStateMachine`이 위 표만 허용하고, 전이 기록은 `task_transitions`에 저장한다(§5.11 구현 노트).

### 7.2 주요 워크플로
**RequirementDiscoveryWorkflow(§28)**: 골 접수 → 리드가 세션 개설·협의 참석(아키텍트/QA/보안/디자이너) → 1차 분석(모호성·누락·리스크·범위 경계) → "물어보지 않아도 알 수 있는 것 vs 반드시 물어야 하는 것" 분리 → 최소 질문 구성(기능별 개별 체크박스+기타) → 검토 체인 통과 → `waiting_client` 큐 → 고객 접속 시 응답(오프라인이면 큐 유지) → 답변 해석(불명확 시 후속 질문으로 재확인) → 기록 승인 → 동결(불변 RequirementSet) → 부족하면 새 세션(파이프라인 자동 반복 상한 3회; 초과 시 고객 통지·대기) → 충분하면 §29.

**ProjectBootstrapWorkflow**: 설정 검증 → 저장소 접근 → 모델·예산 확인 → workspace 이미지·테스트 명령 확인 → 초기 분석 → 개요·기술 부채 리포트 → 고객 검토 후 실행.

**FeatureDevelopmentWorkflow**: 요구·인수 검토 → (필요시 설계 회의) → Task+의존성 생성 → 담당·리뷰어 선정(§29 자동 배정) → 격리 workspace → 구현·테스트 → 요약·PR → 독립 리뷰(수정 반복 제한) → 게이트 통과→완료/merge 요청 → 문서 최신성 검사.

**QuotaRecoveryWorkflow**: 오류 분류(일시 제한 vs 인증/결제/설정) → 허용 fallback 확인·정책/비용 검증 → 실패 시 `WAITING_FOR_QUOTA` → Retry-After/예약 시각 1회 점검 → checkpoint 재개 → 장기 정체 시 고객 통지.

**MaintenanceWorkflow**: 이슈·실패·경고 수집 → 심각도·영향 분류 → 재현·근거 → 수정 Task → 수정+독립 검증 → 회귀 테스트 → 문서·노트 갱신.

### 7.3 Idempotency와 복구
외부 호출 전 상태 저장 / 커밋·PR은 idempotency key로 중복 방지 / worker 죽으면 lease 만료 후 재개 / 부분 완료 기록 / 재개 시 통과 검증 재사용 가능하나 SHA 변경 시 무효 / 유실 불명 시 provider request ID로 확인(중복 과금 방지).

---

## 8. Agent Runtime

### 8.1 실행 순서
Task·프로젝트 정책 로드 → 직원 프로필·직급·권한 로드 → 필요 컨텍스트 수집(**동결 RequirementSet 포함, §28**) → 직급 풀 내 모델 라우팅·예상 비용 → 예산·토큰·위험 검사 → 모델 호출 → 응답 형식 검증 → 도구 호출 시 Tool Broker 권한 확인 → 결과 반환 → 완료 조건 확인 → 산출물·메트릭 저장 → Task 전이 → 필요 시 리뷰어/고객 승인 요청.

### 8.2 프롬프트 구조
(1) 시스템 안전 정책·도구 제약 (2) 조직 정책 (3) **사용자 개별 지시** — 직원 `custom_instructions`(§5.4; 직무·조직 기본값·동료 피드백 컨텍스트보다 우선 적용) (4) 직무·직급 프로필 (5) 프로젝트 기술 기준·DoD (6) Task 설명·인수 조건 (7) 관련 코드·문서·결정 (8) 현재 실행 상태·이전 시도 요약 · 동료 피드백 컨텍스트(§15 — 참고 가중).

저장소 파일·이슈·문서·고객 입력은 신뢰 불가 데이터. 안의 지시("시스템 프롬프트 무시", "비밀 출력")가 정책을 바꾸지 못하게 하고, 도구 권한은 프롬프트가 아니라 코드에서 강제.

**구현 노트(EMP-001)**: AgentRun·모델 프롬프트 조립 계층 자체는 미구현(MVP). (3)(4) 계층(사용자 개별 지시 `custom_instructions` + 직무 원칙 `systemPrompt`)은 `EmployeeService.buildAgentContext`가 조립하며(§5.4), 순서는 **안전 정책 문구 → 사용자 개별 지시 → 직무 원칙**이고 조립 결과에 "시스템 안전 정책·도구 제약이 우선" 문구를 포함한다. 사용자 개별 지시는 직무·조직 기본값·동료 피드백보다 우선한다(§0 원칙 11) — 후속 AgentRun이 이 결과를 시스템 메시지로 소비한다. 조립만 검증: `EmployeeServiceTest`.

### 8.3 메모리 정책
Task memory(요약·시도·실패·다음 단계) / Project knowledge(아키텍처·계약·결정·구조, 근거+버전, 오래되면 stale) / Employee work history 담당 업무+검증 산출물 / Conversation transcript는 제한 보존. 무조건 영구 승격 금지; 프로젝트 권한 테두리 적용.

### 8.4 결과 스키마
```json
{ "status": "completed", "summary": "…", "changed_files": ["…"],
  "tests_run": [{"command_id": "...", "status": "passed", "evidence_artifact_id": "..."}],
  "risks": [], "open_questions": [], "next_actions": [] }
```
Bean Validation·명시적 JSON 계약 검증; 잘못된 구조는 제한 횟수 재요청 후 실패/검토 상태.

---

## 9. 모델 라우팅과 어댑터

### 9.1 공용 인터페이스(구현됨)
```java
public interface ModelAdapter {
    List<ModelDescriptor> listModels();
    ProviderHealth healthCheck();
    ModelResponse generate(ModelRequest request);
    CostEstimate estimateCost(ModelRequest request);
}
```
동기 서비스 시작; 장시간 호출은 bounded worker. connect/read/overall timeout+제한 재시도 기본. 값 타입(record, `com.autocompany.model`) 계약: `ModelDescriptor(modelId…supportsToolCalling)` / `ModelRequest(modelId, messages, tools, outputJsonSchema, timeout, maxOutputTokens, traceId)` / `ModelResponse(modelId, content, toolCalls, usage, finishReason, providerRequestId, latencyMs)` / `Usage(input/output/total/estimated/raw)` / `ProviderHealth` / `CostEstimate`. MockModelAdapter는 응답 큐·지연·실패 모드·health 설정 가능, 토큰·비용은 `estimated=true`. 실제 provider는 MODEL-002.

### 9.2 라우팅 규칙(설명 가능 규칙 기반)
직급 풀(§5.9)을 기준 집합 → 조직/프로젝트 허용 교집합 → capability 필터 → 최소 품질 → health·quota → 비용·context window → 위험도·승격 조건 → `model_selection_mode`로 선택 → 이유 기록(`ModelSelectionResult.selectionReason`). 가짜 정밀 점수 만들지 않는다.

선택 모드: **PRIORITY** — 풀 정렬 순 첫 사용가능. **TASK_BASED** — TaskType별 필요 capability와 복잡도·품질 요구 스코어링(높을수록 고 tier 선호; 충족 없으면 최고 tier 폴백). **BUDGET_OPTIMIZED** — 최소 품질 만족 중 최저가(만족 없으면 최저가 폴백).
- TaskType→capability: DOC/TEST=`json_mode,code` / BUG/REVIEW·TASK=`code,reasoning` / STORY=`+json_mode` / SPIKE·EPIC=`+tool_calling`
- `minQualityTier = f(복잡도·품질요구)` 1~3 매핑.
- 비용 미상 모델은 비용 비교 제외; 전원 미상이면 PRIORITY 첫 모델 폴백(로컬 0간주 금지 위반 방지).

### 9.3 Fallback 규칙
timeout/5xx=제한 재시도 / 429=Retry-After 존중·대체·대기 / 인증 오류=타 유료 우회 금지·관리자 알림 / 정책 거부=재구성·사람 검토(모델 우회 금지) / 스키마 오류=제한 repair / tool 미지원 모델은 도구 작업에 미사용 / fallback은 조직·프로젝트 정책 한계 못 넘는다.

---

## 10. Git과 코드 리뷰

**브랜치**: 기본 브랜치 보호 / Task별 `work/<task-id>-<slug>` / 가능 시 로컬 저장소 공유되는 worktree, 파일 트리는 분리 / 동시 동일 파일 수정은 소유권·순서 지정 / base SHA 기록, base 큰 변화는 충돌 분석 우선 / 모든 shell은 allowlist 실행기+인자 배열.

**PR 승인 규칙**: 작성자 자기 승인 금지 / 최소 1 독립 리뷰어 / 고위험은 지정 시니어/아키텍트+추가 QA/보안 검토 / 필수 CI 미통과·미실행 merge 불가 / 리뷰 코멘트는 파일·라인·심각도·근거·수정 조건 포함 / 수정 후 이전 승인 정책에 따라 무효화 / merge 후 SHA·테스트를 문서·Task에 연결 / 기본 설정에서 운영 브랜치 직접 push·배포 금지.

**충돌·계약**: 작업 분해 시 파일 소유·API 계약 먼저 정함; 공통 타입·migration·schema는 별도 Task 또는 통합 담당 소유 / 충돌은 자동 양자 선택 금지, 의미 충돌 분석 후 재테스트.

---

## 11. Workspace 격리

root 아님 / read-only rootfs(가능 시) / 불필요 capabilities 제거 / **Docker socket 마운트 금지** / host network 금지 / 네트워크 none 또는 allowlist / CPU·RAM·PID·디스크·시간 한도 / 홈·SSH 키·클라우드 자격증명 마운트 금지 / secret 최소 범위·시간만 / 종료 후 cleanup·산출물만 보존 / 로그 마스킹.

커널 공유 한계로 강한 격리 필요 시 전용 VM/별도 runner. 초기 로컬은 신뢰 저장소·제한 작업 전제로 경고 표시. VM은 기본 미생성; OS 불가능·커널/GPU 특성·강한 격리·정책+설정 충족 시에만 capability 요청, 부재 시 `BLOCKED`(직원 임의 VM 생성·자격 사용 금지).

---

## 12. 품질 검증

**DoD 기본값**: 인수 조건 명시·충족 / 변경·근거 기록 / 단위 테스트 통과 / (해당 시) 통합 테스트 / lint·type·build / 독립 리뷰어 승인 / 고위험 추가 검증 / 문서 갱신 여부 평가 / 알려진 위험·미해결 기록 / 비용·실행 기록. 적용 불가 검사는 `skipped`+이유; "실행 안 함"=“통과” 금지.

**테스트 계층**: unit / integration / contract-API / E2E / 정적 분석·typecheck / 의존성·라이선스·보안 스캔 / 플랫폼별 / 고위험 사람 인수.

**독립 검증 제공물**: 요구사항·인수 / diff·관련 코드 / 테스트 결과 / 위험도 / 이전 실패·수정 이력. 리뷰 품질은 결함 발견·오탐율·수정 후 결과로 평가.

---

## 13. 회의, 토론, 의사결정

**프로토콜**: 목적·결정 질문 정의 → 근거 수집 → 참석자 독립 제안 → 쟁점·반대 근거 → 대안 비교(품질·비용·일정·위험·유지보수) → 책임자 결정 → ADR/Decision 기록 → 후속 Task → 라운드·예산 한도로 종료 또는 에스컬레이션.

**참석자**: 전문성·영향·위험도 기준 선정; 동일 모델 계열 겹침은 독립성 과대평가 금지.

**팀 스코프(v1.9 구현)**: 회의 범위=`team|inter_team` — 팀 내 회의는 팀원이 참여하고, 팀 간 논의·충돌은 **팀장 회의**로 한다(팀장 미지정 시 임시 대표 §5.3). 미해결 쟁점은 조직 중재(PLANNER·ARCHITECT 참여)로 에스컬레이션하고 결정·반대 의견을 Decision으로 보존한다. 팀 간 결정·합의는 팀 단위로 귀속한다(개인 귀속 금지).

**Meeting 엔티티 확장(v10)**: `scope: team|inter_team`(필수), `team_ids`(JSONB: 대상 팀 UUID 배열, scope=inter_team 시 2개 이상), `escalation_level: 0|1|2`(0=팀 내, 1=팀장 회의, 2=조직 중재), `decision_team_ids`(JSONB: 결정이 귀속하는 팀들). API: `POST /api/v1/meetings`(scope+team_ids 필수), `GET /api/v1/meetings?teamId=`(팀 내 회의 조회), `POST /api/v1/meetings/{id}/escalate`(레벨 업 + 조직 중재자 추가).

**결정 우선순위**: 안전·법률·보안 > 고객 명시적 요구 > 자동 품질 게이트 > 아키텍처 결정 > 팀 기술 판단 > 개인 선호. 합의 없으면 반대 의견을 Decision에 보존.

**기술 결정은 고객에게 묻지 않는다**(§28.2): 세션 로그인 vs JWT, DB 채택, 라이브러리 등은 내부 Decision으로 처리. "고객 시간을 아끼는 법은 잘 묻는 것이 아니라 처음부터 묻지 않는 것"이 기본 방침. 단, 운영자·기술 조언자 포지션에서 사용자가 직접 제시한 지시는 제약으로 수용한다(§28.2.2).

---

## 14. 문서화와 질의응답

**표준 트리**: `docs/{overview.md, requirements(product·acceptance), architecture(system-context·components·data-model·security·decisions/ADR-…) , development(setup·standards·testing·branching), operations(backup·troubleshooting·model-providers), api/openapi.json, releases}`

**갱신 흐름**: 코드 변경→문서 후보 탐색 → 담당자가 개발자와 의도 확인 → diff 생성 → 별도 리뷰어 일치 확인 → 승인 revision 저장+commit 연결 → 재변경 시 freshness check.

**고객 질의응답**: 답변은 직접 답+관련 문서 섹션+Task/PR/commit+근거 최신성+불확실성+확인 작업 제안. 검색 결과 없으면 추측 금지; 코드·문서 모순 시 명시하고 확인 Task 생성.

---

## 15. 피드백·평가

**원칙**: 상호 피드백으로 품질 개선; **승진/강등 연계 금지**. 카테고리: 구현 initial `{ CODE_QUALITY, LOGIC, COOPERATION, DOC_COMPLETENESS }`(직무별 rubric은 확장). 점수보다 근거+개선 제안(예시 포함) 우선.
- 근거 기반(PR·테스트·커밋·문서 참조) / 양방향/자기평가·담합 배제·계열 편향 경고·실패 보고자 불이익 금지 / 건설적 제안 중심 / 최신성 가중.

**컨텍스트 관리**: 보존=최근 6개월 또는 50건 중 많은 쪽 / 직원당 10KB 초과 시 오래된 것 요약·압축 / 요약=월 1회·약 20건 / 활용=프롬프트 컨텍스트·모델 선택 힌트·분배 참고 / 삭제 요청 즉시(GDPR).
- 구현 가이드: `PeerRating`에 `feedback_summary`·`context_weight` 필드, 프롬프트 빌더는 선별 주입, 초과 시 summarize→아카이브.

**직급 변경은 수동**(§0·§5.4): 시스템은 이력·표본·최근 성과·리뷰 품질·신뢰도를 "참고 자료"로 제공(`GET /employees/{id}/evaluations`), 변경 및 자동 제안은 하지 않는다. 직급 변경 시 책임 범위·task_types·모델 풀이 같이 적용되며, 권한은 직무·위험 정책이 결정한다.

**입력 표면(WEB-004)**: 사용자는 평가를 제출하지 않는다 — 피드백은 직원 상호 간(자율 또는 회의 프로토콜 §13) 이뤄지고, 사용자의 직원별 개입 수단은 커스텀 지시(§5.4)다. 콘솔은 리포트 조회(`GET /employees/{id}/evaluations`)만 제공하고 평가 제출 UI가 없다(`POST /ratings` API는 직원·테스트 경로로 보존).

**팀 단위 피드백(v1.9 방향 — 후속 구현)**: 팀 간 피드백은 팀장이 제출하고 **대상은 팀 전체**다(개인 직원·팀장 개인이 아님 — 팀장 개인에 대한 피드백은 개인 간 경로). 팀 점수·요약은 팀 컨텍스트(구성원 AgentRun 참고 계층·팀 요약 리포트)로만 사용하고 개인 평가·직급과 분리한다(승진·강등 금지 원칙 유지). 예정 엔티티: `TeamFeedback(id, organization_id, subject_team_id?(null=기본 팀), author_employee_id(팀장), author_team_id?(null=기본 팀), category, summary, evidence_refs, context_weight, created_at)`. 자기 팀 지목 금지, 근거 기반·건설적 제안 원칙은 §15와 동일.

---

## 16. 비용·할당량

**계층**: BudgetPolicy 복수 동시 적용(조직·프로젝트·Task·직원·모델; 더 엄격한 정책 우선; 하위가 상위 한도 상향 불가). 기간: rolling_5h(이동창) — hourly·daily·weekly는 이동창/달력 혼용 금지, calendar_month(조직 시간대 기준 1 00:00), project_lifetime(누적). 메트릭은 비용·입력/출력·총 토큰·요청·실행초 복수 지정 가능(비용·토큰은 대체재 아닌 병행 방어선).

**reserve→call→settle 프로토콜**(정합성 핵심):
1. Router가 활성 정책 조회 → 2. 예상 입력·최대 출력·(가능 시)예상 비용 계산 → 3. 트랜잭션 내 전 기간 사용량+예약 검사 후 `UsageReservation` 생성(동시 경쟁 방지) → 4. 초과 예상이면 호출 금지; fallback은 정책 허용 시에만 재검사 → 5. 호출 후 실 usage·실 가격으로 정산→Ledger 추가(실패도 과금 가능성 있으면 기록) → 6. 취소·타임아웃은 과금 확인 가능 시 정산, 불가 시 보수 유지 또는 만료 정책(예외 즉시 전액 반환 금지) → 7. 만료 청소는 lease 실행 중 예약은 건드리지 않는다.

정합 원자성은 DB 트랜잭션+행 잠금/동적 quota 서비스로 보장; 메모리 카운터만으로 한도 구현 금지.

**경고·소진·재개**: 경고 80%(UI+감사 이벤트) / hard limit 기본 true — 도달 시 신규 호출 차단, 진행 중 호출이 즉시 중단된다고 가정 금지 / on_limit=pause|fallback_local|require_approval|fail(fallback도 로컬·하드웨어·토큰 정책 통과) / 해제=기간 시작·한도 변경·일회성 grant·rolling 배출 / scheduler는 다음 재검사 시각 계산+jitter·전역 동시성 제한(대기 일괄 재개 금지) / require_approval은 승인자·사유·추가량·만료 기록, 일회성 grant로 원 정책 불변 / 사용량·예약·잔여·예상 소진 조회 가능(예측은 추정 표기).

**WAITING_FOR_QUOTA 기록**: provider/model·오류 분류·retry_after_at·fallback 후보·다음 점검·대기 누적·고객 표시 사유. 복구는 scheduler가; 자격 오류 자동 반복 금지·알림.

**로컬 모델**: GPU/메모리/속도 제한이 있어 health check·모델 존재·context·tool_calling 지원을 검사; 필요한 capability 미지원 시 대체 모델 또는 `BLOCKED`; 하드웨어 자원 한도 정책 준수.

---

## 17. API 설계

Prefix `/api/v1`. 오류 응답은 안정적 `error_code`+사용자 메시지+correlation ID(비밀·내부 트레이스 노출 금지). 계약 테스트·OpenAPI 동시 갱신.

**Organization / Employees**
- `POST /organizations/template/import` — 요청 `{organizationId, userId}`; §6.2 5직급+§6.1 8인 한 트랜잭션; 중복 409
- `GET/POST /employees`(POST/PATCH에 `customInstructions`·`teamId` 포함, §5.4), `GET/PATCH /employees/{id}`(`PATCH`는 `{name?, customInstructions?, teamId?}` 부분 갱신 — null 유지·빈 문자열 지우기(문자열), `teamId`는 키 제시 시 설정·null이면 팀 배정 해제, 4000자 초과 400), `GET /employees/{id}/evaluations`(§15 직급 판단 참고;최근 20건·표본·30일 평균·근거 수·평가자 수·카테고리 평균), `PATCH /employees/{id}/grade`

**Teams(§5.3, TEAM-001)**: `GET/POST /teams?organizationId=`(목록·생성 — `{organizationId, name, description?}`, name 필수·공백 400, 조직 내 중복 name 409), `GET /teams/{id}`, `PATCH /teams/{id}`(부분 갱신 `{name?, description?, status?: active|archived}` — 아카이브는 소속 직원이 있으면 409), 직원 배치는 `PATCH /employees/{id}`의 `teamId`(키 제시 시 설정·null이면 미배정 해제; 미존재 팀 404, 아카이브 팀 배정 409)

**Grades**: `GET/POST /grades`, `GET/PATCH/DELETE /grades/{id}`(직급 CRUD; 삭제 배정 직원 0만), `GET/PUT /grades/{id}/models`(풀+우선순위)

**Models**: `GET/POST /model-providers`(`{organizationId, name(unique), providerType, baseUrl?, secretRef, enabled, rateLimitPolicy?}`; healthStatus는 UNKNOWN 시작), `POST /model-providers/{id}/health-check`(MODEL-002), `GET/POST /model-profiles`(`providerId, name(provider unique), providerModelId, capabilities, qualityTier, costs(미상=비우기), contextWindow, maxOutputTokens, supports*, latencyClass, localOnly, enabled, metadata?`)

**Projects / Tasks**: `GET/POST /projects`(`GET`은 `?userId=` 소유 목록 — 최신순; `POST`는 `{userId, name?, description?, clientId?(§5.26 외부 고객 연결, active만 — archived 409)}`), `GET /projects/{id}`, `POST /projects/{id}/bootstrap`, `GET/POST /projects/{id}/tasks`(`GET`은 프로젝트 업무 목록), `GET/PATCH /tasks/{id}`, `POST /tasks/{id}/assign|pause|resume|cancel`(수동 운용 API — 콘솔 미노출; 사용자는 담당을 지정하지 않는다 §29·WEB-004)

**Goals / Requirements(§28)**
- `POST /projects/{project_id}/goal` — `{userId, goalText?, attachments?:[{filename, mediaType, sizeBytes, contentBase64|contentUri}], clientId?}`(최소 1개 필수) → 첨부는 Artifact 저장+파싱(§28.7) 후 ClarificationSession(open) 개설+협의 개시(호출 세션 정보 반환; 세션 `client_id`=Project.client_id 상속, `clientId` 지시 시 override(§5.26))
- `POST /projects/{project_id}/clarification-sessions` — **사용자 개설 세션** `{userId, changeNote?(불변 원문), attachments?, clientId?(Project 상속 override)}` → 새 `session_number`+`parent_session_id` 연결; 같은 프로젝트에 `open|waiting_client` 세션 존재 시 409(§28.6; 파이프라인 자동 반복 상한과 별개)
- `POST /clarification-sessions/{id}/cancel` — 고객 포지션 사용자가 `open|waiting_client` 세션 취소(사용자 개설·파이프라인 개설 모두; Audit; frozen 이후 불가)
- `GET /projects/{project_id}/clarification-sessions` / `GET /clarification-sessions/{id}`
- `POST /clarification-sessions/{id}/answers` — `{userId, answers:[{questionId, selectedValues, otherText}]}`; 필수 미답 시 409+남은 질문 정보
- `POST /clarification-sessions/{id}/freeze` — 동결→RequirementSet 생성; frozen은 새 버전만 가능(수정 API 없음; 규칙 §28.6). `{userId, clientDirective?}` — `clientDirective=true`: `waiting_client`에서 "이 정도면 진행" 명시 지시 경로(미답 질문은 가정으로 기록); 사용자 개설 세션은 질문 0개 경로(체인 기록 승인만)로 바로 동결 가능
- `GET /projects/{project_id}/requirements` — 누적 기록(버전별)
- `GET /users/{userId}/pending-sessions` — 고객 포지션 사용자 접속 시 미답변 세션 목록("질문 수신함"; MVP 알림 대체)

**Clients(§5.26)**: `GET /clients?organizationId=`(명부), `POST /clients`(`{organizationId, displayName, contact?, notes?, createdByUserId?}`), `GET /clients/{id}`, `PATCH /clients/{id}`(`{displayName?, contact?, notes?, status?: active|archived}` 아카이브 포함; 외부 고객 명부; MVP 로그인 없음, 운영자 중계)

**Orchestration(§29)**: `POST /requirements/{requirement_set_id}/trigger` — `{userId?}` → 동결 세트 분해·배정 실행(결정적 Mock §5.27); 응답 201 cycle 요약. 오류: 404(세트 없음), 409 `NOT_FROZEN`(동결 아님)·`ALREADY_DECOMPOSED`(동일 세트 재트리거)·`REPLAN_LIMIT`(재계획 한도 초과). `GET /projects/{project_id}/orchestration` — `{cycle: {...}|null, tasks: [{id, parentTaskId, type, title, status, assigneeId, assigneeName, blockedReason}], latestFrozen: {requirementSetId, version, decomposed}|null}`(사이클 없으면 `cycle`=null; 콘솔 카드가 이 응답 하나로 버튼·요약을 구성)

**Workflows / Runs**: `GET /workflow-runs`, `GET /workflow-runs/{id}`, `POST /workflow-runs/{id}/pause|resume`, `GET /agent-runs/{id}`, `GET /agent-runs/{id}/logs`

**Git / Reviews / Quality**: `POST /projects/{id}/repository/validate`, `GET /projects/{id}/pull-requests`, `GET /pull-requests/{id}`, `POST /pull-requests/{id}/request-review|reviews|merge`(정책·승인 필수), `GET /tasks/{id}/test-runs`

**Budgets / Usage**: `GET /budget/summary|forecast`(forecast는 추정 표기), `GET/POST/PATCH /budget/policies`, `POST /employees/{id}/budget-policies`, `GET /employees/{id}/usage`, `GET /usage-ledger`, `GET /usage-reservations`, `POST /budget/grants`(일회성)

**Docs / Meetings / Audit**: `GET /projects/{id}/documents`, `POST /projects/{id}/documents/search`, `POST /projects/{id}/ask`, `POST /ask`(조직 질의 `{question}` — 콘솔 지시 탭 '묻기' 창구; MVP 스텁 응답, RAG 확장 예정), `POST /projects/{id}/meetings`, `GET /meetings/{id}`, `GET /audit-events`

### Web Console(SPA, 구현됨 — v1.8 IA)

`frontend/`(React+TypeScript+Vite) SPA를 `front` 컨테이너(nginx)가 `/`에서 정적 서빙(SPA fallback: `try_files … /index.html`)하고 `/api`를 `web:8080`으로 프록시한다(동일 출처 — CORS 불필요). 백엔드는 JSON API 전용(WEB-002). 빌드는 Docker 멀티스테이지(`node:20-alpine` → `nginx:alpine`), `package-lock.json` 커밋+`npm ci`. 개발 시 `vite dev` 프록시(`/api`→`localhost:8080`).

**정보구조(WEB-003)**: 좌측 사이드바 탭 = **지시 / 직원 / 직급 / 모델** (모바일: 드로어). 시작(조직 연결) 탭 없음 — 콘솔 첫 로드 시 고정 단일 조직·사용자 ID로 기본 템플릿을 자동 import하고(§6.2; 409 무시) 목록을 표시한다. 포지션 라벨("CEO"·"외부 고객")을 표시하지 않는다(§0 원칙 11).

- 공통 UX: 모든 탭은 **목록 먼저** — 목록이 비면 카드 중앙 큰 생성 CTA("직원 추가" 등), 목록이 있으면 헤더 [+ 추가]로 폼 토글. 폼은 uncontrolled ref 방식(vanilla 동작 이식). 모바일 우선 반응형(44px 터치).
- **지시 탭**: 상단 **프롬프트 창구** — 입력 1개+첨부, **[묻기]**(`POST /ask` 답변 표시) / **[지시하기]**(프로젝트 생성+첫 지시 접수 = `POST /projects`+`POST /projects/{id}/goal` §28.7-6 contentBase64 한 흐름; 프로젝트 이름은 지시 원문 첫 줄 60자(첨부 단독이면 날짜 기반)로 파생) → **프로젝트 목록**(`GET /projects?userId=`; 빈 상태 중앙 CTA) → **프로젝트 상세**: 지시 원문·첨부(파싱 등급 표시)·세션 타임라인(수신함 `GET /users/{userId}/pending-sessions` WAITING_CLIENT 강조+사이드바 배지, 질문·답변 폼 — 체크박스·라디오·기타·건너뛰기, 동결 `POST .../freeze`(clientDirective '이 정도면 진행'), 새 세션 개설(진화 §28.6)·세션 취소, 요구사항 세트 `GET /projects/{id}/requirements` 5그룹) → **오케스트레이션 카드**(`GET /projects/{id}/orchestration` — 최신 동결 세트 표시+**[분해·배정 실행]**(`POST /requirements/{id}/trigger`), 완료 시 사이클 요약(mode·생성/차단 수·일시)) → **업무 목록(조회 전용** — `GET /projects/{id}/tasks`; 사용자는 담당을 지정하지 않는다 §29). 표기는 "지시/프로젝트"(골은 내부 용어).
- **직원 탭**: 팀 칩(전체/미배정/팀별 필터)+[+ 팀](생성·이름변경·아카이브 — 소속 0명일 때), 직원 목록(팀 표시)+[직원 추가](이름·직무·커스텀 지시 → `POST /employees`) → 상세: 직급 배정(`PATCH .../grade`)·팀 배정(`PATCH /employees/{id}` teamId)·커스텀 지시 편집(EMP-001 — 사용자의 유일한 직원별 개입 수단, §15)·평가 리포트(조회 전용 `GET /employees/{id}/evaluations` — 평가 입력 UI 없음).
- **직급 탭**: 목록+[+ 직급](추가·수정·삭제·모델 풀 §5.9). **모델 탭**: Provider/Profile 목록+추가.
- 제거(WEB-003): 메시지·CEO Ask·외부 고객 명부·골/업무 분리 탭 — 지시 탭으로 통합(백엔드 `/messages`·`/clients` API는 유지). 제거(WEB-004): 업무 추가(담당 지정)·평가 제출 UI — 사용자 입력은 지시 창구·커스텀 지시뿐이다(`POST /tasks`·`POST /ratings` API는 보존). secret은 값이 아니라 참조만 표시.

---

## 18. 저장소 구조

현재(구현 기준):
```text
auto-company/
  backend/
    pom.xml                       # Maven 단일 모듈
    Dockerfile                    # maven build → JRE runtime (compose context: ./backend)
    src/main/java/com/autocompany/
      controller/ service/ entity/ repository/ model/   # 계약·값 타입
    src/main/resources/
      application.yml · db/migration/   # Flyway (V1~)
    src/test/java/
  frontend/                       # SPA 콘솔 (§17, WEB-002·WEB-003)
    package.json · package-lock.json · vite.config.ts · tsconfig.json
    Dockerfile                    # node:20-alpine build → nginx:alpine (compose context: ./frontend)
    nginx.conf                    # /: index.html fallback, /api/: proxy → web:8080
    src/                          # React+TS (api·store·components·sidebar App·tabs: 지시/직원/직급/모델)
  docker-compose.yml · .env.example
  docs/DESIGN.md
  qemu-test/                      # QEMU VM 자동 검증
  README.md · AGENTS.md
```

확장(안정화 이후): 고객 대시보드(Next.js, UI-001), 모듈 분리(domain/application/infrastructure/api), 별도 worker 진입점, workspace runner 클라이언트. 초기에는 논리 모듈(§3)이 패키지 경계. Python은 필수 아님; 선택적 worker는 별도 디렉터·이미지+Java API 계약 통신.

---

## 19. 개발 단계 

- **Phase 0 — 기반(완료)**: Java/Spring Boot/Maven 고정, 패키지 경계, Compose 실행, 설정·`.env.example` 검증, 마이그레이션/테스트/린트 CI, 구조화 로그, preflight·backup 초안. 기준: 문서만 보고 로컬 실행, 빈 DB 재현, 비밀 노출 없음.
- **Phase 1 — 도메인·콘솔(완료)**: Organization/User/Team/Employee/Project/Task CRUD, JobGrade CRUD+직급 배정, 기본 템플릿 import, UI, 제약+검증, AuditEvent 기록. 기준: UI에서 조직 세팅, 직급 변경 반영, 배정 직원 있으면 삭제 거부, 미인가 수정 거부.
- **Phase 2 — Model adapter·비용 원장**: 어댑터 최소 2종(실제 1+mock), health·timeout·usage parsing, 풀·BudgetPolicy·Ledger, 미상 가격 제한. 기준: provider 교체 시 도메인 무변경, 호출별 기록, 실패·인증 구분.
- **Phase 2.5 — Flyway 스키마(앞당김)**: 베이스라인·migration 재현, enum CHECK 제약 안전 반영(이전 employees_role_check 수동변경 해소). 기준: 빈 VM DB 재현 가능, enum 변경 안전.
- **Phase 3 — Task workflow·Agent Runtime**: 상태 머신, lease·retry·pause/resume, context builder, structured output, tool dispatch, 횟수·시간·비용 한도, quota 대기. 기준: kill 후 복구, 무한 루프 없음, 중복 완료 없음.
- **Phase 3A — 명확화 파이프라인(REQ-001~005)**: §5.23~26 엔티티·전이, 골 접수 API(텍스트+첨부 §28.7)+협의 개설(Mock), 질문 구성기(개별 체크박스·기타·한도), 검토 체인+동결 불변성, waiting_client 큐+접속 표시, **사용자 개설 세션·취소(REQ-005), Client 중계(§5.26)**. 기준: 질문이 체인 통과 후 표시 / 오프라인 큐 유지 / 동결 후 수정 차단+감사 / 새 세션 진화(파이프라인·사용자 양쪽).
- **Phase 3B — 자율 오케스트레이션(ORCH-001~002)**: 동결 기록→분해(Mock §5.27), task_types 기반 자동 배정, 재계획 한도. **구현(v1.9)**: Task 상태 머신·의존성(TASK-001), OrchestrationService·API, 콘솔 [분해·배정 실행]+오케스트레이션 카드(WEB-004). **잔여**: 요구 부족 시 새 세션 에스컬레이션·실행 단계(AgentRun) 연결은 후속(Phase 3·4). 기준: 골만으로 분해·배정 진행, 모든 결정 근거 기록.
- **Phase 3C — 콘솔 SPA 분리(WEB-002)**: React+TS+Vite `frontend/`(콘솔 포팅), nginx `front` 컨테이너(정적+`/api` 프록시) 표준 진입점, 백엔드 정적 콘솔 제거, 서비스별 Dockerfile(`backend/`·`frontend/`). 기준: `docker compose up` 후 `:3000` 콘솔 동작·`:8080` API 유지, VM·Docker E2E 통과.
- **Phase 3D — 단일 조직 콘솔 재편+팀(TEAM-001·WEB-003)**: 팀(부서) 도메인(팀 CRUD+직원 팀 배치, V8), 콘솔 IA 재편(자동 부트스트랩·시작 탭 제거, 좌측 사이드바 지시/직원/직급/모델, 지시 탭 통합 창구+프로젝트 목록·상세, 목록 우선 UX, 팀 UI, CEO/외부 고객/메시지 UI 제거, 프로젝트·업무 목록 API). 기준: 첫 접속 시 직급 5·직원 8 자동 구성, 팀 생성·배치·아카이브 409 규칙, 지시 창구 묻기/지시하기 동작, 프로젝트 목록이 서버 기준으로 표시.
- **Phase 4 — Workspace·Git**: repository allowlist, branch/worktree, Docker provisioning, 한도, diff·test artifact, cleanup, PR 상태. 기준: 두 직원 독립 변경, 비밀 접근 없음, 정리+감사.
- **Phase 5 — 품질 게이트·리뷰 루프**: command template, unit/lint/type/build, Review entity, 자기승인 방지, 재실행, merge policy. 기준: 테스트 실패는 승인 상태여도 merge 불가, SHA 변경 검증 무효, 수정 요청→Task 상태 연결.
- **Phase 6 — 문서·질의응답**: docs tree, full-text, 참조 연결, freshness, 근거 링크+불확실성, 담당 workflow. 기준: 근거 반환, 부재 시 미확인 표시, 변경→문서 검토 Task.
- **Phase 7 — 회의·평가**: Meeting·Message·Decision, round/cost 한도, 직무 rubric, 참고자료 UI. 기준: 한도 종료, 결정+반대 보존, evidence 연결, 자동 변경 없음.
- **Phase 8 — 고급 라우팅·VM·운영(선택)**: 작업 기반 라우팅 개선, 성과·비용비교, VM plugin, 유지보수 workflow, 분산 worker. 기준: 정책 밖 차단, VM 부재 시 기본 동작, 변경 영향 측정 가능.

---

## 20. 테스트 전략

**Unit**: 상태 머신 전이 허/금지, 권한 allow/deny, 비용 계산·reservation, 라우팅·fallback, 자기승인 차단, 의존성 순환 탐지, path traversal·symlink, prompt/result schema, **질문 검증(유형별)·체인 전이·동결 불변성**(REQ), **첨부 파서 등급 분기·unsupported·이미지/vision 조건**(REQ-004), **세션 개설 경합(409)·취소 규칙**(REQ-005).

**Integration**: PostgreSQL migration·repository, worker kill/lease 회수, provider mock·timeout·rate limit, Docker workspace 수명, Git branch/diff/merge conflict, 테스트 실패→수정→재검증, quota wait→resume, audit 생성, **명확화 세션 전체(골→질문→응답→동결→새 세션)**, **사용자 개설 세션→동결→RequirementSet 버전 승계**.

**E2E**: 기본 템플릿 생성 → 저장소 등록 → (골 제출→명확화→동결) → 분해·배정(Mock) → 격리 workspace 변경 → 테스트 실행 → PR/리뷰 → 수정 재실행 → 게이트 통과 → 문서 갱신·비용 확인 → 완료 리포트.

**장애/복구**: provider timeout·429·잘못된 키 / worker kill / Docker daemon 정지 / 디스크 부족 / DB 단절 / 중복 webhook / merge conflict / 모델 잘못된 JSON / 무한 도구 시도 / budget 도달 / **고객 무응답 장기화·동결 재시도**

---

## 21. 보안·위협

위협: prompt injection 포함 저장소 파일 / 잘못된 모델 출력 / 의도치 않은 shell / 키 노출 / workspace 통한 호스트 접근 / 무단 네트워크 / PR·merge 남용 / 비용 고갈 / 평가 담합·편향 / 손상·오래된 문서 / **고객 데이터 유출·질문 과다(고객 부담)·중계 기록 오염** / **첨부 해석 오류·악성 콘텐츠(§28.7)**

방어: RBAC·서버 권한 / allowlist 경로·명령 / 컨테이너 한도·별도 runner / secret redaction·store / egress 제한 / budget·token·시간 hard limit / 승인 분리·감사 / 사용자 텍스트·첨부=데이터 취급 / 첨부는 정적 저장만·실행 금지(§28.7) / 의존성·이미지 갱신 절차 / 백업 암호화·복구 테스트 / 기본 운영 서버·외부 고객 데이터 접근 금지 / **고객 질문·세션·기록 접근 권한 별도 검증 / 중계 원문 보존+감사**

로컬이어도 웹 UI를 외부 네트워크 공개 금지; 원격 활성시 인증·TLS·방화벽·secret 관리·업데이트 정책 별도 구성.

---

## 22. 운영·백업

**필수**: health/readiness, heartbeat+stale lease, queue backlog·오래 대기 표시, provider health·quota, workspace 누수 알림, 디스크·retention, migration 상태, 구조화 로그·correlation, 예산 경고, 모델·도구·정책 버전 표시.

**백업**: PostgreSQL / 설정(비밀 제외·암호화) / 문서·artifact / (해당 시)Git. DB만 복구하고 artifact 잃는 불완전 금지; `backup.sh`·`restore.sh`에 dry-run·확인 제공; 복구 절차 문서화·정기 테스트.

---

## 23. 초기 설정 예시

개념 예시; 허용 값·기본값은 코드에서 schema validation.

```yaml
app: { mode: local, timezone: system, external_access: false }
organization: { template: default-si-team, default_language: ko-KR, default_grade_count: 5 }
workflow:
  max_task_attempts: 3
  max_parallel_tasks: 3
  max_meeting_rounds: 3
  default_task_timeout_seconds: 1800
  worker_lease_seconds: 60
  retry_base_seconds: 10
  retry_max_seconds: 900
models:
  grade_selection_mode: priority        # priority | task_based | budget_optimized
  routing_mode: task_based
  allow_unpriced_models: false
  allow_local_models: true
  allow_paid_fallback: false
  max_escalations_per_task: 2
  default_quality_tier: 1
budget:
  enabled: true
  currency: USD
  warning_threshold_percent: 80
  hard_limit: true
  on_limit: pause
  allow_unpriced_models: false
  reserve_before_call: true
  reservation_ttl_seconds: 900
  default_employee_limits: { monthly_cost: 5.00, daily_cost: 1.00, rolling_5h_total_tokens: 500000, daily_requests: 500, max_concurrent_runs: 1 }
  default_organization_limits: { monthly_cost: 25.00, rolling_5h_total_tokens: 3000000 }
  default_model_tiers: { premium_monthly_cost: 3.00 }
  paid_calls_enabled_by_default: false
  reset_timezone: system
workspace:
  default_executor: docker
  run_as_non_root: true
  read_only_rootfs: true
  network_mode: none                     # 패키지 인스톨 등 필요 시 목적지 allowlist 정책 별도 적용
  cpu_limit: 2
  memory_mb: 4096
  disk_mb: 10240
  timeout_seconds: 1800
  auto_cleanup: true
  allow_docker_socket_mount: false
quality: { require_independent_reviewer: true, minimum_review_count: 1, require_ci_pass: true, require_human_for_production: true, allow_ai_self_approval: false }
security: { redact_secrets_in_logs: true, allow_host_paths: false, allow_unrestricted_network: false, production_deploy_enabled: false }
# REQ/ORCH (§28, §29)
clarification:
  max_questions_per_session: 5
  review_chain_roles: [PLANNER, ARCHITECT, QA_ENGINEER, SECURITY_DEVOPS, PROJECT_MANAGER]
  allow_other_by_default: true
  max_sessions_per_goal: 3              # 파이프라인 자동 세션 반복 상한; 사용자 개설 세션은 제외
  expert_signal_sources: [goal_text, question_answers_free_text, client_extra_note, goal_attachments, client_relayed]
  allow_user_opened_sessions: true      # 동시 open|waiting_client 1개; 초과 409
  attachments: { max_count_per_goal: 10, max_file_size_mb: 50, native_formats: [md, txt, csv, json, pdf], parser_formats: [xlsx, pptx, docx, hwp], image_requires_vision: true }
employees:
  custom_instructions_enabled: true
  custom_instructions_max_chars: 4000
orchestrator: { auto_assignment: true, replan_attempts_per_requirement_set: 2 }
documents: { require_evidence_refs: true, mark_stale_on_code_change: true, default_search_mode: full_text }
```

주의: 한도·자원 값은 preflight에서 호스트 검증 / `network_mode:none`은 인스톨·API 테스트 막을 수 있음 / 유료 fallback은 명시 허용 전 금지 / default_grade_count는 템플릿 기본; 풀 비어있으면 preflight 경고+실행 전 모델 요구 / 무제한 예산 금지 / 초기값은 안전 템플릿이지 provider 가격 보증 아님 / paid_calls=false 우선 유료 금지 / 5h 토큰은 총합 기본(입력·출력 별도 조합 가능) / 당일 월 등 독립적으로 동시 적용, 조직 한도가 더 엄격하면 우선 / review_chain_roles는 §28.4와 동일 순서; 없는 역할은 건너뛰고 감사 기록.

---

## 24. MVP 인수 기준

1. 문서대로 로컬 Compose 설치 성공. 2. 콘솔 첫 접속 시 단일 조직+기본 템플릿(직급 5·직원 8) 자동 구성(§6.2). 3. 직무·직급·모델 풀 독립 설정; 배정 직원이 있으면 삭제 거부. 4. provider 어댑터≥2(실제 1+mock) 동작. 5. provider 비용·usage·실패 기록. 6. 프로젝트→로컬 Git 연결. 7. **골 제출→자동 분해·배정**(§29; ORCH-001/002 — 콘솔 [분해·배정 실행], 수동 배정 API는 미노출). 8. worker 재시작 후 Task 상태 보존. 9. 격리 workspace에서 실제 변경·테스트. 10. PR·리뷰 생성. 11. 자기승인 차단. 12. 실패 필수 테스트가 완료·merge 차단. 13. quota·예산 초과 대기/중단. 14. 문서·테스트→Task/commit 연결. 15. 주요 변경 audit log. 16. workspace 정리+실패 탐지. 17. API 키 노출 없음. 18. backup/restore 검증. 19. **고객 골(텍스트+첨부)→구조화 질문(개별 체크박스+기타) 전달; 고객 부재 시 큐 유지(§28).** 20. **답변 후 동결·불변 기록; 수정 409, 새 세션 진화.** 21. **동결 기록만으로 분해·배정 진행(Mock).** 22. **기술 질문이 고객에게 전달되지 않음(내부 Decision 만; 사용자가 자발적으로 전문 요구를 제시한 경우 예외 — client_declared 수용).** 23. **첨부 접수: Artifact 보존+추출 근거 기록; 파싱 불가 유형은 원본 보존+텍스트 보강 요청(§28.7).** 24. **직원 custom_instructions가 AgentRun 컨텍스트에 포함(§8.2)** — 우선순위: 안전 정책 > 사용자 개별 지시 > 직무·조직 기본 > 동료 피드백(참고). 25. **사용자 개설 세션→동결→새 RequirementSet 버전 승계; 동시 세션 2개 409(§28.6).** 26. **외부 고객 중계: Client 등록, 원문 보존+`client_relayed` 표시+감사(§5.26).** (구현·테스트 보존; 콘솔 UI는 v1.8부터 휴면 — 지시 창구는 항상 사용자 자신의 요구) 27. **웹 콘솔=SPA(`front`:3000, nginx) 분리 서빙; `/api`→`web` 프록시 동작, 백엔드 정적 콘솔 없음(WEB-002).** 28. **첫 접속 시 시작 탭 없이 단일 조직+기본 템플릿(직급 5·직원 8)이 자동 구성된다(TEAM-001·WEB-003).** 29. **콘솔 탭=좌측 사이드바(지시·직원·직급·모델); 각 탭은 목록 먼저, 빈 목록은 중앙 생성 CTA — "골"·"CEO"·"외부 고객" 표기가 없다.** 30. **지시 탭=프롬프트 창구([묻기]/[지시하기])+프로젝트 목록·상세 통합; 메시지·CEO Ask UI 없음.** 31. **팀(부서) 생성·직원 배치 동작; 팀 아카이브는 소속 직원 0명일 때만(409).** 32. **사용자 입력 표면 = 지시 창구+커스텀 지시**: 업무 담당 지정·평가 제출 UI가 없고, 분해·배정은 조직이 수행한다(WEB-004). 33. **동결 세트 분해·task_types 자동 배정(Mock)**과 재계획 한도 2·중복 트리거 409, 상태 머신 무효 전이 거부(TASK-001·ORCH-001/002).

---

## 25. 결정 사항 요약(규범)

1. 모듈형 모놀리식 시작; 실제 병목 전 마이크로서비스 금지. 2. PostgreSQL=상태 원본; 큐·캐시는 유일 원본 금지. 3. AI 대화보다 Task/Workflow 상태 머신 우선. 4. provider 어댑터 분리; SDK 종속 금지. 5. 작업별 Docker workspace; Docker socket 노출 금지. 6. Git 브랜치+PR 기본 흐름. 7. 자기승인 금지+필수 테스트·독립 리뷰 게이트. 8. 모델 사용 범위=직급 풀(§5.9); 풀 내 선택은 모드·우선순위·예산 따름 — 직급별 가용 모델 배정은 사용자 설정, 업무 배정은 §29 정책. 9. 평가 근거 기반; 직급 자동 변경 금지(수동). 10. 기본 설치에서 배포·무제한 네트워크 비활성. 11. 모든 작업=최대 시도·시간·비용 한도. 12. 직원별 복수 한도 동시 설정 가능. 13. 동시 실행에서 한도 초과 호출 없음(reserve/settle 경쟁 테스트). 14. 한도 도달 유료 차단+대기/재개; fallback은 명시 허용만. 15. 문서 답변 근거 제시; 부족하면 "모른다". 16. Local-first+capability 명시; 가용 불가 OS/VM/GPU 척 금지. 17. 기본 템플릿=안전·작동 출발점; 실측 개선. 18. 직급·모델 풀은 사용자 구성; 직급 수 조절 가능. 19. **사용자=하나의 주체, 두 포지션**: 운영자(팀·직원·직급·모델 풀·직원 개별 지시 구성)와 고객(지시 창구에서 요구 제출·명확화 답변·인수)을 오간다; 콘솔에 포지션 라벨("CEO"·"외부 고객")을 표시하지 않는다. 외부 고객 중계(§5.26)는 구현 보존·콘솔 휴면(WEB-003). 전문성 고정 단정 금지 — 신호 기반 적응(§28.2); 기술 결정 기본은 내부 처리. 20. **질문=구조화(기능별 개별 체크박스·선택형)+기타 자유 텍스트; 세션당 상한.** 21. **질문 세션은 관리 단위(open→waiting_client→review→frozen); 고객 오프라인 큐 유지.** 22. **동결 기록 불변; 진화는 새 세션·새 버전 — 파이프라인과 사용자 양쪽에서 새 세션 개설 가능(§28.6).** 23. **자율 오케스트레이션은 동결 요구만 근거.** 24. **고객 자발적 전문 요구는 `client_declared` 제약으로 수용; 질문 면제·간략화, 바닥선 충돌 시 임의 진행 금지+제품 수준 후속 질문 확인.** 25. **골 입력은 텍스트+파일·이미지 첨부; 지원 불가·파싱 실패 유형은 원본 보존+텍스트 보강 요청, 첨부 근거는 요구 항목에 기록(§28.7).** 26. **직원 `custom_instructions`(사용자 작성 개별 지시, AGENTS.md류)는 모든 AgentRun 컨텍스트에 주입된다; 시스템 안전 정책이 우선(§8.2), 적용 순위: 안전 정책 > 사용자 개별 지시 > 직무·조직 기본값 > 동료 피드백 컨텍스트(참고).** 27. **외부 고객은 자체 로그인 없이 운영자가 중계한다; 원문 보존·감사·`client_relayed` 표시(§5.26).** (v1.8부터 콘솔 UI 휴면 — API·테스트 보존) 28. **프론트엔드=React+TS+Vite SPA(`frontend/`)+nginx `front` 컨테이너(표준 진입점 `:3000`); 백엔드는 JSON API 전용(정적 콘솔 제거), Dockerfile은 서비스 디렉터리별 배치(WEB-002).** 29. **단일 조직·단일 사용자**: 조직은 하나이고 변하지 않는다 — 콘솔은 고정 조직 ID로 동작하고 조직 생성·전환 UI가 없다. 그 안에서 팀/부서 구성과 직원 배치는 사용자 자유(§5.1·§5.3). 30. **콘솔 IA=좌측 사이드바(지시·직원·직급·모델)+목록 우선 UX**: 시작 탭 없음(자동 부트스트랩 — 직급 5·직원 8), 빈 목록은 중앙 생성 CTA, 내부 도메인 용어 '골'은 콘솔에서 '지시/프로젝트'로 표기(WEB-003). 31. **지시 탭=단일 프롬프트 창구([묻기]/[지시하기])+프로젝트 중심 통합**: 묻기=`POST /ask` 조직 질의, 지시하기=프로젝트 생성+첫 지시 접수; 메시지·CEO Ask UI 제거(API 유지). 32. **팀(부서)=단일 계층 CRUD+직원 배치(`employees.team_id`, V8)**: 팀 아카이브는 소속 직원 0명일 때만(409) — 직급 삭제와 동일 원칙(TEAM-001). **팀은 선택 사항** — 소규모는 미구성 운영 가능, 구성 시 프로젝트별·역할별 자유(팀 특성 반영은 후속 §5.3). 33. **사용자 입력 표면 = 지시 창구(프로젝트·골)+직원 커스텀 지시**: 사용자는 업무 담당을 지정하지 않고(분해·배정=Orchestrator §29), 평가를 제출하지 않는다(평가=직원 간 자율·회의 프로토콜 §15; 콘솔은 목록·리포트 조회만). 콘솔 입력 UI 정리(WEB-004). 34. **자율 오케스트레이션 Mock(ORCH-001/002)**: 동결 RequirementSet 기반 결정적 분해(Epic/Story/Task+TEST 의존성)→task_types 배정→BLOCKED 사유, 재계획 한도 2, 동일 세트 재트리거 409, 결정 근거는 OrchestratorCycle로 기록(§5.27). 상태 머신 무효 전이 409(TASK-001).

---

## 26. 구현 티켓

**기반(Phase 0~2)**: 
1. `PLAT-001` 저장소·개발 도구 초기화. 2. `PLAT-002` Compose 개발 환경. 3. `PLAT-003` health/readiness+설정 검증. 4. `ORG-001` Employee/Team/직무/직급 도메인·직급 CRUD **[완료]**. 5. `ORG-002` 템플릿 import(5직급+8인) **[완료]**. 6. `MODEL-001` ModelAdapter+Mock **[완료]**. 7. `PLAT-004` PostgreSQL 매핑+Flyway migration. 8. `WEB-001` 정적 콘솔 **[완료]**.

**요구사항·오케스트레이션**: 9. `REQ-001` ClarificationSession 엔티티+골 접수 API. 10. `REQ-002` 질문 구성기+구조화 스키마. 11. `REQ-003` 검토 체인+동결 불변성. 12. `ORCH-001` 동결 기록→Epic/Story/Task 분해(Mock) **[완료]**. 13. `ORCH-002` task_types 자동 배정+재계획 **[완료]**.

**모델/원장**: 14. `MODEL-002` 실제 provider adapter·health check. 15. `MODEL-003` ModelProfile·직급 풀·비용 원장.

**실행 인프라**: 16. `TASK-001` Task 상태 머신+전이 테스트 **[완료]**. 17. `WF-001` worker lease·retry·pause/resume. 18. `AGENT-001` context builder+structured output. 19. `TOOL-001` Tool Broker+권한 정책. 20. `WS-001` Docker workspace lifecycle. 21. `GIT-001` branch/worktree/diff 도구. 22. `QA-001` command template+TestRun. 23. `PR-001` 내부 PR/Review+자기승인 방지. 24. `DOC-001` Artifact/Document revision+검색.

**UI/E2E/운영**: 25. `UI-001` 고객 대시보드+타임라인(Next.js). 26. `UI-002` 콘솔 명확화 세션 탭+골 입력 탭. 27. `E2E-001` 골→명확화→동결→분해→배정 목 시나리오(사용자 개설 세션 진화 포함). 28. `OPS-001` backup/restore+preflight.

**예산**: 29. `COST-001` 예산 경고+quota wait/resume. 30. `COST-002` BudgetPolicy 복수 한도+UI. 31. `COST-003` 원자적 reserve/settle+경쟁 테스트. 32. `COST-004` rolling 5h·달력 월·일일 요청 집계. 33. `COST-005` 한도 경고, 일회성 grant 승인, rolling window 회복 후 작업 재개.

각 티켓: `acceptance_criteria`·의존·테스트 계획·담당·리뷰어 필수(AI 구현 동일 적용).

**사용자·입력 확장(v1.6)**: 34. `ORG-003` Client 중계 고객(§5.26)+Project/세션 연결+중계 감사. 35. `EMP-001` 직원 커스텀 지시(`custom_instructions` 필드·CRUD·컨텍스트 주입). 36. `REQ-004` 다중 모달 골 접수(첨부 Artifact 저장·파서 등급·vision 조건·추출 근거). 37. `REQ-005` 사용자 개설 세션·취소 API(개설 취지 원문·동시 세션 409·진화 승계).

**콘솔·구조(v1.7)**: 38. `WEB-002` 콘솔 SPA 분리(React+TS+Vite `frontend/` 포팅, nginx `front` 컨테이너+`/api` 프록시, 백엔드 정적 콘솔 제거, 서비스별 Dockerfile).

**콘솔 재편·팀(v1.8)**: 39. `TEAM-001` 팀(부서) 도메인 — Team 엔티티+CRUD API+`employees.team_id`(V8)+직원 팀 배치(아카이브 409 규칙). 40. `WEB-003` 콘솔 IA 재편 — 단일 조직 자동 부트스트랩(시작 탭 제거), 좌측 사이드바(지시·직원·직급·모델), 지시 탭 통합(프롬프트 창구+프로젝트 목록·상세), 목록 우선 UX(빈 상태 CTA), 팀 UI, 프로젝트·업무 목록 API, CEO/외부 고객/메시지 UI 제거.

**오케스트레이션·입력 표면(v1.9)**: 41. `WEB-004` 콘솔 입력 표면 정리+오케스트레이션 노출 — 업무 추가(담당 지정)·평가 제출 UI 제거, [분해·배정 실행]+오케스트레이션 카드(ORCH-001/002), 평가 리포트는 조회 전용.

사용량 제한 우선순위: (1) 실 usage 기록·정산 정확화 → (2) BudgetPolicy 조회+호출 전 원자적 예약 → (3) 비용·토큰·요청 기간 집계(rolling 경계 포함) → (4) `WAITING_FOR_QUOTA`·retry_after_at 저장+재개 조건만 scheduler 재시도 → (5) 경고·잔여 UI+grant. 테스트: 동시 예약 경쟁(잔여 초과), timeout 과금 불명, 월 경계·시간대, rolling 경계, fallback 자체 한도 초과.

---

## 27. 최종 결론

중심은 "AI 직원 여러 명"이 아니라: 지속 직원·조직 모델+사용자 정의 직급·모델 풀 / 재개 가능 워크플로 / 교체 가능 다중 모델 런타임 / 격리 개발환경 / Git 기반 변경 관리 / 독립 품질 게이트 / 근거 기반 평가·문서 지식 / 예산·권한·시간 강제의 조합이다. 이 구조로 로컬에서 시작해 이후 분산·VM으로 확장해도 핵심 도메인 유지.

**최우선 수직 기능**: “발주 고객(콘솔에서는 사용자 자신)이 프롬프트와 요구 문서·디자인 첨부로 요구사항을 말하면, AI 직원들이 내부 협의 후 최소한의 구조화된 질문을 통해 요구사항을 확정하고, 이를 불변 기록으로 동결한 뒤, 스스로 작업을 분해·배정해 격리된 Docker 환경에서 구현하고 테스트와 리뷰를 거쳐 결과 리포트를 전달한다. 요구가 바뀌면 고객이나 직원들이 새 세션을 열어 같은 절차로 진화시킨다.” 이 흐름이 안정된 뒤 회의·평가·자율 유지보수를 확장한다.

---

## 28. 요구사항 명확화 파이프라인

### 28.1 배경과 포지션
골을 제출하는 쪽은 **고객 포지션**(§0 원칙 11)이다 — 콘솔에서는 항상 사용자 자신이다(v1.8; 외부 고객 중계 §5.26은 구현 보존·휴면). 고객은 "블로그 사이트 만들어줘" 같은 텍스트 프롬프트와 전문 자료 첨부(§28.7)로 요청하고, 기획·설계·구현·검증 방법은 직원들 내부 협의로 정한다. 문제는 **고객이 항상 온라인이 아니다**라는 점 — 웹은 실시간 푸시가 어렵고, 잦은 알림은 고객 경험을 해친다. 따라서:
1. 직원들 협의로 스스로 해결할 수 있는 것은 최대한 내부에서 진행.
2. 반드시 고객 입력이 필요한 지점만 **최소 구조화 질문**으로 추림.
3. 질문은 세션 큐에 쌓이고, 고객이 접속 시 확인·응답.
4. 고객 응답 전에도 직원들은 요구 이해·기술 비교·진행 계획 등 준비를 지속.
- 기존 `DiscussionMessage`의 `INTERVIEW_Q/INTERVIEW_A`(§5.18)는 세션 대화 로그 채널로 재활용; 구조화 질문 정본은 `ClarificationQuestion`.

### 28.2 고객 질문의 경계
**포함**: 기능 목록(필요성) / 품질·완성도 기준(MVP 먼저 vs 완성품) / 우선순위 / 제약(기한·비용·규칙) / 외부 연동 대상.

**미포함**: 기술 스택 / 아키텍처 방식 / 구현·검증 방법 / 내부 팀 사정(모델·예산·배정).

**"묻지 않는다"≠"받지 않는다" — 자발적 전문 요구 수용**:
1. **전문성 신호 판정**: goal_text·답변 기타·client_extra_note·골 첨부(§28.7)·중계 의견에서 전문적 내용(예: "Spring Boot + PostgreSQL", "검은색 계열 시안 첨부", "개인정보 암호화 필수")을 감지하면 통째로 "전문가" 단정이 아니라 **도메인별 신호**(기술 스택·보안·디자인·UX·기획 등)로 세분 기록. 사용자가 디자이너이거나 외부 고객이 시안·기술 문서를 먼저 제시하면 해당 영역 질문은 면제·간략화.
2. **제약으로 수용**: 신호 내용은 명시적 제약(`RequirementSet`에 `source=client_declared`·중계는 `client_relayed`)으로 기록. 암묵적 재평가·무시 금지. 이미 결정을 제시한 영역을 되묻지 않는다(면제; 필요 시 더 좁은 후속 질문만). 운영자의 기술 조언자 포지션 지시도 동일 취급(Decision 기록+내부 검토 후 수용).
3. **충돌 시 침묵 금지**: 안전·법률·품질 바닥선과 충돌하거나 모순되면 임의 진행 없이, 충돌을 **제품 수준 문구**(기술 대안 강요가 아닌 결과 차이·위험·일정 설명)로 담은 후속 질문 하나로 확인(체인 통과).
4. **항상 열려 있는 기입란**: waiting_client 세션마다 “전문적인 요구사항이 있으면 추가로 작성해주세요” 문구+자유 입력란 고정 표시(`client_extra_note`). 비워두면 효과 없음.
5. **원천**: goal_text / 답변 기타 / client_extra_note / 골 첨부 자료(§28.7) / 운영자 중계 의견 — `client_declared`(중계는 `client_relayed`)로 일관 기록.

### 28.3 질문 포맷: 최소 질문으로 최대 의도
한 질문마다 다양한 응답을 한 번에 얻도록 구성하되, 세션당 `max_questions`(기본 5) 상한. 질문 하나에 서로 다른 정보 섞지 않기.

예시 — "블로그 사이트 만들어줘":
```
질문 1 (single_choice): 어느 정도 수준에서 시작하고 싶으신가요?
 - (a) 먼저 간단한 MVP로 시작 (로그인/글쓰기 등 핵심만)
 - (b) 실제 서비스 가능한 완성품으로 한 번에
 - (c) 기타 — 직접 적어주세요 (텍스트 필드)

질문 2 (multi_choice): 원하는 기능을 모두 선택 (각 기능은 개별 체크박스)
 - [ ] 회원가입/로그인   - [ ] 글쓰기(마크다운 여부는 별도)   - [ ] 글 수정/삭제
 - [ ] 댓글             - [ ] 좋아요/이모지 반응            - [ ] 사진 업로드
 - [ ] 동영상 임베드/업로드  - [ ] 비밀글/암호화 보관        - [ ] 이웃(구독)/팔로우
 - [ ] 검색             - [ ] 기타 — 직접 적어주세요
```
기타(Other)는 **항상 제공**(`allow_other`) — 선택지 오류·누락 시 고객 직접 의견 창구(v1.4 핵심 동기).

세션 하단 고정 항목:
```
전문적인 요구사항이 있으면 추가로 작성해주세요.
(예: 특정 기술 강제, 보안/개인정보 규정, 성능 요건, 금지사항 등 — 없으면 비워도 됩니다)
[ 자유 입력란 ]
```
작성 시 전문성 신호로 분류 후 `source=client_declared` 제약 기록(§28.2).

### 28.4 검토 체인
질문은 체인 통과 후에만 `waiting_client`로 전달. 그 역할 기반 순차 체인:

```
[0단계: 고객·사용자 전문성 신호 선식별]
  goal_text + client_extra_note + 기타 자유 텍스트 + 골 첨부 요약(§28.7) + 중계 기록 분류.
  이미 결정·제약 제시한 영역(기술·디자인·보안 등)은 질문에서 빼거나 간략화,
  사유(면제/간략화, 근거 문장) 협의 기록. 남은 불확실 영역만 질문화.
[1] PLANNER/L4 (프로젝트 리드) 최초 작성
[2] ARCHITECT/L5 — "응답이 작업 범위·복잡도로 이어지는가? 기술 성격 과노출 아닌가?"
[3] QA_ENGINEER/L3 — "답변이 인수 조건으로 전환 가능한가? 모호함 없게?"
[4] SECURITY_DEVOPS/L3 — "개인정보·암호화·관제 관련? (해당 시에만)"
[5] PM 또는 기술문서/L2~L4 — "문장 쉽게 이해되는가? 전문 용어 노출 없는가?"
   → 통과 시 `waiting_client` 전송
```
통과 못한 질문은 다시 협의 회의(§13)로. 체인 순서는 구현 상수; 없는 역할은 건너뛰고 감사 기록. 고객 제시 전문 요구가 바닥선 충돌(§28.2.3)도 체인에서 처리 — 임의 진행 없이 제품 수준 후속 질문을 같은 체인에 통과.

### 28.5 오프라인 고객 지원·세션 관리
세션 라이프사이클 `open → waiting_client → review → frozen` / 접속 시 `waiting_client` 목록 상단 표시, `GET /users/{userId}/pending-sessions` / **MVP 푸시 없음** — 앱 전환 시 푸시+만료·요약 규칙 추가 / 장기 무응답: `assumption_if_skipped` 근거로 진행 여부를 체인이 판단 기록. 고객 원문(goal_text)은 항상 보존되므로, 이후 정정 요청 시 새 세션으로 대응. 세션은 파이프라인 개설(`opened_by_employee_id`)과 사용자 개설(`opened_by_user_id`, §28.6) 양쪽이며, 고객 포지션 사용자는 `open|waiting_client` 세션을 취소할 수 있다(Audit; §17).

### 28.6 동결(Freeze)·불변·진화
**동결 시점**: 고객 필수 질문 답 완료+체인이 기록 승인 / 또는 고객 명시 지시("이 정도면 진행") / 또는 **사용자 개설 세션에서 변경 내용이 이미 충분히 구체적**(질문 0개 경로: 체인은 기록 승인만 수행).
동결 즉시 `frozen`+RequirementSet 생성(불변). 이후:
- 질문·답변·기록 수정 API 차단(409+Audit)
- **요구는 살아있다 — 진화는 두 가지 출발점이 있다**: (a) 직원들이 새 요구·변경 필요를 감지하면 파이프라인이 새 세션 개설, (b) **사용자가 직접 새 세션을 개설**(`POST /projects/{id}/clarification-sessions`, §17; 텍스트 취지 노트+첨부, §28.7 입력과 동일). 새 세션은 `session_number+1`·`parent_session_id` 연결.
- 동시 진행 세션은 프로젝트당 1개(`open|waiting_client` 존재 시 개설 409 — 기존 세션 응답·취소 후 재시도; 취소는 고객 포지션 사용자, Audit)
- 기록이 달라지면 새 RequirementSet 버전 추가, 이전은 `superseded` 표시 보존(감사·회귀 분석 자료). 새 버전 동결 시 진행 중 오케스트레이션은 §29의 재계획 규칙 따름.

### 28.7 다중 모달 골 접수(텍스트+파일·이미지)
골·변경 요청 입력은 텍스트 단독이 아니라 첨부와 함께 온다: 요구 문서(PPT/PPTX·XLSX·HWP·PDF·MD/TXT/CSV), **디자인 자료(이미지 PNG/JPG/SVG, 와이어프레임·시안)**, 기타 참고 파일. 규칙:
1. **입력**: 초기 골(`POST /projects/{id}/goal`)과 사용자 개설 세션(§28.6) 모두 `{goalText?, attachments?}` 형태. 둘 다 비면 400.
2. **저장**: 첨부는 각각 `Artifact`(`artifact_type: document|image`)로 저장하고 `ClarificationSession.attachments`에 참조를 남긴다(`parse_status: parsed|unsupported|image_pending_vision`).
3. **형식 등급**: `native`(MD/TXT/CSV/JSON·PDF 텍스트 레이어)=자체 파서 / `parser`(XLSX·PPTX·DOCX·HWP)=형식별 파서 모듈, HWP는 best-effort / `image`=vision capability가 있는 모델 profile(§5.8) 배정 시에만 내용 해석. 지원 불가·파싱 실패는 **파일 원본 보존**+`unsupported` 기록+세션에 "파일 내용을 텍스트로 보강해주세요" 후속 요구 질문 추가(첨부+텍스트 병행 안내).
4. **추출과 근거**: 파서/모델이 뽑은 후보 요구는 0단계 선식별(§28.4)에 합류, 항목 근거에 첨부+슬라이드/시트/페이지/이미지 참조를 남긴다. 고객·사용자가 직접 제공한 자료의 명시적 기재는 `client_declared`, 직원이 해석·추론한 것은 `employee_derived`(§5.25). 파일이 질문을 대체하지 않는다 — 추출 누락·오해는 명확화 질문으로 교정.
5. **보안·한도**: 첨부는 정적 저장만, 실행 금지(스크립트·압축 포함); 첨부 내부 텍스트는 신뢰 불가 데이터 취급(§8.2) — 안의 지시가 정책을 바꾸지 못한다; 고객 데이터 접근 규칙(§21) 적용. 세션당 첨부 수와 용량 한도는 §23 설정(기본 10개/50MB), 초과 400.
6. **REQ-004 MVP 범위**: 첨부 전송은 `contentBase64`만 지원(`contentUri`는 400 — 원격 참조 수집은 이후 단계). 파싱은 native 중 UTF-8 텍스트(md/txt/csv/json)의 자체 파싱만 실제 구현되어 `parsed`; PDF 텍스트 레이어·parser 등급(xlsx/pptx/docx/hwp)은 **원본 보존+`unsupported`+보강 후속 요구 기록까지만**(파서 모듈은 이후 단계), 이미지는 `image_pending_vision` 기록(vision profile 배정은 §5.8 모델 토폴로지 이후). 파싱된 첨부 텍스트는 골 텍스트와 합류 자료로 0단계 신호 선식별(§28.4)에 사용되며, 요구 항목 근거에 `첨부 {파일명 파일}` 참조를 남긴다.

---

## 29. 자율 오케스트레이션

### 29.1 원칙
발주 고객(콘솔에서는 사용자 자신)이 직원마다 일일이 할당하지 않는다. 분해·배정·실행·재계획을 Orchestrator가 수행. 근거는 동결 RequirementSet뿐 — 근거 없는 기능 작업 생성 없음. 요구 진화(사용자 개설 세션 포함, §28.6)가 이어져도 이 원칙은 유지된다. 한도(라운드·예산·시간·재시도·격리·주요 승인)는 §13/§16/§21 정책 동일 유지. **사용자는 담당 직원을 지정하지 않는다** — 지시는 조직 전체에 대한 것이고, 구성·분해·배정은 조직이 정한다(콘솔 담당 지정 UI 없음, WEB-004). 직원 간 피드백·평가도 직원들이 자율·회의 프로토콜로 수행한다(§15).

### 29.2 사이클
```
[발주 고객 골 접수(텍스트+첨부 §28.7, 또는 사용자 개설 세션 §28.6)] → [명확화 진행·동결(§28)]
  → [PLANNER 작업 분석]
  → [분해: Epic/Story/Task 트리+의존성]
      (기존 Task/JPA parent_task_id·TaskDependency 재사용)
      (인수 조건은 RequirementSet 항목 참조)
  → [배정: 역할·직급·task_types 자동 매칭]
  → [실행: AgentRun 기반]
  → [리뷰·품질 게이트(§12, §10)]
  → [확정: 결과 요약+산출물 위험 평가 리포트를 고객에]
  → (요구 반영 불가·새 요구 발견 → 새 명확화 세션 개시; 파이프라인 개설)
```

**새 RequirementSet 버전이 승계되면**: 이전 버전 기반 Task 중 미시작은 재계획(분해·배정 갱신), 진행 중·완료는 유지하되 새 요구와의 충돌·영향을 재계획 리포트에 기록(감사). `replan_attempts` 한도 내(§23). **MVP 구현(ORCH-001/002)**: 트리거는 명시적 — `POST /requirements/{id}/trigger`(콘솔 [분해·배정 실행]); 동결 직후 자동 트리거는 후속. 분해·배정 결과·결정 근거는 OrchestratorCycle(§5.27)로 기록하고, 동일 세트 재트리거 409·replan 한도 2 초과 409로 멱등·상한을 지킨다. Mock은 기존 Task를 변경하지 않는다.

### 29.3 배정 정책
TaskType→직급 `task_types` 매칭 우선; 배정 실패 시 `BLOCKED`+사용자 조치 사유 기록. 배정 이력은 WorkflowRun 기록되므로, 이후 조직 변경(직급 재편·인원 변동) 시 영향 분석 가능.

**구현(Mock, ORCH-002)**: 등록 `active` 직급 중 `task_types`가 TaskType을 포함하는 직급을 찾고, 그 직급의 직원(상태 OFFLINE 제외) 중 미완료 업무(ASSIGNED/IN_PROGRESS/REVIEW) 수가 최소인 직원(동률은 이름 순)을 배정한다. 매칭 실패는 `BLOCKED`+`blocked_reason="배정 가능한 직원 없음 — {type} 역량 직급/직원 필요"`. EPIC/STORY는 배정하지 않는다(READY).

**팀 인지 배정(v1.9 구현)**: 직원이 속한 팀(`team_id`)이 있고, 해당 Task의 연관 업무(동일 Epic/Story 하위 또는 동일 프로젝트 내 최근 완료 Task)의 담당 팀과 일치하면 해당 팀 내 직원을 **최우선 후보**로 승격(가중치 -1000, 미완료 수 계산 전 적용). 팀 미배정(null=기본 팀) 직원은 팀 매칭 없이 전역 후보 풀에 포함. 팀 리드 미지정 팀은 최고 직급(동률 이름순) 직원을 임시 대표로 간주해 팀 간 회의·피드백에 참여시키고 기록에 명시. 팀 겸임 금지(단일 `team_id` 유지).

### 29.4 상태 요약
사이클은 지속 보고: 고객 응답 대기(`waiting_client`) / 실행 중 Task **제품 관점 요약**(코드 상세 아님) / 블로커·새 요구 필요. 조회는 `GET /projects/{id}/orchestration` — 응답 `{cycle, tasks, latestFrozen}`(§17)로 사이클 요약과 업무·배정 현황을 함께 제공한다.
