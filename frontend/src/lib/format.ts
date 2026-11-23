const TR = "tr-TR";

/** 120000 kuruş -> "₺1.200", 120050 -> "₺1.200,50" */
export function money(kurus: number): string {
  const whole = kurus % 100 === 0;
  return new Intl.NumberFormat(TR, {
    style: "currency",
    currency: "TRY",
    minimumFractionDigits: whole ? 0 : 2,
    maximumFractionDigits: whole ? 0 : 2,
  }).format(kurus / 100);
}

export interface DateParts {
  day: string;
  month: string;
  weekday: string;
  time: string;
}

/** Tarihler her zaman Türkiye saatiyle gösterilir; ziyaretçinin bilgisayar ayarı sonucu değiştirmez. */
export function dateParts(iso: string): DateParts {
  const d = new Date(iso);
  const opt = (o: Intl.DateTimeFormatOptions) =>
    new Intl.DateTimeFormat(TR, { timeZone: "Europe/Istanbul", ...o }).format(d);
  return {
    day: opt({ day: "numeric" }),
    month: opt({ month: "short" }).replace(".", ""),
    weekday: opt({ weekday: "long" }),
    time: opt({ hour: "2-digit", minute: "2-digit", hour12: false }),
  };
}

/** 272 saniye -> "04:32"; negatif değer 00:00 olur. */
export function clock(totalSeconds: number): string {
  const s = Math.max(0, Math.ceil(totalSeconds));
  return `${String(Math.floor(s / 60)).padStart(2, "0")}:${String(s % 60).padStart(2, "0")}`;
}

/** Metinden kararlı bir tamsayı üretir. */
export function hashOf(text: string): number {
  let h = 2166136261;
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

/** Tohumdan tekrarlanabilir sözde rastgele sayılar (0-1). */
export function seeded(seed: number): () => number {
  let s = seed || 1;
  return () => {
    s ^= s << 13;
    s ^= s >>> 17;
    s ^= s << 5;
    return ((s >>> 0) % 100000) / 100000;
  };
}
