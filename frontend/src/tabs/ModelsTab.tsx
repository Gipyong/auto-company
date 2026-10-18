import { useRef, useState } from "react";
import { api, ApiError } from "../api";
import { useStore } from "../store";
import { useToast } from "../toast";
import { fmtNum } from "../lib";
import { Badge, EmptyState } from "../components";

export default function ModelsTab() {
  const store = useStore();
  const toast = useToast();

  // Provider state
  const [provSaving, setProvSaving] = useState(false);
  const [showProvForm, setShowProvForm] = useState(false);
  const [editingProviderId, setEditingProviderId] = useState<string | null>(null);
  const provNameRef = useRef<HTMLInputElement>(null);
  const provTypeRef = useRef<HTMLSelectElement>(null);
  const provBaseUrlRef = useRef<HTMLInputElement>(null);
  const provSecretRef = useRef<HTMLInputElement>(null);
  const provEnabledRef = useRef<HTMLInputElement>(null);
  const provFormRef = useRef<HTMLDivElement>(null);

  // Profile state
  const [profSaving, setProfSaving] = useState(false);
  const [showProfForm, setShowProfForm] = useState(false);
  const [editingProfileId, setEditingProfileId] = useState<string | null>(null);
  const profProviderRef = useRef<HTMLSelectElement>(null);
  const profNameRef = useRef<HTMLInputElement>(null);
  const profModelIdRef = useRef<HTMLInputElement>(null);
  const profTierRef = useRef<HTMLInputElement>(null);
  const profLatencyRef = useRef<HTMLSelectElement>(null);
  const profInCostRef = useRef<HTMLInputElement>(null);
  const profOutCostRef = useRef<HTMLInputElement>(null);
  const profCtxRef = useRef<HTMLInputElement>(null);
  const profMaxOutRef = useRef<HTMLInputElement>(null);
  const profCapsRef = useRef<HTMLInputElement>(null);
  const profToolRef = useRef<HTMLInputElement>(null);
  const profJsonRef = useRef<HTMLInputElement>(null);
  const profStreamRef = useRef<HTMLInputElement>(null);
  const profLocalRef = useRef<HTMLInputElement>(null);
  const profEnabledRef = useRef<HTMLInputElement>(null);
  const profFormRef = useRef<HTMLDivElement>(null);

  // Helpers
  const providerName = (prof: { provider?: { name?: string } | null; providerId: string }) =>
    prof.provider?.name || store.providers.find((x) => x.id === prof.providerId)?.name || "-";

  const resetProvForm = () => {
    setEditingProviderId(null);
    if (provNameRef.current) provNameRef.current.value = "";
    if (provTypeRef.current) provTypeRef.current.value = "OPENAI_COMPATIBLE";
    if (provBaseUrlRef.current) provBaseUrlRef.current.value = "";
    if (provSecretRef.current) provSecretRef.current.value = "";
    if (provEnabledRef.current) provEnabledRef.current.checked = true;
  };

  const openProvForm = (id?: string) => {
    resetProvForm();
    if (id) {
      const p = store.providers.find((x) => x.id === id);
      if (!p) return;
      setEditingProviderId(id);
      if (provNameRef.current) provNameRef.current.value = p.name || "";
      if (provTypeRef.current) provTypeRef.current.value = p.providerType || "OPENAI_COMPATIBLE";
      if (provBaseUrlRef.current) provBaseUrlRef.current.value = p.baseUrl || "";
      if (provSecretRef.current) provSecretRef.current.value = p.secretRef || "";
      if (provEnabledRef.current) provEnabledRef.current.checked = p.enabled !== false;
    }
    setShowProvForm(true);
    window.setTimeout(() => provFormRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }), 50);
  };

  const closeProvForm = () => {
    setShowProvForm(false);
    resetProvForm();
  };

  const saveProvider = async () => {
    const name = provNameRef.current?.value.trim() || "";
    if (!name) {
      toast.show("Provider 이름을 입력하세요.", "err");
      return;
    }
    setProvSaving(true);
    try {
      const body = {
        name,
        providerType: provTypeRef.current?.value,
        baseUrl: provBaseUrlRef.current?.value.trim() || null,
        secretRef: provSecretRef.current?.value.trim() || null,
        enabled: provEnabledRef.current?.checked ?? true,
      };
      if (editingProviderId) {
        await api("PATCH", `/api/v1/model-providers/${editingProviderId}`, body);
        toast.show("Provider를 수정했습니다.");
      } else {
        await api("POST", "/api/v1/model-providers", { ...body, organizationId: store.ctx.organizationId });
        toast.show("Provider를 추가했습니다.");
      }
      closeProvForm();
      await store.refreshAll();
    } catch (e) {
      toast.show((e as Error).message, "err");
    } finally {
      setProvSaving(false);
    }
  };

  const deleteProvider = async (id: string) => {
    const p = store.providers.find((x) => x.id === id);
    if (!p) return;
    if (!confirm(`'${p.name}' Provider를 삭제할까요?\n연결된 Profile이 있으면 거부될 수 있습니다.`)) return;
    try {
      await api("DELETE", `/api/v1/model-providers/${id}`);
      toast.show("Provider를 삭제했습니다.");
      await store.refreshAll();
    } catch (e) {
      const err = e as ApiError;
      toast.show(err.status === 409 ? `삭제 실패: ${err.message}` : `삭제 실패: ${(e as Error).message}`, "err");
    }
  };

  // ==================== Profile ====================

  const resetProfForm = () => {
    setEditingProfileId(null);
    if (profProviderRef.current) profProviderRef.current.value = "";
    if (profNameRef.current) profNameRef.current.value = "";
    if (profModelIdRef.current) profModelIdRef.current.value = "";
    if (profTierRef.current) profTierRef.current.value = "";
    if (profLatencyRef.current) profLatencyRef.current.value = "";
    if (profInCostRef.current) profInCostRef.current.value = "";
    if (profOutCostRef.current) profOutCostRef.current.value = "";
    if (profCtxRef.current) profCtxRef.current.value = "";
    if (profMaxOutRef.current) profMaxOutRef.current.value = "";
    if (profCapsRef.current) profCapsRef.current.value = "";
    if (profToolRef.current) profToolRef.current.checked = false;
    if (profJsonRef.current) profJsonRef.current.checked = false;
    if (profStreamRef.current) profStreamRef.current.checked = false;
    if (profLocalRef.current) profLocalRef.current.checked = false;
    if (profEnabledRef.current) profEnabledRef.current.checked = true;
  };

  const openProfForm = (id?: string) => {
    resetProfForm();
    if (id) {
      const p = store.profiles.find((x) => x.id === id);
      if (!p) return;
      setEditingProfileId(id);
      if (profProviderRef.current) profProviderRef.current.value = p.providerId || "";
      if (profNameRef.current) profNameRef.current.value = p.name || "";
      if (profModelIdRef.current) profModelIdRef.current.value = p.providerModelId || "";
      if (profTierRef.current) profTierRef.current.value = String(p.qualityTier ?? "");
      if (profLatencyRef.current) profLatencyRef.current.value = p.latencyClass || "";
      if (profInCostRef.current) profInCostRef.current.value = String(p.inputCostPerMillion ?? "");
      if (profOutCostRef.current) profOutCostRef.current.value = String(p.outputCostPerMillion ?? "");
      if (profCtxRef.current) profCtxRef.current.value = String(p.contextWindow ?? "");
      if (profMaxOutRef.current) profMaxOutRef.current.value = String(p.maxOutputTokens ?? "");
      if (profCapsRef.current) profCapsRef.current.value = (p.capabilities || []).join(", ");
      if (profToolRef.current) profToolRef.current.checked = p.supportsToolCalling ?? false;
      if (profJsonRef.current) profJsonRef.current.checked = p.supportsJsonSchema ?? false;
      if (profStreamRef.current) profStreamRef.current.checked = p.supportsStreaming ?? false;
      if (profLocalRef.current) profLocalRef.current.checked = p.localOnly ?? false;
      if (profEnabledRef.current) profEnabledRef.current.checked = p.enabled !== false;
    }
    setShowProfForm(true);
    window.setTimeout(() => profFormRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }), 50);
  };

  const closeProfForm = () => {
    setShowProfForm(false);
    resetProfForm();
  };

  const saveProfile = async () => {
    const providerId = profProviderRef.current?.value || "";
    const name = profNameRef.current?.value.trim() || "";
    const providerModelId = profModelIdRef.current?.value.trim() || "";
    if (!providerId) {
      toast.show("Provider를 먼저 만들거나 선택하세요.", "err");
      return;
    }
    if (!name || !providerModelId) {
      toast.show("이름과 Provider 모델 ID는 필수입니다.", "err");
      return;
    }

    const num = (el: HTMLInputElement | null) => {
      const v = el?.value.trim() ?? "";
      return v === "" ? null : Number(v);
    };

    const body: Record<string, unknown> = {
      providerId,
      name,
      providerModelId,
      capabilities: (profCapsRef.current?.value || "").split(",").map((s) => s.trim()).filter(Boolean),
      enabled: profEnabledRef.current?.checked ?? true,
    };
    const tier = num(profTierRef.current);
    if (tier != null) body.qualityTier = tier;
    const inCost = num(profInCostRef.current);
    if (inCost != null) body.inputCostPerMillion = inCost;
    const outCost = num(profOutCostRef.current);
    if (outCost != null) body.outputCostPerMillion = outCost;
    const ctxSize = num(profCtxRef.current);
    if (ctxSize != null) body.contextWindow = ctxSize;
    const maxOut = num(profMaxOutRef.current);
    if (maxOut != null) body.maxOutputTokens = maxOut;
    if (profLatencyRef.current?.value) body.latencyClass = profLatencyRef.current.value;
    body.supportsToolCalling = profToolRef.current?.checked ?? false;
    body.supportsJsonSchema = profJsonRef.current?.checked ?? false;
    body.supportsStreaming = profStreamRef.current?.checked ?? false;
    body.localOnly = profLocalRef.current?.checked ?? false;

    setProfSaving(true);
    try {
      if (editingProfileId) {
        await api("PATCH", `/api/v1/model-profiles/${editingProfileId}`, body);
        toast.show("Profile을 수정했습니다.");
      } else {
        await api("POST", "/api/v1/model-profiles", body);
        toast.show("Profile을 추가했습니다.");
      }
      closeProfForm();
      await store.refreshAll();
    } catch (e) {
      toast.show((e as Error).message, "err");
    } finally {
      setProfSaving(false);
    }
  };

  const deleteProfile = async (id: string) => {
    const p = store.profiles.find((x) => x.id === id);
    if (!p) return;
    if (!confirm(`'${p.name}' Profile을 삭제할까요?\n직급 모델 풀에서 사용 중이면 거부될 수 있습니다.`)) return;
    try {
      await api("DELETE", `/api/v1/model-profiles/${id}`);
      toast.show("Profile을 삭제했습니다.");
      await store.refreshAll();
    } catch (e) {
      const err = e as ApiError;
      toast.show(err.status === 409 ? `삭제 실패: ${err.message}` : `삭제 실패: ${(e as Error).message}`, "err");
    }
  };

  return (
    <>
      {/* ==================== Provider ==================== */}
      <div className="card">
        <div className="row between">
          <h2>Provider <Badge>{store.providers.length}</Badge></h2>
          {store.providers.length ? (
            <button id="btnProvToggle" className="btn ghost small" onClick={() => openProvForm()}>
              {showProvForm ? "닫기" : "+ Provider 추가"}
            </button>
          ) : null}
        </div>
        <div className="list" id="providerList">
          {!store.providers.length ? (
            showProvForm ? null : (
              <EmptyState
                title="아직 Provider가 없습니다"
                hint="모델 Provider(OpenAI 호환·Anthropic·Ollama 등)를 추가하면 Profile과 직급 모델 풀을 구성할 수 있습니다."
                action="첫 Provider 추가"
                onAction={() => openProvForm()}
              />
            )
          ) : (
            store.providers.map((p) => {
              const health =
                ({ HEALTHY: "green", DEGRADED: "amber", UNAVAILABLE: "red" } as Record<string, string>)[
                  p.healthStatus
                ] || "";
              return (
                <div className="item" key={p.id}>
                  <div className="title">
                    {p.name} <Badge kind="indigo">{p.providerType}</Badge>{" "}
                    <Badge kind={health}>{p.healthStatus}</Badge>{" "}
                    <Badge kind={p.enabled ? "green" : ""}>{p.enabled ? "enabled" : "disabled"}</Badge>
                  </div>
                  <div className="sub">
                    {p.baseUrl ? p.baseUrl : "base URL 없음"}
                    {p.secretRef ? ` · secretRef: ${p.secretRef}` : ""}
                  </div>
                  <div className="actions">
                    <button
                      className="btn ghost small"
                      data-act="edit"
                      data-id={p.id}
                      onClick={() => openProvForm(p.id)}
                    >
                      수정
                    </button>
                    <button
                      className="btn danger small"
                      data-act="del"
                      data-id={p.id}
                      onClick={() => deleteProvider(p.id)}
                    >
                      삭제
                    </button>
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>

      {showProvForm ? (
        <div className="card" ref={provFormRef}>
          <div className="row between">
            <h2>{editingProviderId ? "Provider 수정" : "Provider 추가"}</h2>
            <button className="btn ghost small" onClick={closeProvForm}>취소</button>
          </div>
          <label htmlFor="provName">이름</label>
          <input id="provName" ref={provNameRef} autoComplete="off" placeholder="예: OpenAI, 로컬 Ollama" />
          <label htmlFor="provType">유형</label>
          <select id="provType" ref={provTypeRef} defaultValue="OPENAI_COMPATIBLE">
            <option value="OPENAI_COMPATIBLE">OPENAI_COMPATIBLE</option>
            <option value="ANTHROPIC">ANTHROPIC</option>
            <option value="OLLAMA">OLLAMA</option>
            <option value="CUSTOM">CUSTOM</option>
          </select>
          <label htmlFor="provBaseUrl">Base URL (선택)</label>
          <input id="provBaseUrl" ref={provBaseUrlRef} autoComplete="off" placeholder="http://localhost:11434" />
          <label htmlFor="provSecretRef">Secret 참조 (선택 — 값이 아니라 환경변수/secret 이름만)</label>
          <input id="provSecretRef" ref={provSecretRef} autoComplete="off" placeholder="OPENAI_API_KEY" />
          <label className="check">
            <input id="provEnabled" ref={provEnabledRef} type="checkbox" defaultChecked={true} /> 활성화
          </label>
          <button id="btnProvSave" className="btn primary" disabled={provSaving} onClick={saveProvider}>
            {editingProviderId ? "수정" : "추가"}
          </button>
        </div>
      ) : null}

      {/* ==================== Profile ==================== */}
      <div className="card">
        <div className="row between">
          <h2>모델 Profile <Badge>{store.profiles.length}</Badge></h2>
          {store.profiles.length ? (
            <button id="btnProfToggle" className="btn ghost small" onClick={() => openProfForm()}>
              {showProfForm ? "닫기" : "+ Profile 추가"}
            </button>
          ) : null}
        </div>
        <div className="list" id="profileList">
          {!store.profiles.length ? (
            showProfForm ? null : (
              <EmptyState
                title="아직 모델 Profile이 없습니다"
                hint="Profile은 실제 모델 ID·비용·능력 메타데이터입니다. Provider를 먼저 만든 뒤 추가하세요."
                action="첫 Profile 추가"
                onAction={store.providers.length ? () => openProfForm() : () => openProvForm()}
              />
            )
          ) : (
            store.profiles.map((p) => (
              <div className="item" key={p.id}>
                <div className="title">
                  {p.name} <Badge>tier {p.qualityTier == null ? "-" : p.qualityTier}</Badge>{" "}
                  <Badge>{p.latencyClass || "UNKNOWN"}</Badge>
                  {p.localOnly ? <Badge>local</Badge> : null}
                  <Badge kind={p.enabled ? "green" : ""}>{p.enabled ? "enabled" : "disabled"}</Badge>
                </div>
                <div className="sub">
                  {providerName(p)} · {p.providerModelId} · 입력{" "}
                  {p.inputCostPerMillion == null ? "미상" : "$" + p.inputCostPerMillion + "/1M"} · 출력{" "}
                  {p.outputCostPerMillion == null ? "미상" : "$" + p.outputCostPerMillion + "/1M"} · ctx{" "}
                  {fmtNum(p.contextWindow)}
                </div>
                {p.capabilities && p.capabilities.length ? (
                  <div className="chips">
                    {p.capabilities.map((c) => (
                      <span className="chip" key={c}>{c}</span>
                    ))}
                  </div>
                ) : null}
                <div className="actions">
                  <button
                    className="btn ghost small"
                    data-act="edit"
                    data-id={p.id}
                    onClick={() => openProfForm(p.id)}
                  >
                    수정
                  </button>
                  <button
                    className="btn danger small"
                    data-act="del"
                    data-id={p.id}
                    onClick={() => deleteProfile(p.id)}
                  >
                    삭제
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      </div>

      {/* ==================== Provider Form ==================== */}
      {showProvForm ? (
        <div className="card" ref={provFormRef}>
          <div className="row between">
            <h2>{editingProviderId ? "Provider 수정" : "Provider 추가"}</h2>
            <button className="btn ghost small" onClick={closeProvForm}>취소</button>
          </div>
          <label htmlFor="provName">이름</label>
          <input id="provName" ref={provNameRef} autoComplete="off" placeholder="예: OpenAI, 로컬 Ollama" />
          <label htmlFor="provType">유형</label>
          <select id="provType" ref={provTypeRef} defaultValue="OPENAI_COMPATIBLE">
            <option value="OPENAI_COMPATIBLE">OPENAI_COMPATIBLE</option>
            <option value="ANTHROPIC">ANTHROPIC</option>
            <option value="OLLAMA">OLLAMA</option>
            <option value="CUSTOM">CUSTOM</option>
          </select>
          <label htmlFor="provBaseUrl">Base URL (선택)</label>
          <input id="provBaseUrl" ref={provBaseUrlRef} autoComplete="off" placeholder="http://localhost:11434" />
          <label htmlFor="provSecretRef">Secret 참조 (선택 — 값이 아니라 환경변수/secret 이름만)</label>
          <input id="provSecretRef" ref={provSecretRef} autoComplete="off" placeholder="OPENAI_API_KEY" />
          <label className="check">
            <input id="provEnabled" ref={provEnabledRef} type="checkbox" defaultChecked={true} /> 활성화
          </label>
          <button id="btnProvSave" className="btn primary" disabled={provSaving} onClick={saveProvider}>
            {editingProviderId ? "수정" : "추가"}
          </button>
        </div>
      ) : null}

      {/* ==================== Profile Form ==================== */}
      {showProfForm ? (
        <div className="card" ref={profFormRef}>
          <div className="row between">
            <h2>{editingProfileId ? "모델 Profile 수정" : "모델 Profile 추가"}</h2>
            <button className="btn ghost small" onClick={closeProfForm}>취소</button>
          </div>
          <label htmlFor="profProvider">Provider</label>
          <select id="profProvider" ref={profProviderRef}>
            {store.providers.length ? (
              store.providers.map((p) => (
                <option value={p.id} key={p.id}>{p.name}</option>
              ))
            ) : (
              <option value="">Provider 없음</option>
            )}
          </select>
          <label htmlFor="profName">이름</label>
          <input id="profName" ref={profNameRef} autoComplete="off" placeholder="예: gpt-4o-mini" />
          <label htmlFor="profModelId">Provider 모델 ID (필수)</label>
          <input id="profModelId" ref={profModelIdRef} autoComplete="off" placeholder="gpt-4o-mini" />
          <div className="row2">
            <div>
              <label htmlFor="profTier">Quality Tier (0~3)</label>
              <input id="profTier" ref={profTierRef} type="number" min={0} max={3} inputMode="numeric" />
            </div>
            <div>
              <label htmlFor="profLatency">Latency</label>
              <select id="profLatency" ref={profLatencyRef} defaultValue="">
                <option value="">UNKNOWN</option>
                <option value="LOW">LOW</option>
                <option value="MEDIUM">MEDIUM</option>
                <option value="HIGH">HIGH</option>
              </select>
            </div>
          </div>
          <div className="row2">
            <div>
              <label htmlFor="profInCost">입력 비용 ($/1M, 비우면 미상)</label>
              <input id="profInCost" ref={profInCostRef} type="number" step="0.01" inputMode="decimal" />
            </div>
            <div>
              <label htmlFor="profOutCost">출력 비용 ($/1M, 비우면 미상)</label>
              <input id="profOutCost" ref={profOutCostRef} type="number" step="0.01" inputMode="decimal" />
            </div>
          </div>
          <div className="row2">
            <div>
              <label htmlFor="profCtx">Context window</label>
              <input id="profCtx" ref={profCtxRef} type="number" inputMode="numeric" />
            </div>
            <div>
              <label htmlFor="profMaxOut">Max output tokens</label>
              <input id="profMaxOut" ref={profMaxOutRef} type="number" inputMode="numeric" />
            </div>
          </div>
          <label htmlFor="profCaps">Capabilities (콤마: code, reasoning, json_mode, tool_calling)</label>
          <input id="profCaps" ref={profCapsRef} autoComplete="off" />
          <label className="check"><input id="profTool" ref={profToolRef} type="checkbox" /> tool calling 지원</label>
          <label className="check"><input id="profJson" ref={profJsonRef} type="checkbox" /> JSON schema 지원</label>
          <label className="check"><input id="profStream" ref={profStreamRef} type="checkbox" /> streaming 지원</label>
          <label className="check"><input id="profLocal" ref={profLocalRef} type="checkbox" /> local only</label>
          <label className="check"><input id="profEnabled" ref={profEnabledRef} type="checkbox" defaultChecked={true} /> 활성화</label>
          <button id="btnProfSave" className="btn primary" disabled={profSaving} onClick={saveProfile}>
            {editingProfileId ? "수정" : "추가"}
          </button>
        </div>
      ) : null}
    </>
  );
}