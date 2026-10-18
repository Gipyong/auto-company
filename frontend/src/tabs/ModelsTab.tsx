import { useRef, useState } from "react";
import { api } from "../api";
import { useStore } from "../store";
import { useToast } from "../toast";
import { fmtNum } from "../lib";
import { Badge } from "../components";

export default function ModelsTab({ goSetup }: { goSetup: () => void }) {
  const store = useStore();
  const toast = useToast();
  const [provSaving, setProvSaving] = useState(false);
  const [profSaving, setProfSaving] = useState(false);

  const provNameRef = useRef<HTMLInputElement>(null);
  const provTypeRef = useRef<HTMLSelectElement>(null);
  const provBaseUrlRef = useRef<HTMLInputElement>(null);
  const provSecretRef = useRef<HTMLInputElement>(null);

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

  const requireCtx = () => {
    if (!store.ctx) {
      toast.show("먼저 '시작' 탭에서 조직을 연결하세요.", "err");
      goSetup();
      return false;
    }
    return true;
  };

  const saveProvider = async () => {
    if (!requireCtx()) return;
    const name = provNameRef.current?.value.trim() || "";
    if (!name) {
      toast.show("Provider 이름을 입력하세요.", "err");
      return;
    }
    setProvSaving(true);
    try {
      await api("POST", "/api/v1/model-providers", {
        organizationId: store.ctx!.organizationId,
        name,
        providerType: provTypeRef.current?.value,
        baseUrl: provBaseUrlRef.current?.value.trim() || null,
        secretRef: provSecretRef.current?.value.trim() || null,
        enabled: true,
      });
      if (provNameRef.current) provNameRef.current.value = "";
      if (provBaseUrlRef.current) provBaseUrlRef.current.value = "";
      if (provSecretRef.current) provSecretRef.current.value = "";
      toast.show("Provider를 추가했습니다.");
      await store.refreshAll();
    } catch (e) {
      toast.show((e as Error).message, "err");
    } finally {
      setProvSaving(false);
    }
  };

  const saveProfile = async () => {
    if (!requireCtx()) return;
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

    const body: Record<string, unknown> = { providerId, name, providerModelId, capabilities: [], enabled: true };
    const num = (el: HTMLInputElement | null) => {
      const v = el?.value.trim() ?? "";
      return v === "" ? null : Number(v);
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
    body.capabilities = (profCapsRef.current?.value || "")
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);
    body.supportsToolCalling = profToolRef.current?.checked || false;
    body.supportsJsonSchema = profJsonRef.current?.checked || false;
    body.supportsStreaming = profStreamRef.current?.checked || false;
    body.localOnly = profLocalRef.current?.checked || false;

    setProfSaving(true);
    try {
      await api("POST", "/api/v1/model-profiles", body);
      if (profNameRef.current) profNameRef.current.value = "";
      if (profModelIdRef.current) profModelIdRef.current.value = "";
      if (profCapsRef.current) profCapsRef.current.value = "";
      toast.show("Profile을 추가했습니다.");
      await store.refreshAll();
    } catch (e) {
      toast.show((e as Error).message, "err");
    } finally {
      setProfSaving(false);
    }
  };

  const providerName = (prof: { provider?: { name?: string } | null; providerId: string }) =>
    prof.provider?.name || store.providers.find((x) => x.id === prof.providerId)?.name || "-";

  return (
    <>
      <div className="card">
        <h2>Provider 추가</h2>
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
        <button id="btnProvSave" className="btn primary" disabled={provSaving} onClick={saveProvider}>
          Provider 추가
        </button>
      </div>
      <div className="list" id="providerList">
        {!store.providers.length ? (
          <div className="item">
            <div className="sub">Provider가 없습니다.</div>
          </div>
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
              </div>
            );
          })
        )}
      </div>

      <div className="card">
        <h2>모델 Profile 추가</h2>
        <label htmlFor="profProvider">Provider</label>
        <select id="profProvider" ref={profProviderRef}>
          {store.providers.length ? (
            store.providers.map((p) => (
              <option value={p.id} key={p.id}>
                {p.name}
              </option>
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
        <label className="check">
          <input id="profTool" ref={profToolRef} type="checkbox" /> tool calling 지원
        </label>
        <label className="check">
          <input id="profJson" ref={profJsonRef} type="checkbox" /> JSON schema 지원
        </label>
        <label className="check">
          <input id="profStream" ref={profStreamRef} type="checkbox" /> streaming 지원
        </label>
        <label className="check">
          <input id="profLocal" ref={profLocalRef} type="checkbox" /> local only
        </label>
        <button id="btnProfSave" className="btn primary" disabled={profSaving} onClick={saveProfile}>
          Profile 추가
        </button>
      </div>
      <div className="list" id="profileList">
        {!store.profiles.length ? (
          <div className="item">
            <div className="sub">모델 Profile이 없습니다.</div>
          </div>
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
            </div>
          ))
        )}
      </div>
    </>
  );
}
