import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from "react";

export type ToastKind = "ok" | "err";

interface ToastValue {
  show: (msg: string, kind?: ToastKind) => void;
}

const ToastCtx = createContext<ToastValue | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [msg, setMsg] = useState<string | null>(null);
  const [kind, setKind] = useState<ToastKind>("ok");
  const timer = useRef<number | null>(null);

  const show = useCallback((m: string, k: ToastKind = "ok") => {
    setMsg(m);
    setKind(k);
    if (timer.current !== null) window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => setMsg(null), 3200);
  }, []);

  return (
    <ToastCtx.Provider value={{ show }}>
      {children}
      <div id="toast" className={`toast${msg ? ` show ${kind}` : ""}`}>
        {msg}
      </div>
    </ToastCtx.Provider>
  );
}

export function useToast(): ToastValue {
  const v = useContext(ToastCtx);
  if (!v) throw new Error("ToastProvider missing");
  return v;
}
