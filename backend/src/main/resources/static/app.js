/* AutoCompany CEO 콘솔
 *
 * 정적 SPA (Vanilla JS, 외부 의존성 없음).
 * 현재 구현된 API만 사용한다: TEMPLATE import, employees, grades(+pool),
 * model providers/profiles, projects, tasks, ratings, messages, ask,
 * 명확화 파이프라인(골 접수+첨부, 세션 수신함, 질문·답변·동결 — §28/§28.7).
 * 설계 문서 §17 "Web Console (초기 UI)" 참조.
 */
"use strict";

/* ==================== 공통 ==================== */

const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => Array.from(document.querySelectorAll(sel));

const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({
  "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
}[c]));

const fmtDate = (s) => (s ? new Date(s).toLocaleString("ko-KR") : "-");
const fmtNum = (v) => (v == null || v === "" ? "-" : Number(v).toLocaleString("ko-KR"));

function newId() {
  if (crypto.randomUUID) return crypto.randomUUID();
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

let toastTimer = null;
function toast(msg, kind = "ok") {
  const t = $("#toast");
  t.textContent = msg;
  t.className = `toast show ${kind}`;
  t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { t.className = "toast"; }, 3200);
}

async function api(method, path, body) {
  const opts = { method };
  if (body !== undefined) {
    opts.headers = { "Content-Type": "application/json" };
    opts.body = JSON.stringify(body);
  }
  const res = await fetch(path, opts);
  const text = await res.text();
  let data = null;
  if (text) {
    try { data = JSON.parse(text); } catch { data = text; }
  }
  if (!res.ok) {
    const detail = data && typeof data === "object" ? (data.error || data.message || data.detail) : data;
    const err = new Error(detail || `HTTP ${res.status}`);
    err.status = res.status;
    throw err;
  }
  return data;
}

function busy(btn, b) {
  if (!btn) return;
  btn.disabled = b;
}

function requireCtx() {
  if (!state.ctx) {
    toast("먼저 '시작' 탭에서 조직을 연결하세요.", "err");
    showTab("setup");
    return false;
  }
  return true;
}

/* ==================== 상태 ==================== */

const LS_KEYS = { ctx: "ac.ctx", projects: "ac.projects", tasks: "ac.tasks" };

function loadLS(key, fallback) {
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : fallback;
  } catch { return fallback; }
}

const state = {
  ctx: loadLS(LS_KEYS.ctx, null),
  employees: [],
  grades: [],
  gradeMap: {},
  providers: [],
  profiles: [],
  projects: loadLS(LS_KEYS.projects, []),
  tasks: loadLS(LS_KEYS.tasks, []),
  tab: "setup",
  selectedEmployeeId: null,
  editingGradeId: null,
  poolGradeId: null,
  pool: [], // [{ profileId }] — 풀 우선순위 순
  pendingSessions: [], // §28.5 수신함(OPEN+WAITING_CLIENT)
  sessions: [], // 프로젝트별 세션 목록
  currentSession: null,
  questions: [], // 현재 세션 질문
};

function persist() {
  localStorage.setItem(LS_KEYS.ctx, JSON.stringify(state.ctx));
  localStorage.setItem(LS_KEYS.projects, JSON.stringify(state.projects));
  localStorage.setItem(LS_KEYS.tasks, JSON.stringify(state.tasks));
}

/* ==================== 탭 ==================== */

function showTab(name) {
  state.tab = name;
  $$(".tabpage").forEach((el) => el.classList.toggle("active", el.id === `tab-${name}`));
  $$("#tabbar .tab").forEach((b) => b.classList.toggle("active", b.dataset.tab === name));
  if (location.hash !== `#${name}`) history.replaceState(null, "", `#${name}`);
  window.scrollTo(0, 0);
}

/* ==================== 새로고침 ==================== */

async function refreshAll() {
  if (!state.ctx) return;
  const { organizationId, userId } = state.ctx;
  const settle = async (p) => {
    try { return await p; } catch (e) { return { __error: e }; }
  };
  const [emps, grades, provs, profs] = await Promise.all([
    settle(api("GET", `/api/v1/employees?userId=${encodeURIComponent(userId)}`)),
    settle(api("GET", `/api/v1/grades?organizationId=${encodeURIComponent(organizationId)}`)),
    settle(api("GET", `/api/v1/model-providers?organizationId=${encodeURIComponent(organizationId)}`)),
    settle(api("GET", `/api/v1/model-profiles?organizationId=${encodeURIComponent(organizationId)}`)),
  ]);

  if (emps.__error) {
    $("#connStatus").textContent = `연결 실패: ${emps.__error.message}`;
    $("#connStatus").className = "status err";
  } else {
    $("#connStatus").textContent = `연결됨 — 직원 ${emps.length}명`;
    $("#connStatus").className = "status ok";
  }

  state.employees = (emps.__error ? [] : emps)
    .slice()
    .sort((a, b) => String(a.name).localeCompare(String(b.name), "ko"));
  state.grades = (grades.__error ? [] : grades)
    .slice()
    .sort((a, b) => a.ordinal - b.ordinal);
  state.gradeMap = {};
  state.grades.forEach((g) => { state.gradeMap[g.id] = g; });
  state.providers = provs.__error ? [] : provs;
  state.profiles = profs.__error ? [] : profs;

  renderEmployees();
  renderGrades();
  renderProviders();
  renderProfiles();
  renderSelects();
  renderProjects();
  renderTasks();
  await loadPendingSessions(); // 세션 큐 강조(§28.5)
}

/* ==================== 시작(설정) ==================== */

function updateCtxChip() {
  const chip = $("#ctxChip");
  if (state.ctx) {
    chip.textContent = `조직 ${String(state.ctx.organizationId).slice(0, 8)}…`;
    chip.classList.add("on");
  } else {
    chip.textContent = "설정 필요";
    chip.classList.remove("on");
  }
}

async function saveCtx() {
  const organizationId = $("#setupOrgId").value.trim();
  const userId = $("#setupUserId").value.trim() || organizationId;
  if (!organizationId) { toast("조직 ID를 입력하세요.", "err"); return; }
  state.ctx = { organizationId, userId };
  persist();
  updateCtxChip();
  toast("연결 정보를 저장했습니다.");
  await refreshAll();
}

async function importTemplate() {
  if (!requireCtx()) return;
  const btn = $("#btnImportTemplate");
  busy(btn, true);
  try {
    const result = await api("POST", "/api/v1/organizations/template/import", {
      organizationId: state.ctx.organizationId,
      userId: state.ctx.userId,
    });
    $("#templateResult").textContent =
      `생성 완료 — 직급 ${result.grades.length}개, 직원 ${result.employees.length}명`;
    $("#templateResult").className = "status ok";
    toast("기본 회사 템플릿을 가져왔습니다.");
    await refreshAll();
  } catch (e) {
    $("#templateResult").textContent =
      e.status === 409 ? `이미 템플릿이 있거나 직원이 존재합니다. (${e.message})` : `실패: ${e.message}`;
    $("#templateResult").className = "status err";
    toast(e.message, "err");
  } finally {
    busy(btn, false);
  }
}

/* ==================== 직원 ==================== */

function gradeLabel(gradeId) {
  const g = state.gradeMap[gradeId];
  return g ? `${g.code} ${g.name}` : "미배정";
}

function renderEmployees() {
  const list = $("#empList");
  if (!state.ctx) { list.innerHTML = `<div class="item"><div class="sub">먼저 '시작' 탭에서 조직을 연결하세요.</div></div>`; return; }
  if (!state.employees.length) {
    list.innerHTML = `<div class="item"><div class="sub">직원이 없습니다. '시작' 탭에서 기본 회사 템플릿을 가져오거나 API로 채용하세요.</div></div>`;
    return;
  }
  list.innerHTML = state.employees.map((e) => `
    <div class="item click" data-emp="${esc(e.id)}">
      <div class="title">${esc(e.name)} <span class="badge indigo">${esc(e.role)}</span></div>
      <div class="sub">직급: ${esc(gradeLabel(e.gradeId))} · 상태: ${esc(e.status)} · 완료 ${fmtNum(e.completedTasks)}/${fmtNum(e.totalTasks)}</div>
    </div>
  `).join("");
}

function openEmployee(id) {
  const e = state.employees.find((x) => x.id === id);
  if (!e) return;
  state.selectedEmployeeId = id;
  $("#empDetail").hidden = false;
  $("#empDetailTitle").textContent = `${e.name} 상세`;
  $("#empDetailBody").innerHTML = `
    <div class="sub">ID: ${esc(e.id)}</div>
    <div class="sub">역할: ${esc(e.role)} · 직급: ${esc(gradeLabel(e.gradeId))} · 상태: ${esc(e.status)}</div>
    <div class="sub">평균 평점: ${Number(e.avgRating ?? 0).toFixed(2)} · 완료 업무 ${fmtNum(e.completedTasks)}/${fmtNum(e.totalTasks)}</div>
    ${e.systemPrompt ? `<div class="sub">시스템 프롬프트: ${esc(e.systemPrompt)}</div>` : ""}
  `;
  renderSelects();
  $("#empGradeSelect").value = e.gradeId || "";
  $("#evalsBox").innerHTML = "";
  $("#empDetail").scrollIntoView({ behavior: "smooth", block: "start" });
}

async function assignGrade() {
  if (!state.selectedEmployeeId) return;
  const gradeId = $("#empGradeSelect").value || null;
  const btn = $("#btnAssignGrade");
  busy(btn, true);
  try {
    await api("PATCH", `/api/v1/employees/${state.selectedEmployeeId}/grade`, gradeId ? { gradeId } : {});
    toast(gradeId ? "직급을 배정했습니다." : "직급 배정을 해제했습니다.");
    await refreshAll();
    openEmployee(state.selectedEmployeeId);
  } catch (e) {
    toast(e.message, "err");
  } finally {
    busy(btn, false);
  }
}

async function loadEvaluations() {
  if (!state.selectedEmployeeId) return;
  const btn = $("#btnLoadEvals");
  busy(btn, true);
  try {
    const r = await api("GET", `/api/v1/employees/${state.selectedEmployeeId}/evaluations`);
    $("#evalsBox").innerHTML = renderEvaluationReport(r);
  } catch (e) {
    $("#evalsBox").innerHTML = `<div class="status err">평가 리포트 실패: ${esc(e.message)}</div>`;
  } finally {
    busy(btn, false);
  }
}

function renderEvaluationReport(r) {
  const avg = (v) => (v == null ? "-" : Number(v).toFixed(2));
  const cats = (r.categoryAverages || []).map((c) =>
    `<div class="item"><div class="title">${esc(c.category)} <span class="badge">${avg(c.avgScore)}</span></div><div class="sub">${c.count}건</div></div>`
  ).join("");
  const feedback = (r.recentFeedback || []).map((f) => `
    <div class="item">
      <div class="title">${esc(f.category)} <span class="badge indigo">${Number(f.score).toFixed(1)}</span></div>
      <div class="sub">${fmtDate(f.createdAt)} · 가중치 ${Number(f.contextWeight ?? 0).toFixed(2)}${f.archivedAt ? " · 아카이브됨" : ""}</div>
      ${f.comment ? `<div class="sub">${esc(f.comment)}</div>` : ""}
      ${f.feedbackSummary ? `<div class="sub">요약: ${esc(f.feedbackSummary)}</div>` : ""}
      ${(f.evidenceRefs && f.evidenceRefs.length) ? `<div class="chips">${f.evidenceRefs.map((ev) => `<span class="chip">${esc(ev)}</span>`).join("")}</div>` : ""}
    </div>
  `).join("") || `<div class="item"><div class="sub">최근 피드백이 없습니다.</div></div>`;

  return `
    <h3>요약 (자동 직급 변경 없음 — 참고용)</h3>
    <div class="summary-grid">
      <div class="stat"><div class="v">${r.totalFeedbackCount}</div><div class="k">전체 피드백</div></div>
      <div class="stat"><div class="v">${r.activeFeedbackCount}</div><div class="k">활성</div></div>
      <div class="stat"><div class="v">${r.recent30dCount}</div><div class="k">최근 30일</div></div>
      <div class="stat"><div class="v">${avg(r.overallAvgScore)}</div><div class="k">전체 평균</div></div>
      <div class="stat"><div class="v">${avg(r.recent30dAvgScore)}</div><div class="k">최근 30일 평균</div></div>
      <div class="stat"><div class="v">${r.distinctRaterCount}</div><div class="k">평가자 수</div></div>
      <div class="stat"><div class="v">${r.evidenceBackedCount}</div><div class="k">근거 포함</div></div>
      <div class="stat"><div class="v">${esc(r.grade || "-")}</div><div class="k">직급</div></div>
    </div>
    <h3>카테고리별 평균</h3>${cats || `<div class="item"><div class="sub">데이터 없음</div></div>`}
    <h3>최근 피드백</h3>${feedback}
  `;
}

/* ==================== 직급 ==================== */

function renderGrades() {
  const list = $("#gradeList");
  if (!state.grades.length) {
    list.innerHTML = `<div class="item"><div class="sub">직급이 없습니다. 위에서 추가하거나 '시작' 탭에서 기본 템플릿을 가져오세요.</div></div>`;
    return;
  }
  list.innerHTML = state.grades.map((g) => `
    <div class="item">
      <div class="title">${esc(g.code)} ${esc(g.name)}
        <span class="badge">ordinal ${g.ordinal}</span>
        <span class="badge">${esc(g.modelSelectionMode)}</span>
        ${g.templateDefault ? `<span class="badge indigo">템플릿</span>` : ""}
        <span class="badge ${g.status === "ACTIVE" ? "green" : ""}">${esc(g.status)}</span>
      </div>
      ${g.responsibilities ? `<div class="sub">${esc(g.responsibilities)}</div>` : ""}
      ${(g.taskTypes && g.taskTypes.length) ? `<div class="chips">${g.taskTypes.map((t) => `<span class="chip">${esc(t)}</span>`).join("")}</div>` : ""}
      <div class="actions">
        <button class="btn ghost small" data-act="pool" data-id="${esc(g.id)}">모델 풀</button>
        <button class="btn ghost small" data-act="edit" data-id="${esc(g.id)}">수정</button>
        <button class="btn danger small" data-act="del" data-id="${esc(g.id)}">삭제</button>
      </div>
    </div>
  `).join("");
}

function resetGradeForm() {
  state.editingGradeId = null;
  $("#gradeFormTitle").textContent = "직급 추가";
  $("#gradeCode").value = "";
  $("#gradeName").value = "";
  $("#gradeOrdinal").value = state.grades.length ? Math.max(...state.grades.map((g) => g.ordinal)) + 1 : 1;
  $("#gradeResp").value = "";
  $("#gradeTaskTypes").value = "";
  $("#gradeMode").value = "PRIORITY";
  $("#btnGradeCancel").hidden = true;
}

function editGrade(id) {
  const g = state.grades.find((x) => x.id === id);
  if (!g) return;
  state.editingGradeId = id;
  $("#gradeFormTitle").textContent = `직급 수정 — ${g.code} ${g.name}`;
  $("#gradeCode").value = g.code || "";
  $("#gradeName").value = g.name || "";
  $("#gradeOrdinal").value = g.ordinal ?? 1;
  $("#gradeResp").value = g.responsibilities || "";
  $("#gradeTaskTypes").value = (g.taskTypes || []).join(", ");
  $("#gradeMode").value = g.modelSelectionMode || "PRIORITY";
  $("#btnGradeCancel").hidden = false;
  $("#tab-grades").scrollIntoView({ behavior: "smooth", block: "start" });
}

async function saveGrade() {
  if (!requireCtx()) return;
  const body = {
    code: $("#gradeCode").value.trim(),
    name: $("#gradeName").value.trim(),
    ordinal: Number($("#gradeOrdinal").value || 0),
    responsibilities: $("#gradeResp").value.trim() || null,
    taskTypes: $("#gradeTaskTypes").value.split(",").map((s) => s.trim().toUpperCase()).filter(Boolean),
    modelSelectionMode: $("#gradeMode").value,
    status: "ACTIVE",
  };
  if (!body.code || !body.name || !body.ordinal) {
    toast("코드, 이름, 순서는 필수입니다.", "err");
    return;
  }
  const btn = $("#btnGradeSave");
  busy(btn, true);
  try {
    if (state.editingGradeId) {
      await api("PATCH", `/api/v1/grades/${state.editingGradeId}`, body);
      toast("직급을 수정했습니다.");
    } else {
      await api("POST", "/api/v1/grades", { ...body, organizationId: state.ctx.organizationId });
      toast("직급을 추가했습니다.");
    }
    resetGradeForm();
    await refreshAll();
  } catch (e) {
    toast(e.message, "err");
  } finally {
    busy(btn, false);
  }
}

async function deleteGrade(id) {
  const g = state.grades.find((x) => x.id === id);
  if (!g) return;
  if (!confirm(`'${g.code} ${g.name}' 직급을 삭제할까요?\n배정된 직원이 있으면 거부됩니다.`)) return;
  try {
    await api("DELETE", `/api/v1/grades/${id}`);
    toast("직급을 삭제했습니다.");
    await refreshAll();
  } catch (e) {
    toast(`삭제 실패: ${e.message}`, "err");
  }
}

/* --- 모델 풀 편집 --- */

async function openPool(gradeId) {
  const g = state.grades.find((x) => x.id === gradeId);
  if (!g) return;
  state.poolGradeId = gradeId;
  try {
    const models = await api("GET", `/api/v1/grades/${gradeId}/models`);
    state.pool = models.slice().sort((a, b) => a.priority - b.priority).map((m) => ({ profileId: m.modelProfileId }));
  } catch (e) {
    toast(e.message, "err");
    return;
  }
  $("#poolGradeName").textContent = `${g.code} ${g.name}`;
  $("#poolPanel").hidden = false;
  renderPool();
  $("#poolPanel").scrollIntoView({ behavior: "smooth", block: "start" });
}

function renderPool() {
  const inPool = new Set(state.pool.map((p) => p.profileId));
  const avail = state.profiles.filter((p) => !inPool.has(p.id));
  $("#poolAvailable").innerHTML = avail.length
    ? avail.map((p) => `<span class="chip add" data-add="${esc(p.id)}">+ ${esc(profileLabel(p))}</span>`).join("")
    : `<span class="hint">추가할 모델이 없습니다. '모델' 탭에서 Profile을 먼저 만드세요.</span>`;
  $("#poolSelected").innerHTML = state.pool.length
    ? state.pool.map((p, i) => {
        const prof = state.profiles.find((x) => x.id === p.profileId);
        return `
          <div class="item">
            <div class="title">${i + 1}. ${esc(prof ? profileLabel(prof) : p.profileId)}</div>
            <div class="actions">
              <button class="btn ghost small" data-up="${i}" ${i === 0 ? "disabled" : ""}>▲</button>
              <button class="btn ghost small" data-down="${i}" ${i === state.pool.length - 1 ? "disabled" : ""}>▼</button>
              <button class="btn danger small" data-remove="${i}">제거</button>
            </div>
          </div>`;
      }).join("")
    : `<div class="item"><div class="sub">풀이 비어 있습니다.</div></div>`;
}

function profileLabel(p) {
  const cost = p.inputCostPerMillion == null ? "비용 미상" : `$${p.inputCostPerMillion}/1M`;
  return `${p.name} (${cost})`;
}

async function savePool() {
  if (!state.poolGradeId) return;
  const btn = $("#btnPoolSave");
  busy(btn, true);
  try {
    await api("PUT", `/api/v1/grades/${state.poolGradeId}/models`,
      state.pool.map((p) => ({ modelProfileId: p.profileId })));
    toast("모델 풀을 저장했습니다.");
    $("#poolPanel").hidden = true;
    state.poolGradeId = null;
  } catch (e) {
    toast(e.message, "err");
  } finally {
    busy(btn, false);
  }
}

/* ==================== 모델 ==================== */

function renderProviders() {
  const list = $("#providerList");
  if (!state.providers.length) {
    list.innerHTML = `<div class="item"><div class="sub">Provider가 없습니다.</div></div>`;
    return;
  }
  list.innerHTML = state.providers.map((p) => {
    const health = { HEALTHY: "green", DEGRADED: "amber", UNAVAILABLE: "red" }[p.healthStatus] || "";
    return `
      <div class="item">
        <div class="title">${esc(p.name)}
          <span class="badge indigo">${esc(p.providerType)}</span>
          <span class="badge ${health}">${esc(p.healthStatus)}</span>
          <span class="badge ${p.enabled ? "green" : ""}">${p.enabled ? "enabled" : "disabled"}</span>
        </div>
        <div class="sub">${p.baseUrl ? esc(p.baseUrl) : "base URL 없음"}${p.secretRef ? ` · secretRef: ${esc(p.secretRef)}` : ""}</div>
      </div>`;
  }).join("");
}

function renderProfiles() {
  const list = $("#profileList");
  if (!state.profiles.length) {
    list.innerHTML = `<div class="item"><div class="sub">모델 Profile이 없습니다.</div></div>`;
    return;
  }
  const providerName = (prof) =>
    (prof.provider && prof.provider.name) ||
    (state.providers.find((x) => x.id === prof.providerId) || {}).name || "-";
  list.innerHTML = state.profiles.map((p) => `
    <div class="item">
      <div class="title">${esc(p.name)}
        <span class="badge">tier ${p.qualityTier == null ? "-" : p.qualityTier}</span>
        <span class="badge">${esc(p.latencyClass || "UNKNOWN")}</span>
        ${p.localOnly ? `<span class="badge">local</span>` : ""}
        <span class="badge ${p.enabled ? "green" : ""}">${p.enabled ? "enabled" : "disabled"}</span>
      </div>
      <div class="sub">${esc(providerName(p))} · ${esc(p.providerModelId)}
        · 입력 ${p.inputCostPerMillion == null ? "미상" : "$" + p.inputCostPerMillion + "/1M"}
        · 출력 ${p.outputCostPerMillion == null ? "미상" : "$" + p.outputCostPerMillion + "/1M"}
        · ctx ${fmtNum(p.contextWindow)}</div>
      ${(p.capabilities && p.capabilities.length) ? `<div class="chips">${p.capabilities.map((c) => `<span class="chip">${esc(c)}</span>`).join("")}</div>` : ""}
    </div>
  `).join("");
}

async function saveProvider() {
  if (!requireCtx()) return;
  const name = $("#provName").value.trim();
  if (!name) { toast("Provider 이름을 입력하세요.", "err"); return; }
  const btn = $("#btnProvSave");
  busy(btn, true);
  try {
    await api("POST", "/api/v1/model-providers", {
      organizationId: state.ctx.organizationId,
      name,
      providerType: $("#provType").value,
      baseUrl: $("#provBaseUrl").value.trim() || null,
      secretRef: $("#provSecretRef").value.trim() || null,
      enabled: true,
    });
    $("#provName").value = ""; $("#provBaseUrl").value = ""; $("#provSecretRef").value = "";
    toast("Provider를 추가했습니다.");
    await refreshAll();
  } catch (e) {
    toast(e.message, "err");
  } finally {
    busy(btn, false);
  }
}

async function saveProfile() {
  if (!requireCtx()) return;
  const providerId = $("#profProvider").value;
  const name = $("#profName").value.trim();
  const providerModelId = $("#profModelId").value.trim();
  if (!providerId) { toast("Provider를 먼저 만들거나 선택하세요.", "err"); return; }
  if (!name || !providerModelId) { toast("이름과 Provider 모델 ID는 필수입니다.", "err"); return; }

  const body = { providerId, name, providerModelId, capabilities: [], enabled: true };
  const num = (sel) => { const v = $(sel).value.trim(); return v === "" ? null : Number(v); };
  const tier = num("#profTier"); if (tier != null) body.qualityTier = tier;
  const inCost = num("#profInCost"); if (inCost != null) body.inputCostPerMillion = inCost;
  const outCost = num("#profOutCost"); if (outCost != null) body.outputCostPerMillion = outCost;
  const ctx = num("#profCtx"); if (ctx != null) body.contextWindow = ctx;
  const maxOut = num("#profMaxOut"); if (maxOut != null) body.maxOutputTokens = maxOut;
  if ($("#profLatency").value) body.latencyClass = $("#profLatency").value;
  body.capabilities = $("#profCaps").value.split(",").map((s) => s.trim()).filter(Boolean);
  body.supportsToolCalling = $("#profTool").checked;
  body.supportsJsonSchema = $("#profJson").checked;
  body.supportsStreaming = $("#profStream").checked;
  body.localOnly = $("#profLocal").checked;

  const btn = $("#btnProfSave");
  busy(btn, true);
  try {
    await api("POST", "/api/v1/model-profiles", body);
    $("#profName").value = ""; $("#profModelId").value = ""; $("#profCaps").value = "";
    toast("Profile을 추가했습니다.");
    await refreshAll();
  } catch (e) {
    toast(e.message, "err");
  } finally {
    busy(btn, false);
  }
}

/* ==================== 업무 ==================== */

function renderProjects() {
  const list = $("#projectList");
  list.innerHTML = state.projects.length
    ? state.projects.map((p) => `
        <div class="item">
          <div class="title">${esc(p.name)}</div>
          <div class="sub">${esc(p.description || "")} · ${fmtDate(p.createdAt)}</div>
          <div class="sub">ID: ${esc(p.id)}</div>
        </div>`).join("")
    : `<div class="item"><div class="sub">프로젝트가 없습니다. 위에서 생성하세요.</div></div>`;
}

function renderTasks() {
  const list = $("#taskList");
  list.innerHTML = state.tasks.length
    ? state.tasks.slice().reverse().map((t) => `
        <div class="item click" data-task="${esc(t.id)}">
          <div class="title">${esc(t.title)}
            <span class="badge indigo">${esc(t.taskType)}</span>
            <span class="badge">${esc(t.status)}</span>
          </div>
          <div class="sub">프로젝트: ${esc(projectName(t.projectId))} · 담당: ${esc(employeeName(t.assigneeId))} · ${fmtDate(t.createdAt)}</div>
        </div>`).join("")
    : `<div class="item"><div class="sub">생성한 업무가 없습니다.</div></div>`;
}

const projectName = (id) => (state.projects.find((p) => p.id === id) || {}).name || String(id || "-").slice(0, 8);
const employeeName = (id) => (state.employees.find((e) => e.id === id) || {}).name || String(id || "-").slice(0, 8);

async function createProject() {
  if (!requireCtx()) return;
  const name = $("#projName").value.trim();
  if (!name) { toast("프로젝트 이름을 입력하세요.", "err"); return; }
  const btn = $("#btnProjSave");
  busy(btn, true);
  try {
    const p = await api("POST", "/api/v1/projects", {
      userId: state.ctx.userId, name, description: $("#projDesc").value.trim() || null,
    });
    state.projects.push({ id: p.id, name: p.name, description: p.description, createdAt: new Date().toISOString() });
    persist();
    $("#projName").value = ""; $("#projDesc").value = "";
    toast("프로젝트를 생성했습니다.");
    renderProjects();
    renderSelects();
  } catch (e) {
    toast(e.message, "err");
  } finally {
    busy(btn, false);
  }
}

async function createTask() {
  if (!requireCtx()) return;
  const projectId = $("#taskProject").value;
  const assigneeId = $("#taskAssignee").value;
  const title = $("#taskTitle").value.trim();
  if (!projectId || !assigneeId || !title) {
    toast("프로젝트, 담당 직원, 제목은 필수입니다.", "err");
    return;
  }
  const btn = $("#btnTaskSave");
  busy(btn, true);
  try {
    const t = await api("POST", "/api/v1/tasks", {
      projectId, assigneeId,
      taskType: $("#taskType").value,
      title,
      prompt: $("#taskPrompt").value.trim() || null,
    });
    state.tasks.push({
      id: t.id, title: t.title || title, taskType: t.type || $("#taskType").value,
      status: t.status || "DRAFT", projectId, assigneeId, createdAt: new Date().toISOString(),
    });
    persist();
    $("#taskTitle").value = ""; $("#taskPrompt").value = "";
    toast("업무를 생성했습니다.");
    renderTasks();
    renderSelects();
  } catch (e) {
    toast(e.message, "err");
  } finally {
    busy(btn, false);
  }
}

async function showTaskDetail(id) {
  try {
    const t = await api("GET", `/api/v1/tasks/${id}`);
    const local = state.tasks.find((x) => x.id === id) || {};
    $("#taskDetail").innerHTML = `
      <h3>업무 상세</h3>
      <div class="item">
        <div class="title">${esc(t.title || local.title)}
          <span class="badge indigo">${esc(t.type || local.taskType || "")}</span>
          <span class="badge">${esc(t.status || local.status || "")}</span>
        </div>
        <div class="sub">${esc(t.description || "지시 없음")}</div>
        <div class="sub">ID: ${esc(t.id)} · 생성: ${fmtDate(t.createdAt)} · 시도 ${fmtNum(t.attemptCount)}/${fmtNum(t.maxAttempts)}</div>
        ${t.resultSummary ? `<div class="sub">결과: ${esc(t.resultSummary)}</div>` : ""}
      </div>`;
  } catch (e) {
    toast(e.message, "err");
  }
}

async function submitRating() {
  if (!requireCtx()) return;
  const taskId = $("#ratingTask").value;
  const raterId = $("#ratingRater").value;
  const ratedId = $("#ratingRated").value;
  if (!taskId || !raterId || !ratedId) {
    toast("업무, 평가자, 대상은 필수입니다.", "err");
    return;
  }
  const btn = $("#btnRatingSave");
  busy(btn, true);
  try {
    await api("POST", "/api/v1/ratings", {
      taskId, raterId, ratedId,
      category: $("#ratingCategory").value,
      score: Number($("#ratingScore").value || 0),
      comment: $("#ratingComment").value.trim() || null,
      evidenceRefs: $("#ratingEvidence").value.split(",").map((s) => s.trim()).filter(Boolean),
    });
    $("#ratingComment").value = ""; $("#ratingEvidence").value = "";
    toast("평가를 제출했습니다. 직원 상세에서 리포트를 확인하세요.");
  } catch (e) {
    toast(e.message, "err");
  } finally {
    busy(btn, false);
  }
}

async function sendMessage() {
  if (!requireCtx()) return;
  const projectId = $("#msgProject").value;
  const senderEmployeeId = $("#msgSender").value;
  const targetEmployeeId = $("#msgTarget").value;
  const content = $("#msgContent").value.trim();
  if (!projectId || !senderEmployeeId || !targetEmployeeId || !content) {
    toast("프로젝트, 보내는/받는 직원, 내용은 필수입니다.", "err");
    return;
  }
  const btn = $("#btnMsgSave");
  busy(btn, true);
  try {
    await api("POST", "/api/v1/messages", {
      projectId, senderEmployeeId, targetEmployeeId,
      content, messageType: $("#msgType").value,
    });
    $("#msgContent").value = "";
    toast("메시지를 전송했습니다.");
  } catch (e) {
    toast(e.message, "err");
  } finally {
    busy(btn, false);
  }
}

async function ask() {
  const question = $("#askQ").value.trim();
  if (!question) { toast("질문을 입력하세요.", "err"); return; }
  const btn = $("#btnAsk");
  busy(btn, true);
  try {
    const r = await api("POST", "/api/v1/ask", { question });
    $("#askA").innerHTML = `<strong>Q.</strong> ${esc(r.question)}\n<strong>A.</strong> ${esc(r.answer)}`;
    $("#askA").className = "status ok";
  } catch (e) {
    $("#askA").textContent = e.message;
    $("#askA").className = "status err";
  } finally {
    busy(btn, false);
  }
}

/* ==================== 고객 골 (§28 명확화 파이프라인) ==================== */

const SESSION_BADGE = {
  OPEN: "",
  WAITING_CLIENT: "hot", // 강조 — 응답 대기
  REVIEW: "amber",
  FROZEN: "green",
  CANCELLED: "red",
};
const statusBadge = (s) => `<span class="badge ${SESSION_BADGE[s] || ""}">${esc(s)}</span>`;

async function loadPendingSessions() {
  if (!state.ctx) { state.pendingSessions = []; renderInbox(); return; }
  try {
    state.pendingSessions = await api("GET",
      `/api/v1/users/${encodeURIComponent(state.ctx.userId)}/pending-sessions`);
  } catch { state.pendingSessions = []; }
  renderInbox();
}

function renderInbox() {
  const box = $("#pendingSessions");
  if (!box) return;
  const items = state.pendingSessions || [];
  const waiting = items.filter((s) => s.status === "WAITING_CLIENT");
  const tabBadge = $("#goalTabBadge");
  if (tabBadge) {
    tabBadge.textContent = String(waiting.length);
    tabBadge.hidden = waiting.length === 0;
  }
  const cnt = $("#inboxCount");
  if (cnt) { cnt.textContent = String(items.length); cnt.hidden = items.length === 0; }
  box.innerHTML = items.length
    ? items.map((s) => `
        <div class="item click ${s.status === "WAITING_CLIENT" ? "hot" : ""}" data-session="${esc(s.id)}">
          <div class="title">${esc(projectName(s.projectId))} 세션 #${s.sessionNumber} ${statusBadge(s.status)}</div>
          <div class="sub">${esc((s.goalText || "(첨부 중심 접수)").slice(0, 90))}</div>
          <div class="sub">${fmtDate(s.createdAt)}</div>
        </div>`).join("")
    : `<div class="item"><div class="sub">대기 세션이 없습니다. 아래에서 골을 접수하세요.</div></div>`;
}

function readFileAsBase64(file) {
  return new Promise((resolve, reject) => {
    const r = new FileReader();
    r.onload = () => resolve(String(r.result).split(",")[1] || "");
    r.onerror = () => reject(new Error(`파일을 읽지 못했습니다: ${file.name}`));
    r.readAsDataURL(file);
  });
}

function renderGoalFileList() {
  const files = Array.from(($("#goalFiles") || {}).files || []);
  $("#goalFileList").innerHTML = files.length
    ? files.map((f) => `<span class="chip">${esc(f.name)} (${Math.max(1, Math.round(f.size / 1024))}KB)</span>`).join("")
    : "";
}

async function submitGoal() {
  if (!requireCtx()) return;
  const projectId = $("#goalProject").value;
  const goalText = $("#goalText").value.trim();
  const files = Array.from(($("#goalFiles") || {}).files || []);
  if (!projectId) { toast("프로젝트를 먼저 생성하세요. ('업무' 탭)", "err"); showTab("work"); return; }
  if (!goalText && !files.length) {
    toast("골 텍스트 또는 첨부 중 최소 한 가지가 필요합니다. (§28.7-1)", "err");
    return;
  }
  const btn = $("#btnGoalSubmit");
  busy(btn, true);
  try {
    const attachments = [];
    for (const f of files) {
      attachments.push({
        filename: f.name,
        mediaType: f.type || "application/octet-stream",
        sizeBytes: f.size,
        contentBase64: await readFileAsBase64(f),
      });
    }
    const s = await api("POST", `/api/v1/projects/${projectId}/goal`, {
      userId: state.ctx.userId,
      goalText: goalText || null,
      attachments,
    });
    const parsed = (s.attachments || []).map((a) => `${a.filename}=${a.parseStatus}`).join(", ") || "없음";
    $("#goalResult").textContent = `세션 #${s.sessionNumber} 생성 — 첨부 ${s.attachments.length}개: ${parsed}`;
    $("#goalResult").className = "status ok";
    $("#goalText").value = "";
    $("#goalFiles").value = "";
    renderGoalFileList();
    toast("골을 접수했습니다.");
    await loadPendingSessions();
    await openSession(s.id);
  } catch (e) {
    $("#goalResult").textContent = `실패: ${e.message}`;
    $("#goalResult").className = "status err";
    toast(e.message, "err");
  } finally {
    busy(btn, false);
  }
}

async function loadSessions() {
  if (!requireCtx()) return;
  const projectId = $("#sessionProject").value;
  if (!projectId) { toast("프로젝트를 선택하세요.", "err"); return; }
  try {
    state.sessions = await api("GET", `/api/v1/projects/${projectId}/clarification-sessions`);
  } catch (e) { toast(e.message, "err"); return; }
  renderSessionList();
}

function renderSessionList() {
  const list = $("#sessionList");
  list.innerHTML = state.sessions.length
    ? state.sessions.map((s) => `
        <div class="item click" data-session="${esc(s.id)}">
          <div class="title">세션 #${s.sessionNumber} ${statusBadge(s.status)}</div>
          <div class="sub">${esc((s.goalText || "(첨부 중심 접수)").slice(0, 90))} · ${fmtDate(s.createdAt)}</div>
        </div>`).join("")
    : `<div class="item"><div class="sub">이 프로젝트의 세션이 없습니다.</div></div>`;
}

async function openSession(id) {
  try {
    state.currentSession = await api("GET", `/api/v1/clarification-sessions/${id}`);
  } catch (e) { toast(e.message, "err"); return; }
  renderSessionDetail();
}

function renderSessionDetail() {
  const s = state.currentSession;
  if (!s) { $("#sessionDetail").hidden = true; return; }
  $("#sessionDetail").hidden = false;
  $("#sessionDetailTitle").textContent = `${projectName(s.projectId)} — 세션 #${s.sessionNumber}`;
  $("#sessionDetailBody").innerHTML = `
    <div class="sub">상태 ${statusBadge(s.status)} · 생성 ${fmtDate(s.createdAt)} · 질문 상한 ${s.maxQuestions}</div>
    ${s.goalText ? `<div class="sub">골: ${esc(s.goalText)}</div>` : `<div class="sub">첨부 중심 접수 (§28.7-1)</div>`}
    ${(s.attachments || []).length
      ? `<div class="chips">${s.attachments.map((a) => {
          const mark = a.parseStatus === "PARSED" ? "✔ 파싱"
            : a.parseStatus === "IMAGE_PENDING_VISION" ? "🖼 비전 대기"
            : "⚠ 원본 보존";
          return `<span class="chip">${esc(a.filename)} · ${mark}</span>`;
        }).join("")}</div>`
      : ""}
  `;
  const editable = s.status === "WAITING_CLIENT";
  $("#btnCompose").hidden = !["OPEN", "REVIEW"].includes(s.status);
  $("#btnSubmitAnswers").hidden = !editable;
  $("#btnFreeze").hidden = s.status !== "REVIEW";
  $("#reqBox").innerHTML = "";

  if (["OPEN", "WAITING_CLIENT", "REVIEW"].includes(s.status)) {
    $("#questionsBox").innerHTML = `<div class="item"><div class="sub">질문을 불러오는 중…</div></div>`;
    api("GET", `/api/v1/clarification-sessions/${s.id}/questions`).then((qs) => {
      state.questions = qs;
      $("#questionsBox").innerHTML = qs.length
        ? renderQuestions(qs, editable)
        : `<div class="item"><div class="sub">등록된 질문이 없습니다. '질문 구성'을 누르세요.</div></div>`;
    }).catch((e) => {
      $("#questionsBox").innerHTML = e.status === 409
        ? `<div class="status err">회사 검토 체인이 진행 중입니다. 잠시 후 '질문 구성' 또는 새로 열어보세요.</div>`
        : `<div class="status err">질문 조회 실패: ${esc(e.message)}</div>`;
    });
  } else {
    state.questions = [];
    $("#questionsBox").innerHTML = "";
    if (s.status === "FROZEN") showFrozenRequirements(s);
  }
  $("#sessionDetail").scrollIntoView({ behavior: "smooth", block: "start" });
}

function renderQuestions(qs, editable) {
  const typeName = {
    SINGLE_CHOICE: "단일 선택", MULTI_CHOICE: "복수 선택",
    SHORT_TEXT: "단답", FREE_TEXT: "자유 서술", NUMBER_RANGE: "수치",
  };
  return qs.map((q) => `
    <div class="item" id="qform-${esc(q.id)}">
      <div class="title">${esc(q.prompt)}</div>
      <div class="sub">${esc(typeName[q.questionType] || q.questionType)} · ${q.displayOrder}번
        ${q.answerStatus === "ANSWERED" ? '<span class="badge green">응답함</span>' : ""}
        ${q.answerStatus === "SKIPPED" ? '<span class="badge amber">건너뜀</span>' : ""}
      </div>
      ${renderQuestionBody(q, editable)}
      ${editable ? `<label class="check"><input class="q-skip" type="checkbox"> 건너뛰기 (미확정 — 목록 가정으로 기록)</label>` : ""}
    </div>`).join("");
}

function renderOtherLine(kind, q, editable, checked, priorOther) {
  const dis = editable ? "" : "disabled";
  return `
    <label class="check"><input type="${kind}" name="q_${esc(q.id)}" class="q-other-check" value="__other__" ${dis} ${checked ? "checked" : ""}> 기타 (직접 입력)</label>
    <input class="q-other" placeholder="기타 내용" value="${esc(priorOther || "")}" ${dis}>`;
}

function renderQuestionBody(q, editable) {
  const dis = editable ? "" : "disabled";
  const priorSel = new Set(q.selectedValues || []);
  const priorOther = q.otherText;
  const otherChecked = priorOther != null && q.selectedValues == null;
  if (q.questionType === "SINGLE_CHOICE") {
    return (q.options || []).map((o) =>
      `<label class="check"><input type="radio" name="q_${esc(q.id)}" value="${esc(o.value)}" ${dis} ${priorSel.has(o.value) ? "checked" : ""}> ${esc(o.label)}</label>`
    ).join("") + (q.allowOther ? renderOtherLine("radio", q, editable, otherChecked, priorOther) : "");
  }
  if (q.questionType === "MULTI_CHOICE") {
    return (q.options || []).map((o) =>
      `<label class="check"><input type="checkbox" name="q_${esc(q.id)}" class="q-opt" value="${esc(o.value)}" ${dis} ${priorSel.has(o.value) ? "checked" : ""}> ${esc(o.label)}</label>`
    ).join("") + (q.allowOther ? renderOtherLine("checkbox", q, editable, otherChecked, priorOther) : "");
  }
  if (q.questionType === "NUMBER_RANGE") {
    return `<input type="number" class="q-number" value="${q.numericAnswer == null ? "" : esc(q.numericAnswer)}" ${dis} placeholder="숫자 입력">`;
  }
  const common = `class="q-text" ${dis} placeholder="답변"`;
  return q.questionType === "SHORT_TEXT"
    ? `<input ${common} value="${esc(priorOther || "")}">`
    : `<textarea ${common} rows="2">${esc(priorOther)}</textarea>`;
}

function collectAnswers() {
  return (state.questions || []).map((q) => {
    const root = document.getElementById(`qform-${q.id}`);
    if (!root) return null;
    if (root.querySelector(".q-skip") && root.querySelector(".q-skip").checked) {
      return { questionId: q.id, skipped: true };
    }
    const t = q.questionType;
    const other = root.querySelector(".q-other");
    const otherVal = other ? other.value.trim() : "";
    if (t === "SINGLE_CHOICE") {
      const sel = root.querySelector("input[type=radio]:checked");
      if (sel && sel.value === "__other__") return { questionId: q.id, selectedValues: [], otherText: otherVal };
      return { questionId: q.id, selectedValues: sel ? [sel.value] : [], otherText: null };
    }
    if (t === "MULTI_CHOICE") {
      const vals = Array.from(root.querySelectorAll("input.q-opt:checked")).map((c) => c.value);
      const otherChecked = root.querySelector(".q-other-check") && root.querySelector(".q-other-check").checked;
      return { questionId: q.id, selectedValues: vals, otherText: otherChecked ? otherVal : null };
    }
    if (t === "NUMBER_RANGE") {
      const v = root.querySelector(".q-number").value.trim();
      return { questionId: q.id, numericAnswer: v === "" ? null : Number(v) };
    }
    return { questionId: q.id, selectedValues: null, otherText: otherVal || null };
  }).filter(Boolean);
}

function firstUnansweredPrompt() {
  for (const q of state.questions || []) {
    const root = document.getElementById(`qform-${q.id}`);
    if (!root || (root.querySelector(".q-skip") && root.querySelector(".q-skip").checked)) continue;
    const t = q.questionType;
    let ok = false;
    if (t === "SINGLE_CHOICE") ok = !!root.querySelector("input[type=radio]:checked");
    else if (t === "MULTI_CHOICE") {
      ok = !!root.querySelector("input.q-opt:checked")
        || (!!root.querySelector(".q-other-check:checked") && !!root.querySelector(".q-other").value.trim());
    } else if (t === "NUMBER_RANGE") ok = root.querySelector(".q-number").value.trim() !== "";
    else ok = root.querySelector(".q-text").value.trim() !== "";
    if (!ok) return q;
  }
  return null;
}

async function submitAnswers() {
  const s = state.currentSession;
  if (!s || !state.questions.length) return;
  const missing = firstUnansweredPrompt();
  if (missing) { toast("모든 항목에 답하거나 건너뛰어 주세요: " + missing.prompt, "err"); return; }
  try {
    const updated = await api("POST", `/api/v1/clarification-sessions/${s.id}/answers`, {
      userId: state.ctx.userId,
      answers: collectAnswers(),
    });
    state.currentSession = updated;
    toast("답변을 제출했습니다. 동결하면 요구사항이 확정됩니다.");
    await refreshAll();
    await openSession(updated.id);
  } catch (e) {
    toast(`답변 실패: ${e.message}`, "err");
  }
}

async function composeQuestions() {
  const s = state.currentSession;
  if (!s) return;
  try {
    const qs = await api("POST", `/api/v1/clarification-sessions/${s.id}/questions`);
    toast(`질문 ${qs.length}개 구성 — 체인 통과 후 응답 요청이 수신함에 표시됩니다.`);
    await refreshAll();
    await openSession(s.id);
  } catch (e) {
    toast(e.status === 409 ? e.message : `질문 구성 실패: ${e.message}`, "err");
  }
}

async function freezeSession() {
  const s = state.currentSession;
  if (!s) return;
  if (!confirm("요구사항을 동결할까요?\n동결 이후 수정은 새 버전으로만 가능합니다.")) return;
  try {
    const r = await api("POST", `/api/v1/clarification-sessions/${s.id}/freeze`, { userId: state.ctx.userId });
    toast(`요구사항을 동결했습니다 (v${r.version}).`);
    $("#reqBox").innerHTML = renderRequirementSet(r);
    await refreshAll();
    await openSession(s.id);
  } catch (e) {
    toast(`동결 실패: ${e.message}`, "err");
  }
}

async function showFrozenRequirements(s) {
  try {
    const reqs = await api("GET", `/api/v1/projects/${s.projectId}/requirements`);
    if (reqs.length) {
      $("#reqBox").innerHTML = renderRequirementSet(reqs[reqs.length - 1]);
    }
  } catch { /* 조회 실패는 무시 */ }
}

function renderRequirementSet(r) {
  const group = (title, items, dimAssumption) => {
    const list = items || [];
    return `
      <h3>${esc(title)} (${list.length})</h3>
      ${list.length
        ? `<div class="list">${list.map((i) => `
            <div class="item ${dimAssumption && i.source === "assumption" ? "dim" : ""}">
              <div class="title">${esc(i.title)} ${dimAssumption && i.source === "assumption" ? '<span class="badge amber">가정</span>' : ""}</div>
              <div class="sub">${esc(i.detail || "")}</div>
              ${i.evidence ? `<div class="sub">근거: ${esc(i.evidence)}</div>` : ""}
            </div>`).join("")}</div>`
        : `<div class="item"><div class="sub">없음</div></div>`}`;
  };
  const frozen = r.status === "FROZEN";
  return `
    <h3>요구사항 세트 v${r.version} ${frozen ? '<span class="badge green">FROZEN</span>' : statusBadge(r.status)}</h3>
    ${group("기능", r.content.functional, false)}
    ${group("비기능", r.content.nonFunctional, false)}
    ${group("제약", r.content.constraints, false)}
    ${group("범위 밖", r.content.outOfScope, false)}
    ${group("리스크·가정", r.content.openRisks, true)}`;
}

/* ==================== 셀렉트 갱신 ==================== */

function fillSelect(sel, items, placeholder) {
  const el = $(sel);
  const prev = el.value;
  el.innerHTML = (items.length ? "" : `<option value="">${placeholder}</option>`) +
    items.map((it) => `<option value="${esc(it.value)}">${esc(it.label)}</option>`).join("");
  if (items.some((it) => it.value === prev)) el.value = prev;
}

function renderSelects() {
  const gradeItems = state.grades.map((g) => ({ value: g.id, label: `${g.code} ${g.name}` }));
  const empItems = state.employees.map((e) => ({ value: e.id, label: `${e.name} (${e.role})` }));
  const projItems = state.projects.map((p) => ({ value: p.id, label: p.name }));
  const taskItems = state.tasks.slice().reverse().map((t) => ({ value: t.id, label: t.title }));
  const profItems = state.profiles.map((p) => ({ value: p.id, label: profileLabel(p) }));

  fillSelect("#empGradeSelect", [{ value: "", label: "(미배정)" }, ...gradeItems], "(미배정)");
  fillSelect("#profProvider", state.providers.map((p) => ({ value: p.id, label: p.name })), "Provider 없음");
  fillSelect("#taskProject", projItems, "프로젝트 없음");
  fillSelect("#taskAssignee", empItems, "직원 없음");
  fillSelect("#ratingTask", taskItems, "업무 없음");
  fillSelect("#ratingRater", empItems, "직원 없음");
  fillSelect("#ratingRated", empItems, "직원 없음");
  fillSelect("#msgProject", projItems, "프로젝트 없음");
  fillSelect("#msgSender", empItems, "직원 없음");
  fillSelect("#msgTarget", empItems, "직원 없음");
  fillSelect("#goalProject", projItems, "프로젝트 없음");
  fillSelect("#sessionProject", projItems, "프로젝트 없음");
  // 프로필 풀 내보내기용 (사용처: renderPool — state.profiles 직접 사용)
  void profItems;
}

/* ==================== 이벤트 바인딩 ==================== */

function bindEvents() {
  $$("#tabbar .tab").forEach((b) => b.addEventListener("click", () => showTab(b.dataset.tab)));

  // 시작
  $("#btnNewIds").addEventListener("click", () => {
    const id = newId();
    $("#setupOrgId").value = id;
    $("#setupUserId").value = id;
  });
  $("#btnSaveCtx").addEventListener("click", saveCtx);
  $("#btnImportTemplate").addEventListener("click", importTemplate);

  // 직원
  $("#empList").addEventListener("click", (e) => {
    const item = e.target.closest("[data-emp]");
    if (item) openEmployee(item.dataset.emp);
  });
  $("#btnEmpClose").addEventListener("click", () => { $("#empDetail").hidden = true; state.selectedEmployeeId = null; });
  $("#btnAssignGrade").addEventListener("click", assignGrade);
  $("#btnLoadEvals").addEventListener("click", loadEvaluations);

  // 직급
  $("#btnGradeSave").addEventListener("click", saveGrade);
  $("#btnGradeCancel").addEventListener("click", resetGradeForm);
  $("#gradeList").addEventListener("click", (e) => {
    const btn = e.target.closest("[data-act]");
    if (!btn) return;
    const { act, id } = btn.dataset;
    if (act === "edit") editGrade(id);
    if (act === "del") deleteGrade(id);
    if (act === "pool") openPool(id);
  });
  $("#poolAvailable").addEventListener("click", (e) => {
    const chip = e.target.closest("[data-add]");
    if (!chip) return;
    state.pool.push({ profileId: chip.dataset.add });
    renderPool();
  });
  $("#poolSelected").addEventListener("click", (e) => {
    const up = e.target.closest("[data-up]");
    const down = e.target.closest("[data-down]");
    const rm = e.target.closest("[data-remove]");
    if (up) { const i = Number(up.dataset.up); [state.pool[i - 1], state.pool[i]] = [state.pool[i], state.pool[i - 1]]; renderPool(); }
    if (down) { const i = Number(down.dataset.down); [state.pool[i + 1], state.pool[i]] = [state.pool[i], state.pool[i + 1]]; renderPool(); }
    if (rm) { state.pool.splice(Number(rm.dataset.remove), 1); renderPool(); }
  });
  $("#btnPoolSave").addEventListener("click", savePool);
  $("#btnPoolClose").addEventListener("click", () => { $("#poolPanel").hidden = true; state.poolGradeId = null; });

  // 모델
  $("#btnProvSave").addEventListener("click", saveProvider);
  $("#btnProfSave").addEventListener("click", saveProfile);

  // 업무
  $("#btnProjSave").addEventListener("click", createProject);
  $("#btnTaskSave").addEventListener("click", createTask);
  $("#taskList").addEventListener("click", (e) => {
    const item = e.target.closest("[data-task]");
    if (item) showTaskDetail(item.dataset.task);
  });
  $("#btnRatingSave").addEventListener("click", submitRating);
  $("#btnMsgSave").addEventListener("click", sendMessage);
  $("#btnAsk").addEventListener("click", ask);

  // 고객 골 (§28)
  $("#btnInboxRefresh").addEventListener("click", loadPendingSessions);
  $("#btnGoalSubmit").addEventListener("click", submitGoal);
  $("#goalFiles").addEventListener("change", renderGoalFileList);
  $("#btnSessionLoad").addEventListener("click", loadSessions);
  $("#btnSessionClose").addEventListener("click", () => {
    $("#sessionDetail").hidden = true;
    state.currentSession = null;
    state.questions = [];
  });
  $("#btnCompose").addEventListener("click", composeQuestions);
  $("#btnSubmitAnswers").addEventListener("click", submitAnswers);
  $("#btnFreeze").addEventListener("click", freezeSession);
  ["#pendingSessions", "#sessionList"].forEach((sel) => {
    $(sel).addEventListener("click", (e) => {
      const item = e.target.closest("[data-session]");
      if (item) openSession(item.dataset.session);
    });
  });
}

/* ==================== 초기화 ==================== */

window.addEventListener("DOMContentLoaded", async () => {
  bindEvents();
  const hash = location.hash.replace("#", "");
  showTab(["setup", "employees", "grades", "models", "goal", "work"].includes(hash) ? hash : "setup");
  if (state.ctx) {
    $("#setupOrgId").value = state.ctx.organizationId || "";
    $("#setupUserId").value = state.ctx.userId || "";
  }
  updateCtxChip();
  resetGradeForm();
  renderProjects();
  renderTasks();
  renderGoalFileList();
  if (state.ctx) await refreshAll();
  else renderInbox();
});
