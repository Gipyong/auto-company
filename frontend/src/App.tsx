import { useEffect, useState } from "react";
import { StoreProvider, useStore } from "./store";
import { ToastProvider } from "./toast";
import SetupTab from "./tabs/SetupTab";
import EmployeesTab from "./tabs/EmployeesTab";
import GradesTab from "./tabs/GradesTab";
import ModelsTab from "./tabs/ModelsTab";
import GoalTab from "./tabs/GoalTab";
import WorkTab from "./tabs/WorkTab";
import "./styles.css";

const TABS = [
  { key: "setup", label: "시작" },
  { key: "employees", label: "직원" },
  { key: "grades", label: "직급" },
  { key: "models", label: "모델" },
  { key: "goal", label: "고객 골" },
  { key: "work", label: "업무" },
];

function tabFromHash(): string {
  const hash = location.hash.replace("#", "");
  return TABS.some((t) => t.key === hash) ? hash : "setup";
}

function Shell() {
  const store = useStore();
  const [tab, setTab] = useState<string>(tabFromHash);

  useEffect(() => {
    const onHash = () => setTab(tabFromHash());
    window.addEventListener("hashchange", onHash);
    return () => window.removeEventListener("hashchange", onHash);
  }, []);

  useEffect(() => {
    if (store.ctx) {
      store.refreshAll();
    }
  }, []);

  const navigate = (name: string) => {
    setTab(name);
    if (location.hash !== `#${name}`) history.replaceState(null, "", `#${name}`);
    window.scrollTo(0, 0);
  };

  const waiting = store.pendingSessions.filter((s) => s.status === "WAITING_CLIENT").length;

  return (
    <>
      <header className="topbar">
        <div className="brand">
          🤖 <strong>AutoCompany</strong> <span className="brand-sub">CEO 콘솔</span>
        </div>
        <div className={`ctx-chip${store.ctx ? " on" : ""}`}>
          {store.ctx ? `조직 ${String(store.ctx.organizationId).slice(0, 8)}…` : "설정 필요"}
        </div>
      </header>

      <nav className="tabbar" id="tabbar">
        {TABS.map((t) => (
          <button
            key={t.key}
            className={`tab${tab === t.key ? " active" : ""}`}
            data-tab={t.key}
            onClick={() => navigate(t.key)}
          >
            {t.label === "고객 골" ? (
              <>
                고객 골{" "}
                <span id="goalTabBadge" className="tab-badge" hidden={waiting === 0}>
                  {waiting}
                </span>
              </>
            ) : (
              t.label
            )}
          </button>
        ))}
      </nav>

      <main>
        <section className={`tabpage${tab === "setup" ? " active" : ""}`} id="tab-setup">
          <SetupTab goSetup={() => navigate("setup")} />
        </section>
        <section className={`tabpage${tab === "employees" ? " active" : ""}`} id="tab-employees">
          <EmployeesTab goSetup={() => navigate("setup")} />
        </section>
        <section className={`tabpage${tab === "grades" ? " active" : ""}`} id="tab-grades">
          <GradesTab goSetup={() => navigate("setup")} />
        </section>
        <section className={`tabpage${tab === "models" ? " active" : ""}`} id="tab-models">
          <ModelsTab goSetup={() => navigate("setup")} />
        </section>
        <section className={`tabpage${tab === "goal" ? " active" : ""}`} id="tab-goal">
          <GoalTab goSetup={() => navigate("setup")} goWork={() => navigate("work")} />
        </section>
        <section className={`tabpage${tab === "work" ? " active" : ""}`} id="tab-work">
          <WorkTab goSetup={() => navigate("setup")} />
        </section>
      </main>
    </>
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
