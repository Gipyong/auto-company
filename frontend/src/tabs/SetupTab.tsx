import { useEffect, useRef, useState } from "react";
import { api, ApiError } from "../api";
import { useStore } from "../store";
import { useToast } from "../toast";
import { newId } from "../lib";
import { StatusText } from "../components";

export default function SetupTab({ goSetup }: { goSetup: () => void }) {
  const store = useStore();
  const toast = useToast();
  const orgRef = useRef<HTMLInputElement>(null);
  const userRef = useRef<HTMLInputElement>(null);
  const [importing, setImporting] = useState(false);
  const [result, setResult] = useState<{ kind: "ok" | "err"; text: string } | null>(null);

  useEffect(() => {
    if (orgRef.current) orgRef.current.value = store.ctx?.organizationId || "";
    if (userRef.current) userRef.current.value = store.ctx?.userId || "";
  }, []);

  const requireCtx = () => {
    if (!store.ctx) {
      toast.show("먼저 '시작' 탭에서 조직을 연결하세요.", "err");
      goSetup();
      return false;
    }
    return true;
  };

  const genIds = () => {
    const id = newId();
    if (orgRef.current) orgRef.current.value = id;
    if (userRef.current) userRef.current.value = id;
  };

  const saveCtx = async () => {
    const organizationId = orgRef.current?.value.trim() || "";
    const userId = userRef.current?.value.trim() || organizationId;
    if (!organizationId) {
      toast.show("조직 ID를 입력하세요.", "err");
      return;
    }
    const next = { organizationId, userId };
    store.saveCtx(next);
    toast.show("연결 정보를 저장했습니다.");
    await store.refreshAll(next);
  };

  const importTemplate = async () => {
    if (!requireCtx()) return;
    setImporting(true);
    try {
      const r = await api<any>("POST", "/api/v1/organizations/template/import", {
        organizationId: store.ctx!.organizationId,
        userId: store.ctx!.userId,
      });
      setResult({
        kind: "ok",
        text: `생성 완료 — 직급 ${r.grades.length}개, 직원 ${r.employees.length}명`,
      });
      toast.show("기본 회사 템플릿을 가져왔습니다.");
      await store.refreshAll();
    } catch (e) {
      const err = e as ApiError;
      setResult({
        kind: "err",
        text:
          err.status === 409
            ? `이미 템플릿이 있거나 직원이 존재합니다. (${err.message})`
            : `실패: ${err.message}`,
      });
      toast.show(err.message, "err");
    } finally {
      setImporting(false);
    }
  };

  return (
    <>
      <div className="card">
        <h2>1. 조직 연결</h2>
        <p className="hint">
          로컬 단일 사용자 기준입니다. 조직 ID와 사용자 ID를 입력하고 저장하세요. 처음이라면 "새 ID
          생성"을 누르세요.
        </p>
        <label htmlFor="setupOrgId">조직 ID (organizationId)</label>
        <input
          id="setupOrgId"
          ref={orgRef}
          autoComplete="off"
          placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
        />
        <label htmlFor="setupUserId">사용자 ID (userId)</label>
        <input
          id="setupUserId"
          ref={userRef}
          autoComplete="off"
          placeholder="비우면 조직 ID와 동일하게 사용"
        />
        <div className="row">
          <button id="btnNewIds" className="btn ghost" onClick={genIds}>
            새 ID 생성
          </button>
          <button id="btnSaveCtx" className="btn primary" onClick={saveCtx}>
            저장 &amp; 연결
          </button>
        </div>
        {store.conn ? (
          <StatusText kind={store.conn.kind}>
            <span id="connStatus">{store.conn.text}</span>
          </StatusText>
        ) : null}
      </div>

      <div className="card">
        <h2>2. 기본 회사 템플릿</h2>
        <p className="hint">
          5단계 직급(L1~L5)과 기본 8인 조직(리드/아키텍트/개발자 2명/QA/보안·DevOps/문서 담당/디자이너)을
          한 번에 생성합니다. 조직에 이미 직급이 있거나 사용자에게 이미 직원이 있으면 거부됩니다(409).
        </p>
        <button
          id="btnImportTemplate"
          className="btn primary"
          disabled={importing}
          onClick={importTemplate}
        >
          기본 회사 템플릿 가져오기
        </button>
        {result ? <StatusText kind={result.kind}>{result.text}</StatusText> : null}
      </div>
    </>
  );
}
