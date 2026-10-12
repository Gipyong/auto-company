# 🤖 AutoCompany (AI 가상 회사 시스템)

**현재 구현된 내용만 기술** (설계: `docs/DESIGN.md` v1.1.0)

## 구현 상태

### ✅ 구현 완료
- **설계 문서**: `docs/DESIGN.md` (Section 1~7 전체)
- **Java Spring Boot 백엔드**: `backend/` (Entity, Repository, Service, Controller, Config)
- **Python 로컬 워커**: `local_runner/` (Router, Docker, Runner)
- **테스트 코드**: `backend/src/test/` + `local_runner/`
- **QEMU 환경**: `qemu-test/` (`arm64.qcow2` init 스냅샷, Java 21, Ubuntu 26.04 ARM64)
- **Docker**: `Dockerfile` 기반 (네트워크 허용 시 사용 가능)

### 🔧 현재 기능
- 직원 채용 (`POST /api/v1/employees`)
- 업무 할당 (`POST /api/v1/tasks`)
- Cost-Performance Router (작업별 모델 선택 + 승급)
- 평가/등급 (`POST /api/v1/ratings` — Level 1~10, 직급 INTERN~LEAD)
- 토론 (`POST /api/v1/messages`)
- CEO Ask Anything (`POST /api/v1/ask` — RAG 기반, 구조 준비)
- 문서 담당 직원 (`TaskType.DOCUMENTATION`) — 일반 직원 모델로 통합
- Git 기반 협업 (`docs/DESIGN.md` Section 4 구조)

### 📋 사용법 (현재 구현 기준)
```bash
# 1. Python 로컬 워커 테스트
python3 demo.py

# 2. QEMU VM에서 Spring Boot 테스트
ssh -p 2222 ubuntu@localhost
cd ~/autocompany/backend
mvn compile test -B

# 3. 직접 직원 구성 테스트
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
backend/                 # Java Spring Boot
local_runner/            # Python AI 워커
qemu-test/               # QEMU VM 환경
```

### 📚 참고
- **설계**: `docs/DESIGN.md`
- **개발/테스트 프로토콜**: `AGENTS.md`
- **Python 실행**: `python3 local_runner/main.py`
- **Java 빌드**: `mvn compile test -B` (VM/Docker 내)
- **최적 Java 버전**: 21 (LTS, Spring Boot 3.2.0 호환)
