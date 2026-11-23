import { useEffect, useRef, useState } from "react";

import { clock, money } from "../lib/format";

/** Tutar değiştiğinde eski değerden yenisine akan sayı. Hareketi azalt tercihinde doğrudan atlar. */
export function AnimatedMoney({ kurus }: { kurus: number }) {
  const [shown, setShown] = useState(kurus);
  const from = useRef(kurus);
  useEffect(() => {
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduce) {
      setShown(kurus);
      from.current = kurus;
      return;
    }
    const start = performance.now();
    const origin = from.current;
    let raf = 0;
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / 420);
      const eased = 1 - Math.pow(1 - t, 3);
      const value = origin + (kurus - origin) * eased;
      from.current = value;
      setShown(value);
      if (t < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [kurus]);
  return <span className="tnum">{money(Math.round(shown))}</span>;
}

/** Tutmanın kalan süresi. Son bir dakikada vurgulanır; süre dolunca onExpire bir kez çağrılır. */
export function Countdown({ expiresAt, onExpire }: { expiresAt: number; onExpire?: () => void }) {
  const [left, setLeft] = useState(() => (expiresAt - Date.now()) / 1000);
  const fired = useRef(false);
  useEffect(() => {
    fired.current = false;
    const update = () => {
      const s = (expiresAt - Date.now()) / 1000;
      setLeft(s);
      if (s <= 0 && !fired.current) {
        fired.current = true;
        onExpire?.();
      }
    };
    update();
    const id = window.setInterval(update, 250);
    return () => window.clearInterval(id);
  }, [expiresAt, onExpire]);
  return (
    <span className={`tnum countdown${left <= 60 ? " countdown-low" : ""}`} role="timer" aria-label="Kalan süre">
      {clock(left)}
    </span>
  );
}

/** Bilet kodundan türetilen, gerçek bir barkod standardına uymayan görsel çubuk dizisi (süs amaçlıdır). */
export function Barcode({ value }: { value: string }) {
  const bars: { x: number; w: number }[] = [];
  let x = 0;
  let h = 2166136261;
  for (let i = 0; i < 46; i++) {
    h ^= value.charCodeAt(i % value.length) + i;
    h = Math.imul(h, 16777619);
    const w = 1 + ((h >>> 11) % 3);
    if (i % 2 === 0) bars.push({ x, w });
    x += w + 1;
  }
  return (
    <svg className="barcode" viewBox={`0 0 ${x} 40`} preserveAspectRatio="none" aria-hidden="true">
      {bars.map((b, i) => (
        <rect key={i} x={b.x} y={0} width={b.w} height={40} fill="currentColor" />
      ))}
    </svg>
  );
}
