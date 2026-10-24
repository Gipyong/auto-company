import { useEffect, useRef, useState } from "react";
import { api, ApiError } from "../api";
import { useStore } from "../store";
import { useToast } from "../toast";
import { fmtNum } from "../lib";
import { Badge, EmptyState, EvaluationReportView, StatusText } from "../components";
import type { Employee, EvaluationReport, TeamFeedback } from "../types";

const ROLES = [
  "PLANNER",
  "DEVELOPER",
  "DESIGNER",
  "PROJECT_MANAGER",
  "TECHNICAL_WRITER",
  "QA_ENGINEER",
  "ARCHITECT",
  "SECURITY_DEVOPS",
];

export default function EmployeesTab() {
  const store = useStore();
  const toast = useToast();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [evals, setEvals] = useState<EvaluationReport | null>(null);
  const [teamFeedbacks, setTeamFeedbacks] = useState<TeamFeedback[]>([]);
  const [instrResult, setInstrResult] = useState<{ kind: "ok" | "err"; text: string } | null>(null);
  const [assigning, setAssigning] = useState(false);
  const [savingInstr, setSavingInstr] = useState(false);
  const [teamFilter, setTeamFilter] = useState<string>("ALL");
  const [showHire, setShowHire] = useState(false);
  const [showTeams, setShowTeams] = useState(false);
  const [hiring, setHiring] = useState(false);
  const [teamSaving, setTeamSaving] = useState(false);
  const [renamingTeamId, setRenamingTeamId] = useState<string | null>(null);

  // ===== Central View State (Master-Detail) =====
  type CenterView =
    | { type: "welcome" }
    | { type: "employee"; employeeId: string }
    | { type: "team"; teamId: string }
    | { type: "hire" }
    | { type: "teamFeedback"; teamId: string };

  const [centerView, setCenterView] = useState<CenterView>({ type: "welcome" });

  const instrRef = useRef<HTMLTextAreaElement>(null);
  const gradeSelectRef = useRef<HTMLSelectElement>(null);
  const teamSelectRef = useRef<HTMLSelectElement>(null);
  const teamLeadSelectRef = useRef<HTMLSelectElement>(null);
  const detailRef = useRef<HTMLDivElement>(null);

  const hireNameRef = useRef<HTMLInputElement>(null);
  const hireRoleRef = useRef<HTMLSelectElement>(null);
  const hireTeamRef = useRef<HTMLSelectElement>(null);
  const hireInstrRef = useRef<HTMLTextAreaElement>(null);

  const teamNameRef = useRef<HTMLInputElement>(null);
  const teamDescRef = useRef<HTMLInputElement>(null);
  const renameRef = useRef<HTMLInputElement>(null);

  const activeTeams = store.teams.filter((t) => t.status !== "ARCHIVED");
  const archivedTeams = store.teams.filter((t) => t.status === "ARCHIVED");
  const filtered = store.employees.filter((e) =>
    teamFilter === "ALL" ? true : teamFilter === "NONE" ? !e.teamId : e.teamId === teamFilter
  );

  // Derived state for center view
  const selectedEmployee = centerView.type === "employee"
    ? store.employees.find((e) => e.id === centerView.employeeId) || null
    : null;

  const selectedTeam = centerView.type === "team"
    ? store.teams.find((t) => t.id === centerView.teamId) || null
    : null;

  const selectedTeamForFeedback = centerView.type === "teamFeedback"
    ? store.teams.find((t) => t.id === centerView.teamId) || null
    : null;

  useEffect(() => {
    if (selectedId && detailRef.current) {
      detailRef.current.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  }, [selectedId]);

  // ===== Navigation Handlers (Master-Detail) =====
  const openEmployee = (id: string) => {
    setSelectedId(id);
    setEvals(null);
    setInstrResult(null);
    setCenterView({ type: "employee", employeeId: id });
  };

  const openTeam = (id: string) => setCenterView({ type: "team", teamId: id });

  const openHire = () => {
    setShowHire(true);
    setCenterView({ type: "hire" });
  };

  const openTeamFeedback = (teamId: string) => {
    loadTeamFeedbacks(teamId);
    setCenterView({ type: "teamFeedback", teamId });
  };

  const closeCenterView = () => {
    setCenterView({ type: "welcome" });
    setSelectedId(null);
    setEvals(null);
  };

  const hireEmployee = async () => {
    const name = hireNameRef.current?.value.trim() || "";
    if (!name) {
      toast.show("직원 이름을 입력하세요.", "err");
      return;
    }
    setHiring(true);
    try {
      await api("POST", "/api/v1/employees", {
        userId: store.ctx.userId,
        name,
        role: hireRoleRef.current?.value,
        customInstructions: hireInstrRef.current?.value.trim() || null,
        teamId: hireTeamRef.current?.value || null,
      });
      if (hireNameRef.current) hireNameRef.current.value = "";
      if (hireInstrRef.current) hireInstrRef.current.value = "";
      toast.show(`'${name}' 직원을 채용했습니다.`);
      await store.refreshAll();
      setShowHire(false);
      setCenterView({ type: "welcome" });
    } catch (e) {
      const err = e as ApiError;
      toast.show(
        err.status === 409 ? `채용 실패: ${err.message} (아카이브 팀 배정 불가)` : err.message,
        "err"
      );
    } finally {
      setHiring(false);
    }
  };

  const createTeam = async () => {
    const name = teamNameRef.current?.value.trim() || "";
    if (!name) {
      toast.show("팀 이름을 입력하세요.", "err");
      return;
    }
    setTeamSaving(true);
    try {
      await api("POST", "/api/v1/teams", {
        organizationId: store.ctx.organizationId,
        name,
        description: teamDescRef.current?.value.trim() || null,
      });
      if (teamNameRef.current) teamNameRef.current.value = "";
      if (teamDescRef.current) teamDescRef.current.value = "";
      toast.show(`팀 '${name}'을(를) 만들었습니다.`);
      await store.refreshAll();
    } catch (e) {
      toast.show(`팀 생성 실패: ${(e as Error).message}`, "err");
    } finally {
      setTeamSaving(false);
    }
  };

  const renameTeam = async (id: string) => {
    const name = renameRef.current?.value.trim() || "";
    if (!name) {
      toast.show("새 팀 이름을 입력하세요.", "err");
      return;
    }
    setTeamSaving(true);
    try {
      await api("PATCH", `/api/v1/teams/${id}`, { name });
      setRenamingTeamId(null);
      toast.show("팀 이름을 변경했습니다.");
      await store.refreshAll();
    } catch (e) {
      toast.show(`이름 변경 실패: ${(e as Error).message}`, "err");
    } finally {
      setTeamSaving(false);
    }
  };

  const archiveTeam = async (id: string) => {
    const t = store.teams.find((x) => x.id === id);
    if (!t) return;
    if (!confirm(`'${t.name}' 팀을 아카이브할까요?\n소속 직원이 있으면 거부됩니다(409).`)) return;
    setTeamSaving(true);
    try {
      await api("PATCH", `/api/v1/teams/${id}`, { status: "ARCHIVED" });
      toast.show("팀을 아카이브했습니다.");
      if (teamFilter === id) setTeamFilter("ALL");
      await store.refreshAll();
    } catch (e) {
      const err = e as ApiError;
      toast.show(
        err.status === 409 ? `아카이브 실패: ${err.message}` : `아카이브 실패: ${err.message}`,
        "err"
      );
    } finally {
      setTeamSaving(false);
    }
  };

  const assignTeam = async () => {
    if (!selectedId) return;
    const teamId = teamSelectRef.current?.value || null;
    setAssigning(true);
    try {
      await api("PATCH", `/api/v1/employees/${selectedId}`, { teamId });
      toast.show(teamId ? `팀을 배정했습니다 (${store.teamName(teamId)}).` : "팀 배정을 해제했습니다.");
      await store.refreshAll();
    } catch (e) {
      const err = e as ApiError;
      toast.show(
        err.status === 409 ? `배정 실패: ${err.message}` : `배정 실패: ${err.message}`,
        "err"
      );
    } finally {
      setAssigning(false);
    }
  };

  const assignTeamLead = async (teamId: string) => {
    const leadId = teamLeadSelectRef.current?.value || null;
    try {
      await api("PATCH", `/api/v1/teams/${teamId}/lead`, { teamLeadEmployeeId: leadId });
      toast.show(leadId ? `팀 리드를 지정했습니다 (${store.employeeName(leadId)}).` : "팀 리드를 해제했습니다.");
      await store.refreshAll();
    } catch (e) {
      toast.show(`팀 리드 지정 실패: ${(e as Error).message}`, "err");
    }
  };

  const loadTeamFeedbacks = async (teamId: string) => {
    try {
      const data = await api<TeamFeedback[]>("GET", `/api/v1/teams/${teamId}/feedback`);
      setTeamFeedbacks(data);
    } catch (e) {
      toast.show(`팀 피드백 조회 실패: ${(e as Error).message}`, "err");
    }
  };

  const saveEmployeeInstructions = async () => {
    if (!selectedId) return;
    const value = instrRef.current?.value || "";
    setSavingInstr(true);
    try {
      await api("PATCH", `/api/v1/employees/${selectedId}`, { customInstructions: value });
      setInstrResult({
        kind: "ok",
        text: value.trim() ? "커스텀 지시를 저장했습니다." : "커스텀 지시를 지웠습니다.",
      });
      toast.show("직원 지시를 저장했습니다.");
      await store.refreshAll();
    } catch (e) {
      const err = e as ApiError;
      setInstrResult({
        kind: "err",
        text:
          err.status === 400
            ? `저장 실패: ${err.message} (최대 4000자, §26)`
            : `저장 실패: ${err.message}`,
      });
      toast.show(err.message, "err");
    } finally {
      setSavingInstr(false);
    }
  };

  const assignGrade = async () => {
    if (!selectedId) return;
    const gradeId = gradeSelectRef.current?.value || null;
    setAssigning(true);
    try {
      await api("PATCH", `/api/v1/employees/${selectedId}/grade`, gradeId ? { gradeId } : {});
      toast.show(gradeId ? "직급을 배정했습니다." : "직급 배정을 해제했습니다.");
      await store.refreshAll();
    } catch (e) {
      toast.show((e as Error).message, "err");
    } finally {
      setAssigning(false);
    }
  };

  const loadEvaluations = async () => {
    if (!selectedId) return;
    try {
      const r = await api<EvaluationReport>("GET", `/api/v1/employees/${selectedId}/evaluations`);
      setEvals(r);
    } catch (e) {
      setEvals(null);
      toast.show(`평가 리포트 실패: ${(e as Error).message}`, "err");
    }
  };

  // ===== Render Center Panel =====
  const renderCenterPanel = () => {
    switch (centerView.type) {
      case "welcome":
        return (
          <div className="card center-welcome" id="centerWelcome">
            <div className="welcome-content">
              <h3>직원 관리</h3>
              <p className="hint">좌측에서 직원을 선택하거나, 팀을 클릭해 상세 정보를 보세요.</p>
              <div className="welcome-actions">
                <button className="btn primary" onClick={openHire}>+ 직원 채용</button>
                <button className="btn ghost" onClick={() => setShowTeams(true)}>팀 관리</button>
              </div>
            </div>
          </div>
        );

      case "employee": {
        if (!selectedEmployee) return null;
        const e = selectedEmployee;
        return (
          <div className="card" id="empDetail" ref={detailRef}>
            <div className="row between">
              <h2 id="empDetailTitle">{e.name} 상세</h2>
              <button id="btnEmpClose" className="btn ghost small" onClick={closeCenterView}>
                닫기
              </button>
            </div>
            <div id="empDetailBody">
              <div className="sub">ID: {e.id}</div>
              <div className="sub">
                역할: {e.role} · 직급: {store.gradeLabel(e.gradeId)} · 팀:{" "}
                <span id="empTeamLabel">{store.teamName(e.teamId)}</span> · 상태: {e.status}
              </div>
              <div className="sub">
                평균 평점: {Number(e.avgRating ?? 0).toFixed(2)} · 완료 업무{" "}
                {fmtNum(e.completedTasks)}/{fmtNum(e.totalTasks)}
              </div>
              {e.systemPrompt ? (
                <div className="sub">시스템 프롬프트: {e.systemPrompt}</div>
              ) : null}
            </div>
            <div className="row">
              <select
                id="empTeamSelect"
                aria-label="팀 선택"
                ref={teamSelectRef}
                key={`team-${e.id}-${e.teamId || "none"}`}
                defaultValue={e.teamId || ""}
              >
                <option value="">(미배정)</option>
                {activeTeams.map((t) => (
                  <option value={t.id} key={t.id}>
                    {t.name}
                  </option>
                ))}
              </select>
              <button id="btnAssignTeam" className="btn primary" disabled={assigning} onClick={assignTeam}>
                팀 배정
              </button>
            </div>
            <div className="row">
              <select
                id="empGradeSelect"
                aria-label="직급 선택"
                ref={gradeSelectRef}
                key={`grade-${e.id}`}
                defaultValue={e.gradeId || ""}
              >
                <option value="">(미배정)</option>
                {store.grades.map((g) => (
                  <option value={g.id} key={g.id}>
                    {g.code} {g.name}
                  </option>
                ))}
              </select>
              <button id="btnAssignGrade" className="btn primary" disabled={assigning} onClick={assignGrade}>
                직급 배정
              </button>
              <button id="btnLoadEvals" className="btn ghost" onClick={loadEvaluations}>
                평가 리포트 (직원 간 피드백)
              </button>
            </div>
            <h3>커스텀 지시 (custom_instructions, §5.4)</h3>
            <p className="hint">
              사용자가 작성하는 직원 개별 지시(자유 서식, 최대 4000자). 우선순위: 시스템 안전 정책 {'>'}
              사용자 개별 지시 {'>'} 직무·조직 기본값 {'>'} 동료 피드백(참고)(§8.2). 비우고 저장하면 지워집니다.
            </p>
            <textarea
              id="empInstructions"
              key={`instr-${e.id}`}
              ref={instrRef}
              rows={4}
              maxLength={4000}
              defaultValue={e.customInstructions || ""}
              placeholder="예: 항상 테스트를 먼저 작성하고, 커밋은 작은 단위로 나눈다."
            />
            <div className="row">
              <button
                id="btnEmpInstructionsSave"
                className="btn primary"
                disabled={savingInstr}
                onClick={saveEmployeeInstructions}
              >
                지시 저장
              </button>
            </div>
            {instrResult ? <StatusText kind={instrResult.kind}>{instrResult.text}</StatusText> : null}
            <div id="evalsBox">{evals ? <EvaluationReportView r={evals} /> : null}</div>
          </div>
        );
      }

      case "team": {
        if (!selectedTeam) return null;
        const t = selectedTeam;
        const teamEmployees = store.employees.filter((e) => e.teamId === t.id);
        return (
          <div className="card" id="teamDetail">
            <div className="row between">
              <h2>{t.name} 상세</h2>
              <button className="btn ghost small" onClick={closeCenterView}>닫기</button>
            </div>
            <div className="sub">ID: {t.id} · 상태: {t.status === "ACTIVE" ? "활성" : "아카이브"}</div>
            {t.description ? <div className="sub">{t.description}</div> : null}
            <div className="sub">소속 {teamEmployees.length}명</div>

            {t.status !== "ARCHIVED" && (
              <div className="team-lead-row">
                <label htmlFor={`teamLead-detail-${t.id}`}>팀 리드</label>
                <select
                  id={`teamLead-detail-${t.id}`}
                  ref={teamLeadSelectRef}
                  key={`lead-detail-${t.id}-${t.teamLeadEmployeeId || "none"}`}
                  defaultValue={t.teamLeadEmployeeId || ""}
                  onChange={() => assignTeamLead(t.id)}
                >
                  <option value="">(미지정 — 최고 직급 임시 대표)</option>
                  {teamEmployees.map((e) => (
                    <option value={e.id} key={e.id}>
                      {e.name} ({store.gradeLabel(e.gradeId)})
                    </option>
                  ))}
                </select>
              </div>
            )}

            <div className="team-feedback-row">
              <button className="btn ghost small" onClick={() => openTeamFeedback(t.id)}>
                팀 피드백 조회
              </button>
            </div>

            {teamEmployees.length > 0 && (
              <div className="panel">
                <h3>팀원</h3>
                <div className="list">
                  {teamEmployees.map((e) => (
                    <div className="item click" key={e.id} onClick={() => openEmployee(e.id)}>
                      <div className="title">
                        {e.name} <Badge kind="indigo">{e.role}</Badge>
                        <Badge kind="green">{store.gradeLabel(e.gradeId)}</Badge>
                      </div>
                      <div className="sub">완료 {fmtNum(e.completedTasks)}/{fmtNum(e.totalTasks)}</div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        );
      }

      case "hire":
        return (
          <div className="card" id="hirePanel">
            <div className="row between">
              <h3>{store.employees.length ? "직원 채용" : "첫 직원 채용"}</h3>
              <button className="btn ghost small" onClick={() => { setShowHire(false); setCenterView({ type: "welcome" }); }}>닫기</button>
            </div>
            <div className="row2">
              <div>
                <label htmlFor="hireName">이름</label>
                <input id="hireName" ref={hireNameRef} autoComplete="off" placeholder="예: 김개발" />
              </div>
              <div>
                <label htmlFor="hireRole">역할</label>
                <select id="hireRole" ref={hireRoleRef} defaultValue="DEVELOPER">
                  {ROLES.map((r) => (
                    <option value={r} key={r}>{r}</option>
                  ))}
                </select>
              </div>
            </div>
            <label htmlFor="hireTeam">팀 (선택 — 아카이브 팀은 배정 불가)</label>
            <select id="hireTeam" ref={hireTeamRef} defaultValue="">
              <option value="">(미배정)</option>
              {activeTeams.map((t) => (
                <option value={t.id} key={t.id}>{t.name}</option>
              ))}
            </select>
            <label htmlFor="hireInstr">커스텀 지시 (선택, 최대 4000자)</label>
            <textarea id="hireInstr" ref={hireInstrRef} rows={3} maxLength={4000} />
            <div className="row">
              <button id="btnHire" className="btn primary" disabled={hiring} onClick={hireEmployee}>
                채용
              </button>
            </div>
          </div>
        );

      case "teamFeedback": {
        if (!selectedTeamForFeedback) return null;
        const t = selectedTeamForFeedback;
        return (
          <div className="card" id="teamFeedbackPanel">
            <div className="row between">
              <h3>{t.name} — 팀 피드백 (읽기 전용)</h3>
              <button className="btn ghost small" onClick={closeCenterView}>닫기</button>
            </div>
            <p className="hint">팀 간 피드백은 팀장이 상대 팀에 제출하며 대상은 팀 전체입니다. 개인 평가로 분해하지 않습니다(§5.20.1).</p>
            {teamFeedbacks.length > 0 ? (
              <div className="list">
                {teamFeedbacks.map((f) => (
                  <div className="item" key={f.id}>
                    <div className="title">
                      <Badge kind="indigo">{f.category}</Badge>
                      {f.score != null && <Badge kind="green">{Number(f.score).toFixed(1)}</Badge>}
                      <span>작성: {store.employeeName(f.authorEmployeeId)} ({store.teamName(f.authorTeamId)})</span>
                    </div>
                    <div className="sub">{f.summary}</div>
                    {f.evidenceRefs?.length ? (
                      <div className="chips">
                        {f.evidenceRefs.map((ref, i) => (
                          <span className="chip" key={i}>{ref}</span>
                        ))}
                      </div>
                    ) : null}
                    <div className="sub">가중치: {Number(f.contextWeight ?? 1).toFixed(2)} · {new Date(f.createdAt).toLocaleString()}</div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="sub">이 팀에 대한 피드백이 없습니다.</div>
            )}
          </div>
        );
      }
    }
  };

  return (
    <>
      <div className="master-detail-layout">
        {/* Master Panel (Left Sidebar) */}
        <div className="master-panel">
          <div className="card">
            <div className="row between">
              <h2>
                직원 <Badge>{store.employees.length}</Badge>
              </h2>
              {store.employees.length ? (
                <button
                  id="btnEmpHireToggle"
                  className="btn ghost small"
                  onClick={openHire}
                >
                  + 직원 채용
                </button>
              ) : (
                <button
                  id="btnEmpHireToggle"
                  className="btn primary small"
                  onClick={openHire}
                >
                  + 첫 직원 채용
                </button>
              )}
            </div>

            <div className="filters" id="teamFilters">
              <span
                className={`chip${teamFilter === "ALL" ? " active" : ""}`}
                data-team-filter="ALL"
                onClick={() => setTeamFilter("ALL")}
              >
                전체 {store.employees.length}
              </span>
              <span
                className={`chip${teamFilter === "NONE" ? " active" : ""}`}
                data-team-filter="NONE"
                onClick={() => setTeamFilter("NONE")}
              >
                미배정 {store.employees.filter((e) => !e.teamId).length}
              </span>
              {store.teams.map((t) => (
                <span
                  className={`chip${teamFilter === t.id ? " active" : ""}${t.status === "ARCHIVED" ? " dim" : ""}`}
                  data-team-filter={t.id}
                  key={t.id}
                  onClick={() => setTeamFilter(t.id)}
                >
                  {t.name}
                  {t.status === "ARCHIVED" ? " (아카이브)" : ""}{" "}
                  {store.employees.filter((e) => e.teamId === t.id).length}
                </span>
              ))}
              <span
                id="btnTeamManage"
                className="chip add"
                onClick={() => setShowTeams((v) => !v)}
              >
                {showTeams ? "팀 관리 닫기" : "+ 팀 관리"}
              </span>
            </div>

            {showTeams ? (
              <div className="panel" id="teamPanel">
                <h3>팀 만들기</h3>
                <div className="row2">
                  <div>
                    <label htmlFor="teamName">이름 (조직 내 고유)</label>
                    <input id="teamName" ref={teamNameRef} autoComplete="off" placeholder="예: 플랫폼팀" />
                  </div>
                  <div>
                    <label htmlFor="teamDesc">설명 (선택)</label>
                    <input id="teamDesc" ref={teamDescRef} autoComplete="off" />
                  </div>
                </div>
                <button id="btnTeamCreate" className="btn primary" disabled={teamSaving} onClick={createTeam}>
                  팀 만들기
                </button>

                <h3>팀 목록</h3>
                <div className="list" id="teamList">
                  {store.teams.length ? (
                    store.teams.map((t) => (
                      <div className={`item${t.status === "ARCHIVED" ? " dim" : ""}`} key={t.id}>
                        <div className="title">
                          {renamingTeamId === t.id ? (
                            <input
                              ref={renameRef}
                              defaultValue={t.name}
                              autoComplete="off"
                              style={{ maxWidth: 240 }}
                            />
                          ) : (
                            <>
                              {t.name}{" "}
                              <Badge kind={t.status === "ACTIVE" ? "green" : ""}>
                                {t.status === "ACTIVE" ? "활성" : "아카이브"}
                              </Badge>
                            </>
                          )}
                        </div>
                        {t.description ? <div className="sub">{t.description}</div> : null}
                        <div className="sub">
                          소속 {store.employees.filter((e) => e.teamId === t.id).length}명
                        </div>
                        {t.status !== "ARCHIVED" && (
                          <div className="team-lead-row">
                            <label htmlFor={`teamLead-${t.id}`}>팀 리드</label>
                            <select
                              id={`teamLead-${t.id}`}
                              ref={teamLeadSelectRef}
                              key={`lead-${t.id}-${t.teamLeadEmployeeId || "none"}`}
                              defaultValue={t.teamLeadEmployeeId || ""}
                              onChange={() => assignTeamLead(t.id)}
                            >
                              <option value="">(미지정 — 최고 직급 임시 대표)</option>
                              {store.employees
                                .filter((e) => e.teamId === t.id)
                                .map((e) => (
                                  <option value={e.id} key={e.id}>
                                    {e.name} ({store.gradeLabel(e.gradeId)})
                                  </option>
                                ))}
                            </select>
                          </div>
                        )}
                        <div className="team-feedback-row">
                          <button className="btn ghost small" onClick={() => openTeamFeedback(t.id)}>
                            팀 피드백 조회
                          </button>
                        </div>
                        <div className="actions">
                          {renamingTeamId === t.id ? (
                            <>
                              <button
                                className="btn primary small"
                                disabled={teamSaving}
                                onClick={() => renameTeam(t.id)}
                              >
                                저장
                              </button>
                              <button className="btn ghost small" onClick={() => setRenamingTeamId(null)}>
                                취소
                              </button>
                            </>
                          ) : (
                            <>
                              {t.status !== "ARCHIVED" ? (
                                <>
                                  <button
                                    className="btn ghost small"
                                    data-act="rename"
                                    data-id={t.id}
                                    onClick={() => setRenamingTeamId(t.id)}
                                  >
                                    이름 변경
                                  </button>
                                  <button
                                    className="btn danger small"
                                    data-act="archive"
                                    data-id={t.id}
                                    disabled={teamSaving}
                                    onClick={() => archiveTeam(t.id)}
                                  >
                                    아카이브
                                  </button>
                                </>
                              ) : null}
                            </>
                          )}
                        </div>
                      </div>
                    ))
                  ) : (
                    <div className="item">
                      <div className="sub">팀이 없습니다. 위에서 만들거나 직원 탭에서 자유롭게 구성하세요.</div>
                    </div>
                  )}
                </div>
              </div>
            ) : null}

          </div>

          <div className="card">
            <div className="list" id="empList">
              {!store.employees.length ? (
                <EmptyState
                  title="아직 직원이 없습니다"
                  hint="첫 직원을 채용하면 프로젝트 업무를 맡길 수 있습니다. 부트스트랩이 실패한 경우 자동 구성이 재시도됩니다."
                  action="첫 직원 채용"
                  onAction={openHire}
                />
              ) : !filtered.length ? (
                <div className="item">
                  <div className="sub">이 팀에 배정된 직원이 없습니다.</div>
                </div>
              ) : (
                filtered.map((e) => (
                  <div className="item click" data-emp={e.id} key={e.id} onClick={() => openEmployee(e.id)}>
                    <div className="title">
                      {e.name} <Badge kind="indigo">{e.role}</Badge>
                      {e.teamId ? <Badge kind="green">{store.teamName(e.teamId)}</Badge> : <Badge kind="amber">미배정</Badge>}
                    </div>
                    <div className="sub">
                      직급: {store.gradeLabel(e.gradeId)} · 상태: {e.status} · 완료{" "}
                      {fmtNum(e.completedTasks)}/{fmtNum(e.totalTasks)}
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>

        {/* Detail Panel (Right) */}
        <div className="detail-panel">
          {renderCenterPanel()}
        </div>
      </div>
    </>
  );
}