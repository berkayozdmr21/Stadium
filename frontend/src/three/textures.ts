import * as THREE from "three";

import { PITCH_LENGTH, PITCH_WIDTH } from "../lib/geometry";
import { seeded } from "../lib/format";

const PX_PER_M = 20;

/**
 * Saha dokusu: biçilmiş çim şeritleri ve FIFA ölçülerinde çizgiler (105 x 68 m, ceza sahası 16,5 x 40,3 m,
 * kale alanı 5,5 x 18,32 m, orta daire 9,15 m yarıçaplı).
 */
export function pitchTexture(): THREE.CanvasTexture {
  const w = PITCH_LENGTH * PX_PER_M;
  const h = PITCH_WIDTH * PX_PER_M;
  const canvas = document.createElement("canvas");
  canvas.width = w;
  canvas.height = h;
  const g = canvas.getContext("2d")!;

  // Çim şeritleri (7 m genişliğinde)
  const stripes = 15;
  for (let i = 0; i < stripes; i++) {
    g.fillStyle = i % 2 === 0 ? "#2f7a3a" : "#2a7035";
    g.fillRect((i * w) / stripes, 0, w / stripes + 1, h);
  }
  // Çimin dokusu: ince rastgele lekeler
  const rnd = seeded(11);
  for (let i = 0; i < 9000; i++) {
    g.fillStyle = rnd() > 0.5 ? "rgba(255,255,255,0.025)" : "rgba(0,0,0,0.035)";
    g.fillRect(rnd() * w, rnd() * h, 2 + rnd() * 5, 1 + rnd() * 2);
  }

  g.strokeStyle = "rgba(245,247,242,0.96)";
  g.fillStyle = "rgba(245,247,242,0.96)";
  g.lineWidth = Math.round(0.12 * PX_PER_M);
  const m = (v: number) => v * PX_PER_M;
  const cx = w / 2;
  const cy = h / 2;

  // Dış çizgiler ve orta çizgi
  g.strokeRect(g.lineWidth / 2, g.lineWidth / 2, w - g.lineWidth, h - g.lineWidth);
  g.beginPath();
  g.moveTo(cx, 0);
  g.lineTo(cx, h);
  g.stroke();
  // Orta daire ve orta nokta
  g.beginPath();
  g.arc(cx, cy, m(9.15), 0, Math.PI * 2);
  g.stroke();
  g.beginPath();
  g.arc(cx, cy, m(0.3), 0, Math.PI * 2);
  g.fill();

  for (const side of [0, 1]) {
    const dir = side === 0 ? 1 : -1;
    const x0 = side === 0 ? 0 : w;
    // Ceza sahası
    g.strokeRect(side === 0 ? x0 : x0 - m(16.5), cy - m(20.16), m(16.5), m(40.32));
    // Kale alanı
    g.strokeRect(side === 0 ? x0 : x0 - m(5.5), cy - m(9.16), m(5.5), m(18.32));
    // Penaltı noktası ve ceza yayı
    const spotX = x0 + dir * m(11);
    g.beginPath();
    g.arc(spotX, cy, m(0.3), 0, Math.PI * 2);
    g.fill();
    g.beginPath();
    const a = Math.acos((16.5 - 11) / 9.15);
    if (side === 0) g.arc(spotX, cy, m(9.15), -a, a);
    else g.arc(spotX, cy, m(9.15), Math.PI - a, Math.PI + a);
    g.stroke();
  }

  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.anisotropy = 8;
  return tex;
}

/** Blok etiketi (örn. "A07") için küçük bir sprite dokusu. */
export function labelTexture(text: string): THREE.CanvasTexture {
  const canvas = document.createElement("canvas");
  canvas.width = 192;
  canvas.height = 96;
  const g = canvas.getContext("2d")!;
  g.fillStyle = "rgba(8,9,11,0.78)";
  roundRect(g, 6, 14, 180, 68, 22);
  g.fill();
  g.strokeStyle = "rgba(255,255,255,0.55)";
  g.lineWidth = 3;
  roundRect(g, 6, 14, 180, 68, 22);
  g.stroke();
  g.fillStyle = "#ffffff";
  g.font = "600 46px 'Oswald Variable', 'Arial Narrow', sans-serif";
  g.textAlign = "center";
  g.textBaseline = "middle";
  g.fillText(text, 96, 50);
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

function roundRect(g: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  g.beginPath();
  g.moveTo(x + r, y);
  g.arcTo(x + w, y, x + w, y + h, r);
  g.arcTo(x + w, y + h, x, y + h, r);
  g.arcTo(x, y + h, x, y, r);
  g.arcTo(x, y, x + w, y, r);
  g.closePath();
}

/** Reklam panosu şeridi: siyah zemin üstünde açık renk yatay çizgiler. */
export function boardTexture(): THREE.CanvasTexture {
  const canvas = document.createElement("canvas");
  canvas.width = 512;
  canvas.height = 32;
  const g = canvas.getContext("2d")!;
  g.fillStyle = "#101216";
  g.fillRect(0, 0, 512, 32);
  g.fillStyle = "#e9ebef";
  g.fillRect(0, 10, 512, 3);
  g.fillStyle = "rgba(255,255,255,0.4)";
  g.fillRect(0, 18, 512, 1);
  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = THREE.RepeatWrapping;
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}
