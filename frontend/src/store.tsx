import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { api, ApiError } from "./api";
import { ORG_ID, USER_ID } from "./lib";
import type {
  Ctx,
  Employee,
  Grade,
  Profile,
  ProjectLite,
  Provider,
  Session,
  Team,
} from "./types";

// §5.1 WEB-003 — 단일 조직·단일 사용자 고정 컨텍스트(조직 연결·전환 UI 없음)
export const FIXED_CTX: Ctx = { organizationId: ORG_ID, userId: USER_ID };

interface ConnState {
  text: string;
  kind: "ok" | "err";
}

export interface StoreValue {
  ctx: Ctx;
  employees: Employee[];
  grades: Grade[];
  gradeMap: Record<string, Grade>;
  teams: Team[];
  providers: Provider[];
  profiles: Profile[];
  projects: ProjectLite[];
  conn: ConnState | null;
  pendingSessions: Session[];
  refreshAll: () => Promise<void>;
  refreshProjects: () => Promise<void>;
  loadPendingSessions: () => Promise<void>;
  gradeLabel: (gradeId?: string | null) => string;
  teamName: (id?: string | null) => string;
  employeeName: (id?: string | null) => string;
}

const StoreCtx = createContext<StoreValue | null>(null);

type Settled<T> = T | { __error: unknown };

function isErr<T>(v: Settled<T>): v is { __error: unknown } {
  return typeof v === "object" && v !== null && "__error" in (v as object);
}

export function StoreProvider({ children }: { children: ReactNode }) {
  const ctx = FIXED_CTX;
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [grades, setGrades] = useState<Grade[]>([]);
  const [teams, setTeams] = useState<Team[]>([]);
  const [providers, setProviders] = useState<Provider[]>([]);
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [projects, setProjects] = useState<ProjectLite[]>([]);
  const [conn, setConn] = useState<ConnState | null>(null);
  const [pendingSessions, setPendingSessions] = useState<Session[]>([]);

  const loadPendingSessions = useCallback(async () => {
    try {
      const items = await api<Session[]>(
        "GET",
        `/api/v1/users/${encodeURIComponent(USER_ID)}/pending-sessions`
      );
      setPendingSessions(items);
    } catch {
      setPendingSessions([]);
    }
  }, []);

  const refreshProjects = useCallback(async () => {
    try {
      const items = await api<ProjectLite[]>(
        "GET",
        `/api/v1/projects?userId=${encodeURIComponent(USER_ID)}`
      );
      setProjects(items);
    } catch {
      setProjects([]);
    }
  }, []);

  const refreshAll = useCallback(async () => {
    const { organizationId, userId } = FIXED_CTX;
    const settle = async <T,>(p: Promise<T>): Promise<Settled<T>> => {
      try {
        return await p;
      } catch (e) {
        return { __error: e };
      }
    };
    const [emps, grds, tms, provs, profs] = await Promise.all([
      settle(api<Employee[]>("GET", `/api/v1/employees?userId=${encodeURIComponent(userId)}`)),
      settle(api<Grade[]>("GET", `/api/v1/grades?organizationId=${encodeURIComponent(organizationId)}`)),
      settle(api<Team[]>("GET", `/api/v1/teams?organizationId=${encodeURIComponent(organizationId)}`)),
      settle(
        api<Provider[]>(
          "GET",
          `/api/v1/model-providers?organizationId=${encodeURIComponent(organizationId)}`
        )
      ),
      settle(
        api<Profile[]>(
          "GET",
          `/api/v1/model-profiles?organizationId=${encodeURIComponent(organizationId)}`
        )
      ),
    ]);

    if (isErr(emps)) {
      setConn({ text: `연결 실패: ${(emps.__error as Error).message}`, kind: "err" });
    } else {
      setConn({ text: `연결됨 — 직원 ${emps.length}명`, kind: "ok" });
    }

    setEmployees(
      (isErr(emps) ? [] : emps)
        .slice()
        .sort((a, b) => String(a.name).localeCompare(String(b.name), "ko"))
    );
    setGrades((isErr(grds) ? [] : grds).slice().sort((a, b) => a.ordinal - b.ordinal));
    setTeams(isErr(tms) ? [] : tms);
    setProviders(isErr(provs) ? [] : provs);
    setProfiles(isErr(profs) ? [] : profs);
    await refreshProjects();
    await loadPendingSessions();
  }, [loadPendingSessions, refreshProjects]);

  // §6.2 WEB-003 — 첫 로드 시 단일 조직+기본 템플릿 자동 구성(409=이미 구성됨 → 무시)
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        await api("POST", "/api/v1/organizations/template/import", {
          organizationId: ORG_ID,
          userId: USER_ID,
        });
      } catch (e) {
        if ((e as ApiError).status !== 409) {
          // 부트스트랩 실패는 refreshAll의 연결 상태 표시로 전달된다
        }
      }
      if (!cancelled) {
        await refreshAll();
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [refreshAll]);

  const gradeMap = useMemo(() => {
    const m: Record<string, Grade> = {};
    grades.forEach((g) => {
      m[g.id] = g;
    });
    return m;
  }, [grades]);

  const value = useMemo<StoreValue>(
    () => ({
      ctx,
      employees,
      grades,
      gradeMap,
      teams,
      providers,
      profiles,
      projects,
      conn,
      pendingSessions,
      refreshAll,
      refreshProjects,
      loadPendingSessions,
      gradeLabel: (gradeId?: string | null) => {
        const g = gradeId ? gradeMap[gradeId] : undefined;
        return g ? `${g.code} ${g.name}` : "미배정";
      },
      teamName: (id?: string | null) => {
        const t = teams.find((x) => x.id === id);
        return t ? t.name : "미배정";
      },
      employeeName: (id?: string | null) => {
        const e = employees.find((x) => x.id === id);
        return e?.name || String(id || "-").slice(0, 8);
      },
    }),
    [
      ctx,
      employees,
      grades,
      gradeMap,
      teams,
      providers,
      profiles,
      projects,
      conn,
      pendingSessions,
      refreshAll,
      refreshProjects,
      loadPendingSessions,
    ]
  );

  return <StoreCtx.Provider value={value}>{children}</StoreCtx.Provider>;
}

export function useStore(): StoreValue {
  const v = useContext(StoreCtx);
  if (!v) throw new Error("StoreProvider missing");
  return v;
}
