import { useSyncExternalStore, type AnchorHTMLAttributes, type MouseEvent } from "react";
import { flushSync } from "react-dom";

/**
 * Üç sayfalık uygulama için küçük bir yönlendirici. Hazır bir kütüphane yerine elle yazılmasının
 * nedeni: sayfa geçişini tarayıcının View Transitions API'siyle sarmak. Böylece afiş, listeden
 * etkinlik sayfasına geçerken kesilmeden büyüyerek yerine oturur.
 */

const listeners = new Set<() => void>();
const notify = () => listeners.forEach((l) => l());

function withTransition(update: () => void): void {
  const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (reduceMotion || typeof document.startViewTransition !== "function") {
    update();
    return;
  }
  // Tarayıcı eski ekranın görüntüsünü alır, update() içinde DOM eşzamanlı değişir, sonra ikisi arasında canlandırır.
  document.startViewTransition(update);
}

if (typeof window !== "undefined") {
  window.addEventListener("popstate", () => withTransition(() => flushSync(notify)));
}

export function navigate(to: string, options: { replace?: boolean } = {}): void {
  if (to === window.location.pathname) return;
  withTransition(() => {
    if (options.replace) window.history.replaceState(null, "", to);
    else window.history.pushState(null, "", to);
    flushSync(notify);
    window.scrollTo(0, 0);
  });
}

export function usePath(): string {
  return useSyncExternalStore(
    (onChange) => {
      listeners.add(onChange);
      return () => listeners.delete(onChange);
    },
    () => window.location.pathname,
  );
}

interface LinkProps extends Omit<AnchorHTMLAttributes<HTMLAnchorElement>, "href"> {
  to: string;
}

/** Gerçek bir bağlantıdır (yeni sekmede açılabilir, sağ tık çalışır); düz tıklamada sayfa yenilenmeden geçer. */
export function Link({ to, onClick, ...rest }: LinkProps) {
  function handle(e: MouseEvent<HTMLAnchorElement>) {
    onClick?.(e);
    if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
    e.preventDefault();
    navigate(to);
  }
  return <a href={to} onClick={handle} {...rest} />;
}
