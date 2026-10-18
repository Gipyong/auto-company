import type { GoalAttachmentPayload, Profile, Question } from "./types";

// §5.1 WEB-003 — 단일 조직·단일 사용자 고정 컨텍스트(조직 생성·전환 UI 없음)
export const ORG_ID = "a0000000-0000-4000-8000-000000000001";
export const USER_ID = "a0000000-0000-4000-8000-000000000002";

export const fmtDate = (s?: string | null): string =>
  s ? new Date(s).toLocaleString("ko-KR") : "-";

export const fmtNum = (v: unknown): string =>
  v == null || v === "" ? "-" : Number(v).toLocaleString("ko-KR");

export function newId(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export const profileLabel = (p: Profile): string => {
  const cost = p.inputCostPerMillion == null ? "비용 미상" : `$${p.inputCostPerMillion}/1M`;
  return `${p.name} (${cost})`;
};

export const SESSION_BADGE: Record<string, string> = {
  OPEN: "",
  WAITING_CLIENT: "hot",
  REVIEW: "amber",
  FROZEN: "green",
  CANCELLED: "red",
};

export const QUESTION_TYPE_NAME: Record<string, string> = {
  SINGLE_CHOICE: "단일 선택",
  MULTI_CHOICE: "복수 선택",
  SHORT_TEXT: "단답",
  FREE_TEXT: "자유 서술",
  NUMBER_RANGE: "수치",
};

export function readFileAsBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const r = new FileReader();
    r.onload = () => resolve(String(r.result).split(",")[1] || "");
    r.onerror = () => reject(new Error(`파일을 읽지 못했습니다: ${file.name}`));
    r.readAsDataURL(file);
  });
}

export async function toAttachment(file: File): Promise<GoalAttachmentPayload> {
  return {
    filename: file.name,
    mediaType: file.type || "application/octet-stream",
    sizeBytes: file.size,
    contentBase64: await readFileAsBase64(file),
  };
}

export interface AnswerPayload {
  questionId: string;
  skipped?: boolean;
  selectedValues?: string[] | null;
  otherText?: string | null;
  numericAnswer?: number | null;
}

export function collectAnswers(questions: Question[]): AnswerPayload[] {
  return questions
    .map((q): AnswerPayload | null => {
      const root = document.getElementById(`qform-${q.id}`);
      if (!root) return null;
      const skip = root.querySelector<HTMLInputElement>(".q-skip");
      if (skip && skip.checked) {
        return { questionId: q.id, skipped: true };
      }
      const t = q.questionType;
      const other = root.querySelector<HTMLInputElement>(".q-other");
      const otherVal = other ? other.value.trim() : "";
      if (t === "SINGLE_CHOICE") {
        const sel = root.querySelector<HTMLInputElement>("input[type=radio]:checked");
        if (sel && sel.value === "__other__") {
          return { questionId: q.id, selectedValues: [], otherText: otherVal };
        }
        return { questionId: q.id, selectedValues: sel ? [sel.value] : [], otherText: null };
      }
      if (t === "MULTI_CHOICE") {
        const vals = Array.from(root.querySelectorAll<HTMLInputElement>("input.q-opt:checked")).map(
          (c) => c.value
        );
        const otherChecked = root.querySelector<HTMLInputElement>(".q-other-check")?.checked;
        return { questionId: q.id, selectedValues: vals, otherText: otherChecked ? otherVal : null };
      }
      if (t === "NUMBER_RANGE") {
        const v = root.querySelector<HTMLInputElement>(".q-number")!.value.trim();
        return { questionId: q.id, numericAnswer: v === "" ? null : Number(v) };
      }
      return { questionId: q.id, selectedValues: null, otherText: otherVal || null };
    })
    .filter((a): a is AnswerPayload => a !== null);
}

export function firstUnansweredPrompt(questions: Question[]): Question | null {
  for (const q of questions) {
    const root = document.getElementById(`qform-${q.id}`);
    const skip = root?.querySelector<HTMLInputElement>(".q-skip");
    if (!root || (skip && skip.checked)) continue;
    const t = q.questionType;
    let ok = false;
    if (t === "SINGLE_CHOICE") {
      ok = !!root.querySelector("input[type=radio]:checked");
    } else if (t === "MULTI_CHOICE") {
      const otherChecked = root.querySelector<HTMLInputElement>(".q-other-check:checked");
      const otherText = root.querySelector<HTMLInputElement>(".q-other");
      ok = !!root.querySelector("input.q-opt:checked") || (!!otherChecked && !!otherText?.value.trim());
    } else if (t === "NUMBER_RANGE") {
      ok = root.querySelector<HTMLInputElement>(".q-number")!.value.trim() !== "";
    } else {
      ok = root.querySelector<HTMLInputElement>(".q-text")!.value.trim() !== "";
    }
    if (!ok) return q;
  }
  return null;
}

/** §17 WEB-003 — 지시 창구: 지시 원문 첫 줄로 프로젝트 이름을 만든다(첨부 단독이면 날짜 기반) */
export function deriveProjectName(goalText: string, now: Date = new Date()): string {
  const firstLine = goalText
    .split("\n")
    .map((s) => s.trim())
    .find((s) => s.length > 0);
  if (firstLine) {
    return firstLine.length > 60 ? `${firstLine.slice(0, 60)}…` : firstLine;
  }
  return `첨부 지시 ${now.toLocaleDateString("ko-KR")}`;
}
