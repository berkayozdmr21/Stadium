import { useEffect, useState } from "react";

import { api, ApiError, type Layout } from "./api";
import { ToastProvider } from "./components/Toasts";
import Home from "./pages/Home";
import MatchPage from "./pages/MatchPage";
import TicketPage from "./pages/TicketPage";
import { Link, usePath } from "./router";

/** Stadyum yerleşimi (blok geometrisi) bir kez alınır; bütün maç sayfaları aynı yerleşimi kullanır. */
function useLayout(): { layout: Layout | null; error: string | null } {
  const [layout, setLayout] = useState<Layout | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    let alive = true;
    api
      .layout()
      .then((l) => alive && setLayout(l))
      .catch((e) => alive && setError(e instanceof ApiError ? e.message : "Stadyum yüklenemedi."));
    return () => {
      alive = false;
    };
  }, []);
  return { layout, error };
}

export default function App() {
  const path = usePath();
  const { layout, error } = useLayout();

  const match = /^\/mac\/([^/]+)\/?$/.exec(path);
  const ticket = /^\/bilet\/([^/]+)\/?$/.exec(path);

  let page;
  if (match) {
    page = error ? (
      <div className="center-note" role="alert">{error}</div>
    ) : layout ? (
      <MatchPage key={match[1]} slug={decodeURIComponent(match[1])} layout={layout} />
    ) : (
      <div className="center-note">Stadyum yükleniyor…</div>
    );
  } else if (ticket) {
    page = <TicketPage code={decodeURIComponent(ticket[1])} />;
  } else if (path === "/") {
    page = <Home />;
  } else {
    page = (
      <div className="center-note">
        <p>Sayfa bulunamadı.</p>
        <Link to="/" className="btn btn-soft">Ana sayfa</Link>
      </div>
    );
  }

  return <ToastProvider>{page}</ToastProvider>;
}
