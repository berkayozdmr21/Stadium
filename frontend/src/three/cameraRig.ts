import * as THREE from "three";

export type RigMode = "overview" | "block" | "seat";

export interface CamState {
  pos: THREE.Vector3;
  target: THREE.Vector3;
  fov: number;
}

const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));
const easeInOut = (t: number) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2);

interface Transition {
  from: CamState;
  startedAt: number;
  duration: number;
  /** Yolun ortasında kameranın ne kadar yükseleceği: düz çizgi yerine kavisli bir uçuş verir. */
  lift: number;
}

/**
 * Üç kamera kipi:
 *  - overview: kuş bakışı; sürükleyerek dönülür, tekerlekle yakınlaşılır, boştayken yavaşça döner
 *  - block:    seçilen bloğa saha tarafından bakan sabit kamera
 *  - seat:     koltuğun gözünden saha; sürükleyerek etrafa bakılır
 * Kipler arası geçiş kavisli bir uçuştur ve hedef hareket etse de (otomatik dönüş) doğru yere iner.
 */
export class CameraRig {
  mode: RigMode = "overview";

  // overview
  yaw = 0.7;
  pitch = 0.95;
  radius = 190;
  readonly center = new THREE.Vector3(0, 8, 0);
  autoRotate = true;
  private dragging = false;

  // block ve seat için sabit durum
  private fixed: CamState = { pos: new THREE.Vector3(), target: new THREE.Vector3(), fov: 45 };
  // seat: başın dönüşü (varsayılan bakıştan sapma)
  lookYaw = 0;
  lookPitch = 0;
  private seatBaseYaw = 0;
  private seatBasePitch = 0;
  private seatPos = new THREE.Vector3();

  private transition: Transition | null = null;
  private current: CamState = { pos: new THREE.Vector3(0, 100, 150), target: new THREE.Vector3(), fov: 45 };

  constructor(readonly camera: THREE.PerspectiveCamera) {
    this.current = this.resolve();
    this.apply(this.current);
  }

  get isTransitioning(): boolean {
    return this.transition !== null;
  }

  // ---- kip geçişleri ----

  toOverview(ms = 2600): void {
    this.begin("overview", ms, 40);
  }

  toBlock(pos: THREE.Vector3, target: THREE.Vector3, ms = 2000): void {
    this.fixed = { pos: pos.clone(), target: target.clone(), fov: 42 };
    this.begin("block", ms, 22);
  }

  /** Koltuğun gözünden saha. {@code lookAt} sahadaki bakılan nokta. */
  toSeat(eye: THREE.Vector3, lookAt: THREE.Vector3, ms = 3200): void {
    this.seatPos = eye.clone();
    const dir = lookAt.clone().sub(eye);
    this.seatBaseYaw = Math.atan2(dir.x, dir.z);
    this.seatBasePitch = Math.atan2(dir.y, Math.hypot(dir.x, dir.z));
    this.lookYaw = 0;
    this.lookPitch = 0;
    this.fixed.fov = 72;
    this.begin("seat", ms, 55);
  }

  private begin(mode: RigMode, ms: number, lift: number): void {
    const from: CamState = {
      pos: this.current.pos.clone(),
      target: this.current.target.clone(),
      fov: this.current.fov,
    };
    this.mode = mode;
    this.transition = { from, startedAt: performance.now(), duration: ms, lift };
  }

  /** Geçişi hemen bitirir (testler ve "hareketi azalt" tercihi için). */
  finishTransition(): void {
    this.transition = null;
  }

  // ---- kullanıcı girdisi ----

  dragStart(): void {
    this.dragging = true;
  }

  dragEnd(): void {
    this.dragging = false;
  }

  /** Sürükleme: overview'da döndürür, seat'te başı çevirir. dx, dy piksel cinsindendir. */
  drag(dx: number, dy: number): void {
    if (this.mode === "overview") {
      this.yaw -= dx * 0.005;
      this.pitch = clamp(this.pitch + dy * 0.004, 0.22, 1.5);
    } else if (this.mode === "seat") {
      this.lookYaw = clamp(this.lookYaw - dx * 0.004, -1.25, 1.25);
      this.lookPitch = clamp(this.lookPitch + dy * 0.003, -0.55, 0.7);
    }
  }

  zoom(delta: number): void {
    if (this.mode === "overview") {
      this.radius = clamp(this.radius * Math.exp(delta * 0.0012), 70, 330);
    } else if (this.mode === "seat") {
      this.fixed.fov = clamp(this.fixed.fov + delta * 0.02, 30, 95);
    }
  }

  /** Dar (dikey) ekranlarda stadyum sığsın diye kuş bakışı uzaklığı büyütülür. */
  private fit = 1;

  fitAspect(aspect: number): void {
    this.fit = clamp(1.3 / Math.max(0.3, aspect), 1, 2.2);
  }

  /** Seat kipinde ekranın görüş açısı (fov); yakınlaştırma bunu değiştirir. */
  get seatFov(): number {
    return this.fixed.fov;
  }

  // ---- her karede ----

  update(dt: number): void {
    if (this.mode === "overview" && this.autoRotate && !this.dragging && !this.transition) {
      this.yaw += dt * 0.045;
    }
    const dest = this.resolve();
    if (this.transition) {
      const t = clamp((performance.now() - this.transition.startedAt) / this.transition.duration, 0, 1);
      const e = easeInOut(t);
      const { from, lift } = this.transition;
      // Kavisli yol: başlangıç ve varış arasında, ortada yükselen bir kontrol noktasıyla ikinci dereceden Bézier
      const ctrl = from.pos.clone().lerp(dest.pos, 0.5);
      ctrl.y += lift * Math.sin(Math.PI * Math.min(1, e));
      const a = from.pos.clone().lerp(ctrl, e);
      const b = ctrl.clone().lerp(dest.pos, e);
      this.current.pos.copy(a.lerp(b, e));
      this.current.target.copy(from.target).lerp(dest.target, e);
      this.current.fov = from.fov + (dest.fov - from.fov) * e;
      if (t >= 1) this.transition = null;
    } else {
      this.current = dest;
    }
    this.apply(this.current);
  }

  private resolve(): CamState {
    if (this.mode === "overview") {
      const cp = Math.cos(this.pitch);
      const r = this.radius * this.fit;
      const pos = new THREE.Vector3(
        this.center.x + r * cp * Math.sin(this.yaw),
        this.center.y + r * Math.sin(this.pitch),
        this.center.z + r * cp * Math.cos(this.yaw),
      );
      return { pos, target: this.center.clone(), fov: 42 };
    }
    if (this.mode === "block") return this.fixed;
    // seat: bakış yönü = varsayılan yön + baş dönüşü
    const yaw = this.seatBaseYaw + this.lookYaw;
    const pitch = clamp(this.seatBasePitch + this.lookPitch, -1.2, 1.2);
    const dir = new THREE.Vector3(Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
    return { pos: this.seatPos.clone(), target: this.seatPos.clone().add(dir.multiplyScalar(40)), fov: this.fixed.fov };
  }

  private apply(s: CamState): void {
    this.camera.position.copy(s.pos);
    this.camera.lookAt(s.target);
    if (Math.abs(this.camera.fov - s.fov) > 0.01) {
      this.camera.fov = s.fov;
      this.camera.updateProjectionMatrix();
    }
  }
}
