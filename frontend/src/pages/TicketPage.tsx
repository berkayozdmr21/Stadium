import { useEffect, useState } from "react";

import { api, ApiError, type Booking } from "../api";
import Ticket from "../components/Ticket";
import { Link } from "../router";

export default function TicketPage({ code }: { code: string }) {
  const [booking, setBooking] = useState<Booking | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    api
      .booking(code)
      .then((b) => alive && setBooking(b))
      .catch((e) => alive && setError(e instanceof ApiError ? e.message : "Bilet yüklenemedi."));
    return () => {
      alive = false;
    };
  }, [code]);

  return (
    <main className="ticket-page">
      <p className="eyebrow">Demo bilet</p>
      <h1>Biletin hazır</h1>
      {error && <p role="alert" className="error-note">{error}</p>}
      {!booking && !error && <p className="muted">Yükleniyor…</p>}
      {booking && <Ticket booking={booking} />}
      <p className="small muted">Bu bilet gerçek değildir; yalnızca portfolyo demosunda üretildi.</p>
      <Link to="/" className="btn btn-soft">Maçlara dön</Link>
    </main>
  );
}
