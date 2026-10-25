import { useCallback, useEffect, useRef, useState } from "react";
import { api, ApiError } from "../api";
import { useStore } from "../store";
import { useToast } from "../toast";
import {
  collectAnswers,
  deriveProjectName,
  firstUnansweredPrompt,
  fmtDate,
  fmtNum,
  QUESTION_TYPE_NAME,
  toAttachment,
} from "../lib";
import {
  Badge,
  EmptyState,
  FileChips,
  RequirementSetView,
  SessionStatusBadge,
  StatusText,
} from "../components";
import type { ProjectLite, Question, RequirementSet, Session, TaskRow } from "../types";

type ResultMsg = { kind: "ok" | "err"; text: string };

/** 중앙 포커스 뷰 타입 */
type CenterView =
  | { type: "welcome" }
  | { type: "project"; projectId: string }
  | { type: "session"; sessionId: string }
  | { type: "task"; taskId: string }
  | { type: "orchestration"; projectId: string };

export default function InstructionsTab() {
  const store = useStore();
  const toast = useToast();

  // 지시 창구
  const promptRef = useRef<HTMLTextAreaElement>(null);
  const promptFilesRef = useRef<HTMLInputElement>(null);
  const [promptFiles, setPromptFiles] = useState<File[]>([]);
  const [askResult, setAskResult] = useState<ResultMsg | null>(null);
  const [busy, setBusy] = useState(false);

  // 프로젝트/세션/태스크 상태
  const [sessions, setSessions] = useState<Session[]>([]);
  const [currentSessionId, setCurrentSessionId] = useState<string | null>(null);
  const [questions, setQuestions] = useState<Question[]>([]);
  const [questionsNote, setQuestionsNote] = useState<{ kind: "note" | "err"; text: string } | null>(null);
  const [reqSet, setReqSet] = useState<RequirementSet | null>(null);

  const [tasks, setTasks] = useState<TaskRow[]>([]);
  const [orchestration, setOrchestration] = useState<{
    cycle: { id: string; requirementSetId: string; requirementSetVersion: number; mode: string; createdTaskCount: number; blockedTaskCount: number; createdAt: string } | null;
    tasks: Array<{ id: string; parentTaskId: string | null; type: string; title: string; status: string; assigneeId: string | null; assigneeName: string | null; blockedReason: string | null }>;
    latestFrozen: { requirementSetId: string; version: number; decomposed: boolean } | null;
  } | null>(null);
  const [orchBusy, setOrchBusy] = useState(false);

  // 진화 세션
  const evoNoteRef = useRef<HTMLTextAreaElement>(null);
  const evoFilesRef = useRef<HTMLInputElement>(null);
  const [evoFiles, setEvoFiles] = useState<File[]>([]);

  // 중앙 뷰 상태 (GPT 스타일: 좌측 사이드바 고정, 중앙 포커스)
  const [centerView, setCenterView] = useState<CenterView>({ type: "welcome" });

  // 프로젝트 선택 시 상세 로드
  const loadProjectDetail = useCallback(async (projectId: string) => {
    try {
      const [ss, ts, orch] = await Promise.all([
        api<Session[]>("GET", `/api/v1/projects/${projectId}/clarification-sessions`),
        api<TaskRow[]>("GET", `/api/v1/projects/${projectId}/tasks`),
        api("GET", `/api/v1/projects/${projectId}/orchestration`).catch(() => null),
      ]);
      setSessions(ss);
      setTasks(ts);
      if (orch) setOrchestration(orch);
    } catch (e) {
      toast.show((e as Error).message, "err");
    }
  }, [toast]);

  const openProject = useCallback(async (id: string) => {
    setCenterView({ type: "project", projectId: id });
    setCurrentSessionId(null);
    setQuestions([]);
    setQuestionsNote(null);
    setReqSet(null);
    await loadProjectDetail(id);
  }, [loadProjectDetail]);

  const openSession = useCallback(async (id: string) => {
    try {
      const s = await api<Session>("GET", `/api/v1/clarification-sessions/${id}`);
      setCurrentSessionId(id);
      setCenterView({ type: "session", sessionId: id });
    } catch (e) {
      toast.show((e as Error).message, "err");
    }
  }, [toast]);

  const openTask = useCallback((id: string) => {
    setCenterView({ type: "task", taskId: id });
  }, []);

  const openOrchestration = useCallback((projectId: string) => {
    setCenterView({ type: "orchestration", projectId });
  }, []);

  const closeCenterView = () => {
    setCenterView({ type: "welcome" });
    setCurrentSessionId(null);
    setQuestions([]);
    setQuestionsNote(null);
    setReqSet(null);
  };

  // 세션 변경 감지
  useEffect(() => {
    if (!currentSessionId) return;
    const s = sessions.find((x) => x.id === currentSessionId);
    if (!s) return;
    const userOpened = !!s.openedByUserId;
    setReqSet(null);
    setQuestionsNote(null);
    if (["OPEN", "WAITING_CLIENT", "REVIEW"].includes(s.status)) {
      setQuestions([]);
      api<Question[]>("GET", `/api/v1/clarification-sessions/${s.id}/questions`)
        .then((qs) => setQuestions(qs))
        .catch((e) => {
          const err = e as ApiError;
          if (err.status === 409) {
            setQuestionsNote({
              kind: userOpened ? "note" : "err",
              text: userOpened
                ? "내가 개설한 세션입니다. 질문 없이 동결하면 체인은 기록 승인만 수행합니다(§28.6)."
                : "회사 검토 체인이 진행 중입니다. 잠시 후 '질문 구성' 또는 새로 열어보세요.",
            });
          } else {
            setQuestionsNote({ kind: "err", text: `질문 조회 실패: ${err.message}` });
          }
        });
    } else {
      setQuestions([]);
      if (s.status === "FROZEN") {
        api<RequirementSet[]>("GET", `/api/v1/projects/${s.projectId}/requirements`)
          .then((reqs) => {
            if (reqs.length) setReqSet(reqs[reqs.length - 1]);
          })
          .catch(() => {});
      }
    }
  }, [currentSessionId, sessions]);

  // ==================== 지시 창구 ====================

  const ask = async () => {
    const question = promptRef.current?.value.trim() || "";
    if (!question) {
      toast.show("질문을 입력하세요.", "err");
      return;
    }
    setBusy(true);
    try {
      const r = await api<{ question: string; answer: string }>("POST", "/api/v1/ask", { question });
      setAskResult({ kind: "ok", text: `${r.question}\n${r.answer}` });
    } catch (e) {
      setAskResult({ kind: "err", text: `질문 실패: ${(e as Error).message}` });
    } finally {
      setBusy(false);
    }
  };

  const submitInstruction = async () => {
    const goalText = promptRef.current?.value.trim() || "";
    const files = promptFiles;
    if (!goalText && !files.length) {
      toast.show("텍스트와 첨부 중 최소 한 가지가 필요합니다(§28.7-1).", "err");
      return;
    }
    setBusy(true);
    try {
      const attachments = [];
      for (const f of files) attachments.push(await toAttachment(f));
      const p = await api<ProjectLite>("POST", "/api/v1/projects", {
        userId: store.ctx.userId,
        goalText: goalText || null,
        attachments,
      });
      if (promptRef.current) promptRef.current.value = "";
      if (promptFilesRef.current) promptFilesRef.current.value = "";
      setPromptFiles([]);
      setAskResult(null);
      toast.show(`프로젝트 '${p.name}' 생성 — 첫 지시 접수`);
      await openProject(p.id);
    } catch (e) {
      toast.show((e as Error).message, "err");
    } finally {
      setBusy(false);
    }
  };

  const composeQuestions = async () => {
    if (!currentSessionId) return;
    setBusy(true);
    try {
      await api("POST", `/api/v1/clarification-sessions/${currentSessionId}/compose`, { userId: store.ctx.userId });
      toast.show("질문 구성 완료");
      const s = await api<Session>("GET", `/api/v1/clarification-sessions/${currentSessionId}`);
      const idx = sessions.findIndex((x) => x.id === currentSessionId);
      if (idx >= 0) {
        const ns = [...sessions];
        ns[idx] = s;
        setSessions(ns);
      }
      setCurrentSessionId(currentSessionId);
    } catch (e) {
      toast.show((e as Error).message, "err");
    } finally {
      setBusy(false);
    }
  };

  const submitAnswers = async () => {
    if (!currentSessionId) return;
    const answers: Record<string, unknown> = {};
    for (const q of questions) {
      if (q.questionType === "SINGLE_CHOICE") answers[q.id] = q.selectedValues?.[0] ?? null;
      else if (q.questionType === "MULTI_CHOICE") answers[q.id] = q.selectedValues ?? [];
      else if (q.questionType === "NUMBER_RANGE") answers[q.id] = q.numericAnswer ?? null;
      else answers[q.id] = q.otherText ?? "";
    }
    const skipped = questions.filter((q) => q.answerStatus === "SKIPPED").map((q) => q.id);
    setBusy(true);
    try {
      await api("POST", `/api/v1/clarification-sessions/${currentSessionId}/answers`, {
        userId: store.ctx.userId,
        answers,
        skipped,
      });
      toast.show("답변 제출 완료");
      const s = await api<Session>("GET", `/api/v1/clarification-sessions/${currentSessionId}`);
      const idx = sessions.findIndex((x) => x.id === currentSessionId);
      if (idx >= 0) {
        const ns = [...sessions];
        ns[idx] = s;
        setSessions(ns);
      }
      setCurrentSessionId(currentSessionId);
    } catch (e) {
      toast.show((e as Error).message, "err");
    } finally {
      setBusy(false);
    }
  };

  const freezeSession = async () => {
    if (!currentSessionId) return;
    setBusy(true);
    try {
      await api("POST", `/api/v1/clarification-sessions/${currentSessionId}/freeze`, { userId: store.ctx.userId });
      toast.show("요구사항 동결 완료");
      const s = await api<Session>("GET", `/api/v1/clarification-sessions/${currentSessionId}`);
      const idx = sessions.findIndex((x) => x.id === currentSessionId);
      if (idx >= 0) {
        const ns = [...sessions];
        ns[idx] = s;
        setSessions(ns);
      }
      setCurrentSessionId(currentSessionId);
    } catch (e) {
      toast.show((e as Error).message, "err");
    } finally {
      setBusy(false);
    }
  };

  const cancelSessionAction = async () => {
    if (!currentSessionId) return;
    if (!confirm("세션을 취소하려면 '취소'를 입력하세요.")) return;
    setBusy(true);
    try {
      await api("POST", `/api/v1/clarification-sessions/${currentSessionId}/cancel`, { userId: store.ctx.userId });
      toast.show("세션 취소 완료");
      const idx = sessions.findIndex((x) => x.id === currentSessionId);
      if (idx >= 0) {
        const ns = sessions.filter((_, i) => i !== idx);
        setSessions(ns);
      }
      setCurrentSessionId(null);
      await loadProjectDetail(sessions[0]?.projectId || "");
    } catch (e) {
      toast.show(`취소 실패: ${(e as Error).message}`, "err");
    } finally {
      setBusy(false);
    }
  };

  const openEvolutionSession = async () => {
    if (centerView.type !== "project") return;
    const selected = store.projects.find((p) => p.id === centerView.projectId);
    if (!selected) return;
    const changeNote = evoNoteRef.current?.value.trim() || "";
    const files = evoFiles;
    if (!changeNote && !files.length) {
      toast.show("변경 요청 원문 또는 첨부 중 최소 한 가지가 필요합니다(§28.7-1).", "err");
      return;
    }
    setBusy(true);
    try {
      const attachments = [];
      for (const f of files) attachments.push(await toAttachment(f));
      const s = await api<Session>("POST", `/api/v1/projects/${selected.id}/clarification-sessions`, {
        userId: store.ctx.userId,
        changeNote: changeNote || null,
        attachments,
        clientId: null,
      });
      if (evoNoteRef.current) evoNoteRef.current.value = "";
      if (evoFilesRef.current) evoFilesRef.current.value = "";
      setEvoFiles([]);
      toast.show(`새 세션 #${s.sessionNumber} 개설 — 이전 세션에 연결(진화)`);
      await loadProjectDetail(selected.id);
      await openSession(s.id);
    } catch (e) {
      toast.show(`세션 개설 실패: ${(e as Error).message}`, "err");
    } finally {
      setBusy(false);
    }
  };

  // ==================== 오케스트레이션 ====================

  const loadOrchestration = useCallback(async (projectId: string) => {
    try {
      const data = await api("GET", `/api/v1/projects/${projectId}/orchestration`);
      setOrchestration(data);
    } catch (e) {
      console.debug("orchestration load failed", e);
    }
  }, []);

  const triggerOrchestration = useCallback(async () => {
    const projectId = centerView.type === "project" || centerView.type === "orchestration"
      ? (centerView.type === "project" ? centerView.projectId : centerView.projectId)
      : "";
    if (!projectId || !orchestration?.latestFrozen?.requirementSetId) return;
    setOrchBusy(true);
    try {
      await api("POST", `/api/v1/requirements/${orchestration.latestFrozen.requirementSetId}/trigger`, {
        userId: store.ctx.userId,
      });
      toast.show("분해·배정 실행 완료");
      await loadProjectDetail(projectId);
    } catch (e) {
      toast.show((e as Error).message, "err");
    } finally {
      setOrchBusy(false);
    }
  }, [centerView, orchestration, loadProjectDetail]);

  // ==================== 렌더링 헬퍼 ====================

  const userOpened = !!sessions.find((s) => s.id === currentSessionId)?.openedByUserId;
  const editable = currentSessionId ? sessions.find((s) => s.id === currentSessionId)?.status === "WAITING_CLIENT" : false;
  const canFreeze = currentSessionId && sessions.find((s) => s.id === currentSessionId)?.status
    ? ["REVIEW", "WAITING_CLIENT", "OPEN"].includes(sessions.find((s) => s.id === currentSessionId)!.status)
    : false;

  const waitingSessions = store.pendingSessions.filter((s) => s.status === "WAITING_CLIENT");

  // 현재 세션 객체
  const currentSession = currentSessionId ? sessions.find((s) => s.id === currentSessionId) || null : null;
  const currentProject = centerView.type === "project" || centerView.type === "orchestration"
    ? store.projects.find((p) => p.id === (centerView.type === "project" ? centerView.projectId : centerView.projectId)) || null
    : null;

  // 선택된 태스크
  const selectedTask = centerView.type === "task" ? tasks.find((t) => t.id === centerView.taskId) || null : null;

  return (
    <div className="instructions-split">
      {/* ==================== 로컬 좌측 사이드바 (고정) ==================== */}
      <aside className="local-sidebar">
        {/* 대기 세션 배지 */}
        {waitingSessions.length ? (
          <div className="local-sidebar-section pending">
            <div className="local-sidebar-title">
              응답 대기 <Badge kind="hot">{waitingSessions.length}</Badge>
            </div>
            <div className="local-sidebar-list">
              {waitingSessions.map((s) => (
                <div className="local-sidebar-item hot" key={s.id} onClick={() => { openProject(s.projectId); openSession(s.id); }}>
                  <div className="local-sidebar-item-title">
                    {store.projects.find((p) => p.id === s.projectId)?.name || "프로젝트"} — 세션 #{s.sessionNumber}
                    <SessionStatusBadge status={s.status} />
                  </div>
                  <div className="local-sidebar-item-sub">{(s.goalText || "(첨부 중심 접수)").slice(0, 60)}</div>
                </div>
              ))}
            </div>
          </div>
        ) : null}

        {/* 프로젝트 목록 */}
        <div className="local-sidebar-section">
          <div className="local-sidebar-row between">
            <div className="local-sidebar-title">프로젝트</div>
            <button className="btn ghost small" onClick={() => setCenterView({ type: "welcome" })}>+ 새 지시</button>
          </div>
          <div className="local-sidebar-list">
            {store.projects.length ? (
              store.projects.map((p) => (
                <div
                  className={`local-sidebar-item${p.id === (centerView.type === "project" ? centerView.projectId : "") ? " active" : ""}`}
                  key={p.id}
                  onClick={() => openProject(p.id)}
                >
                  <div className="local-sidebar-item-title">{p.name} {p.status ? <Badge>{p.status}</Badge> : null}</div>
                  <div className="local-sidebar-item-sub">{p.description || "설명 없음"} · {fmtDate(p.createdAt)}</div>
                </div>
              ))
            ) : (
              <div className="local-sidebar-item empty">아직 프로젝트가 없습니다</div>
            )}
          </div>
        </div>

        {/* 선택된 프로젝트의 세션 목록 */}
        {currentProject ? (
          <div className="local-sidebar-section">
            <div className="local-sidebar-title">세션 ({sessions.length})</div>
            <div className="local-sidebar-list">
              {sessions.length ? (
                sessions.map((s) => (
                  <div
                    className={`local-sidebar-item${s.id === currentSessionId ? " active" : ""}${s.status === "WAITING_CLIENT" ? " hot" : ""}`}
                    key={s.id}
                    onClick={() => openSession(s.id)}
                  >
                    <div className="local-sidebar-item-title">세션 #{s.sessionNumber} <SessionStatusBadge status={s.status} /></div>
                    <div className="local-sidebar-item-sub">{(s.goalText || "(첨부 중심 접수)").slice(0, 60)} · {fmtDate(s.createdAt)}</div>
                  </div>
                ))
              ) : (
                <div className="local-sidebar-item empty">세션이 없습니다</div>
              )}
            </div>
            <div className="local-sidebar-row">
              <button className="btn ghost small" onClick={openEvolutionSession}>+ 새 세션 (진화)</button>
            </div>
          </div>
        ) : null}

        {/* 오케스트레이션 바로가기 */}
        {currentProject && orchestration ? (
          <div className="local-sidebar-section">
            <div className="local-sidebar-row between">
              <div className="local-sidebar-title">오케스트레이션</div>
              <button className="btn ghost small" onClick={() => openOrchestration(currentProject.id)}>상세</button>
            </div>
            <div className="local-sidebar-list">
              {orchestration.cycle ? (
                <div className="local-sidebar-item">
                  <div className="local-sidebar-item-title">
                    <Badge kind="indigo">{orchestration.cycle.mode}</Badge> 생성 {orchestration.cycle.createdTaskCount}건
                    {orchestration.cycle.blockedTaskCount > 0 && <Badge kind="hot">배정 실패 {orchestration.cycle.blockedTaskCount}건</Badge>}
                  </div>
                  <div className="local-sidebar-item-sub">v{orchestration.cycle.requirementSetVersion}</div>
                </div>
              ) : (
                <div className="local-sidebar-item empty">분해된 사이클 없음</div>
              )}
              {orchestration.latestFrozen && !orchestration.latestFrozen.decomposed && (
                <div className="local-sidebar-item">
                  <button className="btn primary small" disabled={orchBusy} onClick={triggerOrchestration}>
                    {orchBusy ? "실행 중..." : "분해·배정 실행"}
                  </button>
                  <div className="local-sidebar-item-sub hint">v{orchestration.latestFrozen.version} 분해·배정</div>
                </div>
              )}
            </div>
          </div>
        ) : null}
      </aside>

      <main className="center-view">
        {centerView.type === "welcome" && (
          <div className="center-welcome">
            <h1>AutoCompany</h1>
            <p className="hint">좌측에서 프로젝트를 선택하거나, 상단 지시 창구에서 새 업무를 시작하세요.</p>
            <div className="center-stats">
              <div className="stat"><span>{store.projects.length}</span>프로젝트</div>
              <div className="stat"><span>{store.employees.length}</span>직원</div>
              <div className="stat"><span>{store.grades.length}</span>직급</div>
              <div className="stat"><span>{store.profiles.length}</span>모델</div>
            </div>
          </div>
        )}

        {centerView.type === "project" && currentProject && (
          <ProjectDetailView
            project={currentProject}
            sessions={sessions}
            currentSessionId={currentSessionId}
            onOpenSession={openSession}
            onOpenEvolution={openEvolutionSession}
            onOpenOrchestration={() => openOrchestration(currentProject.id)}
            orchestration={orchestration}
            orchBusy={orchBusy}
            onTriggerOrchestration={triggerOrchestration}
            tasks={tasks}
            onOpenTask={openTask}
            onClose={closeCenterView}
          />
        )}

        {centerView.type === "session" && currentSession && (
          <SessionDetailView
            session={currentSession}
            userOpened={!!currentSession.openedByUserId}
            questions={questions}
            questionsNote={questionsNote}
            editable={editable}
            canFreeze={canFreeze}
            reqSet={reqSet}
            onComposeQuestions={composeQuestions}
            onSubmitAnswers={submitAnswers}
            onFreeze={freezeSession}
            onCancel={cancelSessionAction}
            onClose={closeCenterView}
            busy={busy}
          />
        )}

        {centerView.type === "task" && selectedTask && (
          <TaskDetailView
            task={selectedTask}
            onClose={closeCenterView}
          />
        )}

        {centerView.type === "orchestration" && currentProject && (
          <OrchestrationDetailView
            project={currentProject}
            orchestration={orchestration}
            orchBusy={orchBusy}
            onTrigger={triggerOrchestration}
            tasks={tasks}
            onClose={closeCenterView}
          />
        )}

        {/* 하단 프롬프트 입력 */}
        <div style={{ padding: '16px 24px', borderTop: '1px solid var(--line)', background: 'var(--card)' }}>
          <div style={{ display: 'flex', gap: '10px', alignItems: 'flex-end' }}>
            <textarea
              ref={promptRef}
              rows={2}
              placeholder="하고 싶은 일을 적고 [지시하기]..."
              style={{ flex: 1, minHeight: 60, resize: 'vertical', fontSize: 15 }}
            />
            <div style={{ display: 'flex', gap: '6px', flexShrink: 0 }}>
              <button className="btn ghost small" disabled={busy} onClick={ask}>묻기</button>
              <button className="btn primary small" disabled={busy} onClick={submitInstruction}>지시하기</button>
            </div>
          </div>
          {askResult && <StatusText kind={askResult.kind}>{askResult.text}</StatusText>}
        </div>
      </main>
    </div>
  );
}

/* ==================== 하위 컴포넌트들 ==================== */

interface ProjectDetailViewProps {
  project: ProjectLite;
  sessions: Session[];
  currentSessionId: string | null;
  onOpenSession: (id: string) => void;
  onOpenEvolution: () => void;
  onOpenOrchestration: () => void;
  orchestration: any;
  orchBusy: boolean;
  onTriggerOrchestration: () => void;
  tasks: TaskRow[];
  onOpenTask: (id: string) => void;
  onClose: () => void;
}

function ProjectDetailView({
  project,
  sessions,
  currentSessionId,
  onOpenSession,
  onOpenEvolution,
  onOpenOrchestration,
  orchestration,
  orchBusy,
  onTriggerOrchestration,
  tasks,
  onOpenTask,
  onClose,
}: ProjectDetailViewProps) {
  return (
    <div className="center-card">
      <div className="center-header">
        <h2>{project.name}</h2>
        <button className="btn ghost small" onClick={onClose}>닫기</button>
      </div>
      <div className="center-meta">
        <Badge>{project.status || "PLANNING"}</Badge> 생성 {fmtDate(project.createdAt)}
      </div>

      <section className="center-section">
        <h3>세션 ({sessions.length})</h3>
        {(() => {
          if (sessions.length > 0) {
            return (
              <>
                <div className="center-list">
                  {sessions.map((s) => (
                    <div className={`center-item${s.id === currentSessionId ? " active" : ""}${s.status === "WAITING_CLIENT" ? " hot" : ""}`} key={s.id} onClick={() => onOpenSession(s.id)}>
                      <div className="center-item-title">세션 #{s.sessionNumber} <SessionStatusBadge status={s.status} /></div>
                      <div className="center-item-sub">{(s.goalText || "(첨부 중심 접수)").slice(0, 100)}</div>
                    </div>
                  ))}
                </div>
                <button className="btn ghost small" onClick={onOpenEvolution}>+ 새 세션 (진화)</button>
              </>
            );
          } else {
            return (
              <>
                <div className="center-empty">세션이 없습니다</div>
                <button className="btn ghost small" onClick={onOpenEvolution}>+ 새 세션 (진화)</button>
              </>
            );
          }
        })()}
        <button className="btn ghost small" onClick={onOpenEvolution}>+ 새 세션 (진화)</button>
      </section>

      <section className="center-section">
        <h3>오케스트레이션</h3>
        {orchestration?.cycle ? (
          <div className="center-summary">
            <div><Badge kind="indigo">{orchestration.cycle.mode}</Badge> 생성 {orchestration.cycle.createdTaskCount}건 {orchestration.cycle.blockedTaskCount > 0 && <Badge kind="hot">배정 실패 {orchestration.cycle.blockedTaskCount}건</Badge>}</div>
            <div className="hint">v{orchestration.cycle.requirementSetVersion}</div>
          </div>
        ) : (
          <div className="hint">분해된 사이클 없음</div>
        )}
        {orchestration?.latestFrozen && !orchestration.latestFrozen.decomposed && (
          <button className="btn primary" disabled={orchBusy} onClick={onTriggerOrchestration}>
            {orchBusy ? "실행 중..." : "분해·배정 실행"}
          </button>
        )}
        {orchestration?.latestFrozen && orchestration.latestFrozen.decomposed && !orchestration.cycle && (
          <div className="hint">이미 분해됨</div>
        )}
        <button className="btn ghost small" onClick={onOpenOrchestration}>상세 보기</button>
      </section>

      <section className="center-section">
        <h3>업무 ({tasks.length})</h3>
        {tasks.length ? (
          <div className="center-list">
            {tasks.map((t) => (
              <div className="center-item" key={t.id} onClick={() => onOpenTask(t.id)}>
                <div className="center-item-title">{t.title} <Badge kind="indigo">{t.type || "TASK"}</Badge> <Badge>{t.status || "DRAFT"}</Badge></div>
                <div className="center-item-sub">담당: {t.assignee?.name || "미배정"} · {fmtDate(t.createdAt)} {t.blockedReason && <span className="hint err"> · {t.blockedReason}</span>}</div>
              </div>
            ))}
          </div>
        ) : (
          <div className="center-empty">업무는 [분해·배정 실행]으로 자동 생성됩니다</div>
        )}
      </section>
    </div>
  );
}

interface SessionDetailViewProps {
  session: Session;
  userOpened: boolean;
  questions: Question[];
  questionsNote: { kind: "note" | "err"; text: string } | null;
  editable: boolean;
  canFreeze: boolean;
  reqSet: RequirementSet | null;
  onComposeQuestions: () => void;
  onSubmitAnswers: () => void;
  onFreeze: () => void;
  onCancel: () => void;
  onClose: () => void;
  busy: boolean;
}

function SessionDetailView({
  session,
  userOpened,
  questions,
  questionsNote,
  editable,
  canFreeze,
  reqSet,
  onComposeQuestions,
  onSubmitAnswers,
  onFreeze,
  onCancel,
  onClose,
  busy,
}: SessionDetailViewProps) {
  return (
    <div className="center-card">
      <div className="center-header">
        <h2>세션 #{session.sessionNumber} 상세</h2>
        <button className="btn ghost small" onClick={onClose}>닫기</button>
      </div>
      <div className="center-meta">
        <SessionStatusBadge status={session.status} /> · 생성 {fmtDate(session.createdAt)} · 질문 상한 {session.maxQuestions}
      </div>
      <div className="center-meta">개설 {userOpened ? <Badge>내가 개설 (진화)</Badge> : "파이프라인"}{session.parentSessionId ? " · 이전 세션과 연결됨" : ""}</div>
      {session.goalText ? <div className="center-meta">{userOpened ? "변경 요청" : "지시"}: {session.goalText}</div> : <div className="center-meta hint">첨부 중심 접수 (§28.7-1)</div>}
      {session.attachments?.length && <div className="chips">{session.attachments.map((a, i) => <span className="chip" key={i}>{a.filename} · {a.parseStatus === "PARSED" ? "✔ 파싱" : a.parseStatus === "IMAGE_PENDING_VISION" ? "🖼 비전 대기" : "⚠ 원본 보존"}</span>)}</div>}

      <div className="center-actions">
        <button className="btn primary" hidden={userOpened || !["OPEN", "REVIEW"].includes(session.status)} onClick={onComposeQuestions} disabled={busy}>질문 구성</button>
        <button className="btn primary" hidden={!editable} onClick={onSubmitAnswers} disabled={busy}>답변 제출</button>
        <button className="btn danger" hidden={!canFreeze} onClick={onFreeze} disabled={busy}>{session.status === "WAITING_CLIENT" ? "이 정도면 진행(동결)" : "요구사항 동결"}</button>
        <button className="btn ghost" hidden={!["OPEN", "WAITING_CLIENT"].includes(session.status)} onClick={onCancel} disabled={busy}>세션 취소</button>
      </div>

      {questionsNote ? <div className={`center-status ${questionsNote.kind}`}>{questionsNote.text}</div> : null}
      {["OPEN", "WAITING_CLIENT", "REVIEW"].includes(session.status) ? (
        questions.length ? (
          <div className="center-list">
            {questions.map((q) => (
              <div className="center-item" key={q.id}>
                <div className="center-item-title">{q.prompt}</div>
                <div className="center-item-sub">{QUESTION_TYPE_NAME[q.questionType] || q.questionType} · {q.displayOrder}번 {q.answerStatus === "ANSWERED" ? <Badge kind="green">응답함</Badge> : null} {q.answerStatus === "SKIPPED" ? <Badge kind="amber">건너뜀</Badge> : null}</div>
                <QuestionBody q={q} editable={!!editable} />
                {editable && <label className="check"><input className="q-skip" type="checkbox" /> 건너뛰기 (미확정 — 목록 가정으로 기록)</label>}
              </div>
            ))}
          </div>
        ) : (
          <div className="center-empty">{userOpened ? "내가 개설한 세션 — 질문 없이 바로 동결할 수 있습니다 (§28.6)." : "등록된 질문이 없습니다. '질문 구성'을 누르세요."}</div>
        )
      ) : null}
      {reqSet && <RequirementSetView r={reqSet} />}
    </div>
  );
}

interface TaskDetailViewProps {
  task: TaskRow;
  onClose: () => void;
}

function TaskDetailView({ task, onClose }: TaskDetailViewProps) {
  return (
    <div className="center-card task-detail">
      <div className="center-header">
        <h2>{task.title} <Badge kind="indigo">{task.type || "TASK"}</Badge> <Badge>{task.status || "DRAFT"}</Badge></h2>
        <button className="btn ghost small" onClick={onClose}>닫기</button>
      </div>
      <div className="center-meta">담당: {task.assignee?.name || "미배정"} · 생성 {fmtDate(task.createdAt)}</div>
      {task.blockedReason && <div className="center-meta err">차단 사유: {task.blockedReason}</div>}
      <div className="center-meta">ID: {task.id} · 시도 {fmtNum(task.attemptCount)}/{fmtNum(task.maxAttempts)}</div>
      {task.description && <div className="center-desc">{task.description}</div>}
      {task.resultSummary && <div className="center-result">결과: {task.resultSummary}</div>}
    </div>
  );
}

interface OrchestrationDetailViewProps {
  project: ProjectLite;
  orchestration: any;
  orchBusy: boolean;
  onTrigger: () => void;
  tasks: TaskRow[];
  onClose: () => void;
}

function OrchestrationDetailView({
  project,
  orchestration,
  orchBusy,
  onTrigger,
  tasks,
  onClose,
}: OrchestrationDetailViewProps) {
  return (
    <div className="center-card">
      <div className="center-header">
        <h2>오케스트레이션 — {project.name}</h2>
        <button className="btn ghost small" onClick={onClose}>닫기</button>
      </div>
      {orchestration?.cycle && (
        <div className="center-summary">
          <div className="center-summary-row">
            <span className="label">최근 사이클:</span>
            <span><Badge kind="indigo">{orchestration.cycle.mode}</Badge> 생성 {orchestration.cycle.createdTaskCount}건 {orchestration.cycle.blockedTaskCount > 0 && <Badge kind="hot">배정 실패 {orchestration.cycle.blockedTaskCount}건</Badge>}</span>
          </div>
          <div className="center-summary-row">
            <span className="label">요구사항 세트:</span>
            <span>v{orchestration.cycle.requirementSetVersion} ({orchestration.cycle.id.slice(0, 8)})</span>
          </div>
        </div>
      )}
      {orchestration?.latestFrozen && !orchestration.latestFrozen.decomposed && (
        <div className="center-actions">
          <button className="btn primary" disabled={orchBusy} onClick={onTrigger}>{orchBusy ? "실행 중..." : "분해·배정 실행"}</button>
          <span className="hint">동결된 요구사항 세트 v{orchestration.latestFrozen.version} 분해·배정</span>
        </div>
      )}
      {orchestration?.latestFrozen && orchestration.latestFrozen.decomposed && !orchestration.cycle && <div className="hint">이미 분해됨</div>}

      <h3>업무 트리 ({tasks.length})</h3>
      {tasks.length ? (
        <div className="center-list">
          {tasks.map((t) => (
            <div className="center-item" key={t.id}>
              <div className="center-item-title">{t.title} <Badge kind="indigo">{t.type || "TASK"}</Badge> <Badge>{t.status || "DRAFT"}</Badge></div>
              <div className="center-item-sub">담당: {t.assignee?.name || "미배정"} · {fmtDate(t.createdAt)} {t.blockedReason && <span className="hint err"> · {t.blockedReason}</span>}</div>
            </div>
          ))}
        </div>
      ) : (
        <div className="center-empty">업무가 없습니다. [분해·배정 실행]을 누르세요.</div>
      )}
    </div>
  );
}


function OtherLine({
  kind,
  q,
  editable,
  checked,
  priorOther,
}: {
  kind: "radio" | "checkbox";
  q: Question;
  editable: boolean;
  checked: boolean;
  priorOther?: string | null;
}) {
  const dis = !editable;
  return (
    <>
      <label className="check">
        <input
          type={kind}
          name={`q_${q.id}`}
          className="q-other-check"
          value="__other__"
          disabled={dis}
          defaultChecked={checked}
        />{" "}
        기타 (직접 입력)
      </label>
      <input className="q-other" placeholder="기타 내용" defaultValue={priorOther || ""} disabled={dis} />
    </>
  );
}

function QuestionBody({ q, editable }: { q: Question; editable: boolean }) {
  const dis = !editable;
  const priorSel = new Set(q.selectedValues || []);
  const priorOther = q.otherText;
  const otherChecked = priorOther != null && q.selectedValues == null;

  if (q.questionType === "SINGLE_CHOICE") {
    return (
      <>
        {(q.options || []).map((o) => (
          <label className="check" key={o.value}>
            <input
              type="radio"
              name={`q_${q.id}`}
              value={o.value}
              disabled={dis}
              defaultChecked={priorSel.has(o.value)}
            />{" "}
            {o.label}
          </label>
        ))}
        {q.allowOther ? (
          <OtherLine kind="radio" q={q} editable={editable} checked={otherChecked} priorOther={priorOther} />
        ) : null}
      </>
    );
  }
  if (q.questionType === "MULTI_CHOICE") {
    return (
      <>
        {(q.options || []).map((o) => (
          <label className="check" key={o.value}>
            <input
              type="checkbox"
              name={`q_${q.id}`}
              className="q-opt"
              value={o.value}
              disabled={dis}
              defaultChecked={priorSel.has(o.value)}
            />{" "}
            {o.label}
          </label>
        ))}
        {q.allowOther ? (
          <OtherLine kind="checkbox" q={q} editable={editable} checked={otherChecked} priorOther={priorOther} />
        ) : null}
      </>
    );
  }
  if (q.questionType === "NUMBER_RANGE") {
    return (
      <input
        type="number"
        className="q-number"
        defaultValue={q.numericAnswer == null ? "" : String(q.numericAnswer)}
        disabled={dis}
        placeholder="숫자 입력"
      />
    );
  }
  if (q.questionType === "SHORT_TEXT") {
    return <input className="q-text" defaultValue={priorOther || ""} disabled={dis} placeholder="답변" />;
  }
  return (
    <textarea className="q-text" rows={2} defaultValue={priorOther || ""} disabled={dis} placeholder="답변" />
  );
}
