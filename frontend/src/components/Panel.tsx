import { useState, type FormEvent } from "react";

import { ApiError, type HeldSeat } from "../api";
import { money } from "../lib/format";
import type { MatchStore, Snapshot } from "../matchStore";
import { navigate } from "../router";
import { AnimatedMoney, Countdown } from "./Bits";
import { useToast } from "./Toasts";

interface PanelProps {
  store: MatchStore;
  snap: Snapshot;
  focusBlock: string | null;
  onSeatView: (seat: HeldSeat) => void;
}

/** Sağ panel: seçilen koltuklar, kalan süre, "benim için seç" ve ödeme (demo) formu. */
export default function Panel({ store, snap, focusBlock, onSeatView }: PanelProps) {
  const notify = useToast();
  const { hold } = snap;
  const [count, setCount] = useState(2);
  const [paying, setPaying] = useState(false);
  const [busy, setBusy] = useState(false);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setFieldErrors({});
    try {
      const booking = await store.confirm({ name: name.trim(), email: email.trim() });
      navigate(`/bilet/${booking.code}`);
    } catch (err) {
      if (err instanceof ApiError) {
        setFieldErrors(err.errors);
        if (Object.keys(err.errors).length === 0) notify(err.message, "error");
        if (err.code === "HOLD_EXPIRED") setPaying(false);
      } else {
        notify("Satın alma tamamlanamadı.", "error");
      }
    } finally {
      setBusy(false);
    }
  }

  if (!hold.active) {
    return (
      <aside className="panel" data-testid="panel">
        <h2>Koltuklarım</h2>
        <p className="muted">
          {focusBlock
            ? `Bloktan koltuk seç. Koltuğa dokununca senin için ${Math.round((snap.stadium?.holdSeconds ?? 300) / 60)} dakika ayrılır.`
            : "Stadyumda bir blok seç, ardından koltuklarını işaretle."}
        </p>
        {focusBlock && <PickBest store={store} block={focusBlock} count={count} setCount={setCount} max={store.maxSeatsAllowed} />}
      </aside>
    );
  }

  return (
    <aside className="panel" data-testid="panel">
      <header className="panel-head">
        <h2>Koltuklarım</h2>
        <p className="hold-timer">
          Süre <Countdown expiresAt={hold.expiresAt} onExpire={() => void store.refreshSummary()} />
        </p>
      </header>

      <ul className="held-list" data-testid="held-list">
        {hold.seats.map((s) => (
          <li key={s.id}>
            <span className="chip">{s.block}</span>
            <span className="held-name">
              {s.row}. sıra, {s.number}. koltuk
            </span>
            <span className="tnum muted">{money(s.priceKurus)}</span>
            <button type="button" className="link-btn" onClick={() => onSeatView(s)} aria-label={`${s.block} ${s.row}. sıra ${s.number}. koltuktan sahayı gör`}>
              Koltuktan gör
            </button>
            <button
              type="button"
              className="icon-btn"
              aria-label={`${s.block} ${s.row}. sıra ${s.number}. koltuğu bırak`}
              onClick={() => void store.releaseSeat(s.id)}
            >
              ×
            </button>
          </li>
        ))}
      </ul>

      {focusBlock && <PickBest store={store} block={focusBlock} count={count} setCount={setCount} max={store.maxSeatsAllowed} />}

      <p className="total">
        Toplam <strong data-testid="total">
          <AnimatedMoney kurus={hold.totalKurus} />
        </strong>
      </p>

      {!paying ? (
        <div className="row">
          <button type="button" className="btn btn-primary" data-testid="checkout" onClick={() => setPaying(true)}>
            Ödemeye geç
          </button>
          <button type="button" className="btn btn-ghost" onClick={() => void store.releaseAll()}>
            Temizle
          </button>
        </div>
      ) : (
        <form onSubmit={submit} className="checkout" noValidate>
          <p className="small muted">Bu bir demodur; ödeme alınmaz, kart bilgisi istenmez.</p>
          <label>
            Ad soyad
            <input value={name} onChange={(e) => setName(e.target.value)} autoComplete="name" aria-invalid={!!fieldErrors.name} data-testid="name" />
            {fieldErrors.name && <span className="field-error">{fieldErrors.name}</span>}
          </label>
          <label>
            E-posta
            <input value={email} onChange={(e) => setEmail(e.target.value)} type="email" autoComplete="email" aria-invalid={!!fieldErrors.email} data-testid="email" />
            {fieldErrors.email && <span className="field-error">{fieldErrors.email}</span>}
          </label>
          <div className="row">
            <button type="submit" className="btn btn-primary" disabled={busy} data-testid="confirm">
              {busy ? "İşleniyor…" : "Satın al (demo)"}
            </button>
            <button type="button" className="btn btn-ghost" onClick={() => setPaying(false)}>
              Vazgeç
            </button>
          </div>
        </form>
      )}
    </aside>
  );
}

function PickBest(props: { store: MatchStore; block: string; count: number; setCount: (n: number) => void; max: number }) {
  const { store, block, count, setCount, max } = props;
  return (
    <div className="pick-best">
      <div className="stepper" role="group" aria-label="Kaç kişi">
        <button type="button" className="icon-btn" aria-label="Azalt" onClick={() => setCount(Math.max(1, count - 1))}>
          −
        </button>
        <span className="tnum" aria-live="polite">
          {count} kişi
        </span>
        <button type="button" className="icon-btn" aria-label="Arttır" onClick={() => setCount(Math.min(max, count + 1))}>
          +
        </button>
      </div>
      <button type="button" className="btn btn-soft" data-testid="pick-best" onClick={() => void store.pickBest(block, count)}>
        Benim için seç
      </button>
    </div>
  );
}
