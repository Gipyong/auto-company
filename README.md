# 🤖 AutoCompany (AI 가상 회사 시스템)

**현재 구현된 내용만 기술** (설계: `docs/DESIGN.md` v1.6.1)

## 구현 상태

### ✅ 구현 완료
- **설계 문서**: `docs/DESIGN.md` (v1.6.1, Section 0~30)
- **Java Spring Boot 백엔드**: `backend/` (Entity, Repository, Service, Controller, Config)
- **테스트 코드**: `backend/src/test/`
- **QEMU 환경**: `qemu-test/` (`arm64.qcow2` init 스냅샷, Java 21, Ubuntu 26.04 ARM64)
- **Docker**: `Dockerfile` 기반 (네트워크 허용 시 사용 가능)

### 🔧 현재 기능
- 웹 콘솔 (`http://localhost:8080` — 브라우저에서 직원·직급·모델·업무를 직접 조작, 모바일 반응형)
- 기본 회사 템플릿 import (`POST /api/v1/organizations/template/import` — 5단계 직급 L1~L5 + 기본 8인 조직 직원 생성, 이미 직급/직원이 있으면 409)
- 직원 채용 (`POST /api/v1/employees`)
- 직급(JobGrade) 관리 (`GET/POST/PATCH/DELETE /api/v1/grades`, `GET/PUT /api/v1/grades/{id}/models`)
- 직원 직급 배정 (`PATCH /api/v1/employees/{id}/grade`)
- 직원 평가 리포트 (`GET /api/v1/employees/{id}/evaluations` — 피드백 이력·집계, 직급 변경 참고용)
- 모델 Provider/Profile 관리 (`GET/POST /api/v1/model-providers`, `GET/POST /api/v1/model-profiles`)
- 업무 할당 (`POST /api/v1/tasks`)
- 직급별 모델 풀 기반 Cost-Performance Router (PRIORITY / TASK_BASED / BUDGET_OPTIMIZED)
- 피드백 평가 (`POST /api/v1/ratings` — 근거 참조 `evidenceRefs` 포함, 자동 승급 없음)
- 요구 명확화 세션 기초 (REQ-001 — 골 접수 `POST /api/v1/projects/{id}/goal`, 파이프라인 세션 open 개설(PLANNER), 세션 조회 2종, 질문 수신함 `GET /users/{userId}/pending-sessions` /*동결·검토 체인은 REQ-003에서 구현*/)
- 클라리피케이션 질문·답변 (REQ-002 — 질문 구성 `POST /clarification-sessions/{id}/questions`(개별 체크박스·기타·max_questions 상한, 0단계 전문성 신호 선식별 기록), 질문 조회 `GET .../questions`, 구조화 답변 `POST .../answers`(유형별 검증·skipped, 필수 미답 409+남은 질문, 전체 답변 시 review 전환))
- 토론 (`POST /api/v1/messages`)
- CEO Ask Anything (`POST /api/v1/ask` — RAG 기반, 구조 준비)
- 문서 담당 직원 (`TaskType.DOCUMENTATION`) — 일반 직원 모델로 통합
- Git 기반 협업 (`docs/DESIGN.md` Section 4 구조)
- DB 스키마 마이그레이션 (Flyway — `backend/src/main/resources/db/migration/V1__baseline.sql`), 운영 DB는 `ddl-auto: validate` + 기존 DB는 `baseline-on-migrate`

### 📋 사용법 (현재 구현 기준)
```bash
# 1. 환경 변수 준비
cp .env.example .env

# 2. Docker Compose (앱 + PostgreSQL)
docker compose up --build -d
docker compose ps

# 3. 브라우저에서 웹 콘솔 접속
open http://localhost:8080
#  → '시작' 탭에서 새 ID 생성 → 저장 & 연결 → 기본 회사 템플릿 가져오기
#  → 직원/직급/모델/업무 탭에서 직접 조작 (모바일 브라우저도 동일 주소)

# 4. QEMU VM에서 Spring Boot 테스트
ssh -p 2222 ubuntu@localhost
cd ~/autocompany/backend
mvn compile test -B

# 5. (선택) API로 직접 직원 채용
curl -X POST http://localhost:8080/api/v1/employees \
  -H "Content-Type: application/json" \
  -d '{"userId":"test","name":"테스트개발","role":"DEVELOPER"}'
```

### ⚠️ 제한사항
- `docs/DESIGN.md`는 항상 최신 상태 유지 필요
- `README.md`는 현재 구현 내용만 작성 (미구현 기능 포함 금지)
- QEMU VM (`arm64.qcow2`)는 `init` 스냅샷 기반 (이전 테스트 영향 제거 가능)
- Docker 네트워크는 현재 제한됨 (이미지 pull 불가, 컨테이너 실행 가능)

### 📁 파일 구조
```
docs/DESIGN.md          # 설계 문서 (항상 최신)
AGENTS.md                # 테스트/개발 프로토콜
README.md                # 현재 구현 기준 안내
Dockerfile               # 멀티 스테이지 빌드 (Maven → JRE)
docker-compose.yml       # 앱 + PostgreSQL (포트 8080)
.env.example             # 환경 변수 예시
backend/                 # Java Spring Boot
local_runner/            # Python AI 워커
qemu-test/               # QEMU VM 환경
```

### 📚 참고
- **설계**: `docs/DESIGN.md`
- **개발/테스트 프로토콜**: `AGENTS.md`
- **Java 빌드**: `mvn compile test -B` (VM/Docker 내)
- **최적 Java 버전**: 21 (LTS, Spring Boot 3.2.0 호환)
