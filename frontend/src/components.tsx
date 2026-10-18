import type { ReactNode } from "react";
import { fmtDate, SESSION_BADGE } from "./lib";
import type { EvaluationReport, RequirementItem, RequirementSet } from "./types";

export function Badge({ kind, children }: { kind?: string; children: ReactNode }) {
  return <span className={`badge${kind ? ` ${kind}` : ""}`}>{children}</span>;
}

export function StatusText({ kind, children }: { kind?: "ok" | "err"; children: ReactNode }) {
  return <div className={`status${kind ? ` ${kind}` : ""}`}>{children}</div>;
}

export function SessionStatusBadge({ status }: { status: string }) {
  return <Badge kind={SESSION_BADGE[status] || ""}>{status}</Badge>;
}

export function EvaluationReportView({ r }: { r: EvaluationReport }) {
  const avg = (v?: number | null) => (v == null ? "-" : Number(v).toFixed(2));
  const cats = r.categoryAverages || [];
  const feedback = r.recentFeedback || [];

  return (
    <>
      <h3>요약 (자동 직급 변경 없음 — 참고용)</h3>
      <div className="summary-grid">
        <div className="stat"><div className="v">{r.totalFeedbackCount}</div><div className="k">전체 피드백</div></div>
        <div className="stat"><div className="v">{r.activeFeedbackCount}</div><div className="k">활성</div></div>
        <div className="stat"><div className="v">{r.recent30dCount}</div><div className="k">최근 30일</div></div>
        <div className="stat"><div className="v">{avg(r.overallAvgScore)}</div><div className="k">전체 평균</div></div>
        <div className="stat"><div className="v">{avg(r.recent30dAvgScore)}</div><div className="k">최근 30일 평균</div></div>
        <div className="stat"><div className="v">{r.distinctRaterCount}</div><div className="k">평가자 수</div></div>
        <div className="stat"><div className="v">{r.evidenceBackedCount}</div><div className="k">근거 포함</div></div>
        <div className="stat"><div className="v">{r.grade || "-"}</div><div className="k">직급</div></div>
      </div>
      <h3>카테고리별 평균</h3>
      {cats.length ? (
        cats.map((c, i) => (
          <div className="item" key={i}>
            <div className="title">{c.category} <Badge>{avg(c.avgScore)}</Badge></div>
            <div className="sub">{c.count}건</div>
          </div>
        ))
      ) : (
        <div className="item"><div className="sub">데이터 없음</div></div>
      )}
      <h3>최근 피드백</h3>
      {feedback.length ? (
        feedback.map((f, i) => (
          <div className="item" key={i}>
            <div className="title">
              {f.category} <Badge kind="indigo">{Number(f.score).toFixed(1)}</Badge>
            </div>
            <div className="sub">
              {fmtDate(f.createdAt)} · 가중치 {Number(f.contextWeight ?? 0).toFixed(2)}
              {f.archivedAt ? " · 아카이브됨" : ""}
            </div>
            {f.comment ? <div className="sub">{f.comment}</div> : null}
            {f.feedbackSummary ? <div className="sub">요약: {f.feedbackSummary}</div> : null}
            {f.evidenceRefs && f.evidenceRefs.length ? (
              <div className="chips">
                {f.evidenceRefs.map((ev, j) => (
                  <span className="chip" key={j}>{ev}</span>
                ))}
              </div>
            ) : null}
          </div>
        ))
      ) : (
        <div className="item"><div className="sub">최근 피드백이 없습니다.</div></div>
      )}
    </>
  );
}

function RequirementGroup({
  title,
  items,
  dimAssumption,
}: {
  title: string;
  items?: RequirementItem[];
  dimAssumption?: boolean;
}) {
  const list = items || [];
  return (
    <>
      <h3>{title} ({list.length})</h3>
      {list.length ? (
        <div className="list">
          {list.map((i, idx) => (
            <div className={`item${dimAssumption && i.source === "assumption" ? " dim" : ""}`} key={idx}>
              <div className="title">
                {i.title}{" "}
                {dimAssumption && i.source === "assumption" ? <Badge kind="amber">가정</Badge> : null}
                {i.source === "client_relayed" ? <Badge kind="indigo">중계</Badge> : null}
              </div>
              <div className="sub">{i.detail || ""}</div>
              {i.evidence ? <div className="sub">근거: {i.evidence}</div> : null}
            </div>
          ))}
        </div>
      ) : (
        <div className="item"><div className="sub">없음</div></div>
      )}
    </>
  );
}

export function RequirementSetView({ r }: { r: RequirementSet }) {
  const frozen = r.status === "FROZEN";
  return (
    <>
      <h3>
        요구사항 세트 v{r.version} {frozen ? <Badge kind="green">FROZEN</Badge> : <SessionStatusBadge status={r.status} />}
      </h3>
      <RequirementGroup title="기능" items={r.content.functional} />
      <RequirementGroup title="비기능" items={r.content.nonFunctional} />
      <RequirementGroup title="제약" items={r.content.constraints} />
      <RequirementGroup title="범위 밖" items={r.content.outOfScope} />
      <RequirementGroup title="리스크·가정" items={r.content.openRisks} dimAssumption />
    </>
  );
}

export function FileChips({ files, onRemove }: { files: File[]; onRemove?: (i: number) => void }) {
  if (!files.length) return null;
  return (
    <div className="chips">
      {files.map((f, i) => (
        <span className="chip" key={i} onClick={onRemove ? () => onRemove(i) : undefined}>
          {f.name} ({Math.max(1, Math.round(f.size / 1024))}KB)
        </span>
      ))}
    </div>
  );
}

/** §17 WEB-003 — 목록 우선 UX: 빈 목록은 카드 중앙 큰 생성 CTA */
export function EmptyState({
  title,
  hint,
  action,
  onAction,
}: {
  title: string;
  hint?: string;
  action: string;
  onAction: () => void;
}) {
  return (
    <div className="empty-state">
      <div className="empty-title">{title}</div>
      {hint ? <div className="empty-hint">{hint}</div> : null}
      <button className="btn primary empty-cta" onClick={onAction}>
        {action}
      </button>
    </div>
  );
}
