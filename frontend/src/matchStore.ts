import {
  api,
  ApiError,
  holdToken,
  liveUrl,
  type BlockSeat,
  type Booking,
  type HeldSeat,
  type HoldState,
  type Layout,
  type SeatStatus,
  type StadiumView,
} from "./api";
import { indexOf, localIndex, type BlockIndex } from "./lib/seatIndex";
import { SEAT_AVAILABLE, SEAT_HELD, SEAT_MINE, SEAT_SOLD } from "./three/StadiumScene";

export type LiveStatus = "connecting" | "open" | "closed";

export interface LocalHold {
  active: boolean;
  /** İstemci saatine göre bitiş anı (ms). Sunucu ile saat farkı hesaba katılmıştır. */
  expiresAt: number;
  seats: HeldSeat[];
  totalKurus: number;
}

export interface Snapshot {
  phase: "loading" | "ready" | "error";
  error: string | null;
  stadium: StadiumView | null;
  /** Blok koduna göre boş koltuk sayısı. */
  avail: Readonly<Record<string, { available: number; total: number }>>;
  hold: LocalHold;
  viewers: number;
  live: LiveStatus;
  /** Tutma, ziyaretçi istemeden bitti (süre doldu). Arayüz her artışta bir kez bildirim gösterir. */
  lostHold: number;
  loadedBlocks: ReadonlySet<string>;
  pending: ReadonlySet<number>;
}

/** Sahne ve arayüzün dinlediği olaylar. */
export type StoreEvent =
  | { type: "avail" }
  | { type: "block"; code: string; states: Uint8Array }
  | { type: "seat"; code: string; local: number; state: number }
  | { type: "reject"; code: string; local: number };

interface BlockData {
  index: BlockIndex;
  ids: number[];
  states: Uint8Array;
}

const NO_HOLD: LocalHold = { active: false, expiresAt: 0, seats: [], totalKurus: 0 };

const stateOf = (status: SeatStatus, mine: boolean): number =>
  mine ? SEAT_MINE : status === "AVAILABLE" ? SEAT_AVAILABLE : status === "HELD" ? SEAT_HELD : SEAT_SOLD;

/**
 * Bir maçın canlı durumu: blok doluluğu, açılmış blokların koltukları, ziyaretçinin tutması ve WebSocket.
 * React'ten bağımsızdır; arayüz {@link subscribe} ve {@link getSnapshot} ile (useSyncExternalStore) okur,
 * 3B sahne ise {@link onEvent} ile tek tek koltuk değişikliklerini alır.
 */
export class MatchStore {
  private snap: Snapshot = {
    phase: "loading", error: null, stadium: null, avail: {}, hold: NO_HOLD, viewers: 0, live: "connecting",
    lostHold: 0, loadedBlocks: new Set(), pending: new Set(),
  };
  private readonly listeners = new Set<() => void>();
  private readonly eventListeners = new Set<(e: StoreEvent) => void>();
  private readonly blocks = new Map<string, BlockData>();
  private readonly seatIndex = new Map<number, { code: string; local: number }>();
  private readonly blockIndexes = new Map<string, BlockIndex>();

  private socket: WebSocket | null = null;
  private reconnectTimer: number | undefined;
  private summaryTimer: number | undefined;
  private attempt = 0;
  private everOpened = false;
  private closed = false;
  private nonce = 1;
  private confirming = false;
  /** Yerel tutma her değiştiğinde artar; yolda olan eski özet yanıtlarını ayıklamak için. */
  private holdVersion = 0;
  private maxSeats = 6;

  constructor(
    readonly slug: string,
    layout: Layout,
    private readonly notify: (message: string, tone?: "info" | "error") => void,
  ) {
    for (const b of layout.blocks) this.blockIndexes.set(b.code, indexOf(b));
  }

  // ---- React bağlantısı ----

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  getSnapshot = (): Snapshot => this.snap;

  onEvent(cb: (e: StoreEvent) => void): () => void {
    this.eventListeners.add(cb);
    return () => this.eventListeners.delete(cb);
  }

  private set(patch: Partial<Snapshot>): void {
    this.snap = { ...this.snap, ...patch };
    this.listeners.forEach((l) => l());
  }

  private emit(e: StoreEvent): void {
    this.eventListeners.forEach((cb) => cb(e));
  }

  get maxSeatsAllowed(): number {
    return this.maxSeats;
  }

  states(code: string): Uint8Array | null {
    return this.blocks.get(code)?.states ?? null;
  }

  seatIdAt(code: string, rowIndex: number, number: number): number | null {
    const b = this.blocks.get(code);
    const idx = this.blockIndexes.get(code);
    if (!b || !idx) return null;
    return b.ids[localIndex(idx, rowIndex, number)] ?? null;
  }

  // ---- yaşam döngüsü ----

  start(): void {
    void this.refreshSummary(true);
    this.connect();
  }

  stop(): void {
    this.closed = true;
    window.clearTimeout(this.reconnectTimer);
    window.clearTimeout(this.summaryTimer);
    this.socket?.close();
  }

  /** Maçın özeti: blok doluluğu ve (anahtar varsa) ziyaretçinin tutması. */
  async refreshSummary(first = false): Promise<void> {
    const sentVersion = this.holdVersion;
    try {
      const stadium = await api.stadium(this.slug, holdToken.get(this.slug));
      if (this.closed) return;      // sayfadan çıkıldı (ya da geliştirmede çift kurulumun ilki): bu yanıt artık geçersiz
      this.maxSeats = stadium.maxSeats;
      const avail: Record<string, { available: number; total: number }> = {};
      for (const b of stadium.blocks) avail[b.code] = { available: b.availableSeats, total: b.totalSeats };
      // İstek yoldayken koltuk eklendiyse / bırakıldıysa bu yanıttaki tutma bilgisi eskidir; yerel olan doğrudur
      // (ve anahtarı silmek yeni tutmayı yok ederdi). Yalnızca doluluk sayıları tazelenir.
      if (this.holdVersion !== sentVersion) {
        this.set({ phase: "ready", error: null, stadium, avail });
        this.emit({ type: "avail" });
        return;
      }
      const hadHold = this.snap.hold.active && !this.confirming;
      const hold = this.toLocal(stadium.hold, Date.now());
      if (!stadium.hold.active) holdToken.set(this.slug, null);
      this.set({
        phase: "ready", error: null, stadium, avail, hold,
        lostHold: hadHold && !hold.active && !first ? this.nonce++ : this.snap.lostHold,
      });
      this.syncMine(stadium.hold.seats.map((s) => s.id));
      this.emit({ type: "avail" });
    } catch (e) {
      const message = e instanceof ApiError ? e.message : "Maç yüklenemedi.";
      this.set({ phase: this.snap.stadium ? "ready" : "error", error: message });
    }
  }

  private scheduleSummary(): void {
    window.clearTimeout(this.summaryTimer);
    this.summaryTimer = window.setTimeout(() => void this.refreshSummary(), 500);
  }

  // ---- bloklar ----

  /** Bir bloğun koltuklarını yükler (zaten yüklüyse yeniden almaz). */
  async loadBlock(code: string, force = false): Promise<Uint8Array | null> {
    const have = this.blocks.get(code);
    if (have && !force) return have.states;
    try {
      const data = await api.blockSeats(this.slug, code, holdToken.get(this.slug));
      const index = this.blockIndexes.get(code)!;
      const ids = new Array<number>(index.total).fill(0);
      const states = new Uint8Array(index.total);
      for (const s of data.seats as BlockSeat[]) {
        const local = localIndex(index, s.rowIndex, s.number);
        ids[local] = s.id;
        states[local] = stateOf(s.status, s.mine);
        this.seatIndex.set(s.id, { code, local });
      }
      this.blocks.set(code, { index, ids, states });
      this.set({ loadedBlocks: new Set(this.blocks.keys()) });
      this.emit({ type: "block", code, states });
      return states;
    } catch (e) {
      this.notify(e instanceof ApiError ? e.message : "Blok yüklenemedi.", "error");
      return null;
    }
  }

  private patch(code: string, local: number, state: number): void {
    const b = this.blocks.get(code);
    if (!b || b.states[local] === state) return;
    b.states[local] = state;
    this.emit({ type: "seat", code, local, state });
  }

  /** Tutmadaki koltuklar "benim" olarak işaretlenir, bırakılanlar boşa döner. */
  private syncMine(heldIds: number[]): void {
    const mine = new Set(heldIds);
    for (const [code, b] of this.blocks) {
      for (let local = 0; local < b.ids.length; local++) {
        const id = b.ids[local];
        const s = b.states[local];
        if (mine.has(id)) this.patch(code, local, SEAT_MINE);
        else if (s === SEAT_MINE) this.patch(code, local, SEAT_AVAILABLE);
      }
    }
  }

  // ---- tutma ----

  private toLocal(hold: HoldState, now: number): LocalHold {
    if (!hold.active || !hold.expiresAt) return NO_HOLD;
    const skew = Date.parse(hold.serverTime) - now;       // sunucu saati - istemci saati
    return { active: true, expiresAt: Date.parse(hold.expiresAt) - skew, seats: hold.seats, totalKurus: hold.totalKurus };
  }

  private applyHold(hold: HoldState): void {
    this.holdVersion++;
    if (hold.token) holdToken.set(this.slug, hold.token);
    if (!hold.active) holdToken.set(this.slug, null);
    this.set({ hold: this.toLocal(hold, Date.now()) });
    this.syncMine(hold.seats.map((s) => s.id));
  }

  private setPending(id: number, on: boolean): void {
    const pending = new Set(this.snap.pending);
    if (on) pending.add(id);
    else pending.delete(id);
    this.set({ pending });
  }

  /** Koltuğa dokunma: boşsa tutar, kendi koltuğuysa bırakır. */
  async toggleSeat(code: string, local: number): Promise<void> {
    const b = this.blocks.get(code);
    if (!b) return;
    const id = b.ids[local];
    const state = b.states[local];
    if (!id || this.snap.pending.has(id)) return;

    if (state === SEAT_MINE) {
      this.setPending(id, true);
      try {
        this.applyHold(await api.removeSeat(this.slug, id, holdToken.get(this.slug)));
      } catch (e) {
        if (e instanceof ApiError && e.code === "HOLD_EXPIRED") await this.refreshSummary();
        else this.notify(e instanceof ApiError ? e.message : "Koltuk bırakılamadı.", "error");
      } finally {
        this.setPending(id, false);
      }
      return;
    }
    if (state !== SEAT_AVAILABLE) {
      this.emit({ type: "reject", code, local });
      this.notify(state === SEAT_SOLD ? "Bu koltuk satıldı." : "Bu koltuğu şu an başkası tutuyor.");
      return;
    }
    if (this.snap.hold.seats.length >= this.maxSeats) {
      this.notify(`Tek seferde en fazla ${this.maxSeats} koltuk seçebilirsin.`);
      return;
    }
    this.setPending(id, true);
    try {
      this.applyHold(await api.addSeat(this.slug, id, holdToken.get(this.slug)));
    } catch (e) {
      if (e instanceof ApiError && e.code === "SEAT_UNAVAILABLE") {
        this.patch(code, local, SEAT_HELD);
        this.emit({ type: "reject", code, local });
      }
      this.notify(e instanceof ApiError ? e.message : "Koltuk alınamadı.", "error");
    } finally {
      this.setPending(id, false);
    }
  }

  /**
   * "Benim için seç": açık bloktaki yan yana {@code count} boş koltuktan sahaya en yakın, sıranın ortasına
   * en yakın olanını tutar. Yan yana yer yoksa false döner.
   */
  async pickBest(code: string, count: number): Promise<boolean> {
    const b = this.blocks.get(code);
    if (!b) return false;
    const room = this.maxSeats - this.snap.hold.seats.length;
    if (count > room) {
      this.notify(`En fazla ${this.maxSeats} koltuk tutabilirsin; ${room} hakkın kaldı.`);
      return false;
    }
    const best = bestRun(b.index, b.states, count);
    if (!best) {
      this.notify(`Bu blokta yan yana ${count} boş koltuk kalmadı.`);
      return false;
    }
    for (const local of best) {
      await this.toggleSeat(code, local);
      if (b.states[local] !== SEAT_MINE) return false;      // biri kapıldıysa dur, eldekiler tutulmuş kalır
    }
    return true;
  }

  /** Tutmadaki bir koltuğu, bloğu açık olmasa da (örneğin sayfa yenilendikten sonra) bırakır. */
  async releaseSeat(id: number): Promise<void> {
    if (this.snap.pending.has(id)) return;
    this.setPending(id, true);
    try {
      this.applyHold(await api.removeSeat(this.slug, id, holdToken.get(this.slug)));
    } catch (e) {
      if (e instanceof ApiError && e.code === "HOLD_EXPIRED") await this.refreshSummary();
      else this.notify(e instanceof ApiError ? e.message : "Koltuk bırakılamadı.", "error");
    } finally {
      this.setPending(id, false);
    }
  }

  async releaseAll(): Promise<void> {
    try {
      this.applyHold(await api.releaseAll(this.slug, holdToken.get(this.slug)));
    } catch (e) {
      this.notify(e instanceof ApiError ? e.message : "Seçim temizlenemedi.", "error");
    }
  }

  async confirm(customer: { name: string; email: string }): Promise<Booking> {
    this.confirming = true;
    try {
      const booking = await api.confirm(this.slug, holdToken.get(this.slug), customer);
      holdToken.set(this.slug, null);
      // Tutma satın almayla bitti; "süren doldu" uyarısı çıkmasın
      this.holdVersion++;
      this.set({ hold: NO_HOLD });
      return booking;
    } catch (e) {
      if (e instanceof ApiError && e.code === "HOLD_EXPIRED") await this.refreshSummary();
      throw e;
    } finally {
      this.confirming = false;
    }
  }

  // ---- canlı yayın ----

  private connect(): void {
    if (this.closed) return;
    this.set({ live: "connecting" });
    const socket = new WebSocket(liveUrl(this.slug));
    this.socket = socket;
    socket.onopen = () => {
      this.attempt = 0;
      this.set({ live: "open" });
      if (this.everOpened) {
        // Kopukken kaçan değişiklikleri al
        void this.refreshSummary();
        for (const code of this.blocks.keys()) void this.loadBlock(code, true);
      }
      this.everOpened = true;
    };
    socket.onmessage = (event) => {
      try {
        const msg = JSON.parse(String(event.data)) as
          | { type: "seats"; status: SeatStatus; ids: number[]; blocks: Record<string, number> }
          | { type: "viewers"; count: number };
        if (msg.type === "viewers") {
          this.set({ viewers: msg.count });
        } else if (msg.type === "seats") {
          this.onSeatsChanged(msg.status, msg.ids);
        }
      } catch {
        /* tanınmayan ileti yok sayılır */
      }
    };
    socket.onclose = () => {
      if (this.closed) return;
      this.set({ live: "closed" });
      this.reconnectTimer = window.setTimeout(() => this.connect(), Math.min(10_000, 1000 * 2 ** this.attempt++));
    };
  }

  private onSeatsChanged(status: SeatStatus, ids: number[]): void {
    let myLoss = false;
    for (const id of ids) {
      const at = this.seatIndex.get(id);
      if (!at) continue;
      const cur = this.blocks.get(at.code)?.states[at.local];
      if (cur === SEAT_MINE) {
        // Kendi koltuğumuz için "tutuldu" yayını yeni bilgi değildir; "boşaldı" ise tutma bitmiş olabilir
        if (status === "AVAILABLE" && !this.snap.pending.has(id)) myLoss = true;
        continue;
      }
      this.patch(at.code, at.local, stateOf(status, false));
    }
    if (myLoss) void this.refreshSummary();
    else this.scheduleSummary();      // blok doluluk sayaçlarını tazele (en fazla yarım saniyede bir)
  }
}

/** Blokta yan yana {@code count} boş koltuk arar; sahaya en yakın ve sıranın ortasına en yakın olanı seçer. */
export function bestRun(index: BlockIndex, states: Uint8Array, count: number): number[] | null {
  if (count < 1) return null;
  let best: number[] | null = null;
  let bestScore = Infinity;
  for (let row = 0; row < index.rowStart.length; row++) {
    const start = index.rowStart[row];
    const len = (row + 1 < index.rowStart.length ? index.rowStart[row + 1] : index.total) - start;
    for (let i = 0; i + count <= len; i++) {
      let free = true;
      for (let k = 0; k < count; k++) {
        if (states[start + i + k] !== SEAT_AVAILABLE) {
          free = false;
          break;
        }
      }
      if (!free) continue;
      const centerOffset = Math.abs(i + (count - 1) / 2 - (len - 1) / 2);
      const score = row * 1.0 + centerOffset * 0.25;
      if (score < bestScore) {
        bestScore = score;
        best = Array.from({ length: count }, (_, k) => start + i + k);
      }
    }
  }
  return best;
}
