# AGENTS.md

## Project

- Project: AutoCompany — a system for AI employees, tasks, and evaluation
- Tech stack: Java 21, Spring Boot, Maven, PostgreSQL
- Deployment unit: Docker image
- Automated validation environment: QEMU VM
- Human acceptance-testing environment: Docker on the host
- Source of truth for implemented features: `README.md`
- Source of truth for architecture and database design: `docs/DESIGN.md`

These instructions are intended to apply across coding agents, including Codex, OpenCode, and Pi. Verify actual paths, files, commands, and service status in the repository and runtime environment before relying on them.

## Rules That Always Apply

1. Stay within the requested scope. Do not include unrelated refactoring or dependency changes.
2. Prefer established patterns in the existing code and documentation. If a request conflicts with the design, investigate the impact and possible alternatives.
3. Preserve the user's uncommitted changes. Never overwrite or revert them without permission.
4. Never report a test as passing or behavior as verified unless you actually ran the test or verified the behavior.
5. Never put secrets, credentials, or personal data in source code, logs, documentation, images, or commits.
6. Do not guess paths, ports, service names, or snapshot names. Verify them before use.
7. Do not modify this file unless the user explicitly asks you to.

## Workflow

### 1. Before Starting Work

- Check `git status`, the current branch, the repository root, and the directory structure.
- Read relevant source files and tests. Also read `README.md` and `docs/DESIGN.md` when the task affects behavior, APIs, the database, or architecture.
- Verify the actual locations and usage of `pom.xml`, Docker configuration, and QEMU scripts.
- Check whether required environments and services, including the VM, Docker, and the database, are ready.
- Investigate unknowns in the repository and runtime environment first. If a blocking issue remains unresolved, report what you verified and what action is needed.

### 2. Design, Code, and Documentation

- If an API, data model, database schema, domain behavior, or system structure changes, update `docs/DESIGN.md` **before implementing the change**.
- Do not update the design document unnecessarily for bug fixes or internal refactoring that do not change the design. However, correct the documentation if it no longer matches actual behavior.
- Add or update relevant tests for code changes. If you cannot add a test, explain why and report the remaining risk.
- Document only implemented features and currently valid instructions in `README.md`. Never describe an unimplemented feature as implemented.
- Update relevant documentation when APIs, execution procedures, configuration, or user-visible behavior change.

### 3. Git Isolation

- Make code changes in a dedicated `git worktree` and task branch. Place the worktree under `.worktree/` at the repository root, and verify that `.worktree/` is listed in `.gitignore`.
- If the current working directory is already the worktree for this task, use it; do not create a nested worktree. Read-only investigation does not require a worktree.
- Before deleting or cleaning up an existing branch, worktree, or user change, verify its state and which task or person owns it.
- Do not commit until the tests pass. If tests fail or cannot run, record the status and reason; never claim they passed.
- Do not work directly on `main` or merge unverified changes. Commit, merge, and push only as permitted by the user's request and project policy.

## Build and Validation

Run validation in the QEMU VM. Do not install Java, Maven, or PostgreSQL on the host. If the VM or a required service is unavailable, do not silently substitute host-side validation; report why the required validation could not be run.

1. Verify that the QEMU VM is available and confirm the project path inside it.
2. Locate the actual Maven project and verify its test configuration.
3. Run the full build and test suite inside the VM.
4. If the Docker validation environment is ready, build the image, start the Compose services, and run an API smoke test inside the VM.
5. Review the logs and `git diff`. Check for inconsistencies among code, tests, documentation, and configuration.

After verifying the actual `pom.xml` location and project configuration, run this default full-validation command:

```bash
mvn clean compile test -B
```

The following are examples of single-test commands. Verify that each test class exists before running it:

```bash
mvn test -Dtest=CostPerformanceRouterServiceTest
mvn test -Dtest=RatingEngineServiceTest
```

The following are examples of Docker validation commands. Verify the files, ports, endpoints, and service configuration before running them:

```bash
docker build -t autocompany .
docker compose up --build -d
docker compose ps
curl --fail --show-error http://localhost:8080/api/v1/employees
docker compose logs --tail=100
```

- Use these commands only after checking that they match the repository's actual configuration. If they do not match, determine the appropriate commands from the current configuration.
- Full test execution and Docker image validation are separate checks. Report only the checks you actually performed.
- On failure, inspect the logs and distinguish code defects from environment problems. After fixing the cause, rerun the relevant validation.
- If a check cannot be run, state why. Never treat a check that was not run as passing.

## Docker, VM, and Data Safety

- When creating a `Dockerfile` or changing the build approach, use a multi-stage build with Maven in the build stage and a JRE-based runtime stage.
- Inject configuration and secrets through environment variables or an approved secrets-management mechanism. Do not bake secrets into the image.
- Prefer `docker compose down` for routine Compose shutdown.
- `docker compose down -v` can delete volumes and their data. Run it only when test-data reset is clearly required, after verifying the target and potential data loss.
- Shut down the VM before copying a QEMU disk image or creating a snapshot. Restoring a snapshot can roll back disk state, so verify the target and identify changes that must be preserved first.
- Do not commit large binary files such as QEMU disk images. Verify the relevant `.gitignore` rules.
- Inspect the actual QEMU scripts and SSH configuration before running them. Do not disable security checks, such as SSH host-key verification, indiscriminately for convenience.

## Completion Report

After the work, provide a concise report that distinguishes:

- **Changes:** Key files changed and changes that affect users
- **Validation results:** Commands actually run and whether they passed or failed
- **Unverified items:** Tests not run and the reasons they could not be run
- **Remaining risks:** Environment dependencies, data impact, and required follow-up actions

Do not report host-Docker tests that must be performed by a human as completed by the agent. If only partial validation was performed, state its scope explicitly.
