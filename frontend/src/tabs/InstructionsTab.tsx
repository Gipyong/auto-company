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

export default function InstructionsTab() {
  const store = useStore();
  const toast = useToast();

  const promptRef = useRef<HTMLTextAreaElement>(null);
  const promptFilesRef = useRef<HTMLInputElement>(null);
  const [promptFiles, setPromptFiles] = useState<File[]>([]);
  const [askResult, setAskResult] = useState<ResultMsg | null>(null);
  const [busy, setBusy] = useState(false);

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const selected: ProjectLite | null = store.projects.find((p) => p.id === selectedId) || null;

  const [sessions, setSessions] = useState<Session[]>([]);
  const [current, setCurrent] = useState<Session | null>(null);
  const [questions, setQuestions] = useState<Question[]>([]);
  const [questionsNote, setQuestionsNote] = useState<{ kind: "note" | "err"; text: string } | null>(
    null
  );
  const [reqSet, setReqSet] = useState<RequirementSet | null>(null);

  const [tasks, setTasks] = useState<TaskRow[]>([]);
  const [taskDetail, setTaskDetail] = useState<TaskRow | null>(null);

  const evoNoteRef = useRef<HTMLTextAreaElement>(null);
  const evoFilesRef = useRef<HTMLInputElement>(null);
  const [evoFiles, setEvoFiles] = useState<File[]>([]);

  const taskAssigneeRef = useRef<HTMLSelectElement>(null);
  const taskTypeRef = useRef<HTMLSelectElement>(null);
  const taskTitleRef = useRef<HTMLInputElement>(null);
  const taskPromptRef = useRef<HTMLTextAreaElement>(null);

  const ratingTaskRef = useRef<HTMLSelectElement>(null);
  const ratingRaterRef = useRef<HTMLSelectElement>(null);
  const ratingRatedRef = useRef<HTMLSelectElement>(null);
  const ratingCategoryRef = useRef<HTMLSelectElement>(null);
  const ratingScoreRef = useRef<HTMLInputElement>(null);
  const ratingCommentRef = useRef<HTMLTextAreaElement>(null);
  const ratingEvidenceRef = useRef<HTMLInputElement>(null);

  const detailRef = useRef<HTMLDivElement>(null);

  const focusPrompt = () => {
    promptRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
    window.setTimeout(() => promptRef.current?.focus(), 200);
  };

  const loadProjectDetail = useCallback(
    async (id: string) => {
      try {
        const [ss, ts] = await Promise.all([
          api<Session[]>("GET", `/api/v1/projects/${id}/clarification-sessions`),
          api<TaskRow[]>("GET", `/api/v1/projects/${id}/tasks`),
        ]);
        setSessions(ss);
        setTasks(ts);
      } catch (e) {
        toast.show((e as Error).message, "err");
      }
    },
    [toast]
  );

  const openProject = useCallback(
    async (id: string) => {
      setSelectedId(id);
      setCurrent(null);
      setQuestions([]);
      setQuestionsNote(null);
      setReqSet(null);
      setTaskDetail(null);
      await loadProjectDetail(id);
      window.setTimeout(() => {
        detailRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
      }, 50);
    },
    [loadProjectDetail]
  );

  const openSession = useCallback(
    async (id: string) => {
      try {
        const s = await api<Session>("GET", `/api/v1/clarification-sessions/${id}`);
        setCurrent(s);
      } catch (e) {
        toast.show((e as Error).message, "err");
      }
    },
    [toast]
  );

  useEffect(() => {
    if (!current) return;
    const userOpened = !!current.openedByUserId;
    setReqSet(null);
    setQuestionsNote(null);
    if (["OPEN", "WAITING_CLIENT", "REVIEW"].includes(current.status)) {
      setQuestions([]);
      api<Question[]>("GET", `/api/v1/clarification-sessions/${current.id}/questions`)
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
      if (current.status === "FROZEN") {
        api<RequirementSet[]>("GET", `/api/v1/projects/${current.projectId}/requirements`)
          .then((reqs) => {
            if (reqs.length) setReqSet(reqs[reqs.length - 1]);
          })
          .catch(() => {
            /* 조회 실패는 무시 */
          });
      }
    }
  }, [current]);

  // ==================== 지시 창구 ====================

  const ask = async () => {
    const question = promptRef.current?.value.trim() || "";
    if (!question) {
      toast.show("질문을 입력하세요.", "err");
      return;
    }
    setBusy(true);
    try {
      const r = await api<{ question: string; answer: string }>("POST", "/api/v1/ask", {
        question,
      });
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
      toast.show("지시 내용 또는 첨부 중 최소 한 가지가 필요합니다. (§28.7-1)", "err");
      return;
    }
    setBusy(true);
    try {
      const p = await api<ProjectLite>("POST", "/api/v1/projects", {
        userId: store.ctx.userId,
        name: deriveProjectName(goalText),
      });
      const attachments = [];
      for (const f of files) attachments.push(await toAttachment(f));
      const s = await api<Session>("POST", `/api/v1/projects/${p.id}/goal`, {
        userId: store.ctx.userId,
        goalText: goalText || null,
        attachments,
        clientId: null,
      });
      if (promptRef.current) promptRef.current.value = "";
      if (promptFilesRef.current) promptFilesRef.current.value = "";
      setPromptFiles([]);
      setAskResult(null);
      toast.show(`지시를 접수했습니다 — "프로젝트 ${p.name}" 세션 #${s.sessionNumber}`);
      await store.refreshProjects();
      await store.loadPendingSessions();
      await openProject(p.id);
      await openSession(s.id);
    } catch (e) {
      toast.show(`지시 실패: ${(e as Error).message}`, "err");
    } finally {
      setBusy(false);
    }
  };

  // ==================== 세션 (질문·동결·진화) ====================

  const reloadSessions = useCallback(async () => {
    const projectId = selectedId || current?.projectId;
    if (!projectId) return;
    await loadProjectDetail(projectId);
  }, [selectedId, current, loadProjectDetail]);

  const composeQuestions = async () => {
    if (!current) return;
    try {
      const qs = await api<Question[]>(
        "POST",
        `/api/v1/clarification-sessions/${current.id}/questions`
      );
      toast.show(`질문 ${qs.length}개 구성 — 체인 통과 후 응답 요청이 표시됩니다.`);
      await store.loadPendingSessions();
      await openSession(current.id);
    } catch (e) {
      const err = e as ApiError;
      toast.show(err.status === 409 ? err.message : `질문 구성 실패: ${err.message}`, "err");
    }
  };

  const submitAnswers = async () => {
    const s = current;
    if (!s || !questions.length) return;
    const missing = firstUnansweredPrompt(questions);
    if (missing) {
      toast.show("모든 항목에 답하거나 건너뛰어 주세요: " + missing.prompt, "err");
      return;
    }
    try {
      const updated = await api<Session>(
        "POST",
        `/api/v1/clarification-sessions/${s.id}/answers`,
        {
          userId: store.ctx.userId,
          answers: collectAnswers(questions),
        }
      );
      toast.show("답변을 제출했습니다. 동결하면 요구사항이 확정됩니다.");
      await store.loadPendingSessions();
      await openSession(updated.id);
      await reloadSessions();
    } catch (e) {
      toast.show(`답변 실패: ${(e as Error).message}`, "err");
    }
  };

  const freezeSession = async () => {
    const s = current;
    if (!s) return;
    const directive = s.status === "WAITING_CLIENT";
    const msg = directive
      ? "이 정도면 진행하도록 동결할까요?\n미답 질문은 가정(assumption)으로 기록된 뒤 동결됩니다. (§28.6)"
      : s.openedByUserId && s.status === "OPEN"
        ? "질문 없이 즉시 동결할까요?\n검토 체인은 기록 승인만 수행합니다. (§28.6)"
        : "요구사항을 동결할까요?\n동결 이후 수정은 새 버전으로만 가능합니다.";
    if (!confirm(msg)) return;
    try {
      const r = await api<RequirementSet>(
        "POST",
        `/api/v1/clarification-sessions/${s.id}/freeze`,
        {
          userId: store.ctx.userId,
          clientDirective: directive ? true : null,
        }
      );
      toast.show(`요구사항을 동결했습니다 (v${r.version}).`);
      setReqSet(r);
      await store.loadPendingSessions();
      await openSession(s.id);
      await reloadSessions();
    } catch (e) {
      toast.show(`동결 실패: ${(e as Error).message}`, "err");
    }
  };

  const cancelSessionAction = async () => {
    const s = current;
    if (!s) return;
    if (!confirm(`세션 #${s.sessionNumber}을(를) 취소할까요?\n취소 후에는 동결할 수 없습니다. (§28.6)`))
      return;
    try {
      const r = await api<Session>("POST", `/api/v1/clarification-sessions/${s.id}/cancel`, {
        userId: store.ctx.userId,
      });
      toast.show(`세션 #${r.sessionNumber}을(를) 취소했습니다.`);
      await store.loadPendingSessions();
      await openSession(s.id);
      await reloadSessions();
    } catch (e) {
      toast.show(`취소 실패: ${(e as Error).message}`, "err");
    }
  };

  const openEvolutionSession = async () => {
    if (!selected) return;
    const changeNote = evoNoteRef.current?.value.trim() || "";
    const files = evoFiles;
    if (!changeNote && !files.length) {
      toast.show("변경 요청 원문 또는 첨부 중 최소 한 가지가 필요합니다. (§28.7-1)", "err");
      return;
    }
    setBusy(true);
    try {
      const attachments = [];
      for (const f of files) attachments.push(await toAttachment(f));
      const s = await api<Session>(
        "POST",
        `/api/v1/projects/${selected.id}/clarification-sessions`,
        {
          userId: store.ctx.userId,
          changeNote: changeNote || null,
          attachments,
          clientId: null,
        }
      );
      if (evoNoteRef.current) evoNoteRef.current.value = "";
      if (evoFilesRef.current) evoFilesRef.current.value = "";
      setEvoFiles([]);
      toast.show(`새 세션 #${s.sessionNumber} 개설 — 이전 세션에 연결(진화)`);
      await store.loadPendingSessions();
      await reloadSessions();
      await openSession(s.id);
    } catch (e) {
      toast.show(`세션 개설 실패: ${(e as Error).message}`, "err");
    } finally {
      setBusy(false);
    }
  };

  // ==================== 업무 · 평가 ====================

  const createTask = async () => {
    if (!selected) return;
    const assigneeId = taskAssigneeRef.current?.value || "";
    const title = taskTitleRef.current?.value.trim() || "";
    if (!assigneeId || !title) {
      toast.show("담당 직원과 제목은 필수입니다.", "err");
      return;
    }
    setBusy(true);
    try {
      await api("POST", "/api/v1/tasks", {
        projectId: selected.id,
        assigneeId,
        taskType: taskTypeRef.current?.value,
        title,
        prompt: taskPromptRef.current?.value.trim() || null,
      });
      if (taskTitleRef.current) taskTitleRef.current.value = "";
      if (taskPromptRef.current) taskPromptRef.current.value = "";
      toast.show("업무를 추가했습니다.");
      await reloadSessions();
    } catch (e) {
      toast.show((e as Error).message, "err");
    } finally {
      setBusy(false);
    }
  };

  const showTaskDetail = async (id: string) => {
    try {
      const t = await api<TaskRow>("GET", `/api/v1/tasks/${id}`);
      setTaskDetail(t);
    } catch (e) {
      toast.show((e as Error).message, "err");
    }
  };

  const submitRating = async () => {
    const taskId = ratingTaskRef.current?.value || "";
    const raterId = ratingRaterRef.current?.value || "";
    const ratedId = ratingRatedRef.current?.value || "";
    if (!taskId || !raterId || !ratedId) {
      toast.show("업무, 평가자, 대상은 필수입니다.", "err");
      return;
    }
    setBusy(true);
    try {
      await api("POST", "/api/v1/ratings", {
        taskId,
        raterId,
        ratedId,
        category: ratingCategoryRef.current?.value,
        score: Number(ratingScoreRef.current?.value || 0),
        comment: ratingCommentRef.current?.value.trim() || null,
        evidenceRefs: (ratingEvidenceRef.current?.value || "")
          .split(",")
          .map((s) => s.trim())
          .filter(Boolean),
      });
      if (ratingCommentRef.current) ratingCommentRef.current.value = "";
      if (ratingEvidenceRef.current) ratingEvidenceRef.current.value = "";
      toast.show("평가를 제출했습니다. 직원 상세에서 리포트를 확인하세요.");
    } catch (e) {
      toast.show((e as Error).message, "err");
    } finally {
      setBusy(false);
    }
  };

  const userOpened = !!current?.openedByUserId;
  const editable = current?.status === "WAITING_CLIENT";
  const canFreeze =
    current !== null &&
    (current.status === "REVIEW" ||
      current.status === "WAITING_CLIENT" ||
      (userOpened && current.status === "OPEN"));

  const waitingSessions = store.pendingSessions.filter((s) => s.status === "WAITING_CLIENT");

  return (
    <>
      {waitingSessions.length ? (
        <div className="card" id="pendingBox">
          <h2>
            응답 대기 <Badge kind="hot">{waitingSessions.length}</Badge>
          </h2>
          <p className="hint">답변을 기다리는 지시가 있습니다. 눌러서 확인하세요.</p>
          <div className="list">
            {waitingSessions.map((s) => (
              <div
                className="item click hot"
                data-session={s.id}
                key={s.id}
                onClick={async () => {
                  await openProject(s.projectId);
                  await openSession(s.id);
                }}
              >
                <div className="title">
                  {store.projects.find((p) => p.id === s.projectId)?.name || "프로젝트"} — 세션 #
                  {s.sessionNumber} <SessionStatusBadge status={s.status} />
                </div>
                <div className="sub">{(s.goalText || "(첨부 중심 접수)").slice(0, 90)}</div>
              </div>
            ))}
          </div>
        </div>
      ) : null}

      <div className="card promptbox" id="promptBox">
        <h2>지시 창구</h2>
        <p className="hint">
          하고 싶은 일을 적고 [묻기]로 물어보거나 [지시하기]로 프로젝트를 시작하세요. 텍스트와 첨부 중 최소
          한 가지가 필요합니다(§28.7-1).
        </p>
        <textarea
          id="promptText"
          ref={promptRef}
          rows={4}
          placeholder="예: 회원가입·로그인이 있는 블로그 사이트를 만들어줘."
        />
        <label htmlFor="promptFiles">
          첨부 (선택) — text 기반 md·txt·csv·json은 내용 파싱, 그 외 형식은 원본 보존 · 최대 10개·파일당
          50MB
        </label>
        <input
          id="promptFiles"
          ref={promptFilesRef}
          type="file"
          multiple
          onChange={(e) => setPromptFiles(Array.from(e.target.files || []))}
        />
        <FileChips files={promptFiles} />
        <div className="row">
          <button id="btnAsk" className="btn ghost" disabled={busy} onClick={ask}>
            묻기
          </button>
          <button id="btnInstruct" className="btn primary" disabled={busy} onClick={submitInstruction}>
            지시하기
          </button>
        </div>
        {askResult ? <StatusText kind={askResult.kind}>{askResult.text}</StatusText> : null}
      </div>

      <div className="card">
        <div className="row between">
          <h2>프로젝트</h2>
          {store.projects.length ? (
            <button className="btn ghost small" onClick={focusPrompt}>
              + 새 지시
            </button>
          ) : null}
        </div>
        <div className="list" id="projectList">
          {store.projects.length ? (
            store.projects.map((p) => (
              <div
                className={`item click${p.id === selectedId ? " hot" : ""}`}
                data-project={p.id}
                key={p.id}
                onClick={() => openProject(p.id)}
              >
                <div className="title">
                  {p.name} {p.status ? <Badge>{p.status}</Badge> : null}
                </div>
                <div className="sub">
                  {p.description || "설명 없음"} · 생성 {fmtDate(p.createdAt)}
                </div>
              </div>
            ))
          ) : (
            <EmptyState
              title="아직 프로젝트가 없습니다"
              hint="위 지시 창구에 첫 요구사항을 적고 [지시하기]를 누르면 프로젝트가 시작됩니다."
              action="첫 지시 보내기"
              onAction={focusPrompt}
            />
          )}
        </div>
      </div>

      {selected ? (
        <div className="card" id="projectDetail" ref={detailRef}>
          <div className="row between">
            <h2 id="projectDetailTitle">{selected.name}</h2>
            <button
              id="btnProjectClose"
              className="btn ghost small"
              onClick={() => {
                setSelectedId(null);
                setCurrent(null);
                setQuestions([]);
                setReqSet(null);
                setTaskDetail(null);
              }}
            >
              닫기
            </button>
          </div>
          <div className="sub">
            {selected.status ? <Badge>{selected.status}</Badge> : null} 생성 {fmtDate(selected.createdAt)}
          </div>

          <h3>지시 세션</h3>
          <div className="list" id="sessionList">
            {sessions.length ? (
              sessions.map((s) => (
                <div
                  className={`item click${s.status === "WAITING_CLIENT" ? " hot" : ""}`}
                  data-session={s.id}
                  key={s.id}
                  onClick={() => openSession(s.id)}
                >
                  <div className="title">
                    세션 #{s.sessionNumber} <SessionStatusBadge status={s.status} />
                    {s.parentSessionId ? <Badge kind="indigo">진화</Badge> : null}
                  </div>
                  <div className="sub">
                    {(s.goalText || "(첨부 중심 접수)").slice(0, 90)} · {fmtDate(s.createdAt)}
                  </div>
                </div>
              ))
            ) : (
              <div className="item">
                <div className="sub">이 프로젝트의 세션이 없습니다.</div>
              </div>
            )}
          </div>

          <h3>새 세션 개설 (진화)</h3>
          <p className="hint">
            변경 요청 원문 또는 첨부로 새 세션을 개설합니다. 세션 번호가 오르고 이전 세션과 연결됩니다
            (§28.6).
          </p>
          <textarea id="evoNote" ref={evoNoteRef} rows={2} placeholder="예: 결제 기능도 추가해줘." />
          <input
            id="evoFiles"
            ref={evoFilesRef}
            type="file"
            multiple
            onChange={(e) => setEvoFiles(Array.from(e.target.files || []))}
          />
          <FileChips files={evoFiles} />
          <div className="row">
            <button id="btnEvoOpen" className="btn" disabled={busy} onClick={openEvolutionSession}>
              새 세션 개설
            </button>
          </div>

          {current ? (
            <div id="sessionDetail">
              <div className="row between">
                <h3 id="sessionDetailTitle">
                  세션 #{current.sessionNumber} 상세
                </h3>
                <button
                  id="btnSessionClose"
                  className="btn ghost small"
                  onClick={() => {
                    setCurrent(null);
                    setQuestions([]);
                    setReqSet(null);
                  }}
                >
                  닫기
                </button>
              </div>
              <div id="sessionDetailBody">
                <div className="sub">
                  상태 <SessionStatusBadge status={current.status} /> · 생성 {fmtDate(current.createdAt)} ·
                  질문 상한 {current.maxQuestions}
                </div>
                <div className="sub">
                  개설 {userOpened ? <Badge>내가 개설 (진화)</Badge> : "파이프라인"}
                  {current.parentSessionId ? " · 이전 세션과 연결됨" : ""}
                </div>
                {current.goalText ? (
                  <div className="sub">
                    {userOpened ? "변경 요청" : "지시"}: {current.goalText}
                  </div>
                ) : (
                  <div className="sub">첨부 중심 접수 (§28.7-1)</div>
                )}
                {current.attachments && current.attachments.length ? (
                  <div className="chips">
                    {current.attachments.map((a, i) => {
                      const mark =
                        a.parseStatus === "PARSED"
                          ? "✔ 파싱"
                          : a.parseStatus === "IMAGE_PENDING_VISION"
                            ? "🖼 비전 대기"
                            : "⚠ 원본 보존";
                      return (
                        <span className="chip" key={i}>
                          {a.filename} · {mark}
                        </span>
                      );
                    })}
                  </div>
                ) : null}
              </div>
              <div className="row">
                <button
                  id="btnCompose"
                  className="btn primary"
                  hidden={userOpened || !["OPEN", "REVIEW"].includes(current.status)}
                  onClick={composeQuestions}
                >
                  질문 구성
                </button>
                <button id="btnSubmitAnswers" className="btn primary" hidden={!editable} onClick={submitAnswers}>
                  답변 제출
                </button>
                <button id="btnFreeze" className="btn danger" hidden={!canFreeze} onClick={freezeSession}>
                  {current.status === "WAITING_CLIENT" ? "이 정도면 진행(동결)" : "요구사항 동결"}
                </button>
                <button
                  id="btnCancel"
                  className="btn ghost"
                  hidden={!["OPEN", "WAITING_CLIENT"].includes(current.status)}
                  onClick={cancelSessionAction}
                >
                  세션 취소
                </button>
              </div>
              <div id="questionsBox" className="list">
                {questionsNote ? (
                  questionsNote.kind === "note" ? (
                    <div className="status">{questionsNote.text}</div>
                  ) : (
                    <div className="status err">{questionsNote.text}</div>
                  )
                ) : ["OPEN", "WAITING_CLIENT", "REVIEW"].includes(current.status) ? (
                  questions.length ? (
                    questions.map((q) => (
                      <div className="item" id={`qform-${q.id}`} key={q.id}>
                        <div className="title">{q.prompt}</div>
                        <div className="sub">
                          {QUESTION_TYPE_NAME[q.questionType] || q.questionType} · {q.displayOrder}번
                          {q.answerStatus === "ANSWERED" ? <Badge kind="green">응답함</Badge> : null}
                          {q.answerStatus === "SKIPPED" ? <Badge kind="amber">건너뜀</Badge> : null}
                        </div>
                        <QuestionBody q={q} editable={!!editable} />
                        {editable ? (
                          <label className="check">
                            <input className="q-skip" type="checkbox" /> 건너뛰기 (미확정 — 목록 가정으로 기록)
                          </label>
                        ) : null}
                      </div>
                    ))
                  ) : (
                    <div className="item">
                      <div className="sub">
                        {userOpened
                          ? "내가 개설한 세션 — 질문 없이 바로 동결할 수 있습니다 (§28.6)."
                          : "등록된 질문이 없습니다. '질문 구성'을 누르세요."}
                      </div>
                    </div>
                  )
                ) : null}
              </div>
              <div id="reqBox">{reqSet ? <RequirementSetView r={reqSet} /> : null}</div>
            </div>
          ) : null}

          <h3>업무</h3>
          <div className="list" id="taskList">
            {tasks.length ? (
              tasks.map((t) => (
                <div className="item click" data-task={t.id} key={t.id} onClick={() => showTaskDetail(t.id)}>
                  <div className="title">
                    {t.title} <Badge kind="indigo">{t.type || "TASK"}</Badge>{" "}
                    <Badge>{t.status || "DRAFT"}</Badge>
                  </div>
                  <div className="sub">
                    담당: {t.assignee?.name || "미배정"} · {fmtDate(t.createdAt)}
                  </div>
                </div>
              ))
            ) : (
              <div className="item">
                <div className="sub">아직 업무가 없습니다. 아래에서 추가할 수 있습니다.</div>
              </div>
            )}
          </div>
          {taskDetail ? (
            <div id="taskDetail">
              <h3>업무 상세</h3>
              <div className="item">
                <div className="title">
                  {taskDetail.title} <Badge kind="indigo">{taskDetail.type || "TASK"}</Badge>{" "}
                  <Badge>{taskDetail.status || ""}</Badge>
                </div>
                <div className="sub">{taskDetail.description || "지시 없음"}</div>
                <div className="sub">
                  ID: {taskDetail.id} · 생성 {fmtDate(taskDetail.createdAt)} · 시도{" "}
                  {fmtNum(taskDetail.attemptCount)}/{fmtNum(taskDetail.maxAttempts)}
                </div>
                {taskDetail.resultSummary ? (
                  <div className="sub">결과: {taskDetail.resultSummary}</div>
                ) : null}
              </div>
            </div>
          ) : null}
          <h3>업무 추가</h3>
          <label htmlFor="taskAssignee">담당 직원</label>
          <select id="taskAssignee" ref={taskAssigneeRef}>
            {store.employees.length ? (
              store.employees.map((e) => (
                <option value={e.id} key={e.id}>
                  {e.name} ({e.role})
                </option>
              ))
            ) : (
              <option value="">직원 없음</option>
            )}
          </select>
          <label htmlFor="taskType">유형</label>
          <select id="taskType" ref={taskTypeRef} defaultValue="TASK">
            <option>TASK</option>
            <option>BUG</option>
            <option>STORY</option>
            <option>EPIC</option>
            <option>SPIKE</option>
            <option>DOCUMENTATION</option>
            <option>REVIEW</option>
            <option>TEST</option>
          </select>
          <label htmlFor="taskTitle">제목</label>
          <input id="taskTitle" ref={taskTitleRef} autoComplete="off" />
          <label htmlFor="taskPrompt">지시 (prompt)</label>
          <textarea id="taskPrompt" ref={taskPromptRef} rows={3} />
          <div className="row">
            <button id="btnTaskSave" className="btn primary" disabled={busy} onClick={createTask}>
              업무 추가
            </button>
          </div>

          <h3>평가</h3>
          <label htmlFor="ratingTask">업무</label>
          <select id="ratingTask" ref={ratingTaskRef}>
            {tasks.length ? (
              tasks.map((t) => (
                <option value={t.id} key={t.id}>
                  {t.title}
                </option>
              ))
            ) : (
              <option value="">업무 없음</option>
            )}
          </select>
          <label htmlFor="ratingRater">평가자</label>
          <select id="ratingRater" ref={ratingRaterRef}>
            {store.employees.length ? (
              store.employees.map((e) => (
                <option value={e.id} key={e.id}>
                  {e.name} ({e.role})
                </option>
              ))
            ) : (
              <option value="">직원 없음</option>
            )}
          </select>
          <label htmlFor="ratingRated">대상</label>
          <select id="ratingRated" ref={ratingRatedRef}>
            {store.employees.length ? (
              store.employees.map((e) => (
                <option value={e.id} key={e.id}>
                  {e.name} ({e.role})
                </option>
              ))
            ) : (
              <option value="">직원 없음</option>
            )}
          </select>
          <label htmlFor="ratingCategory">카테고리</label>
          <select id="ratingCategory" ref={ratingCategoryRef} defaultValue="CODE_QUALITY">
            <option>CODE_QUALITY</option>
            <option>LOGIC</option>
            <option>COOPERATION</option>
            <option>DOC_COMPLETENESS</option>
          </select>
          <label htmlFor="ratingScore">점수 (0~5)</label>
          <input
            id="ratingScore"
            ref={ratingScoreRef}
            type="number"
            min={0}
            max={5}
            step={0.5}
            defaultValue="4"
            inputMode="decimal"
          />
          <label htmlFor="ratingComment">코멘트</label>
          <textarea id="ratingComment" ref={ratingCommentRef} rows={2} />
          <label htmlFor="ratingEvidence">근거 (콤마: PR-123, commit-abc, test-report)</label>
          <input id="ratingEvidence" ref={ratingEvidenceRef} autoComplete="off" />
          <div className="row">
            <button id="btnRatingSave" className="btn primary" disabled={busy} onClick={submitRating}>
              평가 제출
            </button>
          </div>
        </div>
      ) : null}
    </>
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
