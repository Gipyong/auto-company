import { describe, expect, it } from "vitest";
import { fmtNum, profileLabel, SESSION_BADGE, QUESTION_TYPE_NAME } from "./lib";
import type { Profile } from "./types";

describe("lib", () => {
  it("fmtNum handles null/empty and formats numbers", () => {
    expect(fmtNum(null)).toBe("-");
    expect(fmtNum("")).toBe("-");
    expect(fmtNum(1234)).toBe((1234).toLocaleString("ko-KR"));
  });

  it("profileLabel shows cost or 미상", () => {
    const base: Profile = {
      id: "p1",
      name: "gpt-4o-mini",
      providerId: "prov",
      providerModelId: "gpt-4o-mini",
      enabled: true,
    };
    expect(profileLabel(base)).toBe("gpt-4o-mini (비용 미상)");
    expect(profileLabel({ ...base, inputCostPerMillion: 0.15 })).toBe("gpt-4o-mini ($0.15/1M)");
  });

  it("session badge mapping covers all statuses", () => {
    expect(SESSION_BADGE.WAITING_CLIENT).toBe("hot");
    expect(SESSION_BADGE.FROZEN).toBe("green");
    expect(SESSION_BADGE.CANCELLED).toBe("red");
    expect(SESSION_BADGE.OPEN).toBe("");
  });

  it("question type names are mapped", () => {
    expect(QUESTION_TYPE_NAME.SINGLE_CHOICE).toBe("단일 선택");
    expect(QUESTION_TYPE_NAME.NUMBER_RANGE).toBe("수치");
  });
});
