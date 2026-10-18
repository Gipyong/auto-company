import { useEffect, useRef, useState } from "react";
import { api, ApiError } from "../api";
import { useStore } from "../store";
import { useToast } from "../toast";
import { fmtNum } from "../lib";
import { Badge, EvaluationReportView, StatusText } from "../components";
import type { Employee, EvaluationReport } from "../types";

export default function EmployeesTab({ goSetup }: { goSetup: () => void }) {
  const store = useStore();
  const toast = useToast();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [evals, setEvals] = useState<EvaluationReport | null>(null);
  const [instrResult, setInstrResult] = useState<{ kind: "ok" | "err"; text: string } | null>(null);
  const [assigning, setAssigning] = useState(false);
  const [savingInstr, setSavingInstr] = useState(false);
  const instrRef = useRef<HTMLTextAreaElement>(null);
  const gradeSelectRef = useRef<HTMLSelectElement>(null);
  const detailRef = useRef<HTMLDivElement>(null);

  const selected: Employee | null = store.employees.find((x) => x.id === selectedId) || null;

  const requireCtx = () => {
    if (!store.ctx) {
      toast.show("먼저 '시작' 탭에서 조직을 연결하세요.", "err");
      goSetup();
      return false;
    }
    return true;
  };

  useEffect(() => {
    if (selectedId && detailRef.current) {
      detailRef.current.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  }, [selectedId]);

  const openEmployee = (id: string) => {
    setSelectedId(id);
    setEvals(null);
    setInstrResult(null);
  };

  const closeEmployee = () => {
    setSelectedId(null);
    setEvals(null);
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
    if (!requireCtx()) return;
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

  return (
    <>
      {selected ? (
        <div className="card" id="empDetail" ref={detailRef}>
          <div className="row between">
            <h2 id="empDetailTitle">{selected.name} 상세</h2>
            <button id="btnEmpClose" className="btn ghost small" onClick={closeEmployee}>
              닫기
            </button>
          </div>
          <div id="empDetailBody">
            <div className="sub">ID: {selected.id}</div>
            <div className="sub">
              역할: {selected.role} · 직급: {store.gradeLabel(selected.gradeId)} · 상태:{" "}
              {selected.status}
            </div>
            <div className="sub">
              평균 평점: {Number(selected.avgRating ?? 0).toFixed(2)} · 완료 업무{" "}
              {fmtNum(selected.completedTasks)}/{fmtNum(selected.totalTasks)}
            </div>
            {selected.systemPrompt ? (
              <div className="sub">시스템 프롬프트: {selected.systemPrompt}</div>
            ) : null}
          </div>
          <div className="row">
            <select
              id="empGradeSelect"
              aria-label="직급 선택"
              ref={gradeSelectRef}
              key={`grade-${selected.id}`}
              defaultValue={selected.gradeId || ""}
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
              평가 리포트
            </button>
          </div>
          <h3>커스텀 지시 (custom_instructions, §5.4)</h3>
          <p className="hint">
            사용자가 작성하는 직원 개별 지시(자유 서식, 최대 4000자). 시스템 안전 정책·도구 제약이 항상
            우선합니다(§8.2). 비우고 저장하면 지워집니다.
          </p>
          <textarea
            id="empInstructions"
            key={`instr-${selected.id}`}
            ref={instrRef}
            rows={4}
            maxLength={4000}
            defaultValue={selected.customInstructions || ""}
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
      ) : null}

      <div className="list" id="empList">
        {!store.ctx ? (
          <div className="item">
            <div className="sub">먼저 '시작' 탭에서 조직을 연결하세요.</div>
          </div>
        ) : !store.employees.length ? (
          <div className="item">
            <div className="sub">
              직원이 없습니다. '시작' 탭에서 기본 회사 템플릿을 가져오거나 API로 채용하세요.
            </div>
          </div>
        ) : (
          store.employees.map((e) => (
            <div className="item click" data-emp={e.id} key={e.id} onClick={() => openEmployee(e.id)}>
              <div className="title">
                {e.name} <Badge kind="indigo">{e.role}</Badge>
              </div>
              <div className="sub">
                직급: {store.gradeLabel(e.gradeId)} · 상태: {e.status} · 완료{" "}
                {fmtNum(e.completedTasks)}/{fmtNum(e.totalTasks)}
              </div>
            </div>
          ))
        )}
      </div>
    </>
  );
}
