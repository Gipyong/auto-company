import { useRef, useState } from "react";
import { api } from "../api";
import { useStore } from "../store";
import { useToast } from "../toast";
import { fmtDate, fmtNum } from "../lib";
import { Badge } from "../components";
import type { ProjectLite, TaskLite, TaskResponse } from "../types";

export default function WorkTab({ goSetup }: { goSetup: () => void }) {
  const store = useStore();
  const toast = useToast();
  const [taskDetail, setTaskDetail] = useState<{ t: TaskResponse; local: TaskLite | null } | null>(null);
  const [askResult, setAskResult] = useState<{ kind: "ok" | "err"; q?: string; a?: string; text?: string } | null>(
    null
  );
  const [busy, setBusy] = useState(false);

  const projNameRef = useRef<HTMLInputElement>(null);
  const projDescRef = useRef<HTMLInputElement>(null);
  const projClientRef = useRef<HTMLSelectElement>(null);

  const taskProjectRef = useRef<HTMLSelectElement>(null);
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

  const msgProjectRef = useRef<HTMLSelectElement>(null);
  const msgSenderRef = useRef<HTMLSelectElement>(null);
  const msgTargetRef = useRef<HTMLSelectElement>(null);
  const msgTypeRef = useRef<HTMLSelectElement>(null);
  const msgContentRef = useRef<HTMLTextAreaElement>(null);

  const askQRef = useRef<HTMLTextAreaElement>(null);

  const requireCtx = () => {
    if (!store.ctx) {
      toast.show("먼저 '시작' 탭에서 조직을 연결하세요.", "err");
      goSetup();
      return false;
    }
    return true;
  };

  const activeClients = store.clients.filter((c) => c.status !== "ARCHIVED");

  const createProject = async () => {
    if (!requireCtx()) return;
    const name = projNameRef.current?.value.trim() || "";
    if (!name) {
      toast.show("프로젝트 이름을 입력하세요.", "err");
      return;
    }
    setBusy(true);
    try {
      const p = await api<ProjectLite>("POST", "/api/v1/projects", {
        userId: store.ctx!.userId,
        name,
        description: projDescRef.current?.value.trim() || null,
        clientId: projClientRef.current?.value || null,
      });
      store.addProject({
        id: p.id,
        name: p.name,
        description: p.description,
        clientId: p.clientId,
        createdAt: new Date().toISOString(),
      });
      if (projNameRef.current) projNameRef.current.value = "";
      if (projDescRef.current) projDescRef.current.value = "";
      toast.show("프로젝트를 생성했습니다.");
    } catch (e) {
      toast.show((e as Error).message, "err");
    } finally {
      setBusy(false);
    }
  };

  const createTask = async () => {
    if (!requireCtx()) return;
    const projectId = taskProjectRef.current?.value || "";
    const assigneeId = taskAssigneeRef.current?.value || "";
    const title = taskTitleRef.current?.value.trim() || "";
    if (!projectId || !assigneeId || !title) {
      toast.show("프로젝트, 담당 직원, 제목은 필수입니다.", "err");
      return;
    }
    setBusy(true);
    try {
      const t = await api<any>("POST", "/api/v1/tasks", {
        projectId,
        assigneeId,
        taskType: taskTypeRef.current?.value,
        title,
        prompt: taskPromptRef.current?.value.trim() || null,
      });
      store.addTask({
        id: t.id,
        title: t.title || title,
        taskType: t.type || taskTypeRef.current?.value || "TASK",
        status: t.status || "DRAFT",
        projectId,
        assigneeId,
        createdAt: new Date().toISOString(),
      });
      if (taskTitleRef.current) taskTitleRef.current.value = "";
      if (taskPromptRef.current) taskPromptRef.current.value = "";
      toast.show("업무를 생성했습니다.");
    } catch (e) {
      toast.show((e as Error).message, "err");
    } finally {
      setBusy(false);
    }
  };

  const showTaskDetail = async (id: string) => {
    try {
      const t = await api<TaskResponse>("GET", `/api/v1/tasks/${id}`);
      const local = store.tasks.find((x) => x.id === id) || null;
      setTaskDetail({ t, local });
    } catch (e) {
      toast.show((e as Error).message, "err");
    }
  };

  const submitRating = async () => {
    if (!requireCtx()) return;
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

  const sendMessage = async () => {
    if (!requireCtx()) return;
    const projectId = msgProjectRef.current?.value || "";
    const senderEmployeeId = msgSenderRef.current?.value || "";
    const targetEmployeeId = msgTargetRef.current?.value || "";
    const content = msgContentRef.current?.value.trim() || "";
    if (!projectId || !senderEmployeeId || !targetEmployeeId || !content) {
      toast.show("프로젝트, 보내는/받는 직원, 내용은 필수입니다.", "err");
      return;
    }
    setBusy(true);
    try {
      await api("POST", "/api/v1/messages", {
        projectId,
        senderEmployeeId,
        targetEmployeeId,
        content,
        messageType: msgTypeRef.current?.value,
      });
      if (msgContentRef.current) msgContentRef.current.value = "";
      toast.show("메시지를 전송했습니다.");
    } catch (e) {
      toast.show((e as Error).message, "err");
    } finally {
      setBusy(false);
    }
  };

  const ask = async () => {
    const question = askQRef.current?.value.trim() || "";
    if (!question) {
      toast.show("질문을 입력하세요.", "err");
      return;
    }
    setBusy(true);
    try {
      const r = await api<{ question: string; answer: string }>("POST", "/api/v1/ask", { question });
      setAskResult({ kind: "ok", q: r.question, a: r.answer });
    } catch (e) {
      setAskResult({ kind: "err", text: (e as Error).message });
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <div className="card">
        <h2>프로젝트</h2>
        <label htmlFor="projName">이름</label>
        <input id="projName" ref={projNameRef} autoComplete="off" />
        <label htmlFor="projDesc">설명</label>
        <input id="projDesc" ref={projDescRef} autoComplete="off" />
        <label htmlFor="projClient">외부 고객 (선택 — 프로젝트 소속 중계 고객)</label>
        <select id="projClient" ref={projClientRef} defaultValue="">
          <option value="">(외부 고객 없음)</option>
          {activeClients.map((c) => (
            <option value={c.id} key={c.id}>
              {c.displayName}
            </option>
          ))}
        </select>
        <button id="btnProjSave" className="btn primary" disabled={busy} onClick={createProject}>
          프로젝트 생성
        </button>
        <div className="list" id="projectList">
          {store.projects.length ? (
            store.projects.map((p) => (
              <div className="item" key={p.id}>
                <div className="title">
                  {p.name}{" "}
                  {p.clientId ? <Badge kind="indigo">고객 {store.clientName(p.clientId)}</Badge> : null}
                </div>
                <div className="sub">
                  {p.description || ""} · {fmtDate(p.createdAt)}
                </div>
                <div className="sub">ID: {p.id}</div>
              </div>
            ))
          ) : (
            <div className="item">
              <div className="sub">프로젝트가 없습니다. 위에서 생성하세요.</div>
            </div>
          )}
        </div>
      </div>

      <div className="card">
        <h2>업무 할당</h2>
        <label htmlFor="taskProject">프로젝트</label>
        <select id="taskProject" ref={taskProjectRef}>
          {store.projects.length ? (
            store.projects.map((p) => (
              <option value={p.id} key={p.id}>{p.name}</option>
            ))
          ) : (
            <option value="">프로젝트 없음</option>
          )}
        </select>
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
        <button id="btnTaskSave" className="btn primary" disabled={busy} onClick={createTask}>
          업무 생성
        </button>
        <div className="list" id="taskList">
          {store.tasks.length ? (
            store.tasks
              .slice()
              .reverse()
              .map((t) => (
                <div className="item click" data-task={t.id} key={t.id} onClick={() => showTaskDetail(t.id)}>
                  <div className="title">
                    {t.title} <Badge kind="indigo">{t.taskType}</Badge> <Badge>{t.status}</Badge>
                  </div>
                  <div className="sub">
                    프로젝트: {store.projectName(t.projectId)} · 담당: {store.employeeName(t.assigneeId)} ·{" "}
                    {fmtDate(t.createdAt)}
                  </div>
                </div>
              ))
          ) : (
            <div className="item">
              <div className="sub">생성한 업무가 없습니다.</div>
            </div>
          )}
        </div>
        <div id="taskDetail">
          {taskDetail ? (
            <>
              <h3>업무 상세</h3>
              <div className="item">
                <div className="title">
                  {taskDetail.t.title || taskDetail.local?.title}{" "}
                  <Badge kind="indigo">{taskDetail.t.type || taskDetail.local?.taskType || ""}</Badge>{" "}
                  <Badge>{taskDetail.t.status || taskDetail.local?.status || ""}</Badge>
                </div>
                <div className="sub">{taskDetail.t.description || "지시 없음"}</div>
                <div className="sub">
                  ID: {taskDetail.t.id} · 생성: {fmtDate(taskDetail.t.createdAt)} · 시도{" "}
                  {fmtNum(taskDetail.t.attemptCount)}/{fmtNum(taskDetail.t.maxAttempts)}
                </div>
                {taskDetail.t.resultSummary ? (
                  <div className="sub">결과: {taskDetail.t.resultSummary}</div>
                ) : null}
              </div>
            </>
          ) : null}
        </div>
      </div>

      <div className="card">
        <h2>피드백 평가</h2>
        <label htmlFor="ratingTask">업무</label>
        <select id="ratingTask" ref={ratingTaskRef}>
          {store.tasks.length ? (
            store.tasks
              .slice()
              .reverse()
              .map((t) => (
                <option value={t.id} key={t.id}>{t.title}</option>
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
        <button id="btnRatingSave" className="btn primary" disabled={busy} onClick={submitRating}>
          평가 제출
        </button>
      </div>

      <div className="card">
        <h2>메시지</h2>
        <label htmlFor="msgProject">프로젝트</label>
        <select id="msgProject" ref={msgProjectRef}>
          {store.projects.length ? (
            store.projects.map((p) => (
              <option value={p.id} key={p.id}>{p.name}</option>
            ))
          ) : (
            <option value="">프로젝트 없음</option>
          )}
        </select>
        <label htmlFor="msgSender">보내는 직원</label>
        <select id="msgSender" ref={msgSenderRef}>
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
        <label htmlFor="msgTarget">받는 직원</label>
        <select id="msgTarget" ref={msgTargetRef}>
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
        <label htmlFor="msgType">유형</label>
        <select id="msgType" ref={msgTypeRef} defaultValue="DISCUSSION">
          <option>DISCUSSION</option>
          <option>FEEDBACK</option>
          <option>CODE_PROPOSAL</option>
          <option>INTERVIEW_Q</option>
          <option>INTERVIEW_A</option>
        </select>
        <label htmlFor="msgContent">내용</label>
        <textarea id="msgContent" ref={msgContentRef} rows={2} />
        <button id="btnMsgSave" className="btn primary" disabled={busy} onClick={sendMessage}>
          메시지 전송
        </button>
      </div>

      <div className="card">
        <h2>CEO Ask</h2>
        <label htmlFor="askQ">질문</label>
        <textarea id="askQ" ref={askQRef} rows={2} placeholder="예: 현재 아키텍처는?" />
        <button id="btnAsk" className="btn primary" disabled={busy} onClick={ask}>
          질문하기
        </button>
        <div id="askA">
          {askResult ? (
            askResult.kind === "ok" ? (
              <div className="status ok">
                <strong>Q.</strong> {askResult.q}
                {"\n"}
                <strong>A.</strong> {askResult.a}
              </div>
            ) : (
              <div className="status err">{askResult.text}</div>
            )
          ) : null}
        </div>
      </div>
    </>
  );
}
