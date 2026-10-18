import { useEffect, useRef, useState } from "react";
import { api, ApiError } from "../api";
import { useStore } from "../store";
import { useToast } from "../toast";
import {
  collectAnswers,
  firstUnansweredPrompt,
  fmtDate,
  QUESTION_TYPE_NAME,
  toAttachment,
} from "../lib";
import { Badge, FileChips, RequirementSetView, SessionStatusBadge, StatusText } from "../components";
import type { RequirementSet, Question, Session } from "../types";

type ResultMsg = { kind: "ok" | "err"; text: string };

export default function GoalTab({
  goSetup,
  goWork,
}: {
  goSetup: () => void;
  goWork: () => void;
}) {
  const store = useStore();
  const toast = useToast();

  const clientNameRef = useRef<HTMLInputElement>(null);
  const clientContactRef = useRef<HTMLInputElement>(null);
  const goalProjectRef = useRef<HTMLSelectElement>(null);
  const goalClientRef = useRef<HTMLSelectElement>(null);
  const goalTextRef = useRef<HTMLTextAreaElement>(null);
  const goalFilesRef = useRef<HTMLInputElement>(null);
  const sessionProjectRef = useRef<HTMLSelectElement>(null);
  const evoClientRef = useRef<HTMLSelectElement>(null);
  const evoNoteRef = useRef<HTMLTextAreaElement>(null);
  const evoFilesRef = useRef<HTMLInputElement>(null);

  const [clientResult, setClientResult] = useState<ResultMsg | null>(null);
  const [goalFiles, setGoalFiles] = useState<File[]>([]);
  const [evoFiles, setEvoFiles] = useState<File[]>([]);
  const [goalResult, setGoalResult] = useState<ResultMsg | null>(null);
  const [evoResult, setEvoResult] = useState<ResultMsg | null>(null);
  const [sessions, setSessions] = useState<Session[]>([]);
  const [current, setCurrent] = useState<Session | null>(null);
  const [questions, setQuestions] = useState<Question[]>([]);
  const [questionsNote, setQuestionsNote] = useState<{ kind: "note" | "err"; text: string } | null>(null);
  const [reqSet, setReqSet] = useState<RequirementSet | null>(null);
  const [busy, setBusy] = useState(false);
  const detailRef = useRef<HTMLDivElement>(null);

  const requireCtx = () => {
    if (!store.ctx) {
      toast.show("먼저 '시작' 탭에서 조직을 연결하세요.", "err");
      goSetup();
      return false;
    }
    return true;
  };

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
                ? "사용자 개설 세션입니다. 질문 없이 동결하면 체인은 기록 승인만 수행합니다(§28.6)."
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

  const openSession = async (id: string) => {
    try {
      const s = await api<Session>("GET", `/api/v1/clarification-sessions/${id}`);
      setCurrent(s);
      window.setTimeout(() => {
        detailRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
      }, 50);
    } catch (e) {
      toast.show((e as Error).message, "err");
    }
  };

  const saveClient = async () => {
    if (!requireCtx()) return;
    const displayName = clientNameRef.current?.value.trim() || "";
    if (!displayName) {
      toast.show("고객 표시 이름을 입력하세요.", "err");
      return;
    }
    setBusy(true);
    try {
      await api("POST", "/api/v1/clients", {
        organizationId: store.ctx!.organizationId,
        displayName,
        contact: clientContactRef.current?.value.trim() || null,
        createdByUserId: store.ctx!.userId,
      });
      if (clientNameRef.current) clientNameRef.current.value = "";
      if (clientContactRef.current) clientContactRef.current.value = "";
      setClientResult({ kind: "ok", text: `고객 등록 완료: ${displayName}` });
      toast.show("외부 고객을 등록했습니다.");
      await store.refreshAll();
    } catch (e) {
      const err = e as Error;
      setClientResult({ kind: "err", text: `실패: ${err.message}` });
      toast.show(err.message, "err");
    } finally {
      setBusy(false);
    }
  };

  const archiveClientAction = async (id: string) => {
    const c = store.clients.find((x) => x.id === id);
    if (
      !confirm(
        `'${c ? c.displayName : id}' 고객을 아카이브할까요?\n기존 중계 기록은 보존되며, 신규 중계 연결은 차단됩니다. (§5.26)`
      )
    )
      return;
    try {
      await api("PATCH", `/api/v1/clients/${id}`, { status: "archived" });
      toast.show("고객을 아카이브했습니다.");
      await store.refreshAll();
    } catch (e) {
      toast.show((e as Error).message, "err");
    }
  };

  const submitGoal = async () => {
    if (!requireCtx()) return;
    const projectId = goalProjectRef.current?.value || "";
    const goalText = goalTextRef.current?.value.trim() || "";
    const files = goalFiles;
    if (!projectId) {
      toast.show("프로젝트를 먼저 생성하세요. ('업무' 탭)", "err");
      goWork();
      return;
    }
    if (!goalText && !files.length) {
      toast.show("골 텍스트 또는 첨부 중 최소 한 가지가 필요합니다. (§28.7-1)", "err");
      return;
    }
    setBusy(true);
    try {
      const attachments = [];
      for (const f of files) attachments.push(await toAttachment(f));
      const s = await api<Session>("POST", `/api/v1/projects/${projectId}/goal`, {
        userId: store.ctx!.userId,
        goalText: goalText || null,
        attachments,
        clientId: goalClientRef.current?.value || null,
      });
      const parsed = (s.attachments || []).map((a) => `${a.filename}=${a.parseStatus}`).join(", ") || "없음";
      setGoalResult({ kind: "ok", text: `세션 #${s.sessionNumber} 생성 — 첨부 ${s.attachments?.length ?? 0}개: ${parsed}` });
      if (goalTextRef.current) goalTextRef.current.value = "";
      if (goalFilesRef.current) goalFilesRef.current.value = "";
      setGoalFiles([]);
      toast.show("골을 접수했습니다.");
      await store.loadPendingSessions();
      await openSession(s.id);
    } catch (e) {
      const err = e as Error;
      setGoalResult({ kind: "err", text: `실패: ${err.message}` });
      toast.show(err.message, "err");
    } finally {
      setBusy(false);
    }
  };

  const loadSessions = async () => {
    if (!requireCtx()) return;
    const projectId = sessionProjectRef.current?.value || "";
    if (!projectId) {
      toast.show("프로젝트를 선택하세요.", "err");
      return;
    }
    try {
      const list = await api<Session[]>("GET", `/api/v1/projects/${projectId}/clarification-sessions`);
      setSessions(list);
    } catch (e) {
      toast.show((e as Error).message, "err");
    }
  };

  const composeQuestions = async () => {
    if (!current) return;
    try {
      const qs = await api<Question[]>("POST", `/api/v1/clarification-sessions/${current.id}/questions`);
      toast.show(`질문 ${qs.length}개 구성 — 체인 통과 후 응답 요청이 수신함에 표시됩니다.`);
      await store.refreshAll();
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
      const updated = await api<Session>("POST", `/api/v1/clarification-sessions/${s.id}/answers`, {
        userId: store.ctx!.userId,
        answers: collectAnswers(questions),
      });
      toast.show("답변을 제출했습니다. 동결하면 요구사항이 확정됩니다.");
      await store.refreshAll();
      await openSession(updated.id);
    } catch (e) {
      toast.show(`답변 실패: ${(e as Error).message}`, "err");
    }
  };

  const freezeSession = async () => {
    const s = current;
    if (!s) return;
    const directive = s.status === "WAITING_CLIENT";
    const msg = directive
      ? "고객 명시 지시로 동결할까요?\n미답 질문은 가정(assumption)으로 기록된 뒤 동결됩니다. (§28.6)"
      : s.openedByUserId && s.status === "OPEN"
        ? "질문 없이 즉시 동결할까요?\n검토 체인은 기록 승인만 수행합니다. (§28.6)"
        : "요구사항을 동결할까요?\n동결 이후 수정은 새 버전으로만 가능합니다.";
    if (!confirm(msg)) return;
    try {
      const r = await api<RequirementSet>("POST", `/api/v1/clarification-sessions/${s.id}/freeze`, {
        userId: store.ctx!.userId,
        clientDirective: directive ? true : null,
      });
      toast.show(`요구사항을 동결했습니다 (v${r.version}).`);
      setReqSet(r);
      await store.refreshAll();
      await openSession(s.id);
    } catch (e) {
      toast.show(`동결 실패: ${(e as Error).message}`, "err");
    }
  };

  const openEvolutionSession = async () => {
    if (!requireCtx()) return;
    const projectId = sessionProjectRef.current?.value || "";
    const changeNote = evoNoteRef.current?.value.trim() || "";
    const files = evoFiles;
    if (!projectId) {
      toast.show("프로젝트를 선택하세요.", "err");
      return;
    }
    if (!changeNote && !files.length) {
      toast.show("변경 요청 원문 또는 첨부 중 최소 한 가지가 필요합니다. (§28.7-1)", "err");
      return;
    }
    setBusy(true);
    try {
      const attachments = [];
      for (const f of files) attachments.push(await toAttachment(f));
      const s = await api<Session>("POST", `/api/v1/projects/${projectId}/clarification-sessions`, {
        userId: store.ctx!.userId,
        changeNote: changeNote || null,
        attachments,
        clientId: evoClientRef.current?.value || null,
      });
      setEvoResult({ kind: "ok", text: `세션 #${s.sessionNumber} 개설 — 이전 세션에 연결(진화)` });
      if (evoNoteRef.current) evoNoteRef.current.value = "";
      if (evoFilesRef.current) evoFilesRef.current.value = "";
      setEvoFiles([]);
      toast.show("새 세션을 개설했습니다.");
      await store.loadPendingSessions();
      await loadSessions();
      await openSession(s.id);
    } catch (e) {
      const err = e as Error;
      setEvoResult({ kind: "err", text: `실패: ${err.message}` });
      toast.show(err.message, "err");
    } finally {
      setBusy(false);
    }
  };

  const cancelSessionAction = async () => {
    const s = current;
    if (!s) return;
    if (!confirm(`세션 #${s.sessionNumber}을(를) 취소할까요?\n취소 후에는 동결할 수 없습니다. (§28.6)`)) return;
    try {
      const r = await api<Session>("POST", `/api/v1/clarification-sessions/${s.id}/cancel`, {
        userId: store.ctx!.userId,
      });
      toast.show(`세션 #${r.sessionNumber}을(를) 취소했습니다.`);
      await store.refreshAll();
      await openSession(s.id);
    } catch (e) {
      toast.show(`취소 실패: ${(e as Error).message}`, "err");
    }
  };

  const activeClients = store.clients.filter((c) => c.status !== "ARCHIVED");
  const inheritLabel = "(자동 — 프로젝트 고객 상속)";
  const waiting = store.pendingSessions.filter((s) => s.status === "WAITING_CLIENT").length;

  const userOpened = !!current?.openedByUserId;
  const editable = current?.status === "WAITING_CLIENT";
  const canFreeze =
    current !== null &&
    (current.status === "REVIEW" ||
      current.status === "WAITING_CLIENT" ||
      (userOpened && current.status === "OPEN"));

  return (
    <>
      <div className="card">
        <h2>외부 고객 (중계, §5.26)</h2>
        <p className="hint">
          운영자가 외부 고객을 등록하고 골·세션을 중계합니다. 아카이브된 고객은 신규 중계 연결에 사용할 수
          없습니다(기존 기록은 보존).
        </p>
        <label htmlFor="clientName">표시 이름</label>
        <input id="clientName" ref={clientNameRef} autoComplete="off" />
        <label htmlFor="clientContact">연락처 (선택)</label>
        <input id="clientContact" ref={clientContactRef} autoComplete="off" />
        <button id="btnClientSave" className="btn primary" disabled={busy} onClick={saveClient}>
          고객 등록
        </button>
        {clientResult ? <StatusText kind={clientResult.kind}>{clientResult.text}</StatusText> : null}
        <div className="list" id="clientList">
          {store.clients.length ? (
            store.clients.map((c) => (
              <div className={`item${c.status === "ARCHIVED" ? " dim" : ""}`} key={c.id}>
                <div className="title">
                  {c.displayName}{" "}
                  {c.status === "ARCHIVED" ? <Badge>아카이브</Badge> : <Badge kind="green">활성</Badge>}
                </div>
                <div className="sub">
                  {c.contact || "연락처 없음"} · 등록 {fmtDate(c.createdAt)}
                </div>
                {c.status !== "ARCHIVED" ? (
                  <button
                    className="btn ghost small"
                    data-archive-client={c.id}
                    onClick={() => archiveClientAction(c.id)}
                  >
                    아카이브
                  </button>
                ) : null}
              </div>
            ))
          ) : (
            <div className="item">
              <div className="sub">등록된 외부 고객이 없습니다. 위에서 중계할 고객을 등록하세요.</div>
            </div>
          )}
        </div>
      </div>

      <div className="card">
        <div className="row between">
          <h2>
            세션 수신함{" "}
            <span className="badge hot" id="inboxCount" hidden={!store.pendingSessions.length}>
              {store.pendingSessions.length}
            </span>
          </h2>
          <button id="btnInboxRefresh" className="btn ghost small" onClick={() => store.loadPendingSessions()}>
            새로고침
          </button>
        </div>
        <p className="hint">
          응답 대기(WAITING_CLIENT) 세션이 위에 강조됩니다. 내용을 누르면 상세에서 질문에 답할 수 있습니다.
        </p>
        <div className="list" id="pendingSessions">
          {store.pendingSessions.length ? (
            store.pendingSessions.map((s) => (
              <div
                className={`item click${s.status === "WAITING_CLIENT" ? " hot" : ""}`}
                data-session={s.id}
                key={s.id}
                onClick={() => openSession(s.id)}
              >
                <div className="title">
                  {store.projectName(s.projectId)} 세션 #{s.sessionNumber}{" "}
                  <SessionStatusBadge status={s.status} />
                </div>
                <div className="sub">{(s.goalText || "(첨부 중심 접수)").slice(0, 90)}</div>
                <div className="sub">{fmtDate(s.createdAt)}</div>
              </div>
            ))
          ) : (
            <div className="item">
              <div className="sub">대기 세션이 없습니다. 아래에서 골을 접수하세요.</div>
            </div>
          )}
        </div>
      </div>

      <div className="card">
        <h2>골(Goal) 접수</h2>
        <p className="hint">
          텍스트와 첨부 중 최소 한 가지는 필요합니다(§28.7-1). 예: 회원가입·로그인 등 기능 목록 기술.
        </p>
        <label htmlFor="goalProject">프로젝트</label>
        <select id="goalProject" ref={goalProjectRef}>
          {store.projects.length ? (
            store.projects.map((p) => (
              <option value={p.id} key={p.id}>{p.name}</option>
            ))
          ) : (
            <option value="">프로젝트 없음</option>
          )}
        </select>
        <label htmlFor="goalClient">외부 고객 중계 (선택 — 미선택 시 프로젝트 고객 상속)</label>
        <select id="goalClient" ref={goalClientRef} defaultValue="">
          <option value="">{inheritLabel}</option>
          {activeClients.map((c) => (
            <option value={c.id} key={c.id}>{c.displayName}</option>
          ))}
        </select>
        <label htmlFor="goalText">골 텍스트 (첨부 단독 접수 시 비워도 됩니다)</label>
        <textarea id="goalText" ref={goalTextRef} rows={3} />
        <label htmlFor="goalFiles">
          첨부 파일 (text 기반 md·txt·csv·json은 내용 파싱, 그 외 형식은 원본 보존) — 최대 10개·파일당 50MB
        </label>
        <input
          id="goalFiles"
          ref={goalFilesRef}
          type="file"
          multiple
          onChange={(e) => setGoalFiles(Array.from(e.target.files || []))}
        />
        <FileChips files={goalFiles} />
        <button id="btnGoalSubmit" className="btn primary" disabled={busy} onClick={submitGoal}>
          골 제출
        </button>
        {goalResult ? <StatusText kind={goalResult.kind}>{goalResult.text}</StatusText> : null}
      </div>

      <div className="card">
        <h2>세션 탐색</h2>
        <div className="row">
          <select id="sessionProject" ref={sessionProjectRef}>
            {store.projects.length ? (
              store.projects.map((p) => (
                <option value={p.id} key={p.id}>{p.name}</option>
              ))
            ) : (
              <option value="">프로젝트 없음</option>
            )}
          </select>
          <button id="btnSessionLoad" className="btn primary" onClick={loadSessions}>
            세션 불러오기
          </button>
        </div>
        <div className="list" id="sessionList">
          {sessions.length ? (
            sessions.map((s) => (
              <div className="item click" data-session={s.id} key={s.id} onClick={() => openSession(s.id)}>
                <div className="title">
                  세션 #{s.sessionNumber} <SessionStatusBadge status={s.status} />
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

        <h3>새 세션 개설 (진화, §28.6)</h3>
        <p className="hint">
          변경 요청 원문 또는 첨부로 새 세션을 개설합니다. 세션 번호가 오르고 이전 세션과 연결됩니다.
        </p>
        <label htmlFor="evoClient">외부 고객 중계 (선택 — 미선택 시 프로젝트 고객 상속)</label>
        <select id="evoClient" ref={evoClientRef} defaultValue="">
          <option value="">{inheritLabel}</option>
          {activeClients.map((c) => (
            <option value={c.id} key={c.id}>{c.displayName}</option>
          ))}
        </select>
        <label htmlFor="evoNote">변경 요청 원문 (첨부 단독 개설 시 비워도 됩니다)</label>
        <textarea id="evoNote" ref={evoNoteRef} rows={2} />
        <label htmlFor="evoFiles">첨부 (선택) — 최대 10개·파일당 50MB</label>
        <input
          id="evoFiles"
          ref={evoFilesRef}
          type="file"
          multiple
          onChange={(e) => setEvoFiles(Array.from(e.target.files || []))}
        />
        <FileChips files={evoFiles} />
        <button id="btnEvoOpen" className="btn" disabled={busy} onClick={openEvolutionSession}>
          새 세션 개설
        </button>
        {evoResult ? <StatusText kind={evoResult.kind}>{evoResult.text}</StatusText> : null}

        {current ? (
          <div id="sessionDetail" ref={detailRef}>
            <div className="row between">
              <h3 id="sessionDetailTitle">
                {store.projectName(current.projectId)} — 세션 #{current.sessionNumber}
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
                상태 <SessionStatusBadge status={current.status} /> · 생성 {fmtDate(current.createdAt)} · 질문
                상한 {current.maxQuestions}
              </div>
              <div className="sub">
                개설 {userOpened ? <Badge>사용자 개설 (진화, §28.6)</Badge> : "파이프라인"}
                {current.parentSessionId ? " · 이전 세션과 연결됨" : ""}
                {current.clientId ? (
                  <Badge kind="indigo">중계: {store.clientName(current.clientId)}</Badge>
                ) : null}
              </div>
              {current.goalText ? (
                <div className="sub">
                  {userOpened ? "변경 요청" : "골"}: {current.goalText}
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
                        ? "사용자 개설 세션 — 질문 없이 바로 동결할 수 있습니다 (§28.6)."
                        : "등록된 질문이 없습니다. '질문 구성'을 누르세요."}
                    </div>
                  </div>
                )
              ) : null}
            </div>
            <div id="reqBox">{reqSet ? <RequirementSetView r={reqSet} /> : null}</div>
          </div>
        ) : null}
      </div>
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
