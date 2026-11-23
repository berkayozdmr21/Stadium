import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from "react";

type Tone = "info" | "error";
type Notify = (message: string, tone?: Tone) => void;

interface Toast {
  id: number;
  message: string;
  tone: Tone;
}

const ToastContext = createContext<Notify>(() => undefined);

export const useToast = (): Notify => useContext(ToastContext);

/** Ekranın altında kısa süreli bildirimler. Ekran okuyucular için `aria-live` bölgesidir. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(1);

  const notify = useCallback<Notify>((message, tone = "info") => {
    const id = nextId.current++;
    setToasts((list) => {
      // Aynı ileti zaten ekrandaysa çoğaltma
      if (list.some((t) => t.message === message)) return list;
      return [...list.slice(-2), { id, message, tone }];
    });
    window.setTimeout(() => setToasts((list) => list.filter((t) => t.id !== id)), 4200);
  }, []);

  return (
    <ToastContext.Provider value={notify}>
      {children}
      <div className="toasts" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className={`toast toast-${t.tone}`}>
            {t.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
