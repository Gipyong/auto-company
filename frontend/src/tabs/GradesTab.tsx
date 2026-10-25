import { useEffect, useRef, useState } from "react";
import { api } from "../api";
import { useStore } from "../store";
import { useToast } from "../toast";
import { profileLabel } from "../lib";
import { Badge, EmptyState } from "../components";

const TASK_TYPE_HINT = "TASK, BUG, TEST, REVIEW, DOCUMENTATION, EPIC, STORY, SPIKE";

export default function GradesTab() {
  const store = useStore();
  const toast = useToast();
  const [editingId, setEditingId] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [saving, setSaving] = useState(false);
  const [poolGradeId, setPoolGradeId] = useState<string | null>(null);
  const [pool, setPool] = useState<{ profileId: string }[]>([]);
  const [poolSaving, setPoolSaving] = useState(false);

  const codeRef = useRef<HTMLInputElement>(null);
  const nameRef = useRef<HTMLInputElement>(null);
  const ordinalRef = useRef<HTMLInputElement>(null);
  const respRef = useRef<HTMLTextAreaElement>(null);
  const taskTypesRef = useRef<HTMLInputElement>(null);
  const modeRef = useRef<HTMLSelectElement>(null);
  const formRef = useRef<HTMLDivElement>(null);

  const poolGrade = store.grades.find((x) => x.id === poolGradeId) || null;
  const editingGrade = editingId ? store.grades.find((g) => g.id === editingId) || null : null;

  const resetGradeForm = () => {
    setEditingId(null);
    if (codeRef.current) codeRef.current.value = "";
    if (nameRef.current) nameRef.current.value = "";
    if (ordinalRef.current) {
      ordinalRef.current.value = String(
        store.grades.length ? Math.max(...store.grades.map((g) => g.ordinal)) + 1 : 1
      );
    }
    if (respRef.current) respRef.current.value = "";
    if (taskTypesRef.current) taskTypesRef.current.value = "";
    if (modeRef.current) modeRef.current.value = "PRIORITY";
  };

  const openForm = () => {
    resetGradeForm();
    setShowForm(true);
    window.setTimeout(() => formRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }), 50);
  };

  const toggleForm = () => {
    if (showForm) {
      setShowForm(false);
      resetGradeForm();
      return;
    }
    openForm();
  };

  useEffect(() => {
    resetGradeForm();
  }, []);

  const editGrade = (id: string) => {
    const g = store.grades.find((x) => x.id === id);
    if (!g) return;
    setEditingId(id);
    setShowForm(true);
    if (codeRef.current) codeRef.current.value = g.code || "";
    if (nameRef.current) nameRef.current.value = g.name || "";
    if (ordinalRef.current) ordinalRef.current.value = String(g.ordinal ?? 1);
    if (respRef.current) respRef.current.value = g.responsibilities || "";
    if (taskTypesRef.current) taskTypesRef.current.value = (g.taskTypes || []).join(", ");
    if (modeRef.current) modeRef.current.value = g.modelSelectionMode || "PRIORITY";
    formRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  const saveGrade = async () => {
    const body: Record<string, unknown> = {
      code: codeRef.current?.value.trim() || "",
      name: nameRef.current?.value.trim() || "",
      ordinal: Number(ordinalRef.current?.value || 0),
      responsibilities: respRef.current?.value.trim() || null,
      taskTypes: (taskTypesRef.current?.value || "")
        .split(",")
        .map((s) => s.trim().toUpperCase())
        .filter(Boolean),
      modelSelectionMode: modeRef.current?.value || "PRIORITY",
      status: "ACTIVE",
    };
    if (!body.code || !body.name || !body.ordinal) {
      toast.show("코드, 이름, 순서는 필수입니다.", "err");
      return;
    }
    setSaving(true);
    try {
      if (editingId) {
        await api("PATCH", `/api/v1/grades/${editingId}`, body);
        toast.show("직급을 수정했습니다.");
      } else {
        await api("POST", "/api/v1/grades", { ...body, organizationId: store.ctx.organizationId });
        toast.show("직급을 추가했습니다.");
      }
      setShowForm(false);
      resetGradeForm();
      await store.refreshAll();
    } catch (e) {
      toast.show((e as Error).message, "err");
    } finally {
      setSaving(false);
    }
  };

  const deleteGrade = async (id: string) => {
    const g = store.grades.find((x) => x.id === id);
    if (!g) return;
    if (!confirm(`'${g.code} ${g.name}' 직급을 삭제할까요?\n배정된 직원이 있으면 거부됩니다.`)) return;
    try {
      await api("DELETE", `/api/v1/grades/${id}`);
      toast.show("직급을 삭제했습니다.");
      await store.refreshAll();
    } catch (e) {
      toast.show(`삭제 실패: ${(e as Error).message}`, "err");
    }
  };

  const openPool = async (gradeId: string) => {
    const g = store.grades.find((x) => x.id === gradeId);
    if (!g) return;
    try {
      const models = await api<any[]>("GET", `/api/v1/grades/${gradeId}/models`);
      setPoolGradeId(gradeId);
      setPool(
        models
          .slice()
          .sort((a, b) => a.priority - b.priority)
          .map((m) => ({ profileId: m.modelProfileId }))
      );
    } catch (e) {
      toast.show((e as Error).message, "err");
    }
  };

  const addToPool = (profileId: string) => {
    setPool((prev) => [...prev, { profileId }]);
  };

  const movePool = (i: number, dir: -1 | 1) => {
    setPool((prev) => {
      const next = prev.slice();
      const j = i + dir;
      if (j < 0 || j >= next.length) return prev;
      [next[j], next[i]] = [next[i], next[j]];
      return next;
    });
  };

  const removeFromPool = (i: number) => {
    setPool((prev) => prev.filter((_, idx) => idx !== i));
  };

  const savePool = async () => {
    if (!poolGradeId) return;
    setPoolSaving(true);
    try {
      await api("PUT", `/api/v1/grades/${poolGradeId}/models`, pool.map((p) => ({ modelProfileId: p.profileId })));
      toast.show("모델 풀을 저장했습니다.");
      setPoolGradeId(null);
    } catch (e) {
      toast.show((e as Error).message, "err");
    } finally {
      setPoolSaving(false);
    }
  };

  const inPool = new Set(pool.map((p) => p.profileId));
  const availableProfiles = store.profiles.filter((p) => !inPool.has(p.id));

  return (
    <>
      <div className="card">
        <div className="row between">
          <h2>
            직급 <Badge>{store.grades.length}</Badge>
          </h2>
          {store.grades.length ? (
            <button id="btnGradeAdd" className="btn ghost small" onClick={toggleForm}>
              {showForm ? "닫기" : "+ 직급 추가"}
            </button>
          ) : null}
        </div>
        <div className="list" id="gradeList">
          {!store.grades.length ? (
            showForm ? null : (
              <EmptyState
                title="아직 직급이 없습니다"
                hint="직급은 업무 유형과 모델 선택 방식을 묶는 단위입니다. 기본 템플릿이 적용되면 L1~L5가 자동 생성됩니다."
                action="첫 직급 추가"
                onAction={openForm}
              />
            )
          ) : (
            store.grades.map((g) => (
              <div className="item" key={g.id}>
                <div className="title">
                  {g.code} {g.name} <Badge>ordinal {g.ordinal}</Badge>{" "}
                  <Badge>{g.modelSelectionMode}</Badge>
                  {g.templateDefault ? <Badge kind="indigo">템플릿</Badge> : null}
                  <Badge kind={g.status === "ACTIVE" ? "green" : ""}>{g.status}</Badge>
                </div>
                {g.responsibilities ? <div className="sub">{g.responsibilities}</div> : null}
                {g.taskTypes && g.taskTypes.length ? (
                  <div className="chips">
                    {g.taskTypes.map((t) => (
                      <span className="chip" key={t}>
                        {t}
                      </span>
                    ))}
                  </div>
                ) : null}
                <div className="actions">
                  <button
                    className="btn ghost small"
                    data-act="pool"
                    data-id={g.id}
                    onClick={() => openPool(g.id)}
                  >
                    모델 풀
                  </button>
                  <button
                    className="btn ghost small"
                    data-act="edit"
                    data-id={g.id}
                    onClick={() => editGrade(g.id)}
                  >
                    수정
                  </button>
                  <button
                    className="btn danger small"
                    data-act="del"
                    data-id={g.id}
                    onClick={() => deleteGrade(g.id)}
                  >
                    삭제
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      </div>

      {showForm ? (
        <div className="modal-backdrop" onClick={() => { setShowForm(false); resetGradeForm(); }}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()} ref={formRef}>
          <div className="row between">
            <h2 id="gradeFormTitle">
              {editingGrade ? `직급 수정 — ${editingGrade.code} ${editingGrade.name}` : "직급 추가"}
            </h2>
            <button
              id="btnGradeCancel"
              className="btn ghost small"
              onClick={() => {
                setShowForm(false);
                resetGradeForm();
              }}
            >
              취소
            </button>
          </div>
          <label htmlFor="gradeCode">코드 (조직 내 고유, 예: L1)</label>
          <input id="gradeCode" ref={codeRef} autoComplete="off" />
          <label htmlFor="gradeName">이름</label>
          <input id="gradeName" ref={nameRef} autoComplete="off" />
          <label htmlFor="gradeOrdinal">순서 (ordinal, 클수록 상위)</label>
          <input id="gradeOrdinal" ref={ordinalRef} type="number" min={1} inputMode="numeric" />
          <label htmlFor="gradeResp">책임 범위</label>
          <textarea id="gradeResp" ref={respRef} rows={2} />
          <label htmlFor="gradeTaskTypes">담당 업무 유형 (콤마 구분: {TASK_TYPE_HINT})</label>
          <input id="gradeTaskTypes" ref={taskTypesRef} autoComplete="off" />
          <label htmlFor="gradeMode">모델 선택 방식</label>
          <select id="gradeMode" ref={modeRef} defaultValue="PRIORITY">
            <option value="PRIORITY">PRIORITY (우선순위 순)</option>
            <option value="TASK_BASED">TASK_BASED (작업 특성 기반)</option>
            <option value="BUDGET_OPTIMIZED">BUDGET_OPTIMIZED (비용 최적화)</option>
          </select>
          <button id="btnGradeSave" className="btn primary" disabled={saving} onClick={saveGrade}>
            저장
          </button>
          </div>
        </div>
      ) : null}

      {poolGradeId && poolGrade ? (
        <div className="modal-backdrop" onClick={() => setPoolGradeId(null)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()} id="poolPanel">
          <div className="row between">
            <h2>
              모델 풀 — <span id="poolGradeName">{poolGrade.code} {poolGrade.name}</span>
            </h2>
            <button id="btnPoolClose" className="btn ghost small" onClick={() => setPoolGradeId(null)}>
              닫기
            </button>
          </div>
          <p className="hint">아래 모델을 클릭하면 풀에 추가됩니다. 풀의 순서가 우선순위입니다(위가 최우선).</p>
          <div id="poolAvailable" className="chips">
            {availableProfiles.length ? (
              availableProfiles.map((p) => (
                <span className="chip add" data-add={p.id} key={p.id} onClick={() => addToPool(p.id)}>
                  + {profileLabel(p)}
                </span>
              ))
            ) : (
              <span className="hint">추가할 모델이 없습니다. '모델' 탭에서 Profile을 먼저 만드세요.</span>
            )}
          </div>
          <h3>풀 (우선순위 순)</h3>
          <div id="poolSelected" className="list">
            {pool.length ? (
              pool.map((p, i) => {
                const prof = store.profiles.find((x) => x.id === p.profileId);
                return (
                  <div className="item" key={`${p.profileId}-${i}`}>
                    <div className="title">
                      {i + 1}. {prof ? profileLabel(prof) : p.profileId}
                    </div>
                    <div className="actions">
                      <button className="btn ghost small" disabled={i === 0} onClick={() => movePool(i, -1)}>
                        ▲
                      </button>
                      <button
                        className="btn ghost small"
                        disabled={i === pool.length - 1}
                        onClick={() => movePool(i, 1)}
                      >
                        ▼
                      </button>
                      <button className="btn danger small" onClick={() => removeFromPool(i)}>
                        제거
                      </button>
                    </div>
                  </div>
                );
              })
            ) : (
              <div className="item">
                <div className="sub">풀이 비어 있습니다.</div>
              </div>
            )}
          </div>
          <button id="btnPoolSave" className="btn primary" disabled={poolSaving} onClick={savePool}>
            풀 저장
          </button>
        </div>
      </div>
      ) : null}
    </>
  );
}
