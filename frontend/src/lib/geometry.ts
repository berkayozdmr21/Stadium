/**
 * Tribün çevresinin geometrisi: backend'deki `StadiumGeometry` ile AYNI formül (aynı örnekleme, aynı yay uzunluğu
 * parametresi). Aynı sonucu verdiği `geometry.test.ts` içinde backend'den alınmış referans değerlerle sınanır;
 * böylece koltukların 3B'de durduğu yer ile sunucunun bildiği düzen ayrışmaz.
 *
 * Sahadan {@code d} metre uzaktaki bir sıra, köşeleri yuvarlatılmış bir dikdörtgendir (süper elips):
 * |x/a|^n + |z/b|^n = 1, a = 52,5 + d, b = 34 + d. u = 0 doğu ucun ortasıdır (+x), artarken güneye (+z) ilerler.
 */

export const PITCH_LENGTH = 105;
export const PITCH_WIDTH = 68;
const SAMPLES = 4096;

export interface Point {
  x: number;
  z: number;
}

export interface Outline {
  d: number;
  /** Örnek noktalarına kadar biriken yay uzunluğu (SAMPLES + 1 eleman). */
  cum: Float64Array;
  perimeter: number;
  exponent: number;
}

function at(d: number, t: number, exponent: number): Point {
  const c = Math.cos(t);
  const s = Math.sin(t);
  const p = 2 / exponent;
  return {
    x: (PITCH_LENGTH / 2 + d) * Math.sign(c) * Math.pow(Math.abs(c), p),
    z: (PITCH_WIDTH / 2 + d) * Math.sign(s) * Math.pow(Math.abs(s), p),
  };
}

const cache = new Map<string, Outline>();

/** Aynı (d, üs) için bir kez hesaplanır; 4096 örnek her sıra için ucuzdur ama 47 sıra x çok çağrı pahalı olurdu. */
export function outline(d: number, exponent: number): Outline {
  const key = `${d.toFixed(4)}|${exponent}`;
  const hit = cache.get(key);
  if (hit) return hit;
  const cum = new Float64Array(SAMPLES + 1);
  let prev = at(d, 0, exponent);
  for (let i = 1; i <= SAMPLES; i++) {
    const cur = at(d, (i * 2 * Math.PI) / SAMPLES, exponent);
    cum[i] = cum[i - 1] + Math.hypot(cur.x - prev.x, cur.z - prev.z);
    prev = cur;
  }
  const result: Outline = { d, cum, perimeter: cum[SAMPLES], exponent };
  cache.set(key, result);
  return result;
}

export function perimeter(d: number, exponent: number): number {
  return outline(d, exponent).perimeter;
}

/** Yay uzunluğunun {@code u} oranındaki nokta. {@code u} 1'e göre kalan alınır (negatif olabilir). */
export function pointOn(o: Outline, u: number): Point {
  const target = (u - Math.floor(u)) * o.perimeter;
  let lo = 0;
  let hi = SAMPLES;
  while (lo < hi) {
    const mid = (lo + hi) >>> 1;
    if (o.cum[mid] < target) lo = mid + 1;
    else hi = mid;
  }
  const i = Math.max(1, lo);
  const span = o.cum[i] - o.cum[i - 1];
  const f = span === 0 ? 0 : (target - o.cum[i - 1]) / span;
  return at(o.d, (i - 1 + f) * ((2 * Math.PI) / SAMPLES), o.exponent);
}

export function pointAt(d: number, u: number, exponent: number): Point {
  return pointOn(outline(d, exponent), u);
}

/** Çevrenin {@code u} noktasında dışa bakan birim normal (saha merkezinden uzağa). */
export function outwardNormal(o: Outline, u: number): Point {
  const eps = 0.5 / o.perimeter; // yarım metre
  const a = pointOn(o, u - eps);
  const b = pointOn(o, u + eps);
  let nx = b.z - a.z;
  let nz = -(b.x - a.x);
  const len = Math.hypot(nx, nz) || 1;
  nx /= len;
  nz /= len;
  const p = pointOn(o, u);
  // Normal merkezden uzağa baksın
  if (nx * p.x + nz * p.z < 0) {
    nx = -nx;
    nz = -nz;
  }
  return { x: nx, z: nz };
}
