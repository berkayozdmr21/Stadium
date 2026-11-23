import * as THREE from "three";
import { mergeGeometries } from "three/addons/utils/BufferGeometryUtils.js";

import type { BlockSpec, Layout, TierSpec, Zone } from "../api";
import { hashOf, seeded } from "../lib/format";
import { outline, outwardNormal, perimeter, pointOn, PITCH_LENGTH, PITCH_WIDTH } from "../lib/geometry";
import { type BlockIndex, indexOf, localIndex } from "../lib/seatIndex";
import { CameraRig } from "./cameraRig";
import { boardTexture, labelTexture, pitchTexture } from "./textures";

/** Koltuğun durumu: dizide bir bayt. */
export const SEAT_AVAILABLE = 0;
export const SEAT_HELD = 1;
export const SEAT_SOLD = 2;
export const SEAT_MINE = 3;

export type OverviewMode = "availability" | "price";
export type ViewKind = "overview" | "block" | "seat";

const AISLE = 0.45;           // blok kenarlarında bırakılan koridor (metre, her yana)
// Ölçüler yaklaşıktır; kuş bakışı ve iç fotoğraflara bakılarak ayarlandı (resmî plan değildir).
const FACADE_OFFSET = 57;     // cephe sütunlarının sahadan uzaklığı (m)
const ROOF_OUTER = 58.5;      // çatının dış kenarı
const ROOF_INNER = 8;         // çatı açıklığının kenarı: fotoğraflarda açıklık sahayı çok az aşıyor
const ROOF_Y_OUTER = 33;      // dış kenarda yükseklik
const ROOF_Y_INNER = 41;      // iç halkada yükseklik: çatı içe doğru yükselir
const COLUMN_HEIGHT = 26;

const SEAT_WHITE = new THREE.Color("#e4e6ea");
const SEAT_DARK = new THREE.Color("#1a1d22");

const ZONE_COLOR: Record<Zone, THREE.Color> = {
  VIP: new THREE.Color("#e8c36a"),
  KAT1: new THREE.Color("#f2f2ef"),
  KAT2: new THREE.Color("#b3bbc8"),
  KAT3: new THREE.Color("#7c8696"),
};
const HELD_COLOR = new THREE.Color("#f2a93b");
const SOLD_COLOR = new THREE.Color("#2c3037");
const MINE_COLOR = new THREE.Color("#7dffb4").multiplyScalar(1.5);
const DIM = 0.22;

const AVAIL_STOPS: { at: number; color: THREE.Color }[] = [
  { at: 0.0, color: new THREE.Color("#2b2e35") },
  { at: 0.12, color: new THREE.Color("#e5533d") },
  { at: 0.38, color: new THREE.Color("#f2b33b") },
  { at: 0.7, color: new THREE.Color("#dfe7ea") },
  { at: 1.0, color: new THREE.Color("#ffffff") },
];

function availabilityColor(ratio: number, out: THREE.Color): THREE.Color {
  const r = Math.min(1, Math.max(0, ratio));
  for (let i = 1; i < AVAIL_STOPS.length; i++) {
    if (r <= AVAIL_STOPS[i].at) {
      const a = AVAIL_STOPS[i - 1];
      const b = AVAIL_STOPS[i];
      return out.copy(a.color).lerp(b.color, (r - a.at) / (b.at - a.at));
    }
  }
  return out.copy(AVAIL_STOPS[AVAIL_STOPS.length - 1].color);
}

interface BlockRuntime {
  spec: BlockSpec;
  tier: TierSpec;
  index: BlockIndex;
  plate: THREE.Mesh;
  seats: THREE.InstancedMesh;
  crowd: THREE.InstancedMesh;
  crowdMatrices: Float32Array;
  /** Koltuk başına dünya konumu (x, y, z) ve oturma yönü. */
  positions: Float32Array;
  yaws: Float32Array;
  center: THREE.Vector3;
  normal: THREE.Vector3;
  label: THREE.Sprite;
  available: number;
  total: number;
  /** Koltuk durumları yüklendiyse (blok açılınca), değilse null. */
  states: Uint8Array | null;
}

/**
 * Tüpraş Stadyumu'nun 3B modeli. Sahne tamamen koddan üretilir (görsel dosyası yoktur): saha, iki katlı tribün,
 * her blok için bir basamak plakası ve koltuk/seyirci kümesi, çatı, cephe ve sütunlar.
 *
 * Koltuk konumları backend'in verdiği düzenden (blok aralığı, sıra başına koltuk sayısı) ve `lib/geometry.ts`
 * içindeki, backend ile aynı sonucu veren formülden hesaplanır.
 */
export class StadiumScene {
  readonly renderer: THREE.WebGLRenderer;
  readonly scene = new THREE.Scene();
  readonly camera = new THREE.PerspectiveCamera(42, 1, 0.3, 1800);
  readonly rig: CameraRig;
  readonly blocks = new Map<string, BlockRuntime>();
  readonly totalSeats: number;

  private readonly layout: Layout;
  private readonly raycaster = new THREE.Raycaster();
  private readonly plates: THREE.Mesh[] = [];
  private readonly hemi = new THREE.HemisphereLight(0x9db4d6, 0x181a1f, 0.7);
  private readonly sun = new THREE.DirectionalLight(0xffffff, 0.9);
  // Çatı: ışık geçiren beyaz membran (yastık biçimli paneller), çelik halka, kaburgalar ve lambalar.
  private readonly roofMaterial = new THREE.MeshLambertMaterial({
    color: 0xffffff, vertexColors: true, side: THREE.DoubleSide, transparent: true, opacity: 0, emissive: 0x39404c,
  });
  private readonly roofSteelMaterial = new THREE.MeshLambertMaterial({ color: 0x2b3038, side: THREE.DoubleSide, transparent: true, opacity: 0 });
  private readonly roofRimMaterial = new THREE.MeshLambertMaterial({ color: 0xf2f4f7, side: THREE.DoubleSide, transparent: true, opacity: 0, emissive: 0x30353d });
  private readonly roofLineMaterial = new THREE.LineBasicMaterial({ color: 0x59616f, transparent: true, opacity: 0 });
  private readonly roofLampMaterial = new THREE.MeshBasicMaterial({ color: 0xfff4d6, transparent: true, opacity: 0 });
  private readonly roofGroup = new THREE.Group();
  private readonly timeUniform = { value: 0 };
  private readonly waveUniform = { value: 0 };

  private overviewMode: OverviewMode = "availability";
  private focusBlock: string | null = null;
  private hoverBlock: string | null = null;
  private hoverSeat: { block: string; local: number } | null = null;
  private view: ViewKind = "overview";
  private roofTarget = 0.2;
  private roofUser = false;
  private appearance: "info" | "real" = "info";
  private lightTarget = 0;
  private lightLevel = 0;
  private waveTarget = 0;
  private running = false;
  private rafId = 0;
  private lastTime = 0;
  private disposed = false;
  private readonly tmpColor = new THREE.Color();
  private readonly tmpMatrix = new THREE.Matrix4();

  constructor(canvas: HTMLCanvasElement, layout: Layout) {
    this.layout = layout;
    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: "high-performance" });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.08;

    this.scene.background = new THREE.Color(0x06080c);
    this.scene.fog = new THREE.Fog(0x080b11, 300, 900);
    this.rig = new CameraRig(this.camera);

    this.scene.add(this.hemi);
    this.sun.position.set(-60, 140, 80);
    this.scene.add(this.sun);

    this.buildSky();
    this.buildGround();
    this.buildPitch();
    this.buildShell();
    this.buildBlocks();
    this.buildRoof();
    this.totalSeats = [...this.blocks.values()].reduce((n, b) => n + b.index.total, 0);
  }

  // ------------------------------------------------------------------------------------------ yapı

  private tier(code: string): TierSpec {
    return this.layout.tiers.find((t) => t.code === code)!;
  }

  private rowOffset(t: TierSpec, row: number): number {
    return t.startOffset + row * t.rowDepth;
  }

  private rowHeight(t: TierSpec, row: number): number {
    return t.startHeight + row * t.rowDepth * Math.tan((t.riseDegrees * Math.PI) / 180);
  }

  private buildSky(): void {
    // Akşam gökyüzü: ufukta soluk mavi, tepede koyu; seat görünümünde çatının üstünden görünür
    const geo = new THREE.SphereGeometry(1100, 32, 16);
    const colors: number[] = [];
    const pos = geo.attributes.position;
    const top = new THREE.Color("#04070d");
    const horizon = new THREE.Color("#27384f");
    for (let i = 0; i < pos.count; i++) {
      const y = pos.getY(i) / 1100;
      const c = horizon.clone().lerp(top, Math.min(1, Math.max(0, y * 1.6)));
      colors.push(c.r, c.g, c.b);
    }
    geo.setAttribute("color", new THREE.Float32BufferAttribute(colors, 3));
    const sky = new THREE.Mesh(geo, new THREE.MeshBasicMaterial({ vertexColors: true, side: THREE.BackSide, fog: false }));
    this.scene.add(sky);
  }

  private buildGround(): void {
    const ground = new THREE.Mesh(
      new THREE.CircleGeometry(900, 64),
      new THREE.MeshLambertMaterial({ color: 0x0c0f13 }),
    );
    ground.rotation.x = -Math.PI / 2;
    ground.position.y = -0.12;
    this.scene.add(ground);

    // Stadyumun hemen yanında deniz (Boğaz) var: fotoğraflarda kuş bakışında suyla çevrili görünüyor
    const sea = new THREE.Mesh(
      new THREE.PlaneGeometry(2600, 1000),
      new THREE.MeshLambertMaterial({ color: 0x103550, emissive: 0x06182a }),
    );
    sea.rotation.x = -Math.PI / 2;
    sea.position.set(0, -0.06, -680);
    this.scene.add(sea);

    // Sütunların dışında taş döşemeli meydan ve çevre yolu
    this.scene.add(this.ringStrip([[FACADE_OFFSET + 1, 0.04], [FACADE_OFFSET + 24, 0.04]], 260,
      new THREE.MeshLambertMaterial({ color: 0x3a3d44, side: THREE.DoubleSide })));
    this.scene.add(this.ringStrip([[FACADE_OFFSET + 24, 0.05], [FACADE_OFFSET + 38, 0.05]], 260,
      new THREE.MeshLambertMaterial({ color: 0x1b1d22, side: THREE.DoubleSide })));
  }

  private buildPitch(): void {
    const pitch = new THREE.Mesh(
      new THREE.PlaneGeometry(PITCH_LENGTH, PITCH_WIDTH),
      new THREE.MeshLambertMaterial({ map: pitchTexture(), emissive: 0x0b2a12, emissiveIntensity: 0.55 }),
    );
    pitch.rotation.x = -Math.PI / 2;
    pitch.position.y = 0.02;
    this.scene.add(pitch);

    // Saha çevresi: koşu alanı
    this.scene.add(
      this.ringStrip([[0, 0.01], [7, 0.01]], 220, new THREE.MeshLambertMaterial({ color: 0x1d4a26, side: THREE.DoubleSide })),
    );

    // Kaleler
    const goalMat = new THREE.MeshLambertMaterial({ color: 0xf5f5f2 });
    const netMat = new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.16, side: THREE.DoubleSide });
    for (const sign of [-1, 1]) {
      const g = new THREE.Group();
      const post = (z: number) => {
        const m = new THREE.Mesh(new THREE.BoxGeometry(0.14, 2.44, 0.14), goalMat);
        m.position.set(0, 1.22, z);
        return m;
      };
      g.add(post(-3.66), post(3.66));
      const bar = new THREE.Mesh(new THREE.BoxGeometry(0.14, 0.14, 7.46), goalMat);
      bar.position.set(0, 2.44, 0);
      g.add(bar);
      const back = new THREE.Mesh(new THREE.PlaneGeometry(7.32, 2.44), netMat);
      back.rotation.y = Math.PI / 2;
      back.position.set(sign * 2.0, 1.22, 0);
      g.add(back);
      g.position.set(sign * (PITCH_LENGTH / 2), 0, 0);
      this.scene.add(g);
    }

    // Reklam panoları: sahayı çevreleyen alçak şerit
    const boardTex = boardTexture();
    boardTex.repeat.set(26, 1);
    this.scene.add(
      this.ringStrip([[3.4, 0.0], [3.4, 0.95]], 260, new THREE.MeshBasicMaterial({ map: boardTex, side: THREE.DoubleSide })),
    );
  }

  /** Tribünlerin arkasındaki kabuk: konkur rampası, üst katın alt yüzü, taş cephe ve sütunlar. */
  private buildShell(): void {
    const lower = this.tier("A");
    const upper = this.tier("B");
    const lowerEndD = this.rowOffset(lower, lower.rows);
    const lowerEndY = this.rowHeight(lower, lower.rows);
    const upperStartD = this.rowOffset(upper, 0);
    const upperStartY = this.rowHeight(upper, 0);
    const upperEndD = this.rowOffset(upper, upper.rows);
    const upperEndY = this.rowHeight(upper, upper.rows);

    // Alt katın arkası ile üst katın önü arasındaki kat arası (konkur)
    const concourse = new THREE.MeshLambertMaterial({ color: 0x353a43, side: THREE.DoubleSide });
    this.scene.add(this.ringStrip([[lowerEndD, lowerEndY], [upperStartD, lowerEndY], [upperStartD, upperStartY]], 220, concourse));
    // Alt katın ön korkuluğu
    this.scene.add(
      this.ringStrip([[lower.startOffset, 0], [lower.startOffset, lower.startHeight + 0.9]], 220,
        new THREE.MeshLambertMaterial({ color: 0x1a1d22, side: THREE.DoubleSide })),
    );
    // Üst katın arkasındaki duvar
    this.scene.add(this.ringStrip([[upperEndD, upperEndY], [FACADE_OFFSET - 0.8, upperEndY]], 220, concourse));

    // Üst katın tepesinde, çatının altında yanan beyaz ışık şeridi (fotoğraflarda belirgin)
    this.scene.add(
      this.ringStrip([[upperEndD + 0.3, upperEndY + 2.4], [upperEndD + 0.3, upperEndY + 3.1]], 240,
        new THREE.MeshBasicMaterial({ color: 0xf4f7ff, side: THREE.DoubleSide })),
    );

    // Cephe: koyu iç duvar, krem sütun dizisi, sütunların üstünde koyu kiriş şeridi, taş kaide
    const limestone = new THREE.MeshLambertMaterial({ color: 0xd9d1bd, side: THREE.DoubleSide });
    this.scene.add(
      this.ringStrip([[FACADE_OFFSET - 0.8, 0], [FACADE_OFFSET - 0.8, ROOF_Y_OUTER]], 260,
        new THREE.MeshLambertMaterial({ color: 0x23262c, side: THREE.DoubleSide })),
    );
    this.scene.add(
      this.ringStrip([[FACADE_OFFSET + 0.6, COLUMN_HEIGHT], [FACADE_OFFSET + 0.6, ROOF_Y_OUTER - 0.4]], 260,
        new THREE.MeshLambertMaterial({ color: 0x14171c, side: THREE.DoubleSide })),
    );
    this.scene.add(this.ringStrip([[FACADE_OFFSET + 0.8, 0], [FACADE_OFFSET + 0.8, 2.4]], 260, limestone));

    const o = outline(FACADE_OFFSET + 0.7, this.layout.exponent);
    const count = Math.round(o.perimeter / 3.3);
    const columns = new THREE.InstancedMesh(
      new THREE.BoxGeometry(0.85, COLUMN_HEIGHT, 0.85),
      new THREE.MeshLambertMaterial({ color: 0xe9e1cc }),
      count,
    );
    for (let i = 0; i < count; i++) {
      const p = pointOn(o, i / count);
      this.tmpMatrix.makeTranslation(p.x, COLUMN_HEIGHT / 2, p.z);
      columns.setMatrixAt(i, this.tmpMatrix);
    }
    this.scene.add(columns);
  }

  /**
   * Çatı: fotoğraflardaki gibi yastık biçimli beyaz membran paneller (çevrede 56, radyal 3 sıra), içe doğru
   * yükselir ve ortada oval bir açıklık bırakır. Açıklığın kenarında çelik halka, yürüyüş yolu ve lambalar vardır.
   */
  private buildRoof(): void {
    const exp = this.layout.exponent;
    const PANELS = 56;
    const RINGS = 3;
    const SUB = 4;
    const cols = PANELS * SUB;
    const rowsN = RINGS * SUB;
    const positions: number[] = [];
    const colors: number[] = [];
    const indices: number[] = [];
    const white = new THREE.Color("#f7f9fc");
    const seam = new THREE.Color("#a9b1bf");
    const c = new THREE.Color();
    for (let r = 0; r <= rowsN; r++) {
      const t = r / rowsN;                                   // 0 dış kenar, 1 iç halka
      const o = outline(ROOF_OUTER + (ROOF_INNER - ROOF_OUTER) * t, exp);
      const baseY = ROOF_Y_OUTER + (ROOF_Y_INNER - ROOF_Y_OUTER) * t;
      const fr = (r % SUB) / SUB;
      for (let j = 0; j <= cols; j++) {
        const fu = (j % SUB) / SUB;
        const bulge = Math.sin(Math.PI * fu) * Math.sin(Math.PI * fr);
        const p = pointOn(o, j / cols);
        positions.push(p.x, baseY + bulge * 1.15, p.z);
        c.copy(white).lerp(seam, (1 - bulge) * 0.4);
        colors.push(c.r, c.g, c.b);
      }
    }
    const rowLen = cols + 1;
    for (let r = 0; r < rowsN; r++) {
      for (let j = 0; j < cols; j++) {
        const a = r * rowLen + j;
        const b = (r + 1) * rowLen + j;
        indices.push(a, b, a + 1, b, b + 1, a + 1);
      }
    }
    const geo = new THREE.BufferGeometry();
    geo.setAttribute("position", new THREE.Float32BufferAttribute(positions, 3));
    geo.setAttribute("color", new THREE.Float32BufferAttribute(colors, 3));
    geo.setIndex(indices);
    geo.computeVertexNormals();
    this.roofGroup.add(new THREE.Mesh(geo, this.roofMaterial));

    // Çelik kaburgalar (radyal) ve halka kirişler: çatının altından görünen taşıyıcı iskelet
    const ribs: number[] = [];
    const yAt = (d: number) => ROOF_Y_OUTER + ((ROOF_Y_INNER - ROOF_Y_OUTER) * (ROOF_OUTER - d)) / (ROOF_OUTER - ROOF_INNER) - 0.25;
    const outerO = outline(ROOF_OUTER, exp);
    const innerO = outline(ROOF_INNER, exp);
    for (let i = 0; i < PANELS; i++) {
      const u = i / PANELS;
      const a = pointOn(outerO, u);
      const b = pointOn(innerO, u);
      ribs.push(a.x, yAt(ROOF_OUTER), a.z, b.x, yAt(ROOF_INNER), b.z);
    }
    for (let k = 1; k < RINGS; k++) {
      const d = ROOF_OUTER + ((ROOF_INNER - ROOF_OUTER) * k) / RINGS;
      const o = outline(d, exp);
      for (let j = 0; j < 240; j++) {
        const a = pointOn(o, j / 240);
        const b = pointOn(o, (j + 1) / 240);
        ribs.push(a.x, yAt(d), a.z, b.x, yAt(d), b.z);
      }
    }
    // Açıklık halkasından aşağı sarkan çapraz çubuklar
    const strutO = outline(ROOF_INNER + 20, exp);
    for (let i = 0; i < 28; i++) {
      const u = (i + 0.5) / 28;
      const a = pointOn(innerO, u);
      const b = pointOn(strutO, u);
      ribs.push(a.x, ROOF_Y_INNER - 1.6, a.z, b.x, yAt(ROOF_INNER + 20), b.z);
    }
    const ribGeo = new THREE.BufferGeometry();
    ribGeo.setAttribute("position", new THREE.Float32BufferAttribute(ribs, 3));
    this.roofGroup.add(new THREE.LineSegments(ribGeo, this.roofLineMaterial));

    // Dış kenardaki beyaz şerit ve açıklığın çelik halkası + yürüyüş yolu
    this.roofGroup.add(this.ringStrip([[ROOF_OUTER, ROOF_Y_OUTER - 0.5], [ROOF_OUTER, ROOF_Y_OUTER + 1.0]], 240, this.roofRimMaterial));
    this.roofGroup.add(this.ringStrip([[ROOF_INNER, ROOF_Y_INNER - 1.8], [ROOF_INNER, ROOF_Y_INNER + 0.9]], 240, this.roofRimMaterial));
    this.roofGroup.add(this.ringStrip([[ROOF_INNER, ROOF_Y_INNER - 1.8], [ROOF_INNER + 3.2, ROOF_Y_INNER - 1.8]], 240, this.roofSteelMaterial));

    // Halkanın altındaki lambalar
    const lampO = outline(ROOF_INNER + 1.6, exp);
    const n = 96;
    const lamps = new THREE.InstancedMesh(new THREE.SphereGeometry(0.55, 8, 6), this.roofLampMaterial, n);
    for (let i = 0; i < n; i++) {
      const p = pointOn(lampO, i / n);
      this.tmpMatrix.makeTranslation(p.x, ROOF_Y_INNER - 2.2, p.z);
      lamps.setMatrixAt(i, this.tmpMatrix);
    }
    this.roofGroup.add(lamps);
    this.scene.add(this.roofGroup);
  }

  /**
   * Bir profili ((d, y) çiftleri listesi) stadyumun çevresi boyunca süpürerek kapalı bir halka yüzeyi üretir.
   * Cephe, çatı, konkur ve panolar bununla çizilir.
   */
  private ringStrip(profile: [number, number][], segments: number, material: THREE.Material): THREE.Mesh {
    const outlines = profile.map(([d]) => outline(d, this.layout.exponent));
    const positions: number[] = [];
    for (let j = 0; j <= segments; j++) {
      const u = j / segments;
      for (let k = 0; k < profile.length; k++) {
        const p = pointOn(outlines[k], u);
        positions.push(p.x, profile[k][1], p.z);
      }
    }
    const indices: number[] = [];
    const rowLen = profile.length;
    for (let j = 0; j < segments; j++) {
      for (let k = 0; k < rowLen - 1; k++) {
        const a = j * rowLen + k;
        const b = (j + 1) * rowLen + k;
        indices.push(a, b, a + 1, b, b + 1, a + 1);
      }
    }
    const geo = new THREE.BufferGeometry();
    geo.setAttribute("position", new THREE.Float32BufferAttribute(positions, 3));
    geo.setIndex(indices);
    geo.computeVertexNormals();
    return new THREE.Mesh(geo, material);
  }

  /** Bir blok: basamak plakası + koltuk kümesi + seyirci kümesi + etiket. */
  private buildBlocks(): void {
    const seatGeo = mergeGeometries([
      new THREE.BoxGeometry(0.44, 0.1, 0.42).translate(0, 0.3, 0),
      new THREE.BoxGeometry(0.44, 0.4, 0.06).translate(0, 0.52, -0.2),
    ])!;
    const personGeo = mergeGeometries([
      new THREE.BoxGeometry(0.4, 0.52, 0.26).translate(0, 0.72, 0.02),
      new THREE.BoxGeometry(0.2, 0.2, 0.2).translate(0, 1.1, 0.02),
    ])!;
    const seatMat = new THREE.MeshLambertMaterial({ color: 0xffffff });
    const personMat = new THREE.MeshLambertMaterial({ color: 0xffffff });
    personMat.onBeforeCompile = (shader) => {
      shader.uniforms.uTime = this.timeUniform;
      shader.uniforms.uWave = this.waveUniform;
      shader.vertexShader = shader.vertexShader
        .replace("#include <common>", "#include <common>\nuniform float uTime;\nuniform float uWave;")
        .replace(
          "#include <begin_vertex>",
          `#include <begin_vertex>
#ifdef USE_INSTANCING
  float ph = instanceMatrix[3][0] * 0.11 + instanceMatrix[3][2] * 0.13;
  transformed.y += max(0.0, sin(uTime * 2.4 - ph)) * (0.035 + 0.22 * uWave);
#endif`,
        );
    };

    for (const spec of this.layout.blocks) {
      const tier = this.tier(spec.tier);
      const index = indexOf(spec);
      const n = index.total;

      // --- basamak plakası: her sıra için eğimli bir şerit
      const plate = this.buildPlate(spec, tier);
      this.scene.add(plate);
      this.plates.push(plate);

      // --- koltuk ve seyirci konumları
      const positions = new Float32Array(n * 3);
      const yaws = new Float32Array(n);
      const crowdMatrices = new Float32Array(n * 16);
      const seats = new THREE.InstancedMesh(seatGeo, seatMat, n);
      const crowd = new THREE.InstancedMesh(personGeo, personMat, n);
      const gap = AISLE / perimeter(this.rowOffset(tier, 0), this.layout.exponent);
      const span = spec.u1 - spec.u0;
      const rnd = seeded(hashOf(spec.code));
      const q = new THREE.Quaternion();
      const scale = new THREE.Vector3(1, 1, 1);
      const up = new THREE.Vector3(0, 1, 0);
      let k = 0;
      let cx = 0, cy = 0, cz = 0;
      for (let r = 0; r < spec.seatsPerRow.length; r++) {
        const d = this.rowOffset(tier, r) + tier.rowDepth * 0.5;
        const o = outline(d, this.layout.exponent);
        const rowCount = spec.seatsPerRow[r];
        const rise = tier.rowDepth * Math.tan((tier.riseDegrees * Math.PI) / 180);
        const y = this.rowHeight(tier, r) + rise * 0.5;
        const uStart = spec.u0 + gap;
        const uSpan = span - 2 * gap;
        for (let i = 0; i < rowCount; i++) {
          const u = uStart + ((i + 0.5) / rowCount) * uSpan;
          const p = pointOn(o, u);
          const nrm = outwardNormal(o, u);
          const yaw = Math.atan2(-nrm.x, -nrm.z);         // koltuk sahaya bakar
          positions[k * 3] = p.x;
          positions[k * 3 + 1] = y;
          positions[k * 3 + 2] = p.z;
          yaws[k] = yaw;
          q.setFromAxisAngle(up, yaw);
          this.tmpMatrix.compose(new THREE.Vector3(p.x, y, p.z), q, scale);
          seats.setMatrixAt(k, this.tmpMatrix);
          this.tmpMatrix.toArray(crowdMatrices, k * 16);
          crowd.setMatrixAt(k, this.tmpMatrix);
          // Seyirci rengi: siyah-beyaz ağırlıklı, bir kısmı gri
          const roll = rnd();
          this.tmpColor.set(roll < 0.5 ? "#15171b" : roll < 0.82 ? "#f1f1ee" : roll < 0.94 ? "#8e949e" : "#3a3f48");
          crowd.setColorAt(k, this.tmpColor);
          if (r === Math.floor(spec.seatsPerRow.length / 2) && i === Math.floor(rowCount / 2)) {
            cx = p.x;
            cy = y;
            cz = p.z;
          }
          k++;
        }
      }
      seats.instanceMatrix.needsUpdate = true;
      crowd.instanceMatrix.needsUpdate = true;
      seats.computeBoundingSphere();
      seats.frustumCulled = false;
      crowd.frustumCulled = false;
      crowd.visible = false;
      this.scene.add(seats, crowd);

      const center = new THREE.Vector3(cx, cy, cz);
      const mid = outline(this.rowOffset(tier, Math.floor(tier.rows / 2)), this.layout.exponent);
      const nrm = outwardNormal(mid, (spec.u0 + spec.u1) / 2);
      const normal = new THREE.Vector3(nrm.x, 0, nrm.z);

      const label = new THREE.Sprite(
        new THREE.SpriteMaterial({ map: labelTexture(spec.code), transparent: true, depthTest: false, depthWrite: false, fog: false }),
      );
      label.scale.set(11, 5.5, 1);
      label.position.set(cx, cy + 6, cz);
      label.renderOrder = 10;
      this.scene.add(label);

      const rt: BlockRuntime = {
        spec, tier, index, plate, seats, crowd, crowdMatrices, positions, yaws, center, normal, label,
        available: spec.totalSeats, total: spec.totalSeats, states: null,
      };
      plate.userData.block = spec.code;
      this.blocks.set(spec.code, rt);
      this.recolor(rt);
      this.refreshCrowd(rt);
    }
  }

  private buildPlate(spec: BlockSpec, tier: TierSpec): THREE.Mesh {
    const positions: number[] = [];
    const colors: number[] = [];
    const indices: number[] = [];
    const perim = perimeter(this.rowOffset(tier, 0), this.layout.exponent);
    const gap = (AISLE - 0.1) / perim;
    const u0 = spec.u0 + gap;
    const u1 = spec.u1 - gap;
    const cols = Math.max(2, Math.ceil(((u1 - u0) * perim) / 3));
    const c = new THREE.Color();
    for (let r = 0; r < tier.rows; r++) {
      const oA = outline(this.rowOffset(tier, r), this.layout.exponent);
      const oB = outline(this.rowOffset(tier, r + 1), this.layout.exponent);
      const yA = this.rowHeight(tier, r);
      const yB = this.rowHeight(tier, r + 1);
      c.set(r % 2 === 0 ? "#3a3f48" : "#444a54");
      const base = positions.length / 3;
      for (let j = 0; j <= cols; j++) {
        const u = u0 + ((u1 - u0) * j) / cols;
        const a = pointOn(oA, u);
        const b = pointOn(oB, u);
        positions.push(a.x, yA, a.z, b.x, yB, b.z);
        colors.push(c.r, c.g, c.b, c.r, c.g, c.b);
      }
      for (let j = 0; j < cols; j++) {
        const i = base + j * 2;
        indices.push(i, i + 2, i + 1, i + 1, i + 2, i + 3);
      }
    }
    const geo = new THREE.BufferGeometry();
    geo.setAttribute("position", new THREE.Float32BufferAttribute(positions, 3));
    geo.setAttribute("color", new THREE.Float32BufferAttribute(colors, 3));
    geo.setIndex(indices);
    geo.computeVertexNormals();
    geo.computeBoundingSphere();
    const mat = new THREE.MeshLambertMaterial({ vertexColors: true, side: THREE.DoubleSide });
    return new THREE.Mesh(geo, mat);
  }

  // ------------------------------------------------------------------------------------------ durum

  setOverviewMode(mode: OverviewMode): void {
    this.overviewMode = mode;
    for (const rt of this.blocks.values()) this.recolor(rt);
  }

  setAvailability(code: string, available: number, total: number): void {
    const rt = this.blocks.get(code);
    if (!rt) return;
    rt.available = available;
    rt.total = total;
    this.recolor(rt);
    if (!rt.states) this.refreshCrowd(rt);
  }

  /** Bloğun koltuk durumları yüklendiğinde (veya temizlendiğinde) çağrılır. */
  setSeatStates(code: string, states: Uint8Array | null): void {
    const rt = this.blocks.get(code);
    if (!rt) return;
    rt.states = states;
    this.recolor(rt);
    this.refreshCrowd(rt);
  }

  patchSeatState(code: string, local: number, state: number): void {
    const rt = this.blocks.get(code);
    if (!rt || !rt.states) return;
    rt.states[local] = state;
    this.recolor(rt);
    this.refreshCrowd(rt);
  }

  setFocusBlock(code: string | null): void {
    if (this.focusBlock === code) return;
    this.focusBlock = code;
    for (const rt of this.blocks.values()) this.recolor(rt);
  }

  setHoverBlock(code: string | null): void {
    if (this.hoverBlock === code) return;
    const prev = this.hoverBlock;
    this.hoverBlock = code;
    for (const c of [prev, code]) {
      const rt = c ? this.blocks.get(c) : null;
      if (rt) {
        (rt.plate.material as THREE.MeshLambertMaterial).emissive.set(c === code ? 0x44505f : 0x000000);
        this.recolor(rt);
      }
    }
  }

  setHoverSeat(hover: { block: string; local: number } | null): void {
    const prev = this.hoverSeat;
    this.hoverSeat = hover;
    for (const h of [prev, hover]) {
      const rt = h ? this.blocks.get(h.block) : null;
      if (rt && h) this.paintSeat(rt, h.local);
    }
    for (const h of [prev, hover]) {
      const rt = h ? this.blocks.get(h.block) : null;
      if (rt) rt.seats.instanceColor!.needsUpdate = true;
    }
  }

  /** Kiplere göre çatı, ışıklar ve seyirci görünürlüğü. */
  setView(kind: ViewKind): void {
    this.view = kind;
    const appearance = kind === "seat" ? "real" : "info";
    if (appearance !== this.appearance) {
      this.appearance = appearance;
      for (const rt of this.blocks.values()) this.recolor(rt);
    }
    this.updateRoofTarget();
    this.lightTarget = kind === "seat" ? 1 : 0;
    this.waveTarget = kind === "seat" ? 0.3 : 0;
    for (const rt of this.blocks.values()) {
      rt.crowd.visible = kind !== "overview";
    }
  }

  /** Kuş bakışında çatı gösterilir (kapalı hâli stadyumun simgesi) ya da koltukları görmek için açılır. */
  setRoofVisible(visible: boolean): void {
    this.roofUser = visible;
    this.updateRoofTarget();
  }

  get roofShown(): boolean {
    return this.roofUser;
  }

  private updateRoofTarget(): void {
    // Kuş bakışında çatı varsayılan olarak hayalet gibi yarı saydamdır: bloklar seçilebilsin diye
    this.roofTarget = this.view === "seat" || this.roofUser ? 1 : 0.2;
  }

  // ------------------------------------------------------------------------------------------ renk

  /** Gerçek stadyumun siyah-beyaz koltuk mozaiği: alt kat açık zeminde koyu desen, üst kat koyu zeminde açık desen. */
  private mosaicColor(rt: BlockRuntime, local: number, out: THREE.Color): THREE.Color {
    const row = rt.index.rowStart.length;
    let r = 0;
    let lo = 0;
    let hi = row - 1;
    while (lo < hi) {
      const mid = (lo + hi + 1) >>> 1;
      if (rt.index.rowStart[mid] <= local) lo = mid;
      else hi = mid - 1;
    }
    r = lo;
    const number = local - rt.index.rowStart[r];
    const seed = hashOf(rt.spec.code) % 11;
    // Çapraz şeritler: aynı şerit içinde 6 koltuk yan yana aynı renkte
    const band = Math.floor((number + r * 3 + seed) / 6);
    const patch = band % 4 === 0 || (band + r) % 9 === 0;
    const lowerTier = rt.spec.tier === "A";
    const dark = lowerTier ? patch : !patch;
    return out.copy(dark ? SEAT_DARK : SEAT_WHITE);
  }

  private seatColor(rt: BlockRuntime, local: number, out: THREE.Color): THREE.Color {
    const dimmed = this.focusBlock !== null && this.focusBlock !== rt.spec.code;
    if (this.appearance === "real") {
      if (rt.states && rt.states[local] === SEAT_MINE) return out.copy(MINE_COLOR);
      return this.mosaicColor(rt, local, out);
    }
    if (rt.states) {
      const s = rt.states[local];
      if (s === SEAT_MINE) out.copy(MINE_COLOR);
      else if (s === SEAT_HELD) out.copy(HELD_COLOR);
      else if (s === SEAT_SOLD) out.copy(SOLD_COLOR);
      else out.copy(ZONE_COLOR[rt.spec.zone]);
    } else if (this.overviewMode === "price") {
      out.copy(ZONE_COLOR[rt.spec.zone]);
    } else {
      availabilityColor(rt.total === 0 ? 0 : rt.available / rt.total, out);
    }
    if (dimmed) out.multiplyScalar(DIM);
    else if (this.focusBlock === rt.spec.code) out.multiplyScalar(1.5); // seçilen blok aydınlık: koltuklar seçilebilir görünsün
    else if (this.hoverBlock === rt.spec.code && !this.focusBlock) out.multiplyScalar(1.5);
    return out;
  }

  private paintSeat(rt: BlockRuntime, local: number): void {
    this.seatColor(rt, local, this.tmpColor);
    const h = this.hoverSeat;
    if (h && h.block === rt.spec.code && h.local === local) this.tmpColor.multiplyScalar(1.7).lerp(new THREE.Color(1, 1, 1), 0.35);
    rt.seats.setColorAt(local, this.tmpColor);
  }

  private recolor(rt: BlockRuntime): void {
    for (let i = 0; i < rt.index.total; i++) this.paintSeat(rt, i);
    if (rt.seats.instanceColor) rt.seats.instanceColor.needsUpdate = true;
  }

  /** Dolu koltuklara seyirci koyar. Yüklenmemiş bloklarda doluluk oranına göre sabit tohumla dağıtılır. */
  private refreshCrowd(rt: BlockRuntime): void {
    const n = rt.index.total;
    const ratioTaken = rt.total === 0 ? 0 : 1 - rt.available / rt.total;
    const seed = hashOf(rt.spec.code);
    const m = this.tmpMatrix;
    const zero = new THREE.Matrix4().makeScale(0, 0, 0);
    for (let i = 0; i < n; i++) {
      let occupied: boolean;
      if (rt.states) {
        const s = rt.states[i];
        occupied = s === SEAT_HELD || s === SEAT_SOLD;
      } else {
        occupied = ((Math.imul(i + 1, 2654435761) ^ seed) >>> 0) / 4294967296 < ratioTaken;
      }
      if (occupied) rt.crowd.setMatrixAt(i, m.fromArray(rt.crowdMatrices, i * 16));
      else rt.crowd.setMatrixAt(i, zero);
    }
    rt.crowd.instanceMatrix.needsUpdate = true;
    if (rt.crowd.instanceColor) rt.crowd.instanceColor.needsUpdate = true;
  }

  // ------------------------------------------------------------------------------------------ seçim

  private ray(ndcX: number, ndcY: number): THREE.Raycaster {
    this.raycaster.setFromCamera(new THREE.Vector2(ndcX, ndcY), this.camera);
    return this.raycaster;
  }

  pickBlock(ndcX: number, ndcY: number): string | null {
    const hits = this.ray(ndcX, ndcY).intersectObjects(this.plates, false);
    return hits.length > 0 ? (hits[0].object.userData.block as string) : null;
  }

  /** Yalnızca verilen bloğun koltukları aranır (≈1000 örnek); bütün stadyumu taramak çok yavaş olurdu. */
  pickSeat(ndcX: number, ndcY: number, code: string): number | null {
    const rt = this.blocks.get(code);
    if (!rt) return null;
    const hits = this.ray(ndcX, ndcY).intersectObject(rt.seats, false);
    return hits.length > 0 && hits[0].instanceId !== undefined ? hits[0].instanceId : null;
  }

  // ------------------------------------------------------------------------------------------ kamera

  /** Bloğa sahadan bakan kamera: blok merkezinin sahaya doğru geri çekilmiş, biraz yukarıdaki hali. */
  blockCamera(code: string): { pos: THREE.Vector3; target: THREE.Vector3 } {
    const rt = this.blocks.get(code)!;
    const lowerTier = rt.spec.tier === "A";
    const back = lowerTier ? 36 : 54;
    const lift = lowerTier ? 9 : 22;
    const pos = rt.center.clone().addScaledVector(rt.normal, -back);
    pos.y = rt.center.y + lift;
    return { pos, target: rt.center.clone().add(new THREE.Vector3(0, 0.5, 0)) };
  }

  seatWorld(code: string, rowIndex: number, number: number): THREE.Vector3 {
    const rt = this.blocks.get(code)!;
    const local = localIndex(rt.index, rowIndex, number);
    return new THREE.Vector3(rt.positions[local * 3], rt.positions[local * 3 + 1], rt.positions[local * 3 + 2]);
  }

  /** Koltuğun gözü: oturan bir seyircinin baş yüksekliği. Bakış noktası sahanın ortasıdır. */
  seatEye(code: string, rowIndex: number, number: number): { eye: THREE.Vector3; lookAt: THREE.Vector3 } {
    const p = this.seatWorld(code, rowIndex, number);
    return { eye: new THREE.Vector3(p.x, p.y + 1.18, p.z), lookAt: new THREE.Vector3(0, 0.4, 0) };
  }

  // ------------------------------------------------------------------------------------------ döngü

  resize(width: number, height: number): void {
    this.renderer.setSize(width, height, false);
    this.camera.aspect = width / Math.max(1, height);
    this.camera.updateProjectionMatrix();
    this.rig.fitAspect(this.camera.aspect);
  }

  start(): void {
    if (this.running || this.disposed) return;
    this.running = true;
    this.lastTime = performance.now();
    const loop = (now: number) => {
      if (!this.running) return;
      const dt = Math.min(0.1, (now - this.lastTime) / 1000);
      this.lastTime = now;
      this.step(dt, now / 1000);
      this.rafId = requestAnimationFrame(loop);
    };
    this.rafId = requestAnimationFrame(loop);
  }

  /** Tek kare: testler ve sürekli döngü aynı yolu kullanır. */
  step(dt: number, time: number): void {
    this.timeUniform.value = time;
    this.rig.update(dt);

    // Çatı, ışık ve dalga değerleri hedeflerine yumuşakça yaklaşır
    const k = 1 - Math.exp(-dt * 2.2);
    this.roofMaterial.opacity += (this.roofTarget - this.roofMaterial.opacity) * k;
    const roofOpacity = this.roofMaterial.opacity;
    this.roofMaterial.depthWrite = roofOpacity > 0.95;
    this.roofSteelMaterial.opacity = roofOpacity;
    this.roofRimMaterial.opacity = roofOpacity;
    this.roofLampMaterial.opacity = roofOpacity;
    this.roofLineMaterial.opacity = roofOpacity * 0.85;
    // Çatı açılırken yukarı süzülür; içeriden bakılınca ışık geçen membran gibi parlar
    this.roofGroup.position.y = (1 - roofOpacity) * 10;
    this.roofGroup.visible = roofOpacity > 0.015;
    this.roofMaterial.emissiveIntensity = 0.45 + this.lightLevel * 1.1;
    this.lightLevel += (this.lightTarget - this.lightLevel) * k;
    this.waveUniform.value += (this.waveTarget - this.waveUniform.value) * k;
    this.hemi.intensity = 0.7 + this.lightLevel * 0.55;
    this.sun.intensity = 0.9 + this.lightLevel * 0.6;
    this.renderer.toneMappingExposure = 1.08 + this.lightLevel * 0.12;

    // Etiketler yalnızca kuş bakışında ve uzaktayken görünür
    const showLabels = this.view === "overview" && this.focusBlock === null;
    for (const rt of this.blocks.values()) {
      const d = this.camera.position.distanceTo(rt.center);
      const want = showLabels ? Math.min(1, Math.max(0, (d - 70) / 60)) : 0;
      const mat = rt.label.material as THREE.SpriteMaterial;
      mat.opacity += (want - mat.opacity) * Math.min(1, dt * 6);
      rt.label.visible = mat.opacity > 0.02;
    }
    this.renderer.render(this.scene, this.camera);
  }

  stop(): void {
    this.running = false;
    cancelAnimationFrame(this.rafId);
  }

  dispose(): void {
    this.disposed = true;
    this.stop();
    this.scene.traverse((o) => {
      const mesh = o as THREE.Mesh;
      mesh.geometry?.dispose?.();
      const mat = mesh.material as THREE.Material | THREE.Material[] | undefined;
      if (Array.isArray(mat)) mat.forEach((m) => m.dispose());
      else mat?.dispose?.();
    });
    this.renderer.dispose();
  }
}
