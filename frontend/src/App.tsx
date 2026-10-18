import { useEffect, useState } from "react";
import { StoreProvider, useStore } from "./store";
import { ToastProvider } from "./toast";
import InstructionsTab from "./tabs/InstructionsTab";
import EmployeesTab from "./tabs/EmployeesTab";
import GradesTab from "./tabs/GradesTab";
import ModelsTab from "./tabs/ModelsTab";
import "./styles.css";

// §17 WEB-003 — 좌측 사이드바 IA: 지시 / 직원 / 직급 / 모델
const TABS = [
  { key: "instructions", label: "지시", icon: "💬" },
  { key: "employees", label: "직원", icon: "👥" },
  { key: "grades", label: "직급", icon: "🏅" },
  { key: "models", label: "모델", icon: "🧠" },
];

// v1.7 이전 해시(#setup·#goal·#work)는 지시 탭으로 정규화
const HASH_ALIASES: Record<string, string> = {
  setup: "instructions",
  goal: "instructions",
  work: "instructions",
};

function tabFromHash(): string {
  const raw = location.hash.replace("#", "");
  const hash = HASH_ALIASES[raw] || raw;
  return TABS.some((t) => t.key === hash) ? hash : "instructions";
}

function Shell() {
  const store = useStore();
  const [tab, setTab] = useState<string>(tabFromHash);
  const [drawer, setDrawer] = useState(false);

  useEffect(() => {
    const onHash = () => setTab(tabFromHash());
    window.addEventListener("hashchange", onHash);
    return () => window.removeEventListener("hashchange", onHash);
  }, []);

  const navigate = (name: string) => {
    setTab(name);
    setDrawer(false);
    if (location.hash !== `#${name}`) history.replaceState(null, "", `#${name}`);
    window.scrollTo(0, 0);
  };

  const waiting = store.pendingSessions.filter((s) => s.status === "WAITING_CLIENT").length;

  return (
    <div className="app">
      <header className="mobilebar">
        <button
          className="hamburger"
          aria-label="메뉴"
          onClick={() => setDrawer((v) => !v)}
        >
          ☰
        </button>
        <div className="brand">
          🤖 <strong>AutoCompany</strong>
        </div>
      </header>

      <aside className={`sidenav${drawer ? " open" : ""}`} id="sidenav">
        <div className="sidenav-brand">
          🤖 <strong>AutoCompany</strong>
        </div>
        <nav>
          {TABS.map((t) => (
            <button
              key={t.key}
              className={`nav-item${tab === t.key ? " active" : ""}`}
              data-tab={t.key}
              onClick={() => navigate(t.key)}
            >
              <span className="nav-icon" aria-hidden>
                {t.icon}
              </span>
              <span>{t.label}</span>
              {t.key === "instructions" && waiting > 0 ? (
                <span id="navGoalBadge" className="nav-badge">
                  {waiting}
                </span>
              ) : null}
            </button>
          ))}
        </nav>
        <div className="sidenav-foot" id="connStatus">
          {store.conn ? store.conn.text : "연결 중…"}
        </div>
      </aside>
      {drawer ? <div className="scrim" onClick={() => setDrawer(false)} /> : null}

      <main>
        <section
          className={`tabpage${tab === "instructions" ? " active" : ""}`}
          id="tab-instructions"
        >
          <InstructionsTab />
        </section>
        <section className={`tabpage${tab === "employees" ? " active" : ""}`} id="tab-employees">
          <EmployeesTab />
        </section>
        <section className={`tabpage${tab === "grades" ? " active" : ""}`} id="tab-grades">
          <GradesTab />
        </section>
        <section className={`tabpage${tab === "models" ? " active" : ""}`} id="tab-models">
          <ModelsTab />
        </section>
      </main>
    </div>
  );
}

export default function App() {
  return (
    <StoreProvider>
      <ToastProvider>
        <Shell />
      </ToastProvider>
    </StoreProvider>
  );
}
