# AGENTS.md

AutoCompany: AI 직원 / 업무 / 평가를 다루는 시스템.
Java 21 Spring Boot 백엔드(Maven)이며, **배포 단위는 Docker 이미지**다.
검증은 QEMU VM에서, 사용자 직접 테스트는 호스트의 Docker에서 한다.

작업 전 이 파일을 먼저 읽는다. 현재 구현 범위는 `README.md`, 설계는 `docs/DESIGN.md`가 기준이다.

## 프로젝트 구조

| 경로 | 설명 |
|---|---|
| `backend/` | Spring Boot 백엔드 (경로 확인 필요, TODO 참고) |
| `Dockerfile`, `docker-compose.yml` | 앱 + PostgreSQL 배포 정의 (없으면 만들어야 함, TODO 참고) |
| `docs/DESIGN.md` | 설계 문서 (DB 스키마는 Section 2) |
| `qemu-test/run.sh`, `qemu-test/arm64.qcow2` | 검증용 QEMU ARM64 VM |

## 환경별 역할

| 단계 | 위치 | 내용 |
|---|---|---|
| 단위/통합 테스트 | QEMU VM | `mvn clean compile test -B` |
| 이미지 검증 | QEMU VM | `docker build` → `docker compose up` → curl 스모크 테스트 |
| 사용자 직접 테스트 | 호스트 Docker | `docker compose up` 후 API 호출 |

Java/Maven/PostgreSQL은 호스트에 설치하지 않는다. 빌드와 테스트는 VM 또는 Docker 안에서만 수행한다.

## 명령어

### 단위/통합 테스트 (QEMU VM 안)
```bash
mvn clean compile test -B                        # 전체 빌드 + 테스트
mvn test -Dtest=CostPerformanceRouterServiceTest # 단일 테스트
mvn test -Dtest=RatingEngineServiceTest
```

### 이미지 검증 (QEMU VM 안, Docker 필요)
```bash
docker build -t autocompany .
docker compose up --build -d
docker compose ps
curl http://localhost:8080/api/v1/employees      # 스모크 테스트
docker compose logs --tail=100                   # 실패 시 로그 확인
docker compose down -v                           # 정리 (볼륨 포함)
```

### 사용자 직접 테스트 (호스트 Docker)
```bash
docker compose up --build -d
curl http://localhost:8080/api/v1/employees
docker compose down -v
```
사용자 테스트 절차는 `README.md`와 동일하게 유지한다.

### QEMU VM
```bash
zsh qemu-test/run.sh                                   # VM 시작 (백그라운드)
ssh -o StrictHostKeyChecking=no -p 2222 ubuntu@localhost
qemu-img snapshot -l qemu-test/arm64.qcow2             # 스냅샷 목록 (VM 종료 상태에서)
```
- 스냅샷 `init` = 테스트 기준 상태, `create` = 테스트 전 백업.
- 테스트 전 `create`로 백업 → 테스트 → 실패하거나 오염되면 `init`으로 복원 후 재현.
- 디스크 이미지를 복사하거나 스냅샷을 만들 때는 VM을 먼저 종료한다.

## 작업 규칙

1. **설계 변경** → `docs/DESIGN.md`를 먼저 수정한 뒤 구현한다.
2. **구현** → 코드 변경 시 관련 테스트를 추가/수정한다.
3. **검증 순서** → VM에서 `mvn test` → VM에서 Docker 이미지 검증.
4. **문서** → `README.md`는 현재 구현된 기능만 기술한다 (미구현 기능 금지). 기능 추가/수정 시 함께 고친다. 실행 방법이 바뀌면 README의 `docker compose` 안내도 갱신한다.
5. **커밋** → git worktree를 사용하여 작업마다 브랜치를 별도로 만들어서 작업한다. worktree 경로는 이 프로젝트의 .worktree/ 하위에 만든다. 작업 후 테스트를 수행하며, 테스트 통과 후 `git commit` (코드 + 문서 함께). 실패 상태로 main에 merge하지 않는다.
6. **Dockerfile** → 멀티 스테이지 빌드(빌드 단계는 Maven, 실행 단계는 JRE)를 사용하고, 설정과 비밀 값은 환경 변수로 주입한다.

## 에이전트와 사람의 구분

- 에이전트는 VM에서의 단위/통합 테스트와 이미지 검증까지 수행하고, 자동으로 확인한 범위만 보고한다.
- 호스트에서의 사용자 직접 테스트는 사람이 한다. 대신 완료했다고 보고하지 않는다.

## 하지 말 것

- `qemu-test/arm64.qcow2` 같은 대용량 바이너리를 커밋하지 않는다 (`.gitignore` 확인).
- 호스트에 Java/Maven/PostgreSQL을 설치하지 않는다.
- `docs/DESIGN.md`와 코드가 어긋난 채로 두지 않는다.
- 비밀 키/자격 증명을 코드, 문서, 이미지, 커밋에 넣지 않는다.

## TODO (확인 후 채울 것)

- `Dockerfile`, `docker-compose.yml` 존재 여부 확인 후 없으면 작성 (앱 + PostgreSQL, 포트 8080).
- VM 기본 이미지에 Docker 설치 후 `init` 스냅샷 재생성 (현재 기반 환경은 Java 21 + Maven만 있음).
- 호스트 Mac의 Docker 런타임(Docker Desktop, OrbStack, Colima 등) 선택.
- VM 내 프로젝트 경로 확정 (기존 문서는 `~/autocompany/backend`라고만 되어 있었음).
- 각 POST API의 요청 바디 예시 추가.
- 서비스 이름 확정 후 `docker compose logs <service>` 형태로 명령 구체화.
