# 🤖 AutoCompany (AI 가상 회사 시스템)

**현재 구현된 내용만 기술** (설계: `docs/DESIGN.md` v1.8)

## 구현 상태

### ✅ 구현 완료
- **설계 문서**: `docs/DESIGN.md` (v1.8, Section 0~30)
- **Java Spring Boot 백엔드**: `backend/` (Entity, Repository, Service, Controller, Config — JSON API 전용)
- **SPA 콘솔**: `frontend/` (React + TypeScript + Vite, nginx `front` 컨테이너가 정적 서빙 + `/api` 프록시)
- **테스트 코드**: `backend/src/test/`, `frontend/src/*.test.ts` (Vitest)
- **QEMU 환경**: `qemu-test/` (`arm64.qcow2` init 스냅샷, Java 21, Ubuntu 26.04 ARM64)
- **Docker**: 서비스별 멀티 스테이지 빌드 (`backend/Dockerfile` Maven→JRE, `frontend/Dockerfile` node→nginx)

### 🔧 현재 기능
- 웹 콘솔 (SPA — `http://localhost:3000`, nginx가 `/api`를 web(8080)으로 프록시, 모바일 반응형): 좌측 사이드바(지시/직원/직급/모델 — 모바일은 햄버거 드로어), 첫 접속 시 고정 단일 조직·사용자로 `POST /api/v1/organizations/template/import` 자동 호출(409는 이미 구성됨으로 간주), 대기 세션 배지
- 기본 회사 템플릿 import (`POST /api/v1/organizations/template/import` — 5단계 직급 L1~L5 + 기본 8인 조직 직원 생성, 이미 직급/직원이 있으면 409)
- 팀(부서) 관리 (TEAM-001 — `POST /api/v1/teams`(organizationId·name 필수, 조직 내 중복명 409)·`GET /teams?organizationId=`·`GET /teams/{id}`·`PATCH /teams/{id}`(부분 갱신: 이름·설명·status; 아카이브는 소속 직원 있으면 409, 아카이브 팀 배정 409·미존재 팀 404), DB V8 `teams` 테이블 + `employees.team_id`. 웹 콘솔: 직원 탭 팀 칩 필터·팀 만들기/이름 변경/아카이브·직원 상세 팀 배정)
- 직원 채용 (`POST /api/v1/employees` — 선택 `teamId`(active 팀만), `customInstructions`)
- 직급(JobGrade) 관리 (`GET/POST/PATCH/DELETE /api/v1/grades`, `GET/PUT /api/v1/grades/{id}/models`)
- 직원 직급 배정 (`PATCH /api/v1/employees/{id}/grade`)
- 직원 평가 리포트 (`GET /api/v1/employees/{id}/evaluations` — 피드백 이력·집계, 직급 변경 참고용)
- 모델 Provider/Profile 관리 (`GET/POST /api/v1/model-providers`, `GET/POST /api/v1/model-profiles`)
- 업무 할당 (`POST /api/v1/tasks`)
- 직급별 모델 풀 기반 Cost-Performance Router (PRIORITY / TASK_BASED / BUDGET_OPTIMIZED)
- 피드백 평가 (`POST /api/v1/ratings` — 근거 참조 `evidenceRefs` 포함, 자동 승급 없음)
- 요구 명확화 세션 기초 (REQ-001 — 골 접수 `POST /api/v1/projects/{id}/goal`, 파이프라인 세션 open 개설(PLANNER), 세션 조회 2종, 질문 수신함 `GET /users/{userId}/pending-sessions`)
- 클라리피케이션 질문·답변 (REQ-002 — 질문 구성 `POST /clarification-sessions/{id}/questions`(개별 체크박스·기타·max_questions 상한, 0단계 전문성 신호 선식별 기록), 질문 조회 `GET .../questions`, 구조화 답변 `POST .../answers`(유형별 검증·skipped, 필수 미답 409+남은 질문, 전체 답변 시 review 전환))
- 검토 체인·동결·요구사항 기록 (REQ-003 — 검토 체인 통과 후에만 질문 공개(open에서 재확인), 동결 `POST /clarification-sessions/{id}/freeze`(REVIEW만 가능, frozen 재동결 409 — 변경은 새 세션 REQ-005), RequirementSet v1 기록 생성(기능/비기능/제약/제외/리스크 + 항목 source 태깅 client_declared/client_answered/employee_derived), 누적 기록 `GET /projects/{id}/requirements`(버전 내림차순, 이전 FROZEN은 superseded 보존))
- 다중 모달 골 접수 (REQ-004 — `POST /api/v1/projects/{id}/goal`에 `attachments`(filename·mediaType·contentBase64) 허용, 텍스트·첨부 둘 다 비면 400, 한도 10개/50MB 초과 400, 첨부는 Artifact(§5.19) 저장 + 원본 보존 + 파싱 상태 `PARSED|UNSUPPORTED|IMAGE_PENDING_VISION`(native md/txt/csv/json은 UTF-8 자체 파싱, pdf/xlsx/pptx/docx/hwp·기타는 원본 보존+`UNSUPPORTED`, 이미지는 vision 모델 배정 전 `IMAGE_PENDING_VISION`), `ClarificationSession.attachments` 참조 스냅샷, 파싱 불가 첨부는 "텍스트 보강" 후속 질문 항상 추가, 파싱된 첨부 텍스트는 0단계 신호 선식별+동결 기록 근거(`첨부 {파일명} 파일`)에 합류)
- 웹 콘솔 명확화 UI (UI-002 — 지시 탭(구 "고객 골"): 세션 수신함(WAITING_CLIENT 상단 강조+사이드바 배지)·지시 접수(텍스트+첨부 파일→base64 인코딩 제출)·세션 탐색(질문 폼: 단일/복수 선택·기타 직접 입력·단답·자유 서술·수치·건너뛰기, 답변 제출 후 동결 버튼, 동결 시 5그룹 요구사항+근거·가정 표시))
- 사용자 개설 세션·취소·동결 경로 (REQ-005 — 사용자 개설 진화 세션 `POST /api/v1/projects/{id}/clarification-sessions`(변경 원문 `changeNote`+첨부, `session_number` 승계+`parent_session_id` 연결, 활성 세션 중복 409), 세션 취소 `POST /api/v1/clarification-sessions/{id}/cancel`(open|waiting_client만, Audit 기록, frozen 불가), 동결 확장: 사용자 개설 세션 질문 0개 즉시 동결(검토 체인은 기록 승인만 수행, `frozen_by` 기록), `clientDirective=true`로 waiting_client에서 "이 정도면 진행" 동결(미답 질문은 가정으로 기록). 웹 콘솔: 새 세션 개설 폼·세션 취소 버튼·"이 정도면 진행" 동결 버튼)
- 외부 고객 중계 (ORG-003 — Client 명부 `POST /api/v1/clients`(organizationId·displayName 필수)·`GET /clients?organizationId=`·`GET/PATCH /clients/{id}`(archived 전환, 기존 연결 기록 보존·신규 중계 연결 409), `Project.clientId`·`ClarificationSession.clientId` 연결(세션 생성 API `clientId` 지정 시 프로젝트 고객 override, 미지정 시 상속), 중계 세션 개설·동결 감사 로그("외부 고객 {이름} 중계 기록, 원문 보존"), 동결 기록 고객 원천 항목 source `client_relayed`+근거 "외부 고객 {이름} 중계 — ". v1.8 콘솔에서는 휴면(백엔드·테스트 유지, 콘솔은 `clientId`를 항상 null로 생성 — 외부 고객 카드 없음))
- 직원 커스텀 지시 (EMP-001 — `customInstructions` 필드(V7 `employees.custom_instructions` TEXT), 직원 생성 `POST /employees`·부분 갱신 `PATCH /employees/{id}`(`{name?, customInstructions?}` — null 유지·빈 문자열 지우기, 최대 4000자 초과 400 §26), §8.2 (3) 계층 조립 `EmployeeService.buildAgentContext`(직무 원칙+사용자 개별 지시+안전 정책 우선 문구; 후속 AgentRun 컨텍스트 소비 대상). 웹 콘솔: 직원 상세에서 지시 편집·저장)
- 콘솔 SPA 분리 (WEB-002 — 콘솔을 `frontend/`(React+TS+Vite)로 이전, `front` 컨테이너(nginx:1.27)가 `/` 정적 서빙(SPA fallback)+`/api`→`web:8080` 프록시, 백엔드 정적 콘솔 제거(`/` 404 — `BackendNoStaticConsoleTest`), Dockerfile 서비스 디렉터리별 배치(루트는 `docker-compose.yml`만), 프론트 단위 테스트 Vitest `npm test`)
- 지시 창구 통합 콘솔 (WEB-003 — 좌측 사이드바 IA(지시/직원/직급/모델), localStorage 제거(고정 단일 조직·사용자 상수), 지시 탭 = 단일 프롬프트 창구([묻기] `POST /ask` / [지시하기] 프로젝트 생성+첫 지시 접수) + 프로젝트 목록·상세(세션·질문·답변·동결·진화·업무·평가), 프로젝트 조회 API `GET /projects?userId=`(최신순)·`GET /projects/{id}`·`GET /projects/{id}/tasks`, 목록 우선 UX(빈 목록 중앙 생성 CTA·[+] 폼 토글), 메시지·CEO Ask UI 제거(백엔드 API 유지))
- 토론 (`POST /api/v1/messages` — v1.8 콘솔 미노출)
- CEO Ask Anything (`POST /api/v1/ask` — 콘솔 [묻기] 버튼 연결, 응답은 현재 고정 스텁)
- 문서 담당 직원 (`TaskType.DOCUMENTATION`) — 일반 직원 모델로 통합
- Git 기반 협업 (`docs/DESIGN.md` Section 4 구조)
- DB 스키마 마이그레이션 (Flyway — `backend/src/main/resources/db/migration/V1__baseline.sql`), 운영 DB는 `ddl-auto: validate` + 기존 DB는 `baseline-on-migrate`

### 📋 사용법 (현재 구현 기준)
```bash
# 1. 환경 변수 준비
cp .env.example .env

# 2. Docker Compose (front + web + PostgreSQL)
docker compose up --build -d
docker compose ps

# 3. 브라우저에서 웹 콘솔 접속 (SPA — front 컨테이너, 3000)
open http://localhost:3000
#  → 첫 접속 시 기본 조직·템플릿 자동 구성 (별도 연결 절차 없음)
#  → 지시 탭: 프롬프트 입력 → [묻기] 또는 [지시하기] → 프로젝트·세션 진행
#  → 직원 탭: 팀 만들기·배치, 직급/모델 탭에서 조직 구성 (모바일 브라우저도 동일 주소)
#  → API 직접 호출은 백엔드(8080) 그대로 사용 가능: http://localhost:8080/api/v1/...

# 4. QEMU VM에서 Spring Boot 테스트
ssh -p 2222 ubuntu@localhost
cd ~/autocompany/backend
mvn compile test -B

# 4-1. (선택) 프론트 단위 테스트 (VM Docker node 이미지 또는 Node 20 환경)
cd ../frontend && npm test

# 5. (선택) API로 직접 직원 채용
curl -X POST http://localhost:8080/api/v1/employees \
  -H "Content-Type: application/json" \
  -d '{"userId":"test","name":"테스트개발","role":"DEVELOPER"}'
```

### ⚠️ 제한사항
- `docs/DESIGN.md`는 항상 최신 상태 유지 필요
- `README.md`는 현재 구현 내용만 작성 (미구현 기능 포함 금지)
- QEMU VM (`arm64.qcow2`)는 `init` 스냅샷 기반 (이전 테스트 영향 제거 가능)
- VM/Docker 네트워크는 사용 가능 확인됨 (registry.npmjs.org 도달, 2026-10 실측). 폐쇄망에서는 사전 이미지 pull·npm 캐시 필요

### 📁 파일 구조
```
docs/DESIGN.md            # 설계 문서 (항상 최신)
AGENTS.md                 # 테스트/개발 프로토콜
README.md                 # 현재 구현 기준 안내
docker-compose.yml        # front(3000) + web(8080) + PostgreSQL(5432)
.env.example              # 환경 변수 예시
backend/                  # Java Spring Boot (JSON API) + backend/Dockerfile
frontend/                 # React+TS+Vite SPA + nginx.conf + frontend/Dockerfile
qemu-test/                # QEMU VM 환경
```

### 📚 참고
- **설계**: `docs/DESIGN.md`
- **개발/테스트 프로토콜**: `AGENTS.md`
- **Java 빌드**: `mvn compile test -B` (VM/Docker 내)
- **최적 Java 버전**: 21 (LTS, Spring Boot 3.2.0 호환)
