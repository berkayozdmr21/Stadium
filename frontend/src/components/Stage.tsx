import { useEffect, useRef } from "react";

import type { Layout } from "../api";
import type { MatchStore } from "../matchStore";
import { type OverviewMode, StadiumScene, type ViewKind } from "../three/StadiumScene";

export interface StageProps {
  layout: Layout;
  store: MatchStore;
  view: ViewKind;
  focusBlock: string | null;
  overviewMode: OverviewMode;
  onPickBlock: (code: string) => void;
  onPickSeat: (block: string, local: number) => void;
  /** İmleç bir bloğun üstündeyse bloğun kodu ve ekran konumu. */
  onHoverBlock: (hover: { code: string; x: number; y: number } | null) => void;
  onHoverSeat: (hover: { block: string; local: number; x: number; y: number } | null) => void;
  /** Sahne hazır olduğunda ve her değiştiğinde (kamera uçuşlarını başlatmak için). */
  onScene: (scene: StadiumScene | null) => void;
}

/**
 * 3B sahnenin tuvali. Fare/dokunma girdisini kamera hareketine (sürükle, tekerlek) ve seçime (tıkla) çevirir.
 * Sahne React dışında, kendi döngüsünde çizilir; React yalnızca prop'lar değişince sahneye komut verir.
 */
export default function Stage(props: StageProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const sceneRef = useRef<StadiumScene | null>(null);
  const latest = useRef(props);
  latest.current = props;

  // Sahneyi kur
  useEffect(() => {
    const canvas = canvasRef.current!;
    let scene: StadiumScene;
    try {
      scene = new StadiumScene(canvas, props.layout);
    } catch {
      return; // WebGL yok: sayfa bunu fark edip uyarı gösterir (webglSupported)
    }
    sceneRef.current = scene;
    const resize = () => {
      const r = canvas.getBoundingClientRect();
      scene.resize(Math.max(1, Math.round(r.width)), Math.max(1, Math.round(r.height)));
    };
    resize();
    const ro = new ResizeObserver(resize);
    ro.observe(canvas);
    scene.start();

    // Depodan gelen değişiklikleri sahneye aktar
    const { store } = props;
    const syncAvail = () => {
      const avail = store.getSnapshot().avail;
      for (const [code, a] of Object.entries(avail)) scene.setAvailability(code, a.available, a.total);
    };
    syncAvail();
    for (const code of store.getSnapshot().loadedBlocks) scene.setSeatStates(code, store.states(code));
    const off = store.onEvent((e) => {
      if (e.type === "avail") syncAvail();
      else if (e.type === "block") scene.setSeatStates(e.code, e.states);
      else if (e.type === "seat") scene.patchSeatState(e.code, e.local, e.state);
    });
    props.onScene(scene);
    // Geliştirme sırasında konsoldan ve uçtan uca testlerden sahneye erişmek için
    if (import.meta.env.DEV) (window as unknown as { __scene?: StadiumScene }).__scene = scene;

    return () => {
      off();
      ro.disconnect();
      props.onScene(null);
      scene.dispose();
      sceneRef.current = null;
    };
    // Sahne yalnızca ilk kurulumda oluşur; sonraki değişiklikler aşağıdaki etkilerle uygulanır
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [props.layout, props.store]);

  useEffect(() => {
    sceneRef.current?.setView(props.view);
  }, [props.view]);
  useEffect(() => {
    sceneRef.current?.setFocusBlock(props.focusBlock);
  }, [props.focusBlock]);
  useEffect(() => {
    sceneRef.current?.setOverviewMode(props.overviewMode);
  }, [props.overviewMode]);

  // ---- girdi ----
  const drag = useRef<{ x: number; y: number; moved: number; id: number } | null>(null);
  const hoverRaf = useRef(0);
  const lastPointer = useRef({ x: 0, y: 0 });

  function ndc(e: { clientX: number; clientY: number }) {
    const r = canvasRef.current!.getBoundingClientRect();
    return { x: ((e.clientX - r.left) / r.width) * 2 - 1, y: -((e.clientY - r.top) / r.height) * 2 + 1 };
  }

  function updateHover() {
    hoverRaf.current = 0;
    const scene = sceneRef.current;
    if (!scene || drag.current) return;
    const { view, focusBlock, onHoverBlock, onHoverSeat } = latest.current;
    const p = ndc({ clientX: lastPointer.current.x, clientY: lastPointer.current.y });
    const sx = lastPointer.current.x;
    const sy = lastPointer.current.y;
    if (view === "overview") {
      const code = scene.pickBlock(p.x, p.y);
      scene.setHoverBlock(code);
      onHoverBlock(code ? { code, x: sx, y: sy } : null);
      canvasRef.current!.style.cursor = code ? "pointer" : "grab";
    } else if (view === "block" && focusBlock) {
      const local = scene.pickSeat(p.x, p.y, focusBlock);
      scene.setHoverSeat(local === null ? null : { block: focusBlock, local });
      onHoverSeat(local === null ? null : { block: focusBlock, local, x: sx, y: sy });
      canvasRef.current!.style.cursor = local === null ? "default" : "pointer";
    } else {
      canvasRef.current!.style.cursor = view === "seat" ? "grab" : "default";
    }
  }

  function onPointerDown(e: React.PointerEvent<HTMLCanvasElement>) {
    canvasRef.current!.setPointerCapture(e.pointerId);
    drag.current = { x: e.clientX, y: e.clientY, moved: 0, id: e.pointerId };
    sceneRef.current?.rig.dragStart();
  }

  function onPointerMove(e: React.PointerEvent<HTMLCanvasElement>) {
    lastPointer.current = { x: e.clientX, y: e.clientY };
    const d = drag.current;
    if (d) {
      const dx = e.clientX - d.x;
      const dy = e.clientY - d.y;
      d.moved += Math.abs(dx) + Math.abs(dy);
      d.x = e.clientX;
      d.y = e.clientY;
      if (d.moved > 6) {
        sceneRef.current?.rig.drag(dx, dy);
        canvasRef.current!.style.cursor = "grabbing";
      }
      return;
    }
    if (e.pointerType === "touch") return; // dokunmatikte üzerine gelme yoktur
    if (!hoverRaf.current) hoverRaf.current = requestAnimationFrame(updateHover);
  }

  function onPointerUp(e: React.PointerEvent<HTMLCanvasElement>) {
    const d = drag.current;
    drag.current = null;
    sceneRef.current?.rig.dragEnd();
    canvasRef.current!.releasePointerCapture(e.pointerId);
    if (!d || d.moved > 6) return;
    // Sürüklenmediyse tıklamadır
    const scene = sceneRef.current;
    if (!scene) return;
    const p = ndc(e);
    const { view, focusBlock, onPickBlock, onPickSeat } = latest.current;
    if (view === "overview") {
      const code = scene.pickBlock(p.x, p.y);
      if (code) onPickBlock(code);
    } else if (view === "block" && focusBlock) {
      const local = scene.pickSeat(p.x, p.y, focusBlock);
      if (local !== null) onPickSeat(focusBlock, local);
    }
  }

  function onPointerLeave() {
    const { onHoverBlock, onHoverSeat } = latest.current;
    sceneRef.current?.setHoverBlock(null);
    sceneRef.current?.setHoverSeat(null);
    onHoverBlock(null);
    onHoverSeat(null);
  }

  // Tekerlek olayı React'te pasif olduğundan sayfanın kaymasını engellemek için doğrudan eklenir
  useEffect(() => {
    const canvas = canvasRef.current!;
    const onWheel = (e: WheelEvent) => {
      e.preventDefault();
      sceneRef.current?.rig.zoom(e.deltaY);
    };
    canvas.addEventListener("wheel", onWheel, { passive: false });
    return () => canvas.removeEventListener("wheel", onWheel);
  }, []);

  return (
    <canvas
      ref={canvasRef}
      className="stage"
      data-testid="stage"
      aria-label="Tüpraş Stadyumu 3B görünümü"
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerLeave={onPointerLeave}
    />
  );
}

/** Tarayıcı WebGL destekliyor mu? Desteklemiyorsa 3B sahne kurulamaz ve açıklayıcı bir uyarı gösteririz. */
export function webglSupported(): boolean {
  try {
    const c = document.createElement("canvas");
    return Boolean(c.getContext("webgl2") || c.getContext("webgl"));
  } catch {
    return false;
  }
}
