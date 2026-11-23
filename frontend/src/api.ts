// Backend ile konuşan tek yer. Türler backend'in döndürdüğü JSON ile birebir aynıdır.

export type SeatStatus = "AVAILABLE" | "HELD" | "SOLD";
export type Zone = "VIP" | "KAT1" | "KAT2" | "KAT3";

export interface Layout {
  pitch: { length: number; width: number };
  exponent: number;
  tiers: TierSpec[];
  blocks: BlockSpec[];
  totalSeats: number;
  note: string;
}

export interface TierSpec {
  code: string;
  label: string;
  rows: number;
  rowDepth: number;
  riseDegrees: number;
  startOffset: number;
  startHeight: number;
}

export interface BlockSpec {
  code: string;
  name: string;
  stand: string;
  tier: string;
  zone: Zone;
  priceKurus: number;
  u0: number;
  u1: number;
  seatsPerRow: number[];
  totalSeats: number;
}

export interface EventSummary {
  slug: string;
  title: string;
  category: string;
  venue: string;
  city: string;
  startsAt: string;
  description: string;
  hue: number;
  totalSeats: number;
  availableSeats: number;
  minPriceKurus: number;
}

export interface BlockAvailability {
  code: string;
  totalSeats: number;
  availableSeats: number;
}

export interface ZoneInfo {
  code: Zone;
  label: string;
  priceKurus: number;
}

export interface HeldSeat {
  id: number;
  block: string;
  blockName: string;
  row: string;
  number: number;
  zone: Zone;
  priceKurus: number;
}

export interface HoldState {
  active: boolean;
  /** Yalnızca tutma yeni açıldığında dolu gelir. */
  token: string | null;
  expiresAt: string | null;
  serverTime: string;
  seatIds: number[];
  seats: HeldSeat[];
  totalKurus: number;
}

export interface StadiumView {
  event: EventSummary;
  blocks: BlockAvailability[];
  zones: ZoneInfo[];
  hold: HoldState;
  holdSeconds: number;
  maxSeats: number;
}

export interface BlockSeat {
  id: number;
  rowIndex: number;
  row: string;
  number: number;
  status: SeatStatus;
  mine: boolean;
}

export interface BlockSeats {
  block: string;
  seats: BlockSeat[];
}

export interface TicketSeat {
  block: string;
  row: string;
  number: number;
  zone: string;
  priceKurus: number;
}

export interface Booking {
  code: string;
  eventSlug: string;
  eventTitle: string;
  category: string;
  venue: string;
  city: string;
  startsAt: string;
  hue: number;
  seats: TicketSeat[];
  totalKurus: number;
  customerName: string;
  createdAt: string;
}

/** Backend'in "problem details" yanıtı. Arayüz `code` alanına göre davranır. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly errors: Record<string, string> = {},
  ) {
    super(message);
  }
}

const BASE: string = (import.meta.env.VITE_API_URL as string | undefined) ?? "";
const TOKEN_HEADER = "X-Hold-Token";

async function request<T>(path: string, init: RequestInit = {}, token?: string | null): Promise<T> {
  const headers = new Headers(init.headers);
  if (token) headers.set(TOKEN_HEADER, token);
  let res: Response;
  try {
    res = await fetch(`${BASE}${path}`, { ...init, headers });
  } catch {
    throw new ApiError(0, "NETWORK", "Sunucuya ulaşılamıyor. Bağlantını kontrol et.");
  }
  const body: unknown = await res.json().catch(() => null);
  if (!res.ok) {
    const problem = (body ?? {}) as { code?: string; detail?: string; errors?: Record<string, string> };
    throw new ApiError(
      res.status,
      problem.code ?? "UNKNOWN",
      problem.detail ?? "Beklenmeyen bir hata oluştu.",
      problem.errors ?? {},
    );
  }
  if (body === null) {
    // 200 döndü ama JSON değil: büyük olasılıkla istek backend'e değil başka bir sunucuya gitti
    throw new ApiError(res.status, "BAD_RESPONSE", "Sunucudan beklenmeyen bir yanıt geldi.");
  }
  return body as T;
}

const enc = encodeURIComponent;

export const api = {
  layout: () => request<Layout>("/api/stadium"),
  events: () => request<EventSummary[]>("/api/events"),
  meta: () => request<{ demoCrowd: boolean }>("/api/meta"),
  stadium: (slug: string, token: string | null) => request<StadiumView>(`/api/events/${enc(slug)}`, {}, token),
  blockSeats: (slug: string, block: string, token: string | null) =>
    request<BlockSeats>(`/api/events/${enc(slug)}/blocks/${enc(block)}`, {}, token),
  addSeat: (slug: string, seatId: number, token: string | null) =>
    request<HoldState>(`/api/events/${enc(slug)}/hold/seats/${seatId}`, { method: "PUT" }, token),
  removeSeat: (slug: string, seatId: number, token: string | null) =>
    request<HoldState>(`/api/events/${enc(slug)}/hold/seats/${seatId}`, { method: "DELETE" }, token),
  releaseAll: (slug: string, token: string | null) =>
    request<HoldState>(`/api/events/${enc(slug)}/hold`, { method: "DELETE" }, token),
  confirm: (slug: string, token: string | null, customer: { name: string; email: string }) =>
    request<Booking>(
      `/api/events/${enc(slug)}/hold/confirm`,
      { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(customer) },
      token,
    ),
  booking: (code: string) => request<Booking>(`/api/bookings/${enc(code)}`),
};

/** Canlı yayın adresi: sayfa https ise wss kullanılır. */
export function liveUrl(slug: string): string {
  const base = BASE ? new URL(BASE) : new URL(window.location.href);
  const protocol = base.protocol === "https:" ? "wss:" : "ws:";
  return `${protocol}//${base.host}/ws/events/${enc(slug)}`;
}

// ---- ziyaretçinin tutma anahtarı: sayfa yenilense de seçim kaybolmasın diye tarayıcıda saklanır ----

const key = (slug: string) => `stadyum:hold:${slug}`;

export const holdToken = {
  get(slug: string): string | null {
    try {
      return localStorage.getItem(key(slug));
    } catch {
      return null;
    }
  },
  set(slug: string, token: string | null): void {
    try {
      if (token) localStorage.setItem(key(slug), token);
      else localStorage.removeItem(key(slug));
    } catch {
      /* gizli sekme: anahtar yalnızca bellekte kalır */
    }
  },
};
