import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from "react";

import type { HeldSeat, Layout } from "../api";
import Panel from "../components/Panel";
import Stage, { webglSupported } from "../components/Stage";
import { useToast } from "../components/Toasts";
import { dateParts, money } from "../lib/format";
import { MatchStore } from "../matchStore";
import { Link } from "../router";
import { SEAT_HELD, SEAT_MINE, SEAT_SOLD, type OverviewMode, type StadiumScene, type ViewKind } from "../three/StadiumScene";

const reducedMotion = () => window.matchMedia("(prefers-reduced-motion: reduce)").matches;

/** Maç sayfası: depoyu kurar ve kaldırır; asıl arayüz MatchView'dadır. */
export default function MatchPage({ slug, layout }: { slug: string; layout: Layout }) {
  const notify = useToast();
  const [store, setStore] = useState<MatchStore | null>(null);
  const notifyRef = useRef(notify);
  notifyRef.current = notify;

  useEffect(() => {
    const s = new MatchStore(slug, layout, (m, t) => notifyRef.current(m, t));
    s.start();
    setStore(s);
    return () => {
      s.stop();
      setStore(null);
    };
  }, [slug, layout]);

  if (!store) return <div className="center-note">Yükleniyor…</div>;
  return <MatchView store={store} layout={layout} />;
}

function MatchView({ store, layout }: { store: MatchStore; layout: Layout }) {
  const snap = useSyncExternalStore(store.subscribe, store.getSnapshot);
  const notify = useToast();
  const supported = useMemo(webglSupported, []);

  const [view, setView] = useState<ViewKind>("overview");
  const [focusBlock, setFocusBlock] = useState<string | null>(null);
  const [mode, setMode] = useState<OverviewMode>("availability");
  const [scene, setScene] = useState<StadiumScene | null>(null);
  const [seatView, setSeatView] = useState<string | null>(null); // bakılan koltuğun kimliği
  const [roof, setRoof] = useState(false);
  const [blockHover, setBlockHover] = useState<{ code: string; x: number; y: number } | null>(null);
  const [seatHover, setSeatHover] = useState<{ block: string; local: number; x: number; y: number } | null>(null);

  const blockSpec = useCallback((code: string) => layout.blocks.find((b) => b.code === code), [layout]);

  // Tutma kendiliğinden bittiğinde (süre doldu) bir kez haber ver
  const lastLost = useRef(snap.lostHold);
  useEffect(() => {
    if (snap.lostHold !== lastLost.current) {
      lastLost.current = snap.lostHold;
      notify("Süren doldu, koltuklar serbest bırakıldı.", "error");
      if (view === "seat") backToBlock();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [snap.lostHold]);

  const settle = useCallback(() => {
    if (reducedMotion()) scene?.rig.finishTransition();
  }, [scene]);

  function goOverview() {
    setView("overview");
    setFocusBlock(null);
    setSeatView(null);
    setBlockHover(null);
    setSeatHover(null);
    scene?.rig.toOverview();
    settle();
  }

  function backToBlock() {
    if (!focusBlock || !scene) return goOverview();
    setView("block");
    setSeatView(null);
    const cam = scene.blockCamera(focusBlock);
    scene.rig.toBlock(cam.pos, cam.target);
    settle();
  }

  async function pickBlock(code: string) {
    if (!scene) return;
    setFocusBlock(code);
    setView("block");
    setBlockHover(null);
    const cam = scene.blockCamera(code);
    scene.rig.toBlock(cam.pos, cam.target);
    settle();
    await store.loadBlock(code);
  }

  function lookFromSeat(seat: HeldSeat) {
    if (!scene) return;
    const { eye, lookAt } = scene.seatEye(seat.block, Number(seat.row) - 1, seat.number);
    setFocusBlock(seat.block);
    setSeatView(String(seat.id));
    setView("seat");
    scene.rig.toSeat(eye, lookAt);
    settle();
    void store.loadBlock(seat.block);
  }

  function stepSeat(delta: number) {
    const seats = snap.hold.seats;
    if (seats.length === 0) return;
    const at = Math.max(0, seats.findIndex((s) => String(s.id) === seatView));
    lookFromSeat(seats[(at + delta + seats.length) % seats.length]);
  }

  // Klavye: Esc bir adım geri
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== "Escape") return;
      const target = e.target as HTMLElement | null;
      if (target && /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName)) return;
      if (view === "seat") backToBlock();
      else if (view === "block") goOverview();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [view, focusBlock, scene]);

  // Seyirci sayısı / bağlantı durumu
  const event = snap.stadium?.event;

  if (snap.phase === "error" && !snap.stadium) {
    return (
      <div className="center-note" role="alert">
        <p>{snap.error ?? "Maç yüklenemedi."}</p>
        <Link to="/" className="btn btn-soft">Maçlara dön</Link>
      </div>
    );
  }

  const spec = focusBlock ? blockSpec(focusBlock) : undefined;
  const heldInSeatView = snap.hold.seats.find((s) => String(s.id) === seatView);

  return (
    <div className="match" data-view={view}>
      <div className="stage-wrap">
        {supported ? (
          <Stage
            layout={layout}
            store={store}
            view={view}
            focusBlock={focusBlock}
            overviewMode={mode}
            onPickBlock={(c) => void pickBlock(c)}
            onPickSeat={(b, l) => void store.toggleSeat(b, l)}
            onHoverBlock={setBlockHover}
            onHoverSeat={setSeatHover}
            onScene={setScene}
          />
        ) : (
          <div className="center-note" role="alert">
            <p>Tarayıcın WebGL'i desteklemiyor ya da kapalı; 3B stadyum görüntülenemiyor.</p>
            <p className="muted small">Güncel bir Chrome, Edge, Firefox veya Safari ile dene.</p>
          </div>
        )}

        <header className="hud-top">
          <Link to="/" className="back" aria-label="Maç listesine dön">←</Link>
          <div className="hud-title">
            <h1>{event?.title ?? "Yükleniyor…"}</h1>
            {event && (
              <p className="muted small">
                {dateParts(event.startsAt).weekday} {dateParts(event.startsAt).day} {dateParts(event.startsAt).month} · {dateParts(event.startsAt).time}
              </p>
            )}
          </div>
          <nav className="crumbs" aria-label="Konum">
            <button type="button" className={view === "overview" ? "crumb on" : "crumb"} onClick={goOverview}>
              Stadyum
            </button>
            {spec && (
              <>
                <span aria-hidden="true">›</span>
                <button type="button" className={view === "block" ? "crumb on" : "crumb"} onClick={view === "seat" ? backToBlock : undefined} data-testid="crumb-block">
                  {spec.name}
                </button>
              </>
            )}
            {view === "seat" && heldInSeatView && (
              <>
                <span aria-hidden="true">›</span>
                <span className="crumb on">
                  {heldInSeatView.row}. sıra {heldInSeatView.number}. koltuk
                </span>
              </>
            )}
          </nav>
          <p className={`live live-${snap.live}`} role="status">
            <span className="dot" aria-hidden="true" />
            {snap.live === "open" ? `Canlı · ${snap.viewers} kişi bakıyor` : snap.live === "connecting" ? "Bağlanıyor…" : "Bağlantı koptu, yeniden deneniyor"}
          </p>
        </header>

        {view === "overview" && (
          <div className="hud-bottom">
            <div className="seg" role="group" aria-label="Renklendirme">
              <button type="button" aria-pressed={mode === "availability"} onClick={() => setMode("availability")}>Doluluk</button>
              <button type="button" aria-pressed={mode === "price"} onClick={() => setMode("price")}>Fiyat</button>
            </div>
            <Legend mode={mode} snap={snap} />
            <button
              type="button"
              className="btn btn-ghost"
              aria-pressed={roof}
              onClick={() => {
                scene?.setRoofVisible(!roof);
                setRoof(!roof);
              }}
            >
              {roof ? "Çatıyı gizle" : "Çatıyı göster"}
            </button>
            <p className="hint small muted">Bir bloğa tıkla · sürükle: döndür · tekerlek: yakınlaş</p>
          </div>
        )}

        {view === "block" && (
          <div className="hud-bottom">
            <button type="button" className="btn btn-soft" onClick={goOverview} data-testid="back-overview">
              ← Stadyuma dön
            </button>
            <Legend mode="seat" snap={snap} />
            <p className="hint small muted">Koltuğa tıkla: seç / bırak</p>
          </div>
        )}

        {view === "seat" && (
          <div className="hud-bottom seat-hud" data-testid="seat-hud">
            <button type="button" className="btn btn-soft" onClick={backToBlock}>← Bloğa dön</button>
            {snap.hold.seats.length > 1 && (
              <div className="row">
                <button type="button" className="icon-btn" onClick={() => stepSeat(-1)} aria-label="Önceki koltuk">‹</button>
                <button type="button" className="icon-btn" onClick={() => stepSeat(1)} aria-label="Sonraki koltuk">›</button>
              </div>
            )}
            <p className="hint small">
              {heldInSeatView
                ? `${heldInSeatView.blockName} · ${heldInSeatView.row}. sıra, ${heldInSeatView.number}. koltuk`
                : "Koltuğundan saha"}
              <span className="muted"> · sürükle: etrafa bak · tekerlek: yakınlaş</span>
            </p>
          </div>
        )}

        {blockHover && view === "overview" && <BlockTip hover={blockHover} layout={layout} snap={snap} />}
        {seatHover && view === "block" && <SeatTip hover={seatHover} store={store} layout={layout} />}
      </div>

      <Panel
        store={store}
        snap={snap}
        focusBlock={view === "seat" ? null : focusBlock}
        onSeatView={lookFromSeat}
      />
    </div>
  );
}

function Legend({ mode, snap }: { mode: OverviewMode | "seat"; snap: ReturnType<MatchStore["getSnapshot"]> }) {
  if (mode === "availability") {
    return (
      <div className="legend" aria-label="Doluluk göstergesi">
        <span>Dolu</span>
        <span className="legend-bar" aria-hidden="true" />
        <span>Boş</span>
      </div>
    );
  }
  if (mode === "price") {
    return (
      <ul className="legend" aria-label="Bölge fiyatları">
        {(snap.stadium?.zones ?? []).map((z) => (
          <li key={z.code}>
            <i className={`swatch zone-${z.code}`} aria-hidden="true" />
            {z.label} <span className="tnum muted">{money(z.priceKurus)}</span>
          </li>
        ))}
      </ul>
    );
  }
  return (
    <ul className="legend" aria-label="Koltuk durumları">
      <li><i className="swatch s-free" aria-hidden="true" />Boş</li>
      <li><i className="swatch s-mine" aria-hidden="true" />Seçtiğin</li>
      <li><i className="swatch s-held" aria-hidden="true" />Başkası tutuyor</li>
      <li><i className="swatch s-sold" aria-hidden="true" />Satıldı</li>
    </ul>
  );
}

function BlockTip({ hover, layout, snap }: { hover: { code: string; x: number; y: number }; layout: Layout; snap: ReturnType<MatchStore["getSnapshot"]> }) {
  const spec = layout.blocks.find((b) => b.code === hover.code);
  const a = snap.avail[hover.code];
  if (!spec) return null;
  return (
    <div className="tip" style={{ left: hover.x + 14, top: hover.y + 14 }} role="tooltip">
      <strong>{spec.name}</strong>
      <span className="muted">{spec.stand}</span>
      <span className="tnum">{a ? `${a.available} / ${a.total} boş` : ""}</span>
      <span className="tnum">{money(spec.priceKurus)}</span>
    </div>
  );
}

function SeatTip({ hover, store, layout }: { hover: { block: string; local: number; x: number; y: number }; store: MatchStore; layout: Layout }) {
  const spec = layout.blocks.find((b) => b.code === hover.block);
  const states = store.states(hover.block);
  if (!spec || !states) return null;
  // Koltuğun sırası ve numarası yerel indeksten bulunur
  let row = 0;
  let start = 0;
  for (const n of spec.seatsPerRow) {
    if (hover.local < start + n) break;
    start += n;
    row++;
  }
  const s = states[hover.local];
  const label = s === SEAT_SOLD ? "Satıldı" : s === SEAT_HELD ? "Başkası tutuyor" : s === SEAT_MINE ? "Seçtin" : "Boş";
  return (
    <div className="tip" style={{ left: hover.x + 14, top: hover.y + 14 }} role="tooltip">
      <strong>
        {row + 1}. sıra, {hover.local - start + 1}. koltuk
      </strong>
      <span className="muted">{label}</span>
      <span className="tnum">{money(spec.priceKurus)}</span>
    </div>
  );
}
