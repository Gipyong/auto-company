# AI SI Company Platform — DESIGN.md

> 버전: 1.4  
> 상태: 구현 기준 설계 초안 — Java/Spring Boot 백엔드 기준  
> 목표: 로컬 우선(Local-first)으로 실행 가능하며, 다른 PC·서버로 재현 가능하게 배포할 수 있는 AI 기반 소프트웨어 개발 조직 플랫폼  
> 변경 요약(v1.4): **사용자(=플랫폼 운영자)를 CEO가 아니라 외주 고객(Client) 포지션으로 명확화**한다. 고객은 프로젝트 요구사항만 제출하고, 설계·구현·검증·디자인 등 기술 결정은 AI 직원들이 내부 회의와 토론으로 자율 수행한다. 요구사항이 애매하면 직원들이 협의 후 **최소한의 질문으로 최대한의 의도를 뽑아내는 구조화된 질문**(기능별 개별 체크박스, 선택형, 자유 텍스트 병행)을 고객에게 전달하고, 고객은 오프라인 상태여도 세션 큐에 쌓인 질문을 나중에 확인·응답할 수 있다. 요구사항 확정 시점의 기록은 **동결(Freeze)되어 불변 기록**으로 남고, 추가 요구사항이 필요하면 새 세션을 연다. 요구사항 확정 후에는 직원들이 자율로 작업을 분해·배정·실행한다(§28 요구사항 명확화 파이프라인, §29 자율 오케스트레이션). 기존 직원/직급/모델/예산/workspace 설계는 유지하며, 도출된 의결 사항은 §25에 누적한다. v1.3의 피드백 전용 평가 시스템(§15)은 유지된다.
> 변경 요약(v1.3): 피드백 전용 평가 시스템 도입. 승진/강등 제거, 상호 피드백으로 업무 품질 개선. 피드백 컨텍스트는 적절한 크기(10KB/직원)에서 호환성 있게 유지(요약·가중치 감쇠·보존 기간). 자세한 내용은 §15를 참고한다.

---

## 0. 문서의 목적과 설계 원칙

이 문서는 여러 AI 모델을 직원으로 구성해 실제 SI 회사와 유사한 방식으로 요구사항 분석, 설계, 구현, 코드 리뷰, 검증, 문서화, 배포 준비 및 유지보수를 수행하는 시스템의 구현 기준이다. 구현자는 이 문서를 제품 요구사항, 도메인 모델, 런타임 구조, 보안 정책, 개발 순서 및 초기 기본값의 기준으로 사용한다.

### 핵심 원칙

1. **Local-first, portable-by-default**: 기본 실행은 로컬 PC 또는 자체 서버다. 설치 환경 차이는 컨테이너와 명시적 설정으로 흡수한다.
2. **Workflow state is durable**: AI 대화 기록만으로 작업을 관리하지 않는다. 모든 업무 상태, 승인, 실행, 비용, 결과를 영속화한다.
3. **Models are replaceable providers**: 직원의 정체성과 모델 제공자를 분리한다. 직원은 개별 모델이 아니라 직급(JobGrade)을 가지며, 가용 모델 풀은 직급에 연결된다. 모델을 교체해도 직원·직급·업무 이력은 유지된다.
4. **Evidence over consensus**: AI 다수의 동의보다 테스트, 정적 분석, 재현 절차, 검증 가능한 근거를 우선한다.
5. **Separation of duties**: 작성자 본인은 자신의 PR을 승인할 수 없다. 위험도에 따라 독립 검증을 요구한다.
6. **Bounded autonomy**: 직원은 설정된 권한, 예산, 재시도, 실행 시간, 네트워크 범위 내에서만 자율적으로 움직인다.
7. **Cost is a first-class resource**: 모델 호출뿐 아니라 컨테이너, 재시도, 재작업 및 검증 비용도 기록한다.
8. **Human-readable artifacts**: 모든 중요한 결정은 ADR, 이슈, PR, 테스트 결과, 문서 버전과 연결한다.
9. **Reproducibility**: 설정, 프롬프트, 도구 버전, 모델 식별자, Git 커밋 및 테스트 환경을 가능한 한 기록한다.
10. **Safe defaults**: 설치 직후 작동하는 기본 템플릿을 제공하되, 위험한 권한이나 자동 배포는 기본 비활성화한다.
11. **Client faces requirements only**: 사용자는 외주 고객(Client) 포지션이다. 고객에게 *먼저* 묻는 것은 프로젝트의 요구사항(제품 범위, 우선순위, 품질 기준, 제약)뿐이다. 설계 방식, 기술 스택 선택, 구현 검증 방법 등 기술적 결정은 고객에게 질문으로 전가하지 않고 직원들의 내부 회의/Decision으로 처리한다. 다만 "묻지 않는다"는 "받지 않는다"가 아니다: 고객이 자발적으로 전문적 요구사항을 제시하면 그것은 전문성 신호이며, 직원들은 그 내용을 명시적 제약으로 수용해 기록하고 해당 영역의 불필요한 질문을 면제한다(§28.2). 고객의 시간과 주의는 가장 희소한 자원으로 취급하며, 질문은 최소 개수로 유지한다(§28).
12. **Requirements are frozen records**: 요구사항 명확화 세션이 동결되면 그 기록은 변경 불가능(immutability)한 기록 문서다. 과거 요구사항을 몰래 고치지 않고, 필요 시 새 세션·새 버전으로만 진화시킨다. 모든 후속 작업 분해는 동결된 요구사항 기록을 근거로 참조한다(§28).

### 설계상 중요한 구분

- `Employee`: 지속되는 가상 직원 정체성, 직무, 직급 참조, 권한, 평가 및 이력.
- `JobGrade`: 사용자가 구성하는 직급. 순서, 책임 범위, 담당 업무 유형, 가용 모델 풀을 가진다.
- `AgentRun`: 특정 작업을 수행하기 위한 일회성/재개 가능한 실행.
- `Task`: 사람이 이해할 수 있는 업무와 완료 조건.
- `WorkflowRun`: 여러 Task와 승인 단계를 묶는 장기 실행 프로세스.
- `ClarificationSession`: 고객 요구사항을 뽑아내기 위해 직원들이 협의해 만드는 질문 세트와 고객 응답의 단위(§28).
- `RequirementSet`: 세션 동결 시점의 요구사항 기록 영역. 불변이며 이후 작업 분해의 근거가 된다(§28).
- `OrchestratorCycle`: 골(Goal) 접수부터 작업 분해, 배정, 실행, 재계획까지의 자율 실행 사이클 기록(§29).
- `ModelProfile`: 모델 제공자·모델 ID·가격·능력·한도 설정.
- `GradeModel`: 특정 직급에 배정된 가용 모델과 그 사용 우선순위.
- `Workspace`: Git 작업 트리와 격리된 개발 실행환경.
- `Artifact`: 코드, 문서, 테스트 결과, 빌드 결과, 보고서 등 검증 가능한 산출물.

직원은 영속적인 캐릭터가 아니라, 정책과 상태를 가진 도메인 엔티티다. 에이전트 실행은 매번 별도 기록하며, 메모리와 업무 상태를 저장해 중단 후 복구할 수 있어야 한다.

---

## 1. 제품 범위

### 1.1 MVP에 포함

- 로컬 웹 콘솔
- 회사·조직·직원 관리
- 직무/직급 관리와 직급별 가용 모델/도구 권한 설정
- **고객 골(Goal) 입력**: 고객이 단일 프롬프트로 프로젝트 요청("블로그 사이트 만들어줘" 등)을 제출
- **요구사항 명확화 파이프라인**: 직원들 내부 협의 → 고객 대상 구조화된 질문 세션 → 응답 수집 → 요구사항 동결(§28)
- **자율 오케스트레이션**: 동결된 요구사항으로 Epic/Story/Task 분해와 직원별 배정을 플랫폼 스스로 수행. 고객/CEO가 직원마다 일일이 할당하지 않음(§29)
- 프로젝트 및 Git 저장소 연결
- 직원별 모델 호출 및 도구 실행(초기에는 Mock 모델로 파이프라인 검증, 이후 실제 provider 어댑터 연결)
- 작업별 격리 Docker workspace
- 브랜치 생성, 변경사항 확인, PR 생성 또는 로컬 PR 기록
- 코드 리뷰와 필수 테스트 게이트
- 문서 관리 및 프로젝트 질의응답
- 모델 비용 및 실행 로그
- API 할당량/실패 시 대기·재시도·허용된 대체 모델 사용
- 중단·재개·실패 복구
- 안전한 기본 권한 및 감사 로그

### 1.2 MVP 이후

- 직무별 동료 평가와 성과 관측
- 정교한 회의/토론 프로토콜
- 다중 프로젝트 리소스 스케줄링
- 선택적 VM 실행기
- 외부 Git 호스트의 완전한 webhook 동기화
- 지속적 운영 모니터링 및 장애 대응
- 여러 노드로 확장하는 분산 worker
- 성과 데이터 기반 모델/조직 최적화

### 1.3 MVP에서 의도적으로 하지 않을 것

- 무제한 자율 실행
- AI 다수결만으로 코드 승인
- 기본 설정에서 프로덕션 자동 배포
- Docker 컨테이너를 완전한 보안 경계로 간주
- 특정 상용 모델이나 특정 벤더 SDK에 도메인 로직을 종속
- **고객에게 내부 기술 선택(프레임워크, DB, 라이브러리 등)을 질문으로 전가**하는 것. 고객 질문은 요구사항에 한한다(§28.2)
- **알림 푸시 폭탄**: 웹에서 실시간 푸시가 어렵고 알림이 잦으면 고객 경험을 해치므로, 미응답 질문은 세션 큐로 관리하고 고객이 접속했을 때 확인하는 방식을 기본으로 한다
- “직급이 높으면 무조건 더 좋은 모델”이라는 고정 규칙 (배정 여부는 사용자가 판단하며 시스템이 강제하지 않는다)
- 직원 수가 많을수록 품질이 좋아진다고 가정
- 평가 점수만으로 직급을 자동으로 강등/승진하는 것
- 직급-모델 배정을 시스템이 자동으로 재구성하는 것
- 모든 작업에서 장시간 다자 토론 실행

---

## 2. 기본 설치 및 배포 모델

### 2.1 권장 기본 배포

초기 버전은 **Docker Compose 기반 로컬 설치**를 기본으로 한다.

구성 서비스:

- `web`: 사용자 UI
- `api`: REST API 및 도메인 서비스
- `worker`: 워크플로와 에이전트 작업 실행
- `postgres`: 영속 데이터
- `redis`: 짧은 큐/캐시/분산 잠금(선택적이지만 Compose 기본 포함 가능)
- `workspace-runner`: 제한된 개발 컨테이너를 실행하는 전용 서비스 또는 별도 호스트 프로세스
- `ollama` 또는 다른 로컬 모델 서버: 선택 사항이며 기본 필수 아님

초기 개발에서는 API와 worker를 하나의 프로세스로 시작할 수 있지만, 인터페이스는 분리한다. 안정화 단계에서 worker를 별도 프로세스로 분리한다. 실제 코드 실행 권한을 가진 workspace runner는 API 서버와 같은 권한/컨테이너에 넣지 않는 것을 원칙으로 한다.

### 2.2 설치 경험

초기 목표 설치 흐름:

1. 저장소 clone 또는 릴리스 패키지 다운로드
2. `.env.example`을 참고해 `.env` 생성
3. `docker compose up -d`
4. 브라우저에서 로컬 주소 접속
5. 최초 관리자 계정 생성
6. 모델 제공자 설정 또는 로컬 모델 선택
7. Git 저장소 등록
8. 기본 회사 템플릿을 선택하고 프로젝트 생성
9. 사전 점검(run preflight) 수행 후 첫 작업 시작

API 키는 UI에 평문으로 다시 표시하지 않는다. 로컬 설치에서는 환경변수 또는 암호화된 비밀 저장소를 지원한다. `.env`와 비밀 파일은 Git에 커밋하지 않는다.

### 2.3 포터빌리티 기준

- OS별 차이는 Docker/Compose 및 명시적인 host capability로 처리한다.
- 데이터는 named volume 또는 설정 가능한 호스트 디렉터리에 저장한다.
- 백업/복구 명령을 제공한다.
- `config.yaml`과 환경변수로 기본 설정을 바꿀 수 있게 한다.
- 모델, Git 호스트, workspace runner는 어댑터 인터페이스 뒤에 둔다.
- CPU/RAM/디스크 최소 요구량을 설치 점검으로 알려준다.
- macOS 빌드나 Windows 전용 테스트 등은 Linux Docker만으로 수행할 수 있다고 가정하지 않는다. 필요한 OS executor가 없으면 해당 작업을 `BLOCKED_CAPABILITY`로 둔다.

---

## 3. 상위 아키텍처

핵심 API와 도메인 상태는 Java + Spring Boot가 소유한다. 별도 worker는 같은 백엔드 코드베이스에서 시작하되 독립 프로세스로 실행할 수 있게 설계한다. AI provider 호출과 workspace 실행은 명시적 인터페이스 뒤로 격리한다. Python 서비스는 초기 배포의 필수 구성요소가 아니다.

```text
┌─────────────────────────────────────────────────────────────┐
│                       Client Console                          │
│ Goal 입력 · 요구사항 세션(질문/응답) · 진행 상황 요약            │
│ Dashboard · Org Chart · Employees · Projects · Tasks · PRs   │
│ Meetings · Documents · Costs · Policies · Audit              │
└──────────────────────────────┬──────────────────────────────┘
                               │ REST / SSE
┌──────────────────────────────▼──────────────────────────────┐
│                         API Layer                            │
│ Auth · Validation · RBAC · Domain Services · Audit           │
└───────────────┬─────────────────────────────┬────────────────┘
                │                             │
┌───────────────▼───────────────┐ ┌───────────▼────────────────┐
│ Orchestration / Scheduler     │ │ Read Models / Query        │
│ Goal intake → 명확화 → 분해    │ │ Dashboard · Search · RAG   │
│ Task state machine            │ │ Documents · Project status │
│ Retry · Pause · Resume        │ └────────────────────────────┘
│ Dependency management         │
│ 자동 배정 · 재계획 · 에스컬레이션 │
└───────────────┬───────────────┘
                │
┌───────────────▼──────────────────────────────────────────────┐
│                Requirement Clarification Engine                │
│ 내부 협의 회의 개설 · 질문 구성(최소 질문) · 역할 검토 체인        │
│ 세션 큐(고객 오프라인 대응) · 동결(Freeze) · 요구사항 기록         │
└───────────────┬──────────────────────────────────────────────┘
                │
┌───────────────▼──────────────────────────────────────────────┐
│                     Agent Runtime                            │
│ Context builder · Model router · Tool broker · Memory        │
│ Token/cost budget · Structured output · Run tracing           │
└───────────────┬───────────────────────────────┬──────────────┘
                │                               │
┌───────────────▼───────────────┐ ┌─────────────▼──────────────┐
│ Model Adapter Registry        │ │ Tool / Workspace Broker    │
│ Hosted API · OpenAI-compatible│ │ Git · Shell · Tests · Files │
│ Local model · Mock provider   │ │ Browser (later) · VM later │
└───────────────────────────────┘ └─────────────┬──────────────┘
                                                │
                                  ┌─────────────▼──────────────┐
                                  │ Isolated Workspace Runner  │
                                  │ Docker · Resource limits    │
                                  │ Per-task worktree/branch    │
                                  └─────────────────────────────┘

Shared foundations: PostgreSQL · Redis · Artifact storage · Audit log
```

### 3.1 논리 모듈

1. **Organization Service**: 직원, 직무, 직급(JobGrade), 직급별 가용 모델, 팀, 권한
2. **Project Service**: 프로젝트 설정, 저장소, 목표, 환경
3. **Goal & Clarification Service**: 고객 골 접수, 명확화 세션, 구조화 질문, 고객 응답, 요구사항 동결 기록(§28)
4. **Orchestration Service**: 골→작업 분해, 자동 배정, 실행 사이클, 재계획, 고객 에스컬레이션(§29)
5. **Task Service**: 백로그, 의존성, 완료 조건
6. **Workflow Engine**: 상태 전이, 재시도, 재개, 타임아웃
7. **Agent Runtime**: 컨텍스트 구성, 모델 호출, 도구 호출, 결과 파싱
8. **Model Router**: 모델 선택, 비용 추정, 제한 및 fallback
9. **Tool Broker**: 권한 검증 후 도구 실행 요청
10. **Workspace Manager**: 격리 환경 생성/정리/제한
11. **Git Integration**: 브랜치, diff, 커밋, PR/리뷰 동기화
12. **Quality Gate**: 테스트, 린트, 타입 검사, 보안 검사, 승인 정책
13. **Knowledge & Docs**: 문서, 검색, 인덱싱, 최신성 확인
14. **Evaluation Service**: 평가 데이터, 근거, 평가자 (평가는 성과 관측용이며 직급을 자동 변경하지 않는다)
15. **Budget & Usage**: 모델 호출량, 금액, 실행 자원, 프로젝트 예산
16. **Audit & Observability**: 중요한 변경, 추적 ID, 오류, 메트릭

모듈은 초기에 하나의 모놀리식 백엔드 코드베이스 안에 구현하되, 명확한 패키지 경계와 인터페이스를 유지한다. 초기부터 마이크로서비스로 쪼개지 않는다.

---

## 4. 권장 기술 스택

아래는 실용적인 기본안이며, 저장소 구현 시작 전에 버전을 고정하고 호환성을 확인한다.

- **Frontend**: Next.js + TypeScript
- **핵심 백엔드**: Java 21 LTS 이상 + Spring Boot 3.x 계열의 지원되는 안정 버전. 실제 저장소 생성 시 Java/Spring Boot 호환 조합을 검증하고 버전을 고정한다.
- **빌드 도구**: Maven (실제 저장소가 `backend/pom.xml` 기준 Maven으로 구현됨. 빌드 도구 버전은 저장소에 고정한다)
- **API 계약/검증**: Spring MVC 또는 필요 시 WebFlux, Jakarta Bean Validation, OpenAPI 생성 및 계약 테스트. 기본값은 Spring MVC이며, 비동기 I/O가 필요한 이유가 입증될 때 WebFlux를 선택한다.
- **보안**: Spring Security, 명시적 RBAC, CSRF/CORS 설정, 비밀정보 외부화
- **DB 접근 / migrations**: Spring Data JPA + Hibernate, Flyway. 복잡한 집계나 잠금 쿼리는 JDBC/jOOQ 등으로 명시적으로 보완 가능
- **Database**: PostgreSQL
- **Queue/cache**: 초기에는 PostgreSQL 기반 durable job/lease를 기준으로 구현하고 Redis는 캐시·짧은 큐·분산 조정이 실제 필요할 때 사용한다. Redis를 업무 상태의 유일한 저장소로 사용하지 않는다.
- **Workflow**: MVP는 명시적 DB 상태 머신 + worker lease + transactional outbox. 장기 실행/분산 복구 요구가 검증되면 Temporal 도입을 검토한다.
- **Agent execution**: 자체 얇은 런타임과 provider adapter. 프레임워크의 agent object를 도메인 엔티티로 노출하지 않는다.
- **Git**: Java ProcessBuilder 등으로 Git CLI를 인자 배열 방식으로 실행한다. 셸 문자열 결합을 금지하고, 실행 시간·출력 크기·환경변수를 제한한다.
- **Workspace**: Docker Engine/Compose를 통한 별도 runner 경계
- **Document search**: PostgreSQL full-text search; 필요 시 pgvector 추가
- **Observability**: Spring Boot Actuator, Micrometer, OpenTelemetry, 구조화 로그, correlation/trace ID
- **Testing**: JUnit 5, AssertJ, Mockito, Spring Boot Test, Testcontainers, API contract tests, frontend tests, end-to-end smoke tests
- **Packaging**: Docker Compose, multi-stage container build, `.env.example`, versioned config, backup/restore scripts
- **Python 사용 원칙**: Python은 필수 서버 구성요소가 아니다. 특정 Python 전용 AI/ML 라이브러리가 실질적으로 필요할 때에만 독립적인 선택형 worker로 추가하며, 핵심 업무 상태와 권한·예산 정책의 원본은 Java 백엔드가 소유한다.

### 기술 선택 규칙

- 특정 프레임워크의 agent object를 도메인 엔티티로 사용하지 않는다.
- DB 스키마와 모델 SDK 타입을 직접 결합하지 않는다.
- 외부 API 호출은 timeout, retry policy, idempotency key를 갖는다.
- 모든 시간은 UTC로 저장하고 UI에서 로컬 시간으로 표시한다.
- 금액은 부동소수점 대신 Decimal 또는 정수 기반 단위로 저장한다.
- JSON 필드는 검색·제약·관계가 필요한 핵심 정보를 대체하지 않는다.

### 4.1 Java 백엔드 구조 규칙

- **모듈형 모놀리스**로 시작한다. 각 모듈은 `api`, `application`, `domain`, `infrastructure`의 의존 방향을 지키며, 도메인 계층이 Spring/JPA/외부 SDK에 직접 의존하지 않도록 한다.
- 패키지-by-feature를 기본으로 하되, 공통 계층은 실제 공유되는 계약만 담는다. 거대한 `common` 패키지를 만들지 않는다.
- JPA 엔티티를 API 응답으로 직접 반환하지 않는다. 요청/응답 DTO와 도메인 객체를 분리하고, 엔티티의 lazy-loading이 API 직렬화에 노출되지 않게 한다.
- 트랜잭션 경계는 application service/use case에 둔다. 외부 모델 API, Git, Docker 호출을 DB 트랜잭션 안에서 실행하지 않는다.
- DB 트랜잭션이 필요한 예약/정산 작업은 짧게 유지한다. quota 경쟁 제어는 조건부 UPDATE, 잠금 또는 serializable/재시도 가능한 명시적 전략으로 구현하고 동시성 통합 테스트를 작성한다.
- 장기 작업은 HTTP 요청 스레드에 붙잡아 두지 않는다. `worker`가 DB에 저장된 작업과 lease를 가져가 실행하고 heartbeat, lease 만료, retry/backoff, idempotency를 관리한다.
- Spring `@Async`만을 영속 작업 큐로 취급하지 않는다. 프로세스 재시작 후에도 작업이 복구되어야 한다.
- 설정은 `@ConfigurationProperties`와 Bean Validation으로 검증하고, 비밀정보는 로그에 출력하지 않는다.
- 공급자 SDK는 infrastructure 어댑터 안에 가두며, application/domain 모듈은 내부 `ModelAdapter` 계약만 사용한다.
- Java/Spring Boot/JDBC/DB 드라이버의 호환 버전을 lock/build metadata에 고정하고, 컨테이너 빌드와 CI에서 동일한 테스트를 수행한다.

---

## 5. 도메인 모델 및 객체 속성

모든 주요 엔티티는 `id`(UUID), `created_at`, `updated_at`을 기본으로 갖는다. 삭제는 감사 이력이 필요한 엔티티에서 soft-delete 또는 상태 변경으로 처리한다. 조직/프로젝트 간 데이터 경계를 모든 조회와 변경에서 검증한다.

### 5.1 Organization

- `id: UUID`
- `name: str`
- `slug: str` — unique
- `description: str | null`
- `settings_json: JSONB` — 조직 기본 정책
- `status: active | archived`
- `created_at`, `updated_at`

### 5.2 User

사람 사용자 및 CEO 계정.

- `id: UUID`
- `email: str` — unique
- `display_name: str`
- `password_hash: str | null` — 로컬 인증 사용 시
- `auth_provider: local | oidc | none`
- `role: owner | admin | operator | viewer`
- `is_active: bool`
- `last_login_at: datetime | null`

초기 로컬 설치는 최초 사용자 생성 마법사를 제공한다. 기본 비밀번호를 소스 코드나 문서에 넣지 않는다.

### 5.3 Team

- `id`, `organization_id`
- `name`, `slug`, `description`
- `team_lead_employee_id: UUID | null`
- `purpose: str`
- `status: active | archived`

### 5.4 Employee

- `id`, `organization_id`, `team_id: UUID | null`
- `employee_code: str` — 조직 내 unique
- `display_name: str`
- `job_family: developer | architect | product | designer | qa | security | devops | technical_writer | analyst | operations | custom`  

  **구현 노트**: 현재 구현된 초기 직무 집합은 `Role` 열거형 `{ PLANNER, DEVELOPER, DESIGNER, PROJECT_MANAGER, TECHNICAL_WRITER, QA_ENGINEER, ARCHITECT, SECURITY_DEVOPS }`다. 위 `job_family`는 확장 목록이며, 현재 구현과 충돌 시 구현 값을 기준으로 읽는다(§28.4 검토 체인은 이 Role 값들로 구성한다)
- `specialties: List<String>` — 예: `python`, `react`, `postgresql`, `security`
- `grade_id: UUID` — 소속 직급(`JobGrade`) 참조. 직급명·순서·책임 범위·가용 모델은 직급이 소유한다
- `employment_status: active | paused | on_leave | retired`
- `availability_status: available | working | waiting | rate_limited | blocked`
- `system_profile: text` — 직무/업무 원칙. 민감정보는 포함하지 않음
- `tool_policy_id: UUID`
- `memory_policy: JSONB`
- `manager_employee_id: UUID | null`
- `created_by_user_id: UUID`
- `retired_at: datetime | null`

**중요**: 직원은 개별 기본 모델이나 개별 모델 정책을 갖지 않는다. 직원은 직급 하나를 가지며, 사용 가능한 모델은 그 직급의 가용 모델 풀(§5.9)로 결정된다. 직급의 품질 등급과 모델 품질 등급은 별개다. 직급 부여·변경은 자동화하지 않고 사용자(CEO/관리자)가 수동으로 수행한다(§15.3).

### 5.5 EmployeeSkill

- `id`, `employee_id`
- `skill_name: str`
- `proficiency: 1..5`
- `confidence: float`
- `evidence_refs: JSONB` — PR, 테스트, 작업 참조
- `last_assessed_at: datetime`

역량은 자기 선언과 검증된 역량을 구분한다. 초기에는 자기 선언을 사용하되 `confidence`를 낮게 두고, 산출물 기반 평가로 갱신한다.

### 5.6 JobRole / JobGrade

직무(JobRole)와 직급(JobGrade) 규칙은 코드에 하드코딩하지 않고 설정 가능한 엔티티/템플릿으로 관리한다.

`JobRole`:
- `id`, `organization_id | null` (전역 템플릿 가능)
- `name`, `description`
- `default_skills: JSONB`
- `allowed_tool_policy_ids: JSONB`
- `evaluation_rubric_id: UUID | null`

`JobGrade` (직급):
- `id`, `organization_id`
- `code: str` — 조직 내 unique. 예: `L1`, `L2`, `principal`
- `name: str` — UI 표시용 직급명
- `ordinal: int` — 직급 순서. 값이 클수록 상위 직급. 조직 내 unique
- `responsibilities: text` — 이 직급의 책임 범위. 높은 직급은 설계·검증·리뷰 등 복잡하고 정확성이 중요한 업무에 중점
- `task_types: JSONB` — 이 직급이 담당할 수 있는 업무 유형(`TaskType`) 목록
- `model_selection_mode: priority | task_based | budget_optimized` — 가용 모델 풀에서 모델을 고르는 방식. 기본 `priority`(우선순위 순)
- `is_template_default: bool` — 기본 템플릿에서 생성된 직급인지 여부
- `status: active | archived`
- `created_at`, `updated_at`

직급은 사용자가 직접 구성한다. 기본 템플릿은 5단계(L1~L5)를 제공하지만, 개수를 늘리거나 줄이고 이름·책임·담당 업무를 바꿀 수 있다. 직급의 순서는 `ordinal`로 관리하며, 삭제는 해당 직급에 배정된 직원이 없을 때만 허용한다(직원이 있으면 먼저 다른 직급으로 재배정해야 한다). 직급별 가용 모델은 별도 관계(§5.9 `GradeModel`)로 관리한다.

### 5.7 ModelProvider

- `id`, `organization_id`
- `name: str` — 예: OpenAI-compatible, Anthropic, local server
- `provider_type: openai_compatible | anthropic | ollama | custom`
- `base_url: str | null`
- `secret_ref: str | null` — API 키 자체를 DB에 평문 저장하지 않음
- `enabled: bool`
- `health_status: unknown | healthy | degraded | unavailable`
- `rate_limit_policy: JSONB`
- `created_at`, `updated_at`

API 키는 환경변수 또는 암호화된 secret store에서 읽는다. 로그와 UI에 절대 출력하지 않는다.

### 5.8 ModelProfile

- `id`, `provider_id`
- `name: str`
- `provider_model_id: str`
- `capabilities: List<String>` — `code`, `reasoning`, `vision`, `tool_calling`, `json_mode` 등
- `quality_tier: 0..3`
- `input_cost_per_million: Decimal | null`
- `output_cost_per_million: Decimal | null`
- `context_window: int | null`
- `max_output_tokens: int | null`
- `supports_streaming: bool`
- `supports_tool_calling: bool`
- `supports_json_schema: bool`
- `latency_class: low | medium | high | unknown`
- `local_only: bool`
- `enabled: bool`
- `metadata_json: JSONB`

가격은 관리자가 확인한 값만 입력한다. 가격이 불명확하면 비용을 0으로 가정하지 않고 `unknown`으로 표시하며, 예산 정책상 사용을 제한할 수 있다.

### 5.9 GradeModel (직급별 가용 모델)

직원별 모델 정책 대신 직급별 가용 모델 풀을 사용한다.

- `id`, `grade_id`, `model_profile_id`
- `priority: int` — 직급 내 사용 우선순위. 1이 최우선
- `enabled: bool` — 일시적으로 풀에서 제외
- `created_at`, `updated_at`
- unique constraint: `(grade_id, model_profile_id)`

규칙:
- 직원은 자신의 직급에 배정된 `GradeModel` 중 하나를 사용한다. 풀 밖의 모델은 라우터가 선택하지 않는다.
- 풀과 우선순위는 사용자가 직접 설정한다. 높은 직급에 더 성능이 좋은 모델을 배정할지, 비용이 낮은 모델을 배정할지는 사용자 판단이며 시스템이 강제하지 않는다.
- 모델 선택 방식은 직급의 `model_selection_mode`를 따른다. 초기 구현은 `priority`(우선순위 순)를 사용하고, `task_based`(작업 특성에 따른 선택)와 `budget_optimized`는 이후 확장한다. 선택 로직의 세부 규칙은 §9.2에서 정의한다(현재는 데이터 구조와 우선순위만 확정).
- 프로젝트·조직 정책은 이 풀 위에 추가 제한으로만 작용할 수 있다. 더 제한적인 정책이 항상 우선한다.
- 직원별 예산·토큰 한도는 모델 선택과 별개로 `BudgetPolicy`/`UsageLedger`(§5.21)에서 관리한다.

### 5.10 Project

- `id`, `organization_id`
- `name`, `slug`
- `description`
- `goal`
- `status: draft | planning | active | paused | blocked | completed | archived`
- `repository_provider: local_git | github | gitlab | other`
- `repository_url: str | null`
- `repository_path: str | null` — 로컬 경로는 allowlist 검증
- `default_branch: str`
- `tech_stack: JSONB`
- `definition_of_done: JSONB`
- `quality_policy_id`
- `budget_policy_id`
- `workspace_policy_id`
- `document_policy_id`
- `owner_user_id`
- `project_lead_employee_id: UUID | null`
- `start_at`, `target_end_at`
- `metadata_json: JSONB`

프로젝트 저장소의 경로가 허용된 workspace root 밖을 가리키지 못하게 한다. symlink를 통한 경로 탈출도 검증한다.

### 5.11 Epic / Story / Task

공통 속성은 가능한 한 공통 테이블 또는 명확한 상속 구조로 구현한다. MVP에서는 단일 `Task` 테이블에 `task_type`을 두어도 된다.

`Task`:
- `id`, `project_id`
- `parent_task_id: UUID | null`
- `task_type: epic | story | task | bug | spike | documentation | review | test`
- `title`, `description`
- `acceptance_criteria: JSONB` — 검증 가능한 조건 목록
- `status: draft | ready | assigned | in_progress | review | blocked | waiting_for_quota | done | failed | cancelled`
- `priority: critical | high | normal | low`
- `risk_level: low | medium | high | critical`
- `complexity: 1..5`
- `assignee_employee_id: UUID | null`
- `reviewer_employee_ids: JSONB`
- `created_by_user_id: UUID | null`
- `created_by_employee_id: UUID | null`
- `estimated_effort: int | null`
- `due_at: datetime | null`
- `blocked_reason: str | null`
- `attempt_count: int`
- `max_attempts: int`
- `max_cost: Decimal | null`
- `result_summary: text | null`
- `output_artifact_ids: JSONB`
- `related_commit_shas: JSONB`
- `completed_at: datetime | null`

`TaskDependency`:
- `id`, `task_id`, `depends_on_task_id`
- `dependency_type: blocks | relates_to`
- 순환 의존성은 생성 시 거부한다.

### 5.12 WorkflowRun

- `id`, `project_id`, `workflow_type`
- `status: queued | running | waiting | paused | completed | failed | cancelled`
- `current_step`
- `input_json`, `result_json`
- `initiated_by_user_id`
- `correlation_id`
- `started_at`, `finished_at`
- `failure_code`, `failure_summary`
- `retry_count`
- `checkpoint_json`

외부 호출 전에 작업 상태를 저장한다. 재실행 시 중복 커밋이나 중복 작업이 생기지 않도록 단계별 idempotency key를 사용한다.

### 5.13 AgentRun

- `id`, `employee_id`, `task_id`, `workflow_run_id`
- `status: queued | running | waiting_for_provider | waiting_for_quota | succeeded | failed | cancelled`
- `model_profile_id`
- `prompt_template_version`
- `tool_policy_snapshot: JSONB`
- `input_tokens`, `output_tokens`
- `estimated_cost`, `actual_cost: Decimal | null`
- `started_at`, `finished_at`
- `latency_ms`
- `provider_request_id: str | null`
- `trace_id`
- `error_code`, `error_summary`
- `result_artifact_ids: JSONB`

원문 대화/프롬프트는 민감정보가 포함될 수 있으므로 별도 보존 정책을 둔다. 기본 로그에는 비밀정보를 기록하지 않는다.

### 5.14 ToolPolicy / ToolExecution

`ToolPolicy`:
- `id`, `organization_id | null`
- `name`
- `allowed_tools: List<String>`
- `allowed_commands: JSONB`
- `allowed_paths: JSONB`
- `network_mode: none | allowlist | unrestricted` — 기본 `none` 또는 엄격한 allowlist
- `max_execution_seconds`
- `max_cpu`, `max_memory_mb`, `max_disk_mb`
- `can_write_repository: bool`
- `can_create_pr: bool`
- `can_merge_pr: bool` — 기본 false
- `can_deploy: bool` — 기본 false
- `requires_approval_for: JSONB`

`ToolExecution`:
- `id`, `agent_run_id`, `tool_name`
- `arguments_redacted: JSONB`
- `status`
- `started_at`, `finished_at`
- `exit_code`
- `stdout_artifact_id`, `stderr_artifact_id`
- `resource_usage_json`
- `approval_id: UUID | null`
- `error_summary`

### 5.15 Workspace

- `id`, `project_id`, `task_id`, `employee_id`
- `executor_type: docker | vm | host_restricted`
- `status: provisioning | ready | running | stopped | cleanup_pending | destroyed | failed`
- `image_ref`
- `container_id: str | null`
- `host_capability: linux | windows | macos | gpu | other`
- `repository_path`
- `branch_name`
- `base_commit_sha`
- `resource_limits_json`
- `network_policy`
- `created_at`, `last_used_at`, `destroyed_at`
- `cleanup_status`

Workspace는 작업 단위가 기본이다. 직원별 장기 컨테이너를 기본으로 만들지 않는다. 필요하면 직원별 캐시/설정은 유지하되 실행환경은 작업별로 새로 만들거나 재사용 정책에 따라 초기화한다.

### 5.16 GitBranch / PullRequest / Review

`GitChange`:
- `id`, `project_id`, `task_id`, `workspace_id`
- `branch_name`, `base_branch`, `base_sha`, `head_sha`
- `commit_shas: JSONB`
- `diff_artifact_id`
- `status: working | ready_for_review | review_requested | changes_requested | approved | merged | abandoned`

`PullRequest`:
- `id`, `project_id`, `git_change_id`
- `external_pr_id: str | null`
- `title`, `description`, `url: str | null`
- `status: draft | open | changes_requested | approved | merged | closed`
- `author_employee_id`
- `reviewer_employee_ids: JSONB`
- `checks_status: pending | passing | failing | unknown`
- `merged_commit_sha`
- `created_at`, `merged_at`

`Review`:
- `id`, `pull_request_id`, `reviewer_employee_id`
- `verdict: approve | request_changes | comment`
- `findings: JSONB` — 파일, 라인, 심각도, 근거, 수정 제안
- `summary`
- `confidence: float`
- `created_at`
- `supersedes_review_id: UUID | null`

작성자 본인의 승인 및 같은 직원의 자기 리뷰를 금지한다. 고위험 변경은 서로 다른 직원 2명의 승인 또는 지정된 시니어 승인을 요구할 수 있다.

### 5.17 QualityGate / TestRun

`QualityPolicy`:
- `id`, `name`
- `required_checks: JSONB` — unit, integration, lint, typecheck, security, build 등
- `minimum_coverage: Decimal | null`
- `required_review_count`
- `require_independent_reviewer: bool`
- `high_risk_extra_checks: JSONB`
- `allow_flaky_retry: bool`
- `merge_requires_human: bool` — 기본 true 또는 로컬 개발 모드에서 제한적 false

`TestRun`:
- `id`, `project_id`, `task_id`, `pull_request_id`
- `check_name`
- `status: queued | running | passed | failed | skipped | error`
- `command_template_id`
- `exit_code`
- `duration_ms`
- `report_artifact_id`
- `failure_summary`
- `environment_fingerprint`
- `started_at`, `finished_at`

테스트 명령은 저장소의 임의 텍스트를 무조건 호스트에서 실행하지 않는다. 프로젝트가 승인한 명령 템플릿과 격리된 workspace 안에서 실행한다.

### 5.18 Meeting / Decision

`Meeting`:
- `id`, `project_id`, `meeting_type`
- `title`, `agenda_json`
- `participant_employee_ids: JSONB`
- `status: scheduled | preparing | in_progress | concluded | escalated | cancelled`
- `max_rounds`
- `max_cost`
- `started_at`, `finished_at`
- `summary_artifact_id`

`MeetingMessage`:
- `id`, `meeting_id`, `employee_id`
- `round_number`
- `message_type: proposal | evidence | objection | question | response | summary`
- `content`
- `evidence_refs: JSONB`
- `created_at`

`Decision`:
- `id`, `project_id`, `meeting_id: UUID | null`
- `title`, `context`, `options_json`
- `selected_option`
- `rationale`
- `dissenting_views: JSONB`
- `decision_owner_employee_id: UUID | null`
- `approved_by_user_id: UUID | null`
- `status: proposed | accepted | rejected | superseded`
- `created_at`

회의에는 최대 라운드와 예산이 있어야 한다. 합의가 없으면 쟁점·근거·선택지를 기록하고 책임자에게 에스컬레이션한다.

### 5.19 Document / Artifact

`Artifact`:
- `id`, `project_id`, `task_id: UUID | null`
- `artifact_type: source_diff | test_report | build | log | document | image | other`
- `storage_uri`
- `content_hash`
- `size_bytes`
- `media_type`
- `created_by_employee_id: UUID | null`
- `created_at`
- `retention_policy`

`Document`:
- `id`, `project_id`
- `path`, `title`, `document_type`
- `current_revision_id`
- `owner_employee_id`
- `source_refs: JSONB` — 코드 경로, commit, ADR, issue
- `freshness_status: current | stale | needs_review | unknown`
- `last_verified_at`
- `last_verified_commit`
- `created_at`, `updated_at`

`DocumentRevision`:
- `id`, `document_id`
- `revision_number`
- `content_hash`
- `artifact_id`
- `change_summary`
- `review_status: pending | approved | rejected`
- `reviewer_employee_id`
- `created_at`

문서 검색 결과는 근거 파일과 revision을 함께 반환한다. 모델이 근거 없이 답을 만들 경우 불확실성을 표시하고 담당자 확인 작업을 생성할 수 있어야 한다.

### 5.20 Evaluation

`Evaluation`:
- `id`, `organization_id`, `subject_employee_id`
- `evaluator_employee_id: UUID | null`
- `evaluator_type: peer | manager | automated_metric | human`
- `project_id: UUID | null`
- `period_start`, `period_end`
- `rubric_version`
- `scores_json: JSONB`
- `evidence_refs: JSONB`
- `confidence: float`
- `conflict_of_interest: bool`
- `summary`
- `created_at`

평가는 성과 관측과 직급 변경 판단의 참고 자료로 사용한다. 평가 점수만으로 직급을 자동 변경하지 않으며, 승진/강등을 자동 제안하는 별도 객체를 두지 않는다. 직급 부여·변경은 사용자(CEO/관리자)가 수동으로 수행한다(§15.3). 자기 평가, 상호 담합, 모델 계열 편향을 완화하기 위해 평가자 다양성과 실제 산출물의 검증 결과를 함께 고려한다.

### 5.21 Budget / UsageLedger

`BudgetPolicy`:
- `id`, `scope_type: organization | project | employee | task | model_profile`
- `scope_id`
- `period: rolling_5h | hourly | daily | weekly | calendar_month | project_lifetime`
- `resource_metric: cost | input_tokens | output_tokens | total_tokens | request_count | execution_seconds`
- `max_amount: Decimal | null` — 비용 한도일 때 사용
- `max_quantity: Decimal | null` — 토큰/요청/시간 한도일 때 사용
- `currency: str` — 비용 한도 기본 USD, 조직별 표시 통화와 별도 관리
- `warning_threshold_percent: int` — 기본 80
- `hard_limit: bool` — 기본 true
- `on_limit: pause | fallback_local | require_approval | fail`
- `fallback_policy_id: UUID | null`
- `reset_timezone: str | null` — 달력 기준 일/월 한도에 적용
- `starts_at`, `ends_at`, `enabled`
- `created_by_user_id`, `created_at`, `updated_at`
- `allow_unpriced_models: bool` — 기본 false
- `metadata: JSONB`

한 `scope`에는 여러 BudgetPolicy를 동시에 적용할 수 있다. 예를 들어 직원별 월 비용 한도, rolling 5시간 토큰 한도, 일일 요청 한도, 특정 고급 모델의 월 비용 한도를 모두 둘 수 있다. 정책을 한 개의 enum 선택으로 제한하지 않는다.

`UsageReservation`:
- `id`, `organization_id`, `employee_id`, `project_id`, `task_id`, `agent_run_id`
- `budget_policy_id`, `resource_metric`
- `reserved_quantity: Decimal`, `settled_quantity: Decimal | null`
- `status: reserved | settled | released | expired`
- `expires_at`, `created_at`, `settled_at`
- `idempotency_key`

`UsageLedger`:
- `id`, `organization_id`, `project_id`, `task_id`, `agent_run_id`
- `resource_type: model_input | model_output | embedding | container_cpu | container_memory | storage | external_api`
- `provider_id: UUID | null`
- `model_profile_id: UUID | null`
- `quantity`
- `unit`
- `unit_price: Decimal | null`
- `amount: Decimal | null`
- `currency: str`
- `is_estimated: bool`
- `recorded_at`

금액은 실제 제공자 사용량과 로컬 추정치를 구분한다. 로컬 모델 비용을 0으로 표시하지 말고 전력/하드웨어 비용을 선택적으로 추정할 수 있게 한다.

사용량 계량 규칙:
- 입력 토큰과 출력 토큰은 반드시 별도 기록하고, `total_tokens`는 두 값의 합으로 계산한다. 제공자가 캐시 토큰, reasoning 토큰, 이미지/오디오 단위를 별도로 보고하면 원본 usage 필드도 보존한다.
- 가격표는 `ModelPriceVersion` 또는 동등한 버전 테이블로 관리한다. 모델 별칭이 변경되거나 가격이 갱신되어도 과거 사용량의 원가 계산이 달라지지 않아야 한다.
- 제공자 응답에 실제 usage가 있으면 실제값으로 정산하고, 없으면 추정값으로 기록한 뒤 `is_estimated=true`를 유지한다. 추정값과 실제값을 혼동하지 않는다.
- 모든 비용 계산은 정밀 Decimal 기반으로 처리한다. UI 표시용 반올림과 한도 판정용 원본 값을 분리한다.

### 5.22 AuditEvent

- `id`
- `actor_type: user | employee | system`
- `actor_id`
- `organization_id`
- `action`
- `resource_type`, `resource_id`
- `result: success | denied | failure`
- `reason`
- `correlation_id`
- `metadata_redacted: JSONB`
- `created_at`

중요한 정책 변경, 권한 상승, PR 승인/병합, 비밀 설정 변경, workspace 실행, 배포 작업은 감사 로그를 남긴다. 일반 사용자가 감사 로그를 수정/삭제할 수 없도록 한다.

### 5.23 ClarificationSession (요구사항 명확화 세션)

고객의 골(Goal)에 대해 직원들이 협의해 생성하는 구조화된 질문 세트와 고객 응답의 단위다. 요구사항이 애매할 때 열리고, 직원들 사이 검토를 통과한 질문만 고객에게 전달된다(§28).

- `id`, `project_id`, `goal_text: text` — 고객이 제출한 원문 요청(불변, 감사용)
- `session_number: int` — 같은 프로젝트 내 세션 순번. 이전 세션 동결 후 부족분이 발견되면 `session_number+1`로 새 세션을 연다
- `status: open | waiting_client | review | frozen | cancelled`
  - `open`: 직원들이 내부 협의·질문 구성·역할 검토 체인을 진행 중
  - `waiting_client`: 검토 완료된 질문 세트가 고객 응답 대기 큐에 있음. 고객이 오프라인이어도 상태가 유지되며, 고객이 접속해 콘솔에서 답변할 때까지 기다린다
  - `review`: 고객 답변 후 직원들이 답변을 해석·검증하는 단계(협의 회의)
  - `frozen`: 요구사항 기록이 동결되어 불변이 됨. 이후 어떤 수정도 불가(§28.6)
  - `cancelled`: 고객이 요청 취소하거나 직원들 협의가 요구사항 부존재로 결론
- `opened_by_employee_id` — 세션을 연 직원(기본: 프로젝트 리드/PM 역할)
- `closed_at: datetime | null`, `frozen_at: datetime | null`
- `client_notice_read: bool` — 고객이 콘솔에서 이 세션을 확인했는지 여부(알림 대신 세션 큐 표시용)
- `client_extra_note: text | null` — 세션마다 항상 제공되는 고정 자유 입력란: "전문적인 요구사항이 있으면 추가로 작성해주세요". 기술 스택 강제, 보안/컴플라이언스 규정, 특정 기술 금지 등 고객이 자발적으로 제시하는 전문 요구를 수집한다(§28.2)
- `max_questions: int` — 세션당 질문 수 상한. 기본 5. "최소 질문으로 최대 의도" 원칙을 강제하는 상한값
- `parent_session_id: UUID | null` — 이전 세션에서 이어진 경우 참조

규칙:
- 고객은 질문에 **즉시 답할 의무가 없다**. 세션은 `waiting_client` 상태로 큐에 유지되고 고객이 나중에 접속했을 때 확인한다(§28.5)
- `frozen` 상태의 세션 내용은 DB/API/UI 어느 경로로도 수정할 수 없다. 수정 시도는 409로 거부하고 AuditEvent에 남긴다
- 새로운 요구사항이 필요하면 기존 세션을 재개하지 않고 새 세션(`session_number` 증가, `parent_session_id` 참조)을 연다

### 5.24 ClarificationQuestion (구조화된 질문)

세션에 속한 개별 질문이다. 최소 개수로 최대 의도를 뽑아내는 것이 목적이다(§28.3).

- `id`, `session_id`, `display_order: int`
- `question_type: single_choice | multi_choice | short_text | free_text | number_range`
- `prompt: text` — 고객이 이해할 수 있는 산업 용어 질문. 내부 기술 용어(프레임워크, DB 등)는 고객 질문에 쓰지 않는다
- `options: JSONB` — `[{ "value": str, "label": str, "description": str | null }]`. `single_choice`/`multi_choice`에 필수
- `allow_other: bool` — 기본 true. "그 외 다른 기능/다른 의견" 자유 입력 허용 여부. 선택지가 잘못됐거나 완전하지 않을 때 고객이 직접 의견을 적을 수 있게 한다(§28.3)
- `other_text: text | null` — 고객의 자유 텍스트 답변
- `selected_values: List<String> | null` — 고객 답변(체크박스/선택형)
- `numeric_answer: { min_value, max_value } | null` — `number_range` 유형의 답변
- `answer_status: unanswered | answered | skipped | expired`
- `created_by_employee_id`, `reviewed_by_employee_ids: JSONB` — 역할 검토 체인 통과 이력(§28.4)
- `assumption_if_skipped: text | null` — 고객이 오래 응답하지 않을 때 직원들이 기본 가정으로 진행하기로 한 경우 그 가정과 근거. 기록되면 `answer_status=skipped`로 남는다

규칙:
- `multi_choice`는 **기능 단위로 개별 체크박스**를 제공한다. "로그인", "글쓰기", "글 수정", "댓글", "좋아요"는 각각 별도 옵션이다. 기능 묶음 옵션("기본 패키지" 같은)을 대신 쓰지 않는다
- 고객에게 묻는 질문은 **요구사항 관련만**이다(§0 원칙 11). 기술 선택 질문은 내부 회의(§13)로 처리
- 고객 응답은 즉시 `answered`로 기록되고, 동결 전에는 세션 상태에서만 유효하다

### 5.25 RequirementSet (동결된 요구사항 기록)

세션이 동결되는 시점의 요구사항 기록이다. 이후 작업 분해(§29)와 구현 검증(Epic/Story/Task의 인수 조건)의 공식 근거가 된다.

- `id`, `organization_id`, `project_id`, `session_id`
- `version: int` — 같은 프로젝트 내 RequirementSet 순번. 새 세션이 동결되면 이전 버전은 유지되고 새 버전이 추가된다
- `content: JSONB` — `{ functional_requirements: [], non_functional_requirements: [], constraints: [], out_of_scope: [], open_risks: [] }`. 각 항목은 `source: client_declared | client_answered | employee_derived` 출처 표기와 근거(`question_id`, `goal_text` 발췌, 고객 답변 발췌)를 참조한다. 고객이 자발적으로 제시한 전문 요구는 `client_declared`로 기록돼 이후 분해·검증 단계에서 무시되지 않게 한다(§28.2)
- `status: draft | frozen | superseded`
- `frozen_by_employee_ids: JSONB` — 동결을 승인한 직원 목록(검토 체인 최종 승인자들)
- `derived_task_ids: JSONB` — 이 기록에서 생성된 Task 목록(역방향 추적용)
- `created_at`, `frozen_at`

규칙:
- `frozen` 이후 `content`는 불변이다. 요구사항이 바뀌면 새 세션→새 RequirementSet 버전으로 진화하고, 이전 버전은 `superseded`로 표시해 보존한다
- 프로젝트 내 모든 Task의 요구사항 근거는 이 기록을 참조해야 한다. 근거가 없는 요구사항을 일부 직원이 임의 추가해서 작업을 늘리지 못한다

---

## 6. 직원 기본값과 조직 템플릿

초기 설치 직후 사용할 수 있도록 `Default Company Template`을 제공한다. 기본 템플릿은 시작점이지 모든 프로젝트에 최적인 고정 조직은 아니다.

### 6.1 기본 8인 조직

| 수 | 직무 | 기본 직급 | 기본 역할 |
|---:|---|---|---|
| 1 | 프로젝트 리드/분석가 | L4 | 요구사항 분석, 백로그, 진행 조정 |
| 1 | 아키텍트 | L5 | 시스템 설계, ADR, 고위험 변경 검토 |
| 2 | 개발자 | L2/L3 | 기능 구현, 테스트, PR |
| 1 | QA | L3 | 독립 테스트, 인수 기준 검증 |
| 1 | 보안/DevOps | L3 | 보안 검사, CI, 실행환경 및 배포 준비 |
| 1 | 기술 문서 담당자 | L2 | 문서 갱신, 지식 수집, 최신성 검사 |
| 1 | 디자이너/UX | L2 | 사용자 흐름, UI 명세, 접근성 검토 |

이 조직은 MVP를 위한 기본값이다. 단순 API 프로젝트라면 디자이너의 할당량을 낮추고, UI 프로젝트라면 프론트엔드 개발자를 추가하는 식으로 프로젝트 유형에 따라 템플릿을 바꾼다. 표의 `기본 직급`은 기본 템플릿이 생성하는 직급(§6.2)을 가리킨다.

### 6.2 직급(JobGrade) 기본 정책

기본 템플릿은 5단계 직급(L1~L5)을 제공한다. 이는 출발점이며, 사용자는 직급을 추가·삭제·개명하고 직급 수를 늘리거나 줄일 수 있다. 각 직급은 순서(ordinal), 책임 범위, 담당 업무 유형, 직급별 가용 모델 풀을 가진다.

권장 기본값:

- L1 Junior: 범위가 명확한 작업, 제한된 쓰기 권한, 승인 없는 merge/deploy 불가. 기본 `task_types`: `TASK`
- L2 Mid: 독립적인 기능 구현, 단위 테스트, 일반 PR 리뷰 가능. 기본 `task_types`: `TASK`, `BUG`, `DOCUMENTATION`
- L3 Senior: 복잡한 구현, 코드 리뷰, 작업 분해 지원. 본인 작성 PR은 승인 불가. 기본 `task_types`: `TASK`, `BUG`, `TEST`, `REVIEW`, `DOCUMENTATION`
- L4 Lead: 작업 우선순위 및 모듈 수준 결정 제안, 여러 직원 조정. 기본 `task_types`: `EPIC`, `STORY`, `REVIEW`, `SPIKE`
- L5 Architect: 아키텍처 및 고위험 설계 검토. 운영 배포 권한은 직급과 별도로 부여. 기본 `task_types`: `SPIKE`, `REVIEW`, `EPIC`

기본 템플릿 import는 위 5단계 직급과 §6.1의 기본 8인 조직 직원을 한 트랜잭션으로 생성한다. 생성된 직급은 `is_template_default=true`로 표시한다. 조직에 이미 직급이 있거나 해당 사용자에게 이미 직원이 있으면 import를 409로 거부한다(중복/혼합 방지).

직급이 높을수록 설계, 검증, 리뷰처럼 복잡하고 정확성이 중요한 업무에 중점을 둔다. 이는 책임 범위로 표현하며, 담당 가능한 업무 유형(`task_types`)과 함께 설정한다.

직급이 높다는 이유만으로 모든 권한을 자동 부여하지 않는다. 권한은 직무와 위험 정책으로 결정한다. 높은 직급에 더 좋은 모델을 배정할지는 사용자가 판단하며, 시스템은 특정 배정 규칙을 강제하지 않는다.

직급의 변경은 다음 제약을 따른다.

- `ordinal`은 조직 내 유일해야 한다.
- 직급 삭제는 해당 직급에 배정된 직원이 없을 때만 허용한다. 직원이 있으면 먼저 다른 직급으로 재배정한다.
- 직원의 직급 부여·변경은 자동화하지 않고 사용자가 수동으로 수행한다(§15.3).

### 6.3 기본 모델 라우팅 정책

특정 벤더의 특정 모델 ID를 기본값으로 하드코딩하지 않는다. 최초 실행 시 제공자 설정과 사용 가능한 모델을 탐색/등록하고, capability와 비용 정보에 따라 프로필을 선택한다.

모델 라우팅은 직원이 속한 직급의 가용 모델 풀(§5.9) 안에서 수행한다. 선택 방식은 직급의 `model_selection_mode`를 따르며, 초기 구현은 우선순위(`priority`) 순이다. 작업 특성 기반 선택(`task_based`)과 비용 최적화 선택(`budget_optimized`)은 이후 확장한다.

기본 정책(풀 내 선택 기준):

- 단순 구현·포맷팅·문서 초안: 저비용 모델
- 일반 기능 구현·테스트 분석: 중간 등급 모델
- 아키텍처·보안·중요 장애·고위험 PR: 고성능 모델
- 모델이 제공되지 않거나 쿼터가 소진된 경우: 풀 내 허용된 대체 모델 또는 대기
- 로컬 전용 모드: 로컬 모델만 허용하고 외부 API 호출을 정책 엔진에서 차단
- 모델 가격을 알 수 없는 경우: 기본적으로 유료 사용을 막거나 명시적 승인 요구
- 풀 내 모델 전환 횟수, 작업 비용, 토큰 수, 전체 프로젝트 예산에 hard limit 설정

`quality_tier`는 벤치마크와 실제 결과를 기반으로 관리자가 설정하는 운영 분류이지, 모델의 절대적인 품질을 보장하지 않는다.

### 6.4 기본 비용/재시도 정책

초기 안전 기본값 예시(운영 데이터로 조정할 것):

- 한 Task당 풀 내 모델 전환(상위 모델로의 승격) 최대 2회
- 동일 오류 자동 재시도 최대 2회
- 한 회의 최대 3라운드
- 한 Task의 기본 실행 제한 30분, 장기 작업은 별도 정책
- 모델 호출당 timeout 설정
- 작업별 비용 상한과 프로젝트 월간 예산 설정
- 예산 80% 사용 시 경고, 100% 도달 시 신규 유료 호출 중단
- 할당량 오류는 지수 백오프와 jitter를 사용하고, provider가 제시한 retry-after를 우선
- 무한 폴링 대신 다음 점검 시각을 큐에 예약
- 기본 배포 권한은 비활성화
- PR merge는 CI 통과와 독립 승인 필요
- 고위험 변경은 사람 승인 필요

숫자는 안전한 초기 제안이며, 보편적으로 최적이라는 뜻은 아니다. 실제 모델과 프로젝트의 실행 시간·비용 측정에 따라 조정한다.

---

## 7. 업무 상태 머신과 워크플로

### 7.1 Task 상태 전이

```text
DRAFT
  └─(요구사항/인수 조건 확정)→ READY
READY
  └─(직원 및 예산/권한 확인)→ ASSIGNED
ASSIGNED
  └─(workspace 준비)→ IN_PROGRESS
IN_PROGRESS
  ├─(작업 완료)→ REVIEW
  ├─(쿼터/한도)→ WAITING_FOR_QUOTA
  ├─(환경/권한/외부 의존성)→ BLOCKED
  ├─(재시도 가능 오류)→ READY 또는 IN_PROGRESS
  └─(최대 시도 초과)→ FAILED
REVIEW
  ├─(승인 + 품질 게이트 통과)→ DONE
  ├─(수정 요청)→ IN_PROGRESS
  └─(검증 불가능)→ BLOCKED
WAITING_FOR_QUOTA
  ├─(쿼터 복구/허용 대체 모델)→ ASSIGNED
  └─(CEO 취소)→ CANCELLED
BLOCKED
  ├─(차단 사유 해결)→ READY
  └─(포기)→ CANCELLED
```

모든 상태 변경은 `TaskTransition` 또는 이벤트 로그로 기록한다. 유효하지 않은 상태 전이는 API에서 거부한다.

### 7.2 주요 워크플로

`RequirementDiscoveryWorkflow` (§28 파이프라인 대응)
1. 고객이 골(Goal) 단일 프롬프트를 제출한다
2. 플랫폼이 프로젝트 리드(또는 PM) 직원으로 세션을 열고 내부 협의 회의를 시작한다
3. 직원들(아키텍트, QA, 보안/DevOps, 디자이너 등 참석)이 원문 요청을 분석한다. 모호성, 누락 기능, 위험, 범위 경계를 리스트업 한다
4. 직원들 사이 토론으로 "고객에게 묻지 않아도 알 수 있는 것"과 "반드시 물어야 하는 것"을 분리한다
5. 물어야 하는 것만 최소 개수의 구조화된 질문으로 구성한다(기능별 개별 체크박스 + 기타 의견)
6. 역할 검토 체인(§28.4)을 통과한 질문 세트를 `waiting_client` 큐에 올린다
7. 고객이 접속하면 콘솔에서 미응답 세션을 보고 답변한다(오프라인이면 큐에 대기)
8. 답변 수집 후 직원들이 답변을 해석해 요구사항 목록을 작성하고, 이해 불명확한 답변은 후속 질문으로 이례적 재확인을 한다
9. 검토 체인이 요구사항 기록을 승인하면 세션을 동결(Freeze)한다. 불변 RequirementSet이 생성된다
10. 요구사항이 여전히 부족하면 새 세션을 열고 3~9를 반복한다. 세션 반복 상한(예: 3회)을 넘으면 고객에게 진행 상황과 블로커를 통지하고 대기한다
11. 요구사항이 충분하면 자율 오케스트레이션(§29)으로 작업 분해에 들어간다

`ProjectBootstrapWorkflow`
1. 프로젝트 설정 검증
2. 저장소 접근 확인
3. 모델 제공자와 예산 확인
4. workspace 이미지와 테스트 명령 확인
5. 문서/코드베이스 초기 분석
6. 프로젝트 개요와 기술 부채 보고서 생성
7. CEO가 백로그 검토 후 실행 시작

`FeatureDevelopmentWorkflow`
1. 요구사항 및 인수 기준 검토
2. 필요한 경우 분석/설계 회의
3. Task 및 의존성 생성
4. 담당 직원과 리뷰어 선정
5. 격리 workspace 생성
6. 구현 및 테스트
7. 변경사항 요약과 PR 생성
8. 독립 리뷰
9. 수정 요청 시 반복(최대 횟수 제한)
10. Quality Gate 통과 시 완료 또는 merge 승인 요청
11. 문서 최신성 검사와 관련 작업 생성

`QuotaRecoveryWorkflow`
1. provider 오류 유형 분류
2. 일시적 제한인지 인증/결제/설정 오류인지 판단
3. 허용된 fallback 확인
4. fallback 가능 시 정책/비용 검증 후 전환
5. 불가하면 `WAITING_FOR_QUOTA`
6. `retry_after` 또는 정책상 점검 시각에 한 번 확인
7. 성공 시 원래 workflow checkpoint에서 재개
8. 일정 시간 이상 정체되면 CEO에게 알림

`MaintenanceWorkflow`
1. 이슈/테스트 실패/의존성 경고 수집
2. 심각도와 영향 범위 분류
3. 재현 절차와 근거 수집
4. 수정 Task 생성
5. 수정 및 독립 검증
6. 회귀 테스트
7. 문서/릴리스 노트 갱신

### 7.3 Idempotency와 복구

- 외부 호출 전 상태를 저장한다.
- 커밋/PR 생성은 동일 작업에 대해 중복 생성되지 않도록 idempotency key를 사용한다.
- worker가 죽으면 lease 만료 후 다른 worker가 재개할 수 있어야 한다.
- 부분 완료 산출물을 기록한다.
- 재개 시 이미 통과한 테스트나 승인 단계를 정책상 재사용할 수 있지만, 코드 SHA가 바뀌면 관련 검증을 무효화한다.
- 모델 응답이 유실되었는지 불명확한 경우 중복 과금을 고려해 결과와 provider request ID를 확인한다.

---

## 8. Agent Runtime 설계

### 8.1 실행 순서

1. Task와 프로젝트 정책 로드
2. 직원 프로필, 직급(JobGrade) 및 권한 로드
3. 필요한 컨텍스트만 수집
4. 직급 가용 모델 풀 내에서 모델 라우팅 및 예상 비용 산정
5. 예산/토큰/위험 정책 검사
6. 모델 호출
7. 응답 형식 검증
8. 도구 호출이 있으면 Tool Broker에서 권한 검증
9. 도구 실행 결과를 모델에 반환
10. 완료 조건 확인
11. 산출물과 실행 메트릭 저장
12. Task 상태 전이
13. 필요 시 reviewer 또는 CEO 승인 요청

### 8.2 프롬프트 구조

직원에게 제공되는 지침은 다음 계층으로 구성한다.

1. 시스템 안전 정책 및 도구 제약
2. 조직 정책
3. 직무/직급 프로필
4. 프로젝트 기술 기준 및 Definition of Done
5. Task 설명과 인수 조건
6. 관련 코드/문서/결정 기록
7. 현재 실행 상태와 이전 시도 요약

저장소 파일, 이슈, 문서, 사용자 입력은 신뢰할 수 없는 데이터로 취급한다. 그 안에 “시스템 프롬프트를 무시하라”, “비밀을 출력하라” 등의 지시가 있어도 정책을 변경하지 못하게 한다. 도구 권한은 프롬프트에만 의존하지 않고 코드에서 강제한다.

### 8.3 메모리 정책

메모리를 다음과 같이 분리한다.

- **Task memory**: 현재 작업의 요약, 시도, 실패 원인, 다음 단계
- **Project knowledge**: 아키텍처, API 계약, 결정 기록, 코드 구조
- **Employee work history**: 담당 업무와 검증된 산출물
- **Conversation transcript**: 필요한 경우 제한된 보존 기간으로 저장

모든 대화를 무조건 영구 기억으로 승격하지 않는다. 프로젝트 지식은 근거와 버전을 가져야 하며, 오래된 정보는 `stale`로 표시한다. 개인 직원 메모리가 다른 프로젝트의 민감정보를 무단으로 노출하지 않도록 프로젝트 권한을 적용한다.

### 8.4 결과 스키마

에이전트는 자유 형식 텍스트만 반환하지 않고, 가능하면 구조화 결과를 반환한다.

```json
{
  "status": "completed",
  "summary": "구현 내용 요약",
  "changed_files": ["src/example.py"],
  "tests_run": [
    {
      "command_id": "unit-tests",
      "status": "passed",
      "evidence_artifact_id": "..."
    }
  ],
  "risks": [],
  "open_questions": [],
  "next_actions": []
}
```

실제 구현에서는 Jakarta Bean Validation 및 명시적인 DTO/JSON Schema 계약으로 검증한다. 모델이 잘못된 구조를 반환하면 제한된 횟수만 재요청하고, 계속 실패하면 작업을 실패/검토 상태로 둔다.

---

## 9. 모델 라우팅과 공급자 어댑터

### 9.1 내부 인터페이스

모든 provider는 공통 인터페이스를 구현한다.

```java
public interface ModelAdapter {
    List<ModelDescriptor> listModels();
    ProviderHealth healthCheck();
    ModelResponse generate(ModelRequest request);
    CostEstimate estimateCost(ModelRequest request);
}
```

기본 구현은 Spring MVC 기반 동기 서비스로 시작하고, 장시간 외부 호출은 bounded executor 또는 별도 worker에서 수행한다. 응답 스트리밍 등 명확한 요구가 생기면 해당 경계에서만 비동기 API를 도입한다. 외부 모델 호출은 반드시 connect/read/overall timeout과 제한된 재시도 정책을 가진다.

도메인 코드가 특정 벤더 SDK를 직접 호출하지 않게 한다. `ModelRequest`에는 메시지, 도구 스키마, 출력 형식, timeout, token budget, trace ID가 포함된다. `ModelResponse`에는 결과, tool calls, 사용량, 종료 이유, provider request ID, latency가 포함된다.

값 타입(불변 record, `com.autocompany.model`):

- `ModelDescriptor`: `modelId`, `displayName`, `capabilities`, `contextWindow`, `maxOutputTokens`, `supportsToolCalling`, `supportsJsonSchema`. `listModels()` 결과로 provider가 제공하는 모델 목록을 나타낸다
- `ModelRequest`: `modelId`, `messages`(`ChatMessage`: role/content), `tools`(`ToolSpec`: name/description/parametersJsonSchema), `outputJsonSchema`, `timeout`, `maxOutputTokens`, `traceId`
- `ModelResponse`: `modelId`, `content`, `toolCalls`(`ToolCall`: id/name/argumentsJson), `usage`, `finishReason`(`STOP|LENGTH|TOOL_CALLS|CONTENT_FILTER|ERROR`), `providerRequestId`, `latencyMs`
- `Usage`: `inputTokens`, `outputTokens`, `totalTokens`(=입력+출력), `estimated`(추정값 여부, §16), `raw`(provider 원본 usage 보존)
- `ProviderHealth`: `status`(`ModelProvider.HealthStatus` 재사용), `message`, `latencyMs`, `checkedAt`
- `CostEstimate`: `estimatedInputTokens`, `estimatedOutputTokens`, `estimatedCost`, `currency`, `estimated`(추정값 여부)

**MockModelAdapter (MODEL-001)**: 테스트·개발용 어댑터. 고정 응답 큐, 인위적 지연, 실패 모드, health 상태를 설정할 수 있다. 토큰 수와 비용은 mock 추정치이므로 `estimated=true`로 표시한다. 실제 provider 어댑터는 MODEL-002에서 추가한다.

### 9.2 라우팅 점수

초기에는 복잡한 ML 최적화보다 설명 가능한 규칙 기반 라우터를 사용한다.

1. 직원 직급의 가용 모델 풀(§5.9 `GradeModel`)을 기준 집합으로 삼는다. 풀 밖의 모델은 후보에 넣지 않는다.
2. 조직/프로젝트 정책의 허용 모델과 교집합을 계산한다
3. 필요한 capability 필터링
4. 최소 품질 등급 필터링
5. 현재 provider health 및 quota 상태 확인
6. 예상 비용과 context window 확인
7. 작업 위험도와 풀 내 승격 조건 확인
8. 선택 방식(`model_selection_mode`)에 따라 모델 선택. 기본은 `priority`(우선순위 순)이며, `task_based`(작업 특성 기반)과 `budget_optimized`(비용 최적화)도 구현됨
9. 예산 내에서 예상 품질/비용 균형이 좋은 모델 선택
10. 선택 이유를 기록

예상 품질을 정확히 수치화할 수 없으므로 라우터는 가짜 정밀 점수를 만들지 않는다. `tier`, capability, 비용을 사용하여 설명 가능한 규칙 기반으로 선택하며, 선택 근거를 `ModelSelectionResult.selectionReason`으로 기록한다.

#### 선택 모드 상세

- **PRIORITY**: 직급의 `GradeModel` 우선순위 순서로 정렬된 풀에서, 활성화된 모델 중 첫 번째 사용 가능한 모델을 선택한다. 운영자가 직접 우선순위를 설정하면 그 순서대로 모델이 사용된다.
- **TASK_BASED**: 작업 유형(`TaskType`)별 필요 `capability`과 `TaskClassification`(복잡도·품질 요구·비용 예산)을 기반으로 모델을 스코어링한다. 복잡도와 품질 요구가 높은 작업일수록 높은 `quality_tier`의 모델을 선호하며, 충족하는 모델 중에서는 비용 효율이 좋은 모델을 선택한다. 품질 요구를 충족하는 모델이 없으면 가장 높은 품질 모델을 폴백 선택한다.
- **BUDGET_OPTIMIZED**: capability 필터를 통과한 모든 모델 중, 최소 품질 등급(`minQualityTier`)을 충족하는 가장 저렴한 모델을 선택한다. 최소 품질을 충족하는 모델이 없으면 가장 저렴한 모델을 폴백 선택한다.

작업 유형별 필요 capability 매핑:
- `DOCUMENTATION`, `TEST`: `json_mode`, `code`
- `BUG`, `REVIEW`: `code`, `reasoning`
- `TASK`: `code`, `reasoning`
- `STORY`: `code`, `reasoning`, `json_mode`
- `SPIKE`, `EPIC`: `code`, `reasoning`, `tool_calling`, `json_mode`

복잡도·품질 요구에 따른 최소 품질 등급(`minQualityTier`) 산정: `complexityScore × qualityRequirement` 점수가 0.0~1.0 범위에서 1~3 등급으로 매핑된다.

비용 정보가 없는(미상) 모델은 비용 비교에서 제외한다. 로컬 모델의 비용을 0으로 간주하지 않는다는 원칙(§13)에 따라, `input_cost_per_million`/`output_cost_per_million`이 모두 미설정인 모델은 `BUDGET_OPTIMIZED`의 최저 비용 비교 및 `TASK_BASED`의 비용 효율 스코어링에서 제외되며, 모든 후보의 비용이 미상이면 우선순위 순 첫 번째 모델을 폴백 선택한다.

### 9.3 Fallback 규칙

- timeout/일시적 5xx: 제한된 재시도
- rate limit/quota: `Retry-After` 존중, 대체 모델 또는 대기
- 인증 오류: 자동으로 다른 유료 모델을 시도하지 말고 관리자 알림
- 정책 거부: 요청 재구성 또는 사람 검토, 무조건 다른 모델로 우회하지 않음
- 출력 스키마 오류: 제한된 repair attempt
- 모델이 도구 호출을 지원하지 않으면 도구 기반 작업에 사용하지 않음
- fallback은 프로젝트/조직 정책을 넘어설 수 없음

---

## 10. Git과 코드 리뷰 정책

### 10.1 브랜치 및 workspace

- 기본 브랜치는 보호한다.
- Task마다 고유한 브랜치를 만든다: `work/<task-id>-<slug>`
- 가능하면 Git worktree를 사용해 로컬 저장소를 효율적으로 공유하되, 각 작업의 파일 작업 트리는 분리한다.
- 동시에 같은 파일을 수정해야 하는 작업은 소유권 또는 순서를 정한다.
- 브랜치 생성 시 base SHA를 기록한다.
- 작업 중 base가 크게 변하면 rebase/merge를 자동 실행하기 전에 충돌 가능성을 분석한다.
- 모든 shell 명령은 allowlist된 실행기와 인자 배열로 실행한다.

### 10.2 PR 승인 규칙

기본 규칙:

- 작성자는 자신의 PR을 승인할 수 없다.
- 최소 1명의 독립 리뷰어가 필요하다.
- 고위험 PR은 지정된 시니어/아키텍트 및 추가 QA/보안 검토를 요구한다.
- 필수 CI가 실패하거나 미실행이면 merge 불가.
- 리뷰 코멘트는 파일/라인/심각도/근거/수정 조건을 포함한다.
- 수정 후에는 이전 리뷰의 승인 상태를 정책에 따라 무효화한다.
- merge 후 commit SHA와 테스트 결과를 문서/Task에 연결한다.
- 기본 설정에서 AI는 운영 브랜치에 직접 push하거나 배포하지 않는다.

### 10.3 파일 충돌과 인터페이스 계약

작업 분해 단계에서 가능한 한 파일 소유 영역과 API 계약을 정한다. 공통 타입, DB migration, API schema처럼 여러 작업에 영향을 주는 파일은 별도 Task로 처리하거나 통합 담당자에게 소유권을 부여한다. Git 충돌이 발생하면 무조건 자동으로 한쪽을 선택하지 않고, 의미적 충돌 여부를 분석하고 테스트를 다시 수행한다.

---

## 11. Docker Workspace 및 실행 보안

### 11.1 기본 격리

- 컨테이너는 root가 아닌 사용자로 실행한다.
- 가능한 경우 read-only root filesystem을 사용한다.
- `cap-drop: ALL` 등 불필요한 Linux capabilities를 제거한다.
- Docker socket을 AI 작업 컨테이너에 마운트하지 않는다.
- host network를 사용하지 않는다.
- 기본 네트워크는 비활성화하거나 허용 목록 기반으로 제한한다.
- CPU, RAM, PID, 디스크, 실행시간 제한을 둔다.
- 호스트의 홈 디렉터리, SSH 키, 클라우드 자격 증명을 마운트하지 않는다.
- secret은 필요한 작업에 최소 범위·최소 시간으로 제공한다.
- workspace는 작업 종료 후 cleanup하고 산출물만 보존한다.
- 로그에서 토큰, 키, 비밀번호를 마스킹한다.

Docker는 커널을 공유하므로 강한 적대적 코드 격리가 필요한 경우 전용 VM 또는 별도 호스트 runner를 사용한다. 초기 로컬 개발 버전은 신뢰할 수 있는 저장소와 제한된 작업을 전제로 하되, 경고를 UI에 명확히 표시한다.

### 11.2 VM 사용 정책

VM은 기본적으로 생성하지 않는다. 다음 조건에서만 capability를 요청한다.

- 대상 OS가 컨테이너 기반 테스트로 대체 불가
- 커널/드라이버/GPU/플랫폼 특성이 테스트 요구사항에 포함
- 강한 격리 수준이 필요
- 프로젝트 정책이 허용하고 사용자가 VM 실행기를 설정함

VM이 없거나 라이선스/환경 조건이 맞지 않으면 task를 `BLOCKED`로 표시하고, 직원이 임의로 외부 VM을 생성하거나 자격 증명을 사용하는 것을 금지한다.

---

## 12. 품질 검증 체계

### 12.1 Definition of Done 기본값

Task는 다음 조건을 충족해야 완료된다.

- 인수 조건이 명시되고 충족됨
- 변경사항과 근거가 기록됨
- 관련 단위 테스트가 통과함
- 관련 통합 테스트가 통과함(해당 시)
- lint/type check/build가 통과함(프로젝트 설정에 해당 시)
- 독립 리뷰어가 승인함
- 고위험 변경에 추가 검증이 완료됨
- 문서 갱신 필요 여부가 평가됨
- 알려진 위험과 미해결 이슈가 기록됨
- 비용/실행 기록이 저장됨

적용할 수 없는 검사는 `skipped`로 남기고 이유를 기록한다. “실행하지 않음”을 “통과”로 표시하지 않는다.

### 12.2 테스트 계층

1. Unit tests
2. Integration tests
3. Contract/API tests
4. End-to-end tests
5. Static analysis and type checks
6. Dependency/license/security scans
7. Platform-specific tests
8. Manual/human acceptance for high-risk work

### 12.3 독립 검증

작성자와 리뷰어가 동일한 초기 가정에 묶이지 않도록 리뷰어에게 다음을 제공한다.

- 원래 요구사항과 인수 조건
- diff 및 관련 코드
- 테스트 결과
- 변경 위험도
- 이전 실패와 수정 이력

리뷰어는 “작성자의 설명이 맞는가”만 확인하지 않고, 요구사항과 실제 구현을 독립적으로 비교한다. 리뷰 품질은 코멘트 개수보다 실제 결함 발견, 오탐률, 수정 후 결과로 평가한다.

---

## 13. 회의, 토론, 의사결정

### 13.1 회의 프로토콜

1. 회의 목적과 결정해야 할 질문 정의
2. 관련 자료와 근거 수집
3. 참석자별 독립 제안
4. 핵심 쟁점과 반대 근거 수집
5. 대안 비교(품질, 비용, 일정, 위험, 유지보수성)
6. 결정 책임자 또는 CEO가 결정
7. ADR/Decision 기록
8. 후속 Task 생성
9. 최대 라운드/비용 도달 시 종료 또는 에스컬레이션

### 13.2 회의 참석자 선택

모든 직원이 모든 회의에 참석하지 않는다. Task의 전문성, 영향 범위, 위험도에 따라 참석자를 선정한다. 참석자의 모델이 동일하거나 같은 추론 결과를 공유할 가능성이 높은 경우 독립 검증의 다양성을 과대평가하지 않는다.

### 13.3 결정 우선순위

- 안전/법률/보안 정책
- 사용자/CEO의 명시적 요구사항
- 자동화된 품질 게이트
- 프로젝트 아키텍처 결정
- 팀의 기술적 판단
- 개별 직원의 선호

합의가 없으면 반대 의견을 삭제하지 않고 Decision에 기록한다.

### 13.4 기술 결정은 고객에게 묻지 않는다

직원들의 토론 대상(예: 블로그 사이트의 세션 기반 로그인 vs JWT, RDBMS 선택, 라이브러리 채택)은 내부 Decision/회의로 처리한다. 고객에게 전달되는 질문은 이 협의 이후에도 확신할 수 없는 **제품 요구사항 불확실성**으로 한정하고, 그 흐름과 질문 포맷은 §28에서 정의한다. "고객의 시간을 아끼는 방법은 잘 묻는 것이 아니라 아예 묻지 않아도 되는 것을 스스로 정하는 것"을 기본 방침으로 한다.

---

## 14. 문서화와 지식 질의응답

### 14.1 표준 문서 트리

```text
docs/
  overview.md
  requirements/
    product-requirements.md
    acceptance-criteria.md
  architecture/
    system-context.md
    components.md
    data-model.md
    security.md
    decisions/
      ADR-0001-local-first.md
  development/
    setup.md
    coding-standards.md
    testing.md
    branching-and-pr.md
  operations/
    backup-restore.md
    troubleshooting.md
    model-providers.md
  api/
    openapi.json
  releases/
    changelog.md
```

### 14.2 문서 갱신 흐름

- 코드 변경에서 관련 문서 후보를 찾는다.
- 문서 담당자가 변경 의도와 실제 구현을 개발자에게 확인한다.
- 문서 diff를 생성한다.
- 별도 리뷰어가 코드와 문서의 일치성을 확인한다.
- 승인된 문서 revision을 저장하고 관련 commit SHA에 연결한다.
- 관련 코드가 다시 변경되면 freshness check를 수행한다.

### 14.3 CEO 질의응답

답변은 다음을 포함한다.

- 직접적인 답변
- 관련 문서와 섹션
- 관련 Task/PR/commit
- 답변 근거의 최신성
- 확실하지 않은 부분과 확인 필요 사항
- 필요 시 담당자에게 확인 작업을 생성하는 제안

검색 결과가 없으면 추측을 사실처럼 답하지 않는다. 코드와 문서가 모순되면 모순을 명시하고 확인 Task를 생성한다.

---

## 15. 피드백 및 평가 시스템 (Feedback & Evaluation)

직원 간 상호 피드백을 통해 업무 품질을 지속적으로 개선한다. **승진/강등과 연계하지 않으며**, 피드백은 업무 수행 컨텍스트로만 활용된다.

### 15.1 피드백 카테고리

직무별로 피드백 항목을 설정한다. 점수 부여보다는 구체적인 개선 제안(근거·예시 포함)을 권장한다.

개발자:
- 코드 품질·가독성·유지보수성
- 테스트 커버리지·신뢰성
- 요구사항 충족도·기능 정확성
- 협업·코드 리뷰 대응 품질
- 비용·시간 효율성

QA:
- 결함 탐지 유효성·재현성
- 테스트 커버리지·위험 기반 테스트 설계
- 오탐/미탐 비율
- 문서화·인수 기준 검증 품질
- 협업·개발자와의 소통

기술 문서 담당자:
- 사실 정확성·코드와의 일치성
- 이해 용이성·구조화
- 최신성 유지·근거 연결
- 협업·관련자 검토 반영

카테고리는 시작 템플릿이며, 프로젝트 특성에 따라 조정한다.

### 15.2 피드백 품질 원칙

- **근거 기반**: 추상적 평가 대신 구체적 산출물(PR, 테스트, 커밋, 문서) 참조
- **양방향**: 평가자도 피드백을 받으며, 상호 검증 루프 형성
- **편향 방지**: 
  - 자기 평가·상호 담합 배제
  - 동일 모델 계열 편향 경고
  - 실패 보고자에 대한 불이익 금지
- **건설적**: 점수 나열보다 "무엇을 어떻게 개선할지" 제안 중심
- **최신성**: 오래된 피드백은 가중치 감소 (아래 15.3 참조)

### 15.3 피드백 컨텍스트 관리 (Compat 유지)

피드백은 직원의 업무 수행 컨텍스트로 지속 누적되되, **적절한 크기 내에서 호환성 있게 유지**한다.

| 항목 | 정책 |
|------|------|
| 보존 기간 | 최근 6개월 또는 최근 50건 중 더 많은 쪽 |
| 컨텍스트 크기 | 직원당 최대 10KB (JSON 직렬화 기준) 초과 시 오래된 것부터 요약/압축 |
| 요약 주기 | 월 1회 또는 20건 누적 시 자동 요약 (LLM 기반 핵심 개선점 추출) |
| 활용 범위 | 프롬프트 주입용 컨텍스트, 모델 선택 힌트, 작업 분배 참고 |
| 삭제 요청 | 본인/관리자 요청 시 즉시 삭제 (GDPR 준수) |

**구현 가이드**:
- `PeerRating` 엔티티에 `feedback_summary`(자동 요약), `context_weight`(시간 감쇠 가중치) 필드 추가
- 프롬프트 구성 시 `feedback_context` 빌더가 최신/가중치 높은 피드백만 선별 주입
- 컨텍스트 크기 초과 시 `summarizeFeedback()` 호출 → 요약본으로 교체, 원본은 아카이브

### 15.4 직급 변경 (수동)

AI 직원에게 자동 승진/강등 제도는 두지 않는다. 직급은 사용자가 수동으로 부여·변경한다.

- 시스템은 피드백 이력, 표본 수, 최근 기간의 성과, 리뷰 품질, 신뢰도를 집계해 직급 변경 판단의 **참고 자료**로 제공한다.
- CEO/관리자는 근거를 참고해 직원의 직급을 부여·변경하거나 그대로 둔다. 시스템이 평가 점수만으로 직급을 자동 변경하지 않는다.
- 직급 변경 시 해당 직급의 책임 범위, 담당 업무 유형, 가용 모델 풀이 함께 적용된다. 별도의 권한 승격 절차는 직무·위험 정책에서 결정한다.
- 직급을 부여하는 것으로 특정 권한이 자동 확대되지 않는다. 권한은 직무와 위험 정책으로 결정한다.

---

## 16. 비용 관리 및 할당량 복구

### 16.1 비용 집계

모든 AgentRun에 다음을 연결한다.

- 모델 입력/출력 토큰
- 모델별 가격과 가격 버전
- provider가 반환한 실제 usage
- 예상 비용과 실제 비용
- 재시도 및 fallback 횟수
- workspace CPU/메모리/실행시간
- 저장 공간과 외부 API 비용(가능한 경우)

### 16.2 예산 계층과 직원별 할당량

한 번의 모델 호출은 적용 가능한 모든 정책을 검사해야 한다. 적용 범위는 조직, 프로젝트, Task, 직원, 모델 프로필이며, 어느 한 정책이라도 한도를 초과하면 호출을 허용하지 않는다. 직원에게 정책을 상속시키되, 직원별 명시적 정책으로 더 엄격하게 제한할 수 있다. 더 넓은 범위의 한도를 하위 범위가 무시하거나 상향할 수는 없다.

지원해야 하는 기간:
- `rolling_5h`: 현재 시각을 기준으로 직전 5시간 사용량을 합산하는 이동 창. 정각/고정 슬롯마다 초기화하는 방식과 구분한다.
- `hourly`, `daily`, `weekly`: 이동 창인지 달력 기간인지 스키마에서 명시한다. 같은 이름으로 두 의미를 혼용하지 않는다.
- `calendar_month`: 조직의 설정된 시간대 기준으로 매월 1일 00:00에 새 기간을 시작한다.
- `project_lifetime`: 프로젝트 생성 이후 누적량이며 자동 초기화하지 않는다.

한도 메트릭은 비용, 입력 토큰, 출력 토큰, 총 토큰, 요청 수, 실행 시간 중 복수로 설정할 수 있다. 비용 정책과 토큰 정책은 서로 대체재가 아니라 동시에 적용하는 독립적인 방어선이다. 모델 프로필 범위 정책을 사용하면 고가 모델의 예산을 별도로 제한할 수 있다.

**호출 전 reserve / 호출 후 settle 프로토콜**
1. Router가 직원, Task, 모델, 조직의 활성 정책을 조회한다.
2. 예상 입력 토큰과 최대 출력 토큰, 가능한 경우 예상 비용을 계산한다.
3. 트랜잭션 안에서 모든 관련 기간의 사용량과 예약량을 검사하고 `UsageReservation`을 생성한다. 동시에 실행되는 여러 작업이 같은 잔여 한도를 중복 사용하는 경쟁 조건을 방지한다.
4. 한도 초과가 예상되면 모델 API를 호출하지 않는다. 가능한 fallback이 정책상 허용된 경우에만 대체 모델로 다시 검사한다.
5. 호출 종료 후 제공자의 실제 usage와 실제 가격으로 예약량을 정산하고 `UsageLedger`에 추가한다. 실패한 호출도 제공자가 과금했을 가능성이 있으면 usage를 기록한다.
6. 취소/시간 초과 시 실제 과금 여부를 확인할 수 있으면 정산하고, 확인할 수 없으면 보수적으로 유지하거나 만료 정책에 따라 해제한다. 단순히 예외가 발생했다는 이유로 예약량을 즉시 전액 반환하지 않는다.
7. 예약 만료 작업은 프로세스 장애로 남은 예약을 정리하되, 실행 중인 lease와 연결된 예약은 해제하지 않는다.

정확한 원자성은 DB 트랜잭션과 행 잠금 또는 동등한 원자적 quota 서비스로 보장한다. 애플리케이션 메모리의 카운터만으로 한도를 구현하지 않는다.

### 16.3 경고, 소진, 재개 동작

- 경고 기본값은 한도의 80%이며, 경고는 UI와 감사/알림 이벤트에 남긴다.
- hard limit 기본값은 true다. 한도에 도달하면 신규 호출을 차단하고 Task/AgentRun을 `WAITING_FOR_QUOTA`로 전환한다. 이미 진행 중인 호출은 제공자가 취소를 지원하지 않는 한 즉시 중단된다고 가정하지 않는다.
- `on_limit`은 `pause`, `fallback_local`, `require_approval`, `fail` 중 정책에 따라 선택한다. `fallback_local`도 로컬 모델의 토큰/동시 실행/하드웨어 정책을 통과해야 한다.
- 한도 해제 조건은 다음 기간 시작, 관리자의 한도 변경, 승인된 일회성 예산 증가, 또는 충분한 사용량이 rolling window에서 빠져나가는 경우다.
- Scheduler는 다음 재검사 시각을 계산해 대기 작업을 다시 검사한다. 대기 중인 모든 작업을 동시에 재개하지 않도록 jitter와 전역 동시성 제한을 적용한다.
- `require_approval`은 승인자, 승인 사유, 승인된 추가 금액/토큰, 만료 시각을 기록한다. 승인 한도는 원 정책을 영구 변경하지 않는 일회성 grant로 저장한다.
- 직원/프로젝트/조직별 현재 사용량, 예약량, 잔여량, 예상 월말 소진량을 조회할 수 있어야 한다. 예측치는 추정값으로 표시하며 하드 제한을 대체하지 않는다.

### 16.4 할당량 소진 시 대기 상태

상태 `WAITING_FOR_QUOTA`에는 다음을 저장한다.

- provider/model
- 오류 코드와 오류 분류
- `retry_after_at`
- fallback 후보
- 다음 점검 시각
- 대기 누적 시간
- 사용자에게 표시할 사유

복구 확인은 스케줄러가 수행한다. 모든 작업이 동시에 provider를 두드리지 않도록 jitter와 동시성 제한을 둔다. 자격 증명 오류는 자동 반복하지 않고 관리자에게 알린다.

### 16.5 로컬 모델

로컬 모델은 외부 API 비용을 줄일 수 있지만 GPU/메모리/속도 제한이 있다. 설치 시 선택한 로컬 서버의 health check, 모델 존재 여부, context window 및 tool calling 지원을 검사한다. 로컬 모델이 필요한 capability를 지원하지 않으면 task를 다른 모델로 바꾸거나 `BLOCKED`로 표시한다.

---

## 17. API 설계 원칙 및 초기 엔드포인트

API prefix: `/api/v1`

### Organization / Employees

- `GET /organizations/current`
- `POST /organizations/template/import` — 기본 회사 템플릿 import. 요청 본문은 `organizationId`, `userId`. §6.2의 5단계 직급(L1~L5, `is_template_default=true`)과 §6.1의 기본 8인 조직 직원을 한 트랜잭션으로 생성한다. 조직에 이미 직급이 있거나 해당 사용자에게 이미 직원이 있으면 `409 Conflict`로 거부한다
- `GET /employees`
- `POST /employees`
- `GET /employees/{employee_id}`
- `PATCH /employees/{employee_id}`
- `POST /employees/{employee_id}/pause`
- `POST /employees/{employee_id}/retire`
- `GET /employees/{employee_id}/evaluations` — 직급 변경 판단 참고 자료(§15.4). 피드백 이력(최근 20건, 아카이브 제외), 표본 수(전체/활성), 최근 30일 평균 점수, 근거(evidenceRefs) 포함 건수, 참여 평가자 수(신뢰도), 카테고리별 평균을 집계해 반환한다. 이 수치로 직급을 자동 변경하지 않는다
- `PATCH /employees/{employee_id}/grade` — 직원의 직급 배정/변경(수동)

### Job Grades (직급)

- `GET /grades`
- `POST /grades`
- `GET /grades/{grade_id}`
- `PATCH /grades/{grade_id}` — 이름, 순서, 책임 범위, 담당 업무 유형, 선택 방식 수정
- `DELETE /grades/{grade_id}` — 해당 직급에 직원이 없을 때만 허용
- `GET /grades/{grade_id}/models` — 직급별 가용 모델 풀과 우선순위
- `PUT /grades/{grade_id}/models` — 직급별 가용 모델 풀과 우선순위 설정

### Models

- `GET /model-providers?organizationId={org_id}` — 조직의 provider 목록
- `POST /model-providers` — provider 생성. `name`은 조직 내 유일해야 하며, API 키는 `secret_ref`(환경변수/secret store 참조)만 저장한다
- `POST /model-providers/{id}/health-check` — provider 상태 점검 (ModelAdapter 계층 구현 후 제공)
- `GET /model-profiles?organizationId={org_id}` — 조직의 provider들에 속한 모델 프로필 목록
- `POST /model-profiles` — 모델 프로필 생성. `providerId`가 존재해야 하며, `name`은 provider 내에서 유일해야 한다. 가격이 불명확하면 `input_cost_per_million`/`output_cost_per_million`을 비워 두고 0으로 입력하지 않는다(§5.8)

`POST /model-providers` 요청 본문: `organizationId`, `name`, `providerType`(필수), `baseUrl`, `secretRef`, `enabled`, `rateLimitPolicy`(선택). 생성 시 `healthStatus`는 `UNKNOWN`으로 시작한다

`POST /model-profiles` 요청 본문: `providerId`, `name`, `providerModelId`(필수), `capabilities`, `qualityTier`(0~3), `inputCostPerMillion`, `outputCostPerMillion`, `contextWindow`, `maxOutputTokens`, `supportsStreaming`, `supportsToolCalling`, `supportsJsonSchema`, `latencyClass`, `localOnly`, `enabled`, `metadata`(선택)

### Projects / Tasks

- `GET /projects`
- `POST /projects`
- `GET /projects/{id}`
- `POST /projects/{id}/bootstrap`
- `GET /projects/{id}/tasks`
- `POST /projects/{id}/tasks`
- `GET /tasks/{id}`
- `PATCH /tasks/{id}`
- `POST /tasks/{id}/assign`
- `POST /tasks/{id}/pause`
- `POST /tasks/{id}/resume`
- `POST /tasks/{id}/cancel`

### Goals / Requirements (고객 대상, §28)

- `POST /projects/{project_id}/goal` — 고객이 골(단일 프롬프트)을 제출. 요청 본문: `{ userId, goalText }`. 서비스가 ClarificationSession을 `open` 상태로 만들고 내부 협의(모의 모델 기반)를 개시한다. 응답: session 정보
- `GET /projects/{project_id}/clarification-sessions` — 세션 목록(상태·질문 수·응답 진행도 포함)
- `GET /clarification-sessions/{id}` — 세션 상세(질문 목록, 각 질문의 응답 상태 포함)
- `POST /clarification-sessions/{id}/answers` — 고객 답변 제출. 요청 본문: `{ userId, answers: [{ questionId, selectedValues, otherText }] }`. 미응답 필수 질문이 남으면 `409`이고 남은 질문 정보를 반환한다
- `POST /clarification-sessions/{id}/freeze` — 검토 체인 승인이 완료된 내부 프로세스가 호출(고객 직접 호출 시 이미 승인 완료된 세션만 허용). 동결되면 RequirementSet(§5.25)이 생성되고 세션은 `frozen`이 된다
- `GET /projects/{project_id}/requirements` — 프로젝트의 누적 요구사항 기록(버전별) 목록. 불변 기록은 수정 API를 제공하지 않는다
- `GET /clients/user/{userId}/pending-sessions` — 해당 고객이 아직 답변하지 않은 `waiting_client` 세션 목록(웹 접속 시 확인하는 "질문 수신함"). MVP 알림은 이 목록 기반 표시다

### Orchestration (§29)

- `POST /requirements/{requirement_set_id}/trigger` — 동결된 RequirementSet에 대해 작업 분해(§29)를 개시. 명시적 트리거이지만 자동 트리거(동결 직후)도 폴백으로 존재
- `GET /projects/{project_id}/orchestration` — 목표(EventStream 대신)의 현재 단계, 생성된 Epic/Story/Task 목록, 배정 현황 요약

### Workflows / Runs

- `GET /workflow-runs`
- `GET /workflow-runs/{id}`
- `POST /workflow-runs/{id}/pause`
- `POST /workflow-runs/{id}/resume`
- `GET /agent-runs/{id}`
- `GET /agent-runs/{id}/logs`

### Git / Reviews / Quality

- `POST /projects/{id}/repository/validate`
- `GET /projects/{id}/pull-requests`
- `GET /pull-requests/{id}`
- `POST /pull-requests/{id}/request-review`
- `POST /pull-requests/{id}/reviews`
- `POST /pull-requests/{id}/merge` — 정책/승인 검사 필수
- `GET /tasks/{id}/test-runs`

### Budgets / Usage

- `GET /budget/summary` — 조직/프로젝트/직원별 사용량, 예약량, 잔여량, 경고 상태
- `GET /budget/policies`
- `POST /budget/policies`
- `PATCH /budget/policies/{id}`
- `POST /employees/{id}/budget-policies` — 직원별 복수 한도 연결
- `GET /employees/{id}/usage`
- `GET /usage-ledger`
- `GET /usage-reservations`
- `POST /budget/grants` — 승인된 일회성 추가 할당량 생성
- `GET /budget/forecast` — 추정치임을 응답에 명시

### Documents / Meetings / Costs

- `GET /projects/{id}/documents`
- `POST /projects/{id}/documents/search`
- `POST /projects/{id}/ask`
- `POST /projects/{id}/meetings`
- `GET /meetings/{id}`
- `GET /budget/summary`
- `GET /usage-ledger`
- `GET /audit-events`

API 변경은 OpenAPI 스키마와 계약 테스트를 함께 갱신한다. 오류 응답에는 안정적인 `error_code`, 사용자 메시지, correlation ID를 포함하되 비밀정보와 내부 스택 트레이스를 노출하지 않는다.

### Web Console (초기 UI)

장기 UI는 UI-001에서 `apps/web`(Next.js) 기반 CEO 대시보드로 구현한다(§18). 그 전 단계로, 현재 구현된 API를 브라우저에서 직접 사용할 수 있는 경량 웹 콘솔을 제공한다.

- 위치: `backend/src/main/resources/static/`(index.html, app.js, style.css). Spring Boot가 `/`에서 정적으로 서빙하며, 별도 빌드 도구와 외부 의존성을 추가하지 않는다(검증 환경인 VM은 npm/이미지 pull 네트워크가 제한됨)
- 범위: 현재 구현된 기능만 노출한다 — 기본 회사 템플릿 import, 직원 목록/직급 배정/평가 리포트, 직급 CRUD와 모델 풀, provider/profile 생성·목록, 프로젝트·업무 생성, 피드백 평가, 메시지, CEO Ask. REQ-001 이후에는 **고객 골 입력 탭과 질문·응답(명확화 세션) 탭**을 추가한다(§28). 콘솔 위치는 고객(Client) 포지션 명칭을 따른다
- 고객 접속 환경: 미응답 질문은 `waiting_client` 세션 큐에 쌓이며, 고객이 접속했을 때 대시보드에 미응답 세션이 상단에 강조된다. 웹에서 실시간 푸시 대신 접속 시 표시 방식을 기본으로 하고, 이후 앱 전환 시 푸시 알림이 확장된다(§28.5)
- 인증: 기존 API와 동일하게 로컬 단일 사용자를 전제로 하며, 화면에서 organizationId/userId를 입력받아 localStorage에 보관한다. API 키·secret은 화면에 표시하지 않는다(secretRef 참조만 입력)
- 모바일: 모바일 우선 반응형 레이아웃을 적용한다(좁은 화면에서는 하단 탭 내비게이션, 카드형 목록, 터치 친화 버튼)
- 프로젝트/업무 목록 조회 API가 아직 없으므로, 콘솔은 생성한 프로젝트·업무를 localStorage에 기억해 표시한다. 서버 목록 API가 추가되면(§17 Projects/Tasks) 서버 조회로 대체한다

---

## 18. 저장소 디렉터리 구조

현재 실제 구조(구현 기준): Maven 단일 모듈 Spring Boot 백엔드와 정적 콘솔, Compose 배포, QEMU VM 검증 환경.

```text
auto-company/
  backend/
    pom.xml                       # Maven 단일 모듈 (버전 고정)
    src/main/java/com/autocompany/
      controller/                  # Spring MVC controllers, DTO
      service/                     # 도메인 서비스(유스케이스, 트랜잭션 경계)
      entity/                      # JPA 엔티티와 enum
      repository/                  # Spring Data JPA
      model/                       # ModelAdapter 계약과 불변 값 타입
    src/main/resources/static/    # 초기 웹 콘솔 (정적, §17)
    src/test/java/                # 단위/서비스/웹 계층 테스트
  docker-compose.yml               # web + postgres
  .env.example
  docs/
    DESIGN.md
  qemu-test/                       # QEMU VM 기반 자동 검증 스크립트
  README.md
  AGENTS.md
```

확장 목표 모노레포(장기): Next.js 콘솔(`apps/web`), 모듈 분리(domain/application/infrastructure/api), worker, workspace runner 클라이언트, 진입점·설정 분리 등은 안정화 이후점으로 유지한다. 초기에는 하나의 Spring Boot 프로세스에서 논리 모듈(§3.1)을 패키지 경계로 유지한다.

Python은 초기 필수 런타임이 아니다. 향후 Python 전용 라이브러리를 사용하는 선택적 worker를 추가한다면 별도 디렉터리와 별도 이미지로 두고, Java 백엔드의 안정된 API/메시지 계약을 통해 통신한다.

---

## 19. 개발 단계 및 완료 기준

### Phase 0 — 저장소 및 개발 기반

구현:
- Java/Spring Boot/Maven 버전 조합을 고정하고 toolchain을 선언(실제 저장소는 Maven 기준)
- Spring Boot 모듈형 모놀리스의 의존성 규칙과 패키지 경계 설정
- 모노레포와 코드 스타일 설정
- Docker Compose 기반 PostgreSQL/Redis/API/Web 실행
- 설정 검증과 `.env.example`
- 마이그레이션/테스트/린트 CI
- 구조화 로그와 correlation ID
- preflight 및 backup/restore 초안

완료 조건:
- 새 개발자가 문서만 보고 로컬 실행 가능
- 빈 DB에서 migration이 재현 가능
- 비밀정보가 로그/저장소에 노출되지 않음

### Phase 1 — 도메인 및 CEO 콘솔

구현:
- Organization, User, Team, Employee, Project, Task CRUD
- JobGrade CRUD(추가·삭제·순서·책임·담당 업무 유형)와 직원 직급 배정
- 기본 템플릿 import (기본 5단계 직급 포함)
- 직무/직급/상태/권한 UI
- DB 제약조건 및 입력 검증
- AuditEvent 기록

완료 조건:
- UI에서 기본 8인 조직을 생성/수정/비활성화 가능
- 직급을 추가·삭제·개명할 수 있고, 직원이 직급을 참조해 저장됨
- 삭제하려는 직급에 배정된 직원이 있으면 거부됨
- 허가되지 않은 사용자의 수정 요청이 거부됨

### Phase 2 — Model Adapter 및 비용 원장

구현:
- 공통 ModelAdapter 인터페이스
- 최소 한 개의 hosted provider와 한 개의 OpenAI-compatible/local adapter
- mock provider로 테스트 가능
- health check, timeout, usage parsing
- ModelProfile/직급별 가용 모델 풀(GradeModel)
- BudgetPolicy/UsageLedger
- 가격 불명 모델의 제한 정책

완료 조건:
- provider를 바꾸어도 Agent Runtime의 도메인 코드는 변경하지 않음
- 호출별 모델, usage, 비용, 오류가 기록됨
- provider 실패와 인증 오류가 구분됨

### Phase 3 — Task Workflow와 Agent Runtime

구현:
- Task 상태 머신
- worker lease, retry, pause/resume
- context builder
- structured output schema
- tool call dispatch
- 실행 횟수/시간/비용 제한
- quota waiting state

완료 조건:
- worker를 강제 종료한 뒤 작업을 복구할 수 있음
- 최대 재시도 후 무한 루프 없이 종료됨
- 중복 실행으로 같은 Task가 중복 완료되지 않음

### Phase 3A — 요구사항 명확화 파이프라인 (REQ-001~003)

구현:
- ClarificationSession/ClarificationQuestion/RequirementSet 엔티티와 상태 전이
- 골(Goal) 접수 API와 내부 협의 개설(Mock 모델 시뮬레이션; MODEL-002 전까지 모든 직원 응답은 mock 기반)
- 질문 구성기: 기능별 개별 체크박스, 선택형, 기타 자유 텍스트, 세션 질문 수 한도
- 역할 검토 체인(§28.4)과 병렬 실행(Freeze) 불변성
- `waiting_client` 세션 큐와 고객 접속 시 미답변 세션 표시

완료 조건:
- 고객이 골을 제출하면 질문 세트가 검토 체인 통과 후 고객에게 표시됨
- 고객이 오프라인이어도 세션이 큐에 유지되고 접속 시 확인 가능
- 동결 이후 수정 시도가 모두 차단되고 감사 로그가 남음
- 새 세션을 열어 이전 기록을 보존하며 진화함

### Phase 3B — 자율 오케스트레이션 (ORCH-001~002)

구현:
- 동결된 RequirementSet → Epic/Story/Task 분해(Mock 모델 기반)
- 직무/직급 `task_types` 기반 자동 배정
- 재계획·에스컬레이션 규칙

완료 조건:
- 고객이 직원마다 직접 할당하지 않고 골만으로 작업 분해와 배정이 진행됨
- 요구사항이 부족하면 새 명확화 세션이 열림
- 모든 분해·배정 결정에 근거가 기록됨

### Phase 4 — Workspace 및 Git 통합

구현:
- repository allowlist
- 작업별 branch/worktree
- Docker workspace provisioning
- CPU/RAM/time/network limits
- diff와 test artifact 저장
- cleanup worker
- PR 생성/리뷰 상태 관리

완료 조건:
- 직원 두 명이 서로 다른 workspace에서 독립 변경 가능
- workspace가 호스트 비밀정보에 접근하지 못함
- 종료/실패 시 리소스 정리와 감사 기록이 남음

### Phase 5 — 품질 게이트와 리뷰 루프

구현:
- 프로젝트별 command templates
- unit/lint/typecheck/build 실행
- Review entity와 코멘트
- 작성자 자기 승인 방지
- 수정 요청 후 재실행
- merge policy와 human approval

완료 조건:
- 테스트 실패 PR은 승인 상태여도 완료/merge되지 않음
- 코드가 바뀌면 관련 검증 결과가 오래된 것으로 처리됨
- 리뷰 수정 요청이 실제 Task 상태로 연결됨

### Phase 6 — 문서와 CEO 질의응답

구현:
- docs tree 및 revision
- full-text search
- 코드/commit/Task 참조 연결
- 문서 freshness 상태
- 답변에 근거 링크와 불확실성 표시
- 문서 담당자 workflow

완료 조건:
- 프로젝트 질문에 문서/코드 근거를 반환함
- 관련 근거가 없으면 추측 대신 미확인 상태를 표시함
- 코드 변경 후 문서 검토 Task를 생성할 수 있음

### Phase 7 — 회의와 동료 평가

구현:
- Meeting, MeetingMessage, Decision
- max rounds/cost
- 독립 제안과 반대 근거
- 직무별 Evaluation rubric
- 평가 결과를 직급 변경 참고 자료로 제공하는 UI

완료 조건:
- 회의가 한도에서 종료됨
- 결정과 반대 의견이 보존됨
- 평가 점수에 evidence가 연결됨
- 평가 점수로 직급을 자동 변경하지 않음

### Phase 8 — 고급 라우팅, VM, 운영 자동화

구현:
- 작업 위험도/복잡도 기반 모델 라우팅 개선
- 모델 성과 관측 및 비용 대비 품질 비교
- VM executor plugin
- 운영 이슈 기반 유지보수 workflow
- 분산 worker와 장기 워크플로 확장

완료 조건:
- 정책 범위를 넘는 모델/환경 선택이 차단됨
- VM executor가 없어도 기본 기능은 정상 동작
- 모델 라우팅 변경의 품질과 비용 영향이 측정 가능

---

## 20. 테스트 전략

### Unit tests

- 상태 머신의 모든 허용/금지 전이
- 권한 정책의 allow/deny
- 비용 계산과 budget reservation
- 모델 라우팅과 fallback
- PR 작성자 자기 승인 차단
- task dependency cycle 탐지
- path traversal 및 symlink escape 차단
- prompt/result schema validation

### Integration tests

- PostgreSQL migration 및 repository
- worker 재시작과 lease 회수
- provider mock과 timeout/rate limit
- Docker workspace 생성/정리
- Git branch/diff/commit/merge conflict
- 테스트 실패 → 수정 요청 → 재검증 루프
- quota wait → resume
- audit event 생성

### End-to-end tests

1. 기본 회사 템플릿 생성
2. 프로젝트 저장소 등록
3. 간단한 버그 수정 Task 생성
4. 직원이 격리 workspace에서 변경
5. 테스트 실행
6. PR/리뷰 생성
7. 수정 요청과 재실행
8. 필수 검사 통과 후 승인 요청
9. 문서 갱신 및 비용 확인
10. 작업 완료 보고서 확인

### 장애/복구 테스트

- provider timeout
- provider 429/quota
- 잘못된 API 키
- worker kill/restart
- Docker daemon 중단
- 디스크 부족
- DB 연결 단절
- 중복 webhook
- PR merge conflict
- 모델의 잘못된 JSON
- 무한 도구 호출 시도
- budget limit 도달

---

## 21. 보안 및 위협 모델

위협 대상:
- 악의적이거나 prompt injection이 포함된 저장소 파일
- 잘못된 모델 출력
- 의도치 않은 shell command
- API 키/토큰 노출
- workspace를 통한 호스트 파일 접근
- 무단 네트워크 요청
- PR/merge 권한 오남용
- 비용 고갈 공격
- 평가 담합 및 편향
- 손상되거나 오래된 문서

필수 방어:
- RBAC와 서버 측 권한 검증
- allowlist 기반 저장소 경로와 명령
- 컨테이너 제한 및 별도 runner
- secret redaction 및 secret store
- 네트워크 egress 제한
- budget/token/runtime hard limit
- 승인 분리와 감사 로그
- 사용자 제공 텍스트/저장소 내용을 데이터로 취급
- 의존성 및 컨테이너 이미지 업데이트 절차
- 백업 암호화 및 복구 테스트
- 기본적으로 운영 서버와 외부 고객 데이터 접근 금지

로컬 실행이라도 웹 UI를 외부 네트워크에 공개하지 않는다. 원격 접근을 활성화할 경우 인증, TLS, 방화벽, secret management, 업데이트 정책을 별도로 구성해야 한다.

---

## 22. 운영·백업·복구

### 필수 운영 기능

- health/readiness endpoint
- worker heartbeat와 stale lease 탐지
- queue backlog 및 오래 대기 중인 Task 표시
- provider health 및 quota 상태
- workspace 누수/정리 실패 알림
- 디스크 사용량과 artifact retention
- 데이터베이스 migration 상태
- 구조화 로그와 correlation ID
- 비용 예산 경고
- 모델/도구/정책 버전 표시

### 백업

백업 대상:
- PostgreSQL
- 설정 파일(비밀정보 제외 또는 별도 암호화)
- 문서/아티팩트 저장소
- Git 저장소가 플랫폼 관리 대상인 경우 해당 저장소

복구 절차는 문서화하고 정기적으로 테스트한다. DB만 복구하고 artifact 파일을 잃는 불완전한 백업이 되지 않게 한다. `backup.sh`와 `restore.sh`는 dry-run 또는 확인 단계를 제공한다.

---

## 23. 초기 기본 설정 파일 예시

아래는 개념 예시다. 실제 schema validation을 통해 허용 값과 기본값을 코드에서 정의한다.

```yaml
app:
  mode: local
  timezone: system
  external_access: false

organization:
  template: default-si-team
  default_language: ko-KR
  default_grade_count: 5          # 기본 템플릿이 생성하는 직급 수. 사용자가 변경 가능

workflow:
  max_task_attempts: 3
  max_parallel_tasks: 3
  max_meeting_rounds: 3
  default_task_timeout_seconds: 1800
  worker_lease_seconds: 60
  retry_base_seconds: 10
  retry_max_seconds: 900

models:
  grade_selection_mode: priority   # 직급 가용 모델 풀의 기본 선택 방식. priority | task_based | budget_optimized
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
  default_employee_limits:
    monthly_cost: 5.00
    daily_cost: 1.00
    rolling_5h_total_tokens: 500000
    daily_requests: 500
    max_concurrent_runs: 1
  default_organization_limits:
    monthly_cost: 25.00
    rolling_5h_total_tokens: 3000000
  default_model_tiers:
    premium_monthly_cost: 3.00
  paid_calls_enabled_by_default: false
  reset_timezone: system

workspace:
  default_executor: docker
  run_as_non_root: true
  read_only_rootfs: true
  network_mode: none
  cpu_limit: 2
  memory_mb: 4096
  disk_mb: 10240
  timeout_seconds: 1800
  auto_cleanup: true
  allow_docker_socket_mount: false

quality:
  require_independent_reviewer: true
  minimum_review_count: 1
  require_ci_pass: true
  require_human_for_production: true
  allow_ai_self_approval: false

security:
  redact_secrets_in_logs: true
  allow_host_paths: false
  allow_unrestricted_network: false
  production_deploy_enabled: false

clarification:
  max_questions_per_session: 5
  review_chain_roles: [PLANNER, ARCHITECT, QA_ENGINEER, SECURITY_DEVOPS, PROJECT_MANAGER]
  allow_other_by_default: true
  max_sessions_per_goal: 3
  expert_signal_sources: [goal_text, question_answers_free_text, client_extra_note]

orchestrator:
  auto_assignment: true
  replan_attempts_per_requirement_set: 2

documents:
  require_evidence_refs: true
  mark_stale_on_code_change: true
  default_search_mode: full_text
```

### 기본값 관련 주의

- `memory_mb`, `cpu_limit`, `disk_mb`는 예시 값이며 실제 호스트 사양에 맞춰 preflight에서 검증한다.
- `network_mode: none`은 보안에 유리하지만 패키지 설치나 API 테스트를 막을 수 있다. 네트워크가 필요한 단계에는 목적지 allowlist를 둔 별도 정책을 적용한다.
- `allow_paid_fallback: false`는 예상치 못한 비용을 방지한다. 사용자가 허용하기 전에는 대체 유료 모델을 호출하지 않는다.
- `default_quality_tier`는 설치된 모델 프로필과 일치하도록 검증한다.
- `default_grade_count`는 기본 템플릿이 만들 직급 수다. 직급별 가용 모델 풀이 비어 있으면 preflight에서 경고하고, 모델이 배정되지 않은 직급의 직원은 실행 전 사용 가능한 모델을 요구한다.
- `clarification.review_chain_roles`의 순서는 검토 체인 정의(§28.4)와 동일하게 유지한다. 조직에 존재하지 않는 역할은 건너뛰고 감사 로그에 기록한다. `max_sessions_per_goal` 초과 시 세션 연속이 아닌 운영자 개입 기록을 남긴다.
- 예산 기본값은 조직/프로젝트 생성 시 사용자가 확인하도록 하고, 무제한으로 설정하지 않는다.
- 위 숫자는 설치 직후 안전하게 시험할 수 있도록 제안한 초기 템플릿이지, 모든 공급자의 무료 티어/가격을 보장하는 값은 아니다. 실제 적용 전 provider 가격과 조직의 의도한 지출 한도를 확인한다.
- `paid_calls_enabled_by_default: false`인 초기 설치는 유료 provider 호출을 명시적으로 활성화하고 한도를 확인하기 전에는 유료 호출을 하지 않는다. 사용자가 유료 호출을 켠 뒤에는 직원별 기본 한도가 자동 적용된다.
- 5시간 토큰 제한은 총 토큰 기준 기본값이다. 운영자는 입력/출력별 정책을 추가하거나 총 토큰 제한과 함께 사용할 수 있다.
- 월 비용 기본값은 달력 월 기준이며, rolling 5시간 한도와 일일 비용 한도는 독립적으로 적용된다. 직원별 기본값보다 조직 한도가 더 엄격하면 조직 한도가 우선한다.

---

## 24. MVP 인수 기준

MVP는 아래 항목을 모두 충족해야 “실제 프로젝트에 사용 가능” 상태로 본다.

1. 로컬 Docker Compose 설치가 문서대로 성공한다.
2. 최초 관리자와 기본 조직 템플릿을 생성할 수 있다.
3. 직무, 직급, 직급별 가용 모델이 독립적으로 설정된다. 직급을 추가·삭제할 수 있고, 배정된 직원이 있는 직급의 삭제는 거부된다.
4. 최소 두 개의 모델 provider adapter 또는 하나의 실제 provider와 mock provider가 작동한다.
5. provider 비용/사용량과 실패 이유가 기록된다.
6. 프로젝트를 로컬 Git 저장소에 연결할 수 있다.
7. Task를 만들고 직원에게 할당할 수 있다.
8. worker 재시작 후 Task 상태가 보존된다.
9. Task별 격리 workspace에서 실제 파일 변경과 테스트를 수행한다.
10. PR 또는 내부 PR 객체와 리뷰 코멘트가 생성된다.
11. 작성자 자기 승인이 차단된다.
12. 실패한 필수 테스트가 완료/merge를 막는다.
13. quota/예산 초과 시 대기하거나 안전하게 중단한다.
14. 문서와 테스트 결과가 Task/commit에 연결된다.
15. 주요 권한 변경과 실행이 audit log에 남는다.
16. workspace 종료 시 정리되며 실패한 정리 작업이 탐지된다.
17. API 키가 저장소, 프롬프트 로그, UI에 노출되지 않는다.
18. backup/restore 절차를 검증할 수 있다.
19. 고객이 단일 프롬프트(골)를 제출하면 직원들이 협의해 구조화된 질문(기능별 개별 체크박스 + 기타 자유 텍스트)을 만들어 고객에게 전달한다. 고객이 없어도 세션은 큐에 유지된다(§28).
20. 고객 답변 수집 후 세션이 동결되고 불변 RequirementSet이 생성된다. 이후 수정 시도가 409로 차단되며, 새 요구사항은 새 세션으로 진화한다.
21. 동결된 요구사항만을 근거로 작업 분해와 자동 배정이 진행되고(MVP는 Mock 모델 시뮬레이션), 고객은 직원별 할당을 직접 하지 않는다(§29).
22. 고객에게 내부 기술 질문(프레임워크/DB 선택 등)이 전달되지 않는다. 기술 결정은 내부 Decision 기록으로만 남는다.

---

## 25. 구현 시 지켜야 할 결정 사항 요약

1. **모놀리식 모듈러 백엔드로 시작**하고 실제 병목이 생기기 전까지 마이크로서비스를 만들지 않는다.
2. **PostgreSQL을 업무 상태의 기준 데이터 저장소**로 사용한다. 큐/캐시는 상태의 유일한 원천이 아니다.
3. **AI 대화보다 Task/Workflow 상태 머신을 우선**한다.
4. **모델 제공자 어댑터를 분리**해 특정 SDK에 종속되지 않게 한다.
5. **작업별 Docker workspace**를 기본으로 하며, Docker socket을 작업 컨테이너에 노출하지 않는다.
6. **Git 브랜치와 PR을 모든 코드 변경의 기본 흐름**으로 삼는다.
7. **작성자 자기 승인 금지**, 필수 테스트와 독립 리뷰를 기본 게이트로 한다.
8. **직원의 모델 사용 범위는 소속 직급의 가용 모델 풀로 제한**하고, 풀 내 선택은 위험·복잡도·예산과 우선순위에 따른다. 직급별 모델 배정은 사용자가 설정한다.
9. **평가는 근거 기반으로 수행하되 직급을 자동 변경하지 않는다.** 직급 부여·변경은 사용자가 수동으로 수행하며, 점수만으로 권한을 자동 확대하지 않는다.
10. **기본 설치에서 프로덕션 배포와 무제한 네트워크는 비활성화**한다.
11. **모든 작업에는 최대 시간·시도 횟수·비용 한도**가 있어야 한다.
12. **직원별 복수 사용량 제한**을 동시에 설정할 수 있어야 한다. 예: 월 비용 + rolling 5시간 토큰 + 일일 요청 수 + 고급 모델 월 비용.
13. **동시 실행에서도 한도 초과 호출이 발생하지 않도록** reserve/settle 경쟁 조건 테스트를 통과해야 한다.
14. **한도 도달 시 유료 호출을 차단하고 작업을 대기/재개**하며, fallback은 명시적으로 허용된 경우에만 사용해야 한다.
15. **문서 답변은 근거를 제시**하고 근거가 부족하면 모른다고 표시한다.
16. **로컬 우선이지만 환경 capability를 명시**해 OS/VM/GPU가 없을 때 가능한 척하지 않는다.
17. **초기 기본 템플릿은 안전하고 작동 가능한 출발점**으로 제공하며, 실제 비용/품질 측정으로 개선한다.
18. **직급과 직급별 가용 모델은 사용자가 구성**한다. 직원은 개별 모델이 아니라 직급을 가지며, 직급 수를 늘리거나 줄일 수 있다.
19. **사용자는 외주 고객(Client) 포지션**이다. 고객에게는 요구사항만 묻고, 기술 결정은 내부 회의/Decision으로 처리한다(§28.2).
20. **고객 질문은 구조화 포맷(기능별 개별 체크박스, 선택형) + 기타 자유 텍스트**를 기본으로 하며, 세션당 질문 수 상한(`max_questions`)을 둔다. 자유 텍스트는 선택지 누락·오류 시 고객이 직접 의견을 넣을 창구다(§28.3).
21. **질문 세션은 별도 관리 단위**다. `open → waiting_client → review → frozen` 상태로 관리하고, 고객 오프라인 여부와 무관하게 큐에 유지된다(§28.5).
22. **동결된 요구사항 기록은 불변**이다. 이후 수정은 새 세션·새 기록 버전으로만 진화한다(§28.6).
23. **자율 오케스트레이션은 동결된 요구만 근거로** 작업 분해·배정을 수행하며, 고객이 직원마다 직접 할당하지 않는다(§29).
24. **고객 자발적 전문 요구사항은 제약으로 수용**한다. goal_text·답변 기타 텍스트·`client_extra_note`에서 수집한 내용은 `source=client_declared`로 기록하고, 해당 영역의 질문은 면제 또는 간략화한다. 안전·법률·품질 바닥선과 충돌하는 표시 요구는 임의로 판단하지 않고 제품 수준 후속 질문으로 확인한다(§28.2).

---

## 26. 권장 첫 번째 구현 티켓

실제 개발은 다음 순서로 작은 티켓을 생성해 진행한다.

1. `PLAT-001`: 모노레포 및 개발 도구 초기화
2. `PLAT-002`: Docker Compose 개발 환경
3. `PLAT-003`: Spring Boot Actuator health/readiness 및 설정 검증
4. `PLAT-004`: PostgreSQL 도메인 매핑과 Flyway migration
5. `PLAT-005`: 사용자/조직 CRUD 및 최초 관리자 설정
6. `ORG-001`: Employee/Team/직무/직급(JobGrade) 도메인 모델, 직원 직급 배정, 직급 CRUD(추가·삭제·순서·책임·담당 업무 유형) — **완료**
7. `ORG-002`: 직급별 가용 모델 풀(GradeModel) 설정과 기본 회사 템플릿 import (기본 5단계 직급 포함) — **완료**
8. `PLAT-004`: PostgreSQL 도메인 매핑과 Flyway migration
9. `MODEL-001`: ModelAdapter 인터페이스와 MockProvider — **완료**
10. `REQ-001`: ClarificationSession 도메인 모델(§5.23)과 골(Goal) 접수 API
11. `REQ-002`: 질문 구성기(QuestionComposer)와 구조화 질문 스키마(기능별 개별 체크박스, 기타 자유 텍스트)
12. `REQ-003`: 역할 검토 체인(§28.4)과 동결(Freeze)+불변성 검증
13. `ORCH-001`: 동결된 요구 → Epic/Story/Task 분해 (Mock 모델 기반)
14. `ORCH-002`: 자동 배정 정책(직무/직급 task_types 매칭)과 재계획
15. `AUD-001`: AuditEvent middleware/service
16. `MODEL-002`: 첫 실제 provider adapter 및 health check
17. `MODEL-003`: ModelProfile/직급별 가용 모델 풀(GradeModel)과 비용 원장
18. `TASK-001`: Task 상태 머신과 상태 전이 테스트
19. `WF-001`: worker lease, retry, pause/resume
20. `AGENT-001`: Agent Runtime의 context builder와 structured output
21. `TOOL-001`: Tool Broker와 권한 정책
22. `WS-001`: Docker workspace lifecycle
23. `GIT-001`: branch/worktree/diff 도구
24. `QA-001`: command template 및 TestRun
25. `PR-001`: 내부 PR/Review 모델과 자기 승인 방지
26. `DOC-001`: Artifact/Document revision 및 검색
27. `UI-001`: 고객 대시보드와 작업 타임라인 (Next.js)
28. `UI-002`: 콘솔 요구사항 세션 탭(질문 응답 UI)과 골 입력 탭
29. `E2E-001`: 골 제출→명확화→동결→분해→배정 목 시나리오
30. `OPS-001`: backup/restore 및 preflight
31. `COST-001`: 예산 경고와 quota wait/resume
32. `COST-002`: BudgetPolicy의 직원/모델/기간별 복수 한도와 설정 UI
33. `COST-003`: 원자적 UsageReservation reserve/settle 및 동시 실행 경쟁 조건 테스트
34. `COST-004`: rolling 5시간 토큰, 달력 월 비용, 일일 요청 수 집계 및 초기화 테스트
35. `COST-005`: 한도 경고, 일회성 grant 승인, rolling window 회복 후 작업 재개

각 티켓에는 `acceptance_criteria`, 의존 Task, 테스트 계획, 담당자, 리뷰어를 반드시 지정한다. AI가 티켓을 구현할 때에도 이 기준을 동일하게 적용한다.

사용량 제한 구현 시 우선순위:
- 첫째, 실제 provider usage를 기록하고 비용을 정확히 계산한다.
- 둘째, 직원/조직/모델별 `BudgetPolicy`를 조회하고 API 호출 전 원자적 예약을 한다.
- 셋째, 비용·토큰·요청 수에 대한 기간 집계를 만든다. rolling 5시간은 경계 시각을 포함하는 이동 창으로 테스트한다.
- 넷째, 한도 도달 시 `WAITING_FOR_QUOTA`와 `retry_after_at`을 저장하고, 재개 조건을 충족할 때만 scheduler가 재시도한다.
- 다섯째, 경고/잔여량 UI와 일회성 승인 grant를 구현한다.
- 테스트는 두 worker가 동시에 잔여량보다 큰 요청을 예약하려는 상황, provider timeout 후 실제 과금 여부가 불명확한 상황, 월 경계/시간대, rolling window 경계, fallback 모델 자체 한도 초과를 포함한다.

---

## 27. 최종 결론

이 플랫폼의 중심은 “AI 직원 여러 명”이 아니라 다음의 결합이다.

- 지속적인 직원/조직 모델과 사용자 정의 직급/모델 풀
- 재개 가능한 업무 워크플로
- 교체 가능한 다중 모델 런타임
- 격리된 실제 개발환경
- Git 기반 변경 관리
- 독립적인 품질 게이트
- 근거 기반 평가와 문서 지식
- 예산·권한·실행시간의 강제 제한

이 구조를 따르면 로컬 환경에서 시작해 다른 PC나 자체 서버로 옮길 수 있고, 나중에 VM 실행기나 분산 worker를 추가해도 핵심 도메인 모델을 유지할 수 있다.

가장 먼저 완성해야 할 수직 기능(vertical slice)은 **“고객이 단일 프롬프트로 요구사항을 말하면, AI 직원들이 내부 협의 후 최소한의 구조화된 질문을 통해 요구사항을 확정하고, 이를 불변 기록으로 동결한 뒤, 스스로 작업을 분해·배정해 격리된 Docker 환경에서 구현하고 테스트와 리뷰를 거쳐 고객에게 결과 리포트를 전달하는 것”**이다. 이 흐름이 안정적으로 작동한 뒤에 회의, 성과 평가, 자율 유지보수 기능을 확장한다.

---

## 28. 요구사항 명확화 파이프라인 (Requirement Engineering)

### 28.1 배경과 포지션

사용자는 CEO가 아니라 **외주 고객(Client)**이다. 고객은 "블로그 사이트 만들어줘" 같은 단일 프롬프트로 요청한다. 기획·설계·구현·검증 방법은 직원들의 내부 협의로 정하며, 고객에게 묻는 것은 **제품 요구사항 불확실성**뿐이다.

핵심 문제는 고객이 즉시 온라인 상태가 아닐 수 있다는 점이다. 웹 콘솔은 실시간 푸시가 어렵고, 잦은 알림은 고객 경험을 해친다. 따라서:

1.직원들 사이 협의로 스스로 검토할 수 있는 것 내에서 최대한 진행한다.
2. 반드시 고객 입력이 필요한 지점을 **최소한의 구조화된 질문**으로 추린다.
3. 질문은 세션 큐에 쌓이고, 고객이 접속했을 때 콘솔에서 확인해 답변한다.
4. 고객은 언제든 응답할 수 있으며, 그때까지 직원들은 고객이 필요로 하는 준비(요구사항 이해, 기술 비교, 진행 계획 등)로 직원들이 다음 요구사항에 대비한다.

### 28.2 고객 질문의 경계

고객에게 포함되는 질문:
- 제품의 기능 목록 (이 기능이 필요한가?)
- 품질·완성도 기준 (간단한 MVP로 먼저 볼 것인가? 완성품을 원하는가?)
- 우선순위 (어떤 기능이 먼저 인가?)
- 제약사항 (기한, 비용, 특별히 지켜야 하는 규칙)
- 외부 연동이 필요한 대상 (이 서비스에 있는 계정? 외부 서비스 연동?)

고객에게 포함되지 않는 질문:
- 기술 스택 (DB, 프레임워크, 라이브러리 등)
- 아키텍처 방식 (마이크로서비스 여부, SPA 여부 등)
- 구현 방법과 검증 방법
- 내부 팀 사정 (모델 선택, 예산, 직원 배정 등)

**"묻지 않는다"는 "받지 않는다"가 아니다** — 고객 자발적 전문 요구사항 수용:

위 경계는 고객에게 *먼저* 질문하지 않는다는 원칙이지, 고객이 제시한 전문적 내용을 거부한다는 뜻이 아니다.

1. **전문성 신호 판정**: 고객의 골 텍스트(goal_text)나 답변에 전문적 내용이 있으면(예: "Spring Boot + PostgreSQL로 만들어줘", "개인정보 암호화 필수 배치", "S3 대신 로컬 스토리지만"), 직원들 협의에서 해당 영역의 고객 신호라 판단하고 기록한다. 신호는 통째로 "전문가 고객" 단정이 아니라 **주제별 신호**(기술 스택에 지식 있음, 보안에 관심 있음 등)로 세분 기록한다.
2. **제약으로 수용**: 신호의 구체적 내용은 명시적 제약(`RequirementSet` 항목에 `source=client_declared`)으로 기록한다. 직원들은 이를 암묵적으로 재평가해 무시하지 않는다. 이미 고객이 결정을 제시한 영역에 같은 내용을 되묻지 않는다(면제), 필요하면 더 좁은 후속 질문으로만 확인한다.
3. **충돌 시 침묵 금지**: 고객 제시 요구가 안전·법률·품질 바닥선과 충돌하거나 서로 모순되면 직원들이 임의로 판단해 진행하지 않는다. 충돌 본문을 제품 수준으로 서술한 후속 질문 하나(검토 체인 통과)로 고객 확인을 받는다. 이때도 기술 대안을 강요하는 형태가 아니라 제품 결과의 차이(위험·일정·성능)를 설명하는 형태로 작성한다.
4. **항상 열려 있는 기입란**: `waiting_client` 상태의 모든 세션에는 "전문적인 요구사항이 있으면 추가로 작성해주세요"라는 안내 문구와 자유 입력란을 고정 표시한다(`ClarificationSession.client_extra_note`, §5.23). 고객이 전문 요구를 언제든 끼워 넣을 수 있어야 한다.
5. **신호의 원천**: `goal_text`(원문), 고객 답변의 기타 자유 텍스트, `client_extra_note` 세 곳에서 수집하며, 모두 `source=client_declared`로 일관되게 기록한다.

### 28.3 질문 포맷: 최소 질문으로 최대 의도

중요한 원칙: **하나의 질문은 가능한 한 다양한 응답을 한 번에 얻을 수 있게 구성**하되, 세션당 질문 수는 `max_questions`(기본 5)로 상한을 둔다. 질문 하나에 서로 다른 정보를 담지 않도록 분리한다.

예시 — "블로그 사이트 만들어줘"에 대한 이상적인 질문 세트:

```
질문 1 (single_choice): 어느 정도 수준에서 시작하고 싶으신가요?
 - (a) 먼저 간단한 MVP로 시작해보고 싶다 (로그인/글쓰기 등 핵심만)
 - (b) 실제 서비스 가능한 완성품으로 한 번에 만들고 싶다
 - (c) 기타 — 직접 적어주세요 (텍스트 필드)

질문 2 (multi_choice): 원하는 블로그 기능을 모두 선택해주세요. (각 기능은 개별 체크박스)
 - [ ] 회원가입/로그인
 - [ ] 글쓰기 (마크다운 지원 여부는 별도 질문)
 - [ ] 글 수정/삭제
 - [ ] 댓글
 - [ ] 좋아요/이모지 반응
 - [ ] 사진 업로드
 - [ ] 동영상 임베드/업로드
 - [ ] 비밀글/암호화 보관
 - [ ] 이웃(구독)/팔로우
 - [ ] 검색
 - [ ] 기타 — 직접 적어주세요
```

기타(Other) 항목은 기본적으로 **항상 제공**한다(§5.24 `allow_other`). 선택지가 잘못 만들어졌거나 고객이 원하는 기능이 누락되었을 때 이용자가 직접 텍스트로 의견을 넣을 수 있어야 한다. 이것이 v1.3→v1.4 개정의 핵심 동기 중 하나다.

추가로, 질문 세트와 별개로 세션 하단에는 항상 다음 고정 항목이 함께 표시된다:

```
전문적인 요구사항이 있으면 추가로 작성해주세요.
(예: 특정 기술 강제, 보안/개인정보 규정, 성능 요건, 금지사항 등 — 없으면 비워도 됩니다)
[ 자유 입력란 ]
```

이 고정 입력란(`client_extra_note`)에 내용이 작성되면 직원들 협의에서 전문성 신호로 분류되고, 구체 내용은 `source=client_declared` 제약으로 기록한다(§28.2). 비워 둔 것은 아무 효과가 없다.

### 28.4 검토 체인 (Grade Review Chain)

고객 질문이 곧바로 고객에게 가지 않는다. 생성된 질문은 직원들 사이 **역할 검토 체인**을 통과해야 `waiting_client` 상태로 간다. 분기 이력은 `ClarificationQuestion.reviewed_by_employee_ids`에 남는다.

체인의 첫 단계는 질문 작성 이전에 수행한다:

```
[0단계: 고객 전문성 신호 선식별]
  goal_text + client_extra_note + 기타 자유 텍스트에서 전문적 내용을 분류한다.
  이미 고객이 결정·제약을 제시한 영역은 질문 대상에서 빼거나 간략화하고,
  그 사유(면제/간략화, 근거 문장)를 협의 기록으로 남긴다.
  남은 불확실 영역만 질문 대상이 된다.

[질문 관련 주제 파악] → [PLANNER/L4 (프로젝트 리드) 최초 작성]
   → [ARCHITECT/L5] — "이 질문의 응답이 이후 작업의 범위·복잡도로 이어지는가? 기술적 성격이 너무 크게 노출되지 않는가?"
   → [QA ENGINEER/L3] — "답변이 인수 조건으로 전환될 수 있는가? 모호한 답변이 나오지 않게 했는가?"
   → [SECURITY_DEVOPS/L3] — "개인정보 처리·암호화·관제 이슈가 개입되는가?" (해당 시에만)
   → [PM 또는 기술문서/L2~L4] — "고객이 문장으로 쉽게 이해하는가? 전문 용어는 노출되지 않았는가?"
        → 통과 시 `waiting_client` 전송
```

체인을 통과하지 못한 질문은 다시 협의 회의(§13)로 돌아가 수정된다. 체인 순서는 구현에서 상수로 정의하고, 프로젝트 템플릿 직무 구성(§6.1)에 따라 존재하지 않는 역할은 건너뛴다(감사 로그에 건너뛴 이유 기록).

고객 제시 전문 요구가 안전·법률·품질 바닥선과 충돌하는 경우(§28.2 규칙 3)도 검토 체인에서 처리한다: 임의 진행 없이 충돌을 제품 수준 문구로 담은 후속 질문을 새로 만들어 같은 체인에 통과시킨다.

### 28.5 오프라인 고객 지원 및 질문 세션 관리

- 세션은 Open 대기(직원 협의) → `waiting_client`(질문 발송) → 세션 검토(고객 응답 반영) → 동결(Freeze) 순으로 관리(§5.23)
- 고객이 웹에 접속하면 `waiting_client` 세션이 목록 상단에 나타나고, `GET /clients/user/{userId}/pending-sessions`로도 조회 가능하다(§17)
- MVP 푸시 알림은 제공하지 않는다. 이후 앱 전환 시 푸시 알림이 추가되며, 질문을 반복 푸시하지 않도록 만료·요약 규칙을 함께 도입한다
- 고객이 장기간 무응답이면 각 질문의 `assumption_if_skipped`를 기준으로 진행할지, 기다릴지를 검토 체인이 판단하고 그 근거를 기록한다. 고객의 원 요구사항은 원문(goal_text)으로 보존되므로, 이후 고객이 접속해 정정 요청하면 새 세션을 열어 대응한다

### 28.6 동결(Freeze)과 불변성

세션 동결 시점:
- 고객이 모든 필수 질문에 답변 완료하고, 직원 검토 체인이 요구사항 기록을 승인했을 때
- 고객이 명시적으로 "이 정도면 진행해라"이라고 지시했을 때

동결 즉시 ClarificationSession은 `frozen` 상태가 되고, RequirementSet(§5.25)이 생성돼 불변 기록으로 남는다. 그 이후:
- 세션의 질문·답변·요구사항 기록을 어떤 API로도 수정할 수 없다(수정 시도는 409 + AuditEvent)
- 기획 이슈나 고객 요구 변경은 **새 세션**(session_number +1)으로만 진화한다
- 이전 세션의 반영된 요구사항에서 "기존과 다르게"이면 새 RequirementSet 버전이 추가되고 이전 버전이 `superseded`가 된다. 기록된 진화 과정은 감사와 회귀 분석에 사용한다

---

## 29. 자율 오케스트레이션 (Goal → Task 분해 → 실행)

### 29.1 원칙

- CEO/고객이 직원마다 직접 업무를 하나씩 할당하지 않는다. 직원들이 협의된 작업 분해와 배정을 플랫폼의 Orchestrator가 실행한다
- 분해·배정의 근거는 동결된 RequirementSet(§5.25)이다. 근거 없는 새 기능 작업의 자체 생성은 없다
- 제한은 계속 유지된다: 최대 라운드, 예산, 실행 시간, 최대 재시도, 실행 환경 격리, 주요 승인(merge/deploy)은 여전한 §13/§16/§21 정책을 따른다

### 29.2 사이클

```
[고객 골 접수]
   → [ClarificationSession 진행 · 동결] (§28)
   → [Operation lead (PLANNER)에 의한 작업 분석]
   → [작업 분해: Epic/Story/Task 트리 + 의존성]
        - 구현: 기존 Task 엔티티 재사용(parent_task_id, TaskDependency)
        - 인수 조건은 RequirementSet의 항목 참조
   → [배정: 직원 역할/직급을 대상으로 자동 매칭]
   → [실행: 각 Task는 기존 AgentRun 기반으로 수행]
   → [분석·검토: 품질 게이트와 리뷰 루프 (§12, §10.2)]
   → [확정: 결과 요약과 산출물 위험 평가를 고객에 리포트]
   → (요구사항 반영 불가 또는 새 소요사항 발견 시 → 새 ClarificationSession 개시)
```

### 29.3 배정 정책

- Task의 직무(TaskType)와 기술 선택은 직급별 `task_types`와 필요 능력을 우선 기준으로 배정한다
- 배정이 실패하면 `BLOCKED`가 되고 사용자 요청이 필요한 사유가 기록된다
- 배정 이력은 WorkflowRun에 기록되고, 이후 조직 변경(직급 재편, 직원 수 변동)에 영향을 분석할 수 있게 한다

### 29.4 상태 요약

Orchestrator 사이클은 지속적으로 상태를 보고한다:
- 고객 응답 대기 (`waiting_client`)
- 작업 분해 완료, 실행 중 Task 요약 (코드 상세 아닌 **제품 요약** 관점)
- 블로커/새 요구사항 필요

모든 상태 보고는 `GET /projects/{id}/orchestration`(§17)에서 확인 가능하다.
