import { useEffect, useState } from "react";

import { api, ApiError, type EventSummary } from "../api";
import { dateParts, money } from "../lib/format";
import { Link } from "../router";

/** Maç listesi. Her kart sahte bir afiş rengi taşır; markalar ve gerçek takım isimleri kullanılmaz. */
export default function Home() {
  const [events, setEvents] = useState<EventSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    api
      .events()
      .then((e) => alive && setEvents(e))
      .catch((e) => alive && setError(e instanceof ApiError ? e.message : "Maçlar yüklenemedi."));
    return () => {
      alive = false;
    };
  }, []);

  return (
    <main className="home">
      <section className="hero">
        <p className="eyebrow">3B koltuk seçimi</p>
        <h1>Tüpraş Stadyumu'nu kuş bakışı gez, koltuğundan sahayı gör.</h1>
        <p className="lead">
          Bir maç seç, bloğa yaklaş, koltuğunu işaretle; kamera seni oturduğun yere götürsün. Portfolyo demosudur:
          maçlar, takımlar ve fiyatlar kurgudur, gerçek bilet satılmaz.
        </p>
      </section>

      {error && <p role="alert" className="error-note">{error}</p>}
      {!events && !error && <p className="muted">Maçlar yükleniyor…</p>}

      <ul className="cards">
        {events?.map((e) => {
          const d = dateParts(e.startsAt);
          const ratio = e.totalSeats === 0 ? 0 : e.availableSeats / e.totalSeats;
          return (
            <li key={e.slug}>
              <Link to={`/mac/${e.slug}`} className="card" style={{ ["--hue" as string]: e.hue }} data-testid="match-card">
                <div className="card-date">
                  <span className="day tnum">{d.day}</span>
                  <span>{d.month}</span>
                </div>
                <div className="card-body">
                  <p className="eyebrow">{e.category}</p>
                  <h2>{e.title}</h2>
                  <p className="muted small">
                    {d.weekday} · {d.time} · {e.venue}
                  </p>
                  <p className="meter" aria-hidden="true"><i style={{ width: `${Math.round(ratio * 100)}%` }} /></p>
                  <p className="small">
                    <span className="tnum">{e.availableSeats.toLocaleString("tr-TR")}</span> boş koltuk ·{" "}
                    <span className="tnum">{money(e.minPriceKurus)}</span>'den başlayan
                  </p>
                </div>
              </Link>
            </li>
          );
        })}
      </ul>

      <footer className="foot small muted">
        Bağımsız bir yazılım demosudur; kulüp, lig veya stadyum işletmecisiyle bağlantısı yoktur. Stadyum modeli
        fotoğraflara bakılarak yaklaşık çizilmiştir; blok numaraları ve kapasite birebir değildir.
      </footer>
    </main>
  );
}
