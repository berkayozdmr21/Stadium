// Demo kaydı: arayüzü gerçek bir tarayıcıda gezer ve Playwright'ın ekran kaydıyla (webm) kaydeder.
//
//   node scripts/record-demo.mjs <çıktı-klasörü>
//
// Backend (8080) ve Vite (5173) çalışıyor olmalı. Tarayıcı görünmez pencerede ama gerçek ekran kartıyla açılır
// (yazılım çizimiyle kare hızı çok düşük kalır). Çıktıdaki .webm dosyasını GIF'e çevirmek için scripts/make_gif.py
// (karelere ayrılmış hâliyle) kullanılır.
import { mkdirSync } from "node:fs";
import { chromium } from "@playwright/test";

const out = process.argv[2];
if (!out) throw new Error("Kullanım: node scripts/record-demo.mjs <çıktı-klasörü>");
mkdirSync(out, { recursive: true });

const size = { width: 1100, height: 640 };
const browser = await chromium.launch({ channel: process.env.PW_CHANNEL ?? "msedge" });
const context = await browser.newContext({ viewport: size, recordVideo: { dir: out, size } });
const page = await context.newPage();

await page.goto("http://127.0.0.1:5173/mac/ornek-mac-marmara");
await page.waitForFunction(() => Boolean(window.__scene));
await page.waitForTimeout(2500);

// 1) Kuş bakışı: kamera kendiliğinden döner
await page.waitForTimeout(4500);

// 3) Bir alt kat bloğuna tıkla (döndürmeyi durdur ki koordinat sabit kalsın)
await page.evaluate(() => { window.__scene.rig.autoRotate = false; });
await page.waitForTimeout(600);
const inBlockView = () => page.locator(".match").getAttribute("data-view").then((v) => v === "block");
for (let attempt = 0; attempt < 4 && !(await inBlockView()); attempt++) {
  const target = await page.evaluate(() => {
    const s = window.__scene;
    const r = s.renderer.domElement.getBoundingClientRect();
    for (const [code, rt] of s.blocks) {
      if (rt.spec.tier !== "A") continue;
      const v = rt.center.clone().project(s.camera);
      if (Math.abs(v.x) > 0.7 || Math.abs(v.y) > 0.7) continue;
      if (s.pickBlock(v.x, v.y) !== code) continue;
      return { x: r.left + (v.x * 0.5 + 0.5) * r.width, y: r.top + (-v.y * 0.5 + 0.5) * r.height };
    }
    return null;
  });
  if (!target) throw new Error("Seçilebilir blok bulunamadı");
  await page.mouse.move(target.x, target.y, { steps: 12 });
  await page.waitForTimeout(1000);
  await page.mouse.click(target.x, target.y);
  await page.waitForTimeout(1500);
}
if (!(await inBlockView())) throw new Error("Bloğa tıklama tutmadı");
await page.waitForTimeout(3500);

// 4) İki koltuk seç
await page.getByTestId("pick-best").click();
await page.waitForTimeout(2500);

// 5) Koltuktan görüş
await page.getByRole("button", { name: /koltuktan sahayı gör/ }).first().click();
await page.waitForTimeout(6500);

// 6) Etrafa bak
const box = await page.getByTestId("stage").boundingBox();
const cx = box.x + box.width / 2;
const cy = box.y + box.height / 2;
await page.mouse.move(cx, cy);
await page.mouse.down();
await page.mouse.move(cx - 170, cy, { steps: 40 });
await page.mouse.up();
await page.waitForTimeout(1200);
await page.mouse.down();
await page.mouse.move(cx + 120, cy, { steps: 40 });
await page.mouse.up();
await page.waitForTimeout(1500);

// 7) Geri dön ve çatıyı göster (kapalı çatı stadyumun simgesi)
await page.getByRole("button", { name: "← Bloğa dön" }).click();
await page.waitForTimeout(2800);
await page.getByTestId("back-overview").click();
await page.waitForTimeout(3200);
await page.getByRole("button", { name: "Çatıyı göster" }).click();
await page.waitForTimeout(4000);

const video = page.video();
await context.close(); // video dosyası kapanırken yazılır
await video.saveAs(`${out}/demo.webm`);
console.log("video:", `${out}/demo.webm`);
await browser.close();
