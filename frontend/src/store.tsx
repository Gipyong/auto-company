import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { api } from "./api";
import type {
  Client,
  Ctx,
  Employee,
  Grade,
  Profile,
  ProjectLite,
  Provider,
  Session,
  TaskLite,
} from "./types";

const LS_KEYS = { ctx: "ac.ctx", projects: "ac.projects", tasks: "ac.tasks" };

function loadLS<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    return fallback;
  }
}

interface ConnState {
  text: string;
  kind: "ok" | "err";
}

export interface StoreValue {
  ctx: Ctx | null;
  employees: Employee[];
  grades: Grade[];
  gradeMap: Record<string, Grade>;
  providers: Provider[];
  profiles: Profile[];
  clients: Client[];
  projects: ProjectLite[];
  tasks: TaskLite[];
  conn: ConnState | null;
  pendingSessions: Session[];
  saveCtx: (c: Ctx) => void;
  refreshAll: (override?: Ctx) => Promise<void>;
  loadPendingSessions: (override?: Ctx) => Promise<void>;
  addProject: (p: ProjectLite) => void;
  addTask: (t: TaskLite) => void;
  gradeLabel: (gradeId?: string | null) => string;
  clientName: (id?: string | null) => string;
  projectName: (id?: string | null) => string;
  employeeName: (id?: string | null) => string;
}

const StoreCtx = createContext<StoreValue | null>(null);

type Settled<T> = T | { __error: unknown };

function isErr<T>(v: Settled<T>): v is { __error: unknown } {
  return typeof v === "object" && v !== null && "__error" in (v as object);
}

export function StoreProvider({ children }: { children: ReactNode }) {
  const [ctx, setCtx] = useState<Ctx | null>(() => loadLS<Ctx | null>(LS_KEYS.ctx, null));
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [grades, setGrades] = useState<Grade[]>([]);
  const [providers, setProviders] = useState<Provider[]>([]);
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [clients, setClients] = useState<Client[]>([]);
  const [projects, setProjects] = useState<ProjectLite[]>(() =>
    loadLS<ProjectLite[]>(LS_KEYS.projects, [])
  );
  const [tasks, setTasks] = useState<TaskLite[]>(() => loadLS<TaskLite[]>(LS_KEYS.tasks, []));
  const [conn, setConn] = useState<ConnState | null>(null);
  const [pendingSessions, setPendingSessions] = useState<Session[]>([]);

  useEffect(() => {
    localStorage.setItem(LS_KEYS.ctx, JSON.stringify(ctx));
  }, [ctx]);
  useEffect(() => {
    localStorage.setItem(LS_KEYS.projects, JSON.stringify(projects));
  }, [projects]);
  useEffect(() => {
    localStorage.setItem(LS_KEYS.tasks, JSON.stringify(tasks));
  }, [tasks]);

  const loadPendingSessions = useCallback(
    async (override?: Ctx) => {
      const c = override ?? ctx;
      if (!c) {
        setPendingSessions([]);
        return;
      }
      try {
        const items = await api<Session[]>(
          "GET",
          `/api/v1/users/${encodeURIComponent(c.userId)}/pending-sessions`
        );
        setPendingSessions(items);
      } catch {
        setPendingSessions([]);
      }
    },
    [ctx]
  );

  const refreshAll = useCallback(
    async (override?: Ctx) => {
      const c = override ?? ctx;
      if (!c) return;
      const { organizationId, userId } = c;
      const settle = async <T,>(p: Promise<T>): Promise<Settled<T>> => {
        try {
          return await p;
        } catch (e) {
          return { __error: e };
        }
      };
      const [emps, grds, provs, profs, clis] = await Promise.all([
        settle(api<Employee[]>("GET", `/api/v1/employees?userId=${encodeURIComponent(userId)}`)),
        settle(api<Grade[]>("GET", `/api/v1/grades?organizationId=${encodeURIComponent(organizationId)}`)),
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
        settle(api<Client[]>("GET", `/api/v1/clients?organizationId=${encodeURIComponent(organizationId)}`)),
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
      setProviders(isErr(provs) ? [] : provs);
      setProfiles(isErr(profs) ? [] : profs);
      setClients(isErr(clis) ? [] : clis);
      await loadPendingSessions(c);
    },
    [ctx, loadPendingSessions]
  );

  const saveCtx = useCallback((c: Ctx) => setCtx(c), []);

  const addProject = useCallback((p: ProjectLite) => setProjects((prev) => [...prev, p]), []);
  const addTask = useCallback((t: TaskLite) => setTasks((prev) => [...prev, t]), []);

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
      providers,
      profiles,
      clients,
      projects,
      tasks,
      conn,
      pendingSessions,
      saveCtx,
      refreshAll,
      loadPendingSessions,
      addProject,
      addTask,
      gradeLabel: (gradeId?: string | null) => {
        const g = gradeId ? gradeMap[gradeId] : undefined;
        return g ? `${g.code} ${g.name}` : "미배정";
      },
      clientName: (id?: string | null) => {
        const c = clients.find((x) => x.id === id);
        return c ? c.displayName : String(id || "-").slice(0, 8);
      },
      projectName: (id?: string | null) => {
        const p = projects.find((x) => x.id === id);
        return p?.name || String(id || "-").slice(0, 8);
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
      providers,
      profiles,
      clients,
      projects,
      tasks,
      conn,
      pendingSessions,
      saveCtx,
      refreshAll,
      loadPendingSessions,
      addProject,
      addTask,
    ]
  );

  return <StoreCtx.Provider value={value}>{children}</StoreCtx.Provider>;
}

export function useStore(): StoreValue {
  const v = useContext(StoreCtx);
  if (!v) throw new Error("StoreProvider missing");
  return v;
}
