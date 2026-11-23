import type { Booking } from "../api";
import { dateParts, money } from "../lib/format";
import { Barcode } from "./Bits";

/** Satın alınan biletin görünümü. Tüm veriler sunucudan gelir; barkod yalnızca görseldir. */
export default function Ticket({ booking }: { booking: Booking }) {
  const d = dateParts(booking.startsAt);
  return (
    <article className="ticket" data-testid="ticket" style={{ ["--hue" as string]: booking.hue }}>
      <div className="ticket-main">
        <p className="eyebrow">{booking.category}</p>
        <h2>{booking.eventTitle}</h2>
        <p className="muted">
          {d.weekday}, {d.day} {d.month} · {d.time} · {booking.venue}, {booking.city}
        </p>
        <ul className="ticket-seats">
          {booking.seats.map((s) => (
            <li key={`${s.block}-${s.row}-${s.number}`}>
              <span className="chip">{s.block}</span>
              <span>
                {s.row}. sıra, {s.number}. koltuk
              </span>
              <span className="tnum muted">{money(s.priceKurus)}</span>
            </li>
          ))}
        </ul>
        <p className="ticket-total">
          Toplam <strong className="tnum">{money(booking.totalKurus)}</strong>
        </p>
      </div>
      <div className="ticket-stub">
        <Barcode value={booking.code} />
        <p className="tnum code" data-testid="booking-code">
          {booking.code}
        </p>
        <p className="muted small">{booking.customerName}</p>
      </div>
    </article>
  );
}
