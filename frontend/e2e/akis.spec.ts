import { expect, test, type Page } from "@playwright/test";

// Geliştirme sürümünde sahne window.__scene üzerinden erişilebilir (Stage.tsx).
interface TestScene {
  rig: { mode: string; isTransitioning: boolean; autoRotate: boolean };
  blocks: Map<string, { center: { clone(): { project(c: unknown): { x: number; y: number } } } }>;
  camera: unknown;
  renderer: { domElement: HTMLCanvasElement };
}
declare global {
  interface Window {
    __scene?: TestScene;
  }
}

async function openMatch(page: Page, slug: string) {
  await page.goto(`/mac/${slug}`);
  await page.waitForFunction(() => Boolean(window.__scene));
  await page.evaluate(() => {
    window.__scene!.rig.autoRotate = false; // tıklama koordinatları sabit kalsın
  });
}

async function settled(page: Page) {
  await page.waitForFunction(() => !window.__scene!.rig.isTransitioning, undefined, { timeout: 30_000 });
}

/**
 * Alt kattaki bir bloğun ekrandaki yerine tıklar (canvas'ta DOM öğesi yoktur). Kuş bakışında yakın taraftaki alt
 * bloklar üst katın arkasında kalabilir; bu yüzden merkezi gerçekten o bloğu seçen ilk bloğa tıklanır.
 * Tıklanan bloğun kodunu döndürür.
 */
async function clickAnyLowerBlock(page: Page): Promise<string> {
  const target = await page.evaluate(() => {
    const s = window.__scene! as unknown as {
      blocks: Map<string, { center: { clone(): { project(c: unknown): { x: number; y: number } } }; spec: { tier: string } }>;
      camera: unknown;
      renderer: { domElement: HTMLCanvasElement };
      pickBlock(x: number, y: number): string | null;
    };
    const r = s.renderer.domElement.getBoundingClientRect();
    for (const [code, rt] of s.blocks) {
      if (rt.spec.tier !== "A") continue;
      const v = rt.center.clone().project(s.camera);
      if (Math.abs(v.x) > 0.8 || Math.abs(v.y) > 0.8) continue;
      if (s.pickBlock(v.x, v.y) !== code) continue;
      return { code, x: r.left + (v.x * 0.5 + 0.5) * r.width, y: r.top + (-v.y * 0.5 + 0.5) * r.height };
    }
    return null;
  });
  expect(target, "ekranda seçilebilir bir alt kat bloğu bulunamadı").not.toBeNull();
  await page.mouse.move(target!.x, target!.y);
  await page.mouse.click(target!.x, target!.y);
  return target!.code;
}

test("ana sayfa maçları ve uyarıyı gösterir", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByTestId("match-card")).toHaveCount(3);
  await expect(page.getByText("gerçek bilet satılmaz")).toBeVisible();
});

test("kuş bakışından blok, koltuk, koltuktan görüş ve bilet", async ({ page }) => {
  await openMatch(page, "ornek-mac-anadolu");
  await settled(page);
  expect(await page.evaluate(() => window.__scene!.rig.mode)).toBe("overview");

  // 1) kuş bakışından bloğa
  await clickAnyLowerBlock(page);
  await expect(page.getByTestId("back-overview")).toBeVisible();
  await settled(page);
  expect(await page.evaluate(() => window.__scene!.rig.mode)).toBe("block");

  // 2) iki bitişik koltuk
  await page.getByTestId("pick-best").click();
  await expect(page.getByTestId("held-list").locator("li")).toHaveCount(2);

  // 3) koltuktan görüş
  await page.getByRole("button", { name: /koltuktan sahayı gör/ }).first().click();
  await expect(page.getByTestId("seat-hud")).toBeVisible();
  await settled(page);
  expect(await page.evaluate(() => window.__scene!.rig.mode)).toBe("seat");

  // Esc bir adım geri götürür
  await page.keyboard.press("Escape");
  await settled(page);
  expect(await page.evaluate(() => window.__scene!.rig.mode)).toBe("block");

  // 4) ödeme ve bilet
  await page.getByTestId("checkout").click();
  await page.getByTestId("name").fill("Test Kullanıcı");
  await page.getByTestId("email").fill("test@example.com");
  await page.getByTestId("confirm").click();
  await expect(page).toHaveURL(/\/bilet\/[A-Z0-9-]+$/);
  await expect(page.getByTestId("ticket")).toContainText("Test Kullanıcı");
  await expect(page.getByTestId("booking-code")).not.toBeEmpty();
});

test("aynı koltuğu iki kişi tutamaz", async ({ request }) => {
  const slug = "ornek-mac-kuzey";
  const block = await (await request.get(`/api/events/${slug}/blocks/A05`)).json();
  const free = block.seats.find((s: { status: string }) => s.status === "AVAILABLE");

  const first = await request.put(`/api/events/${slug}/hold/seats/${free.id}`);
  expect(first.ok()).toBeTruthy();
  const second = await request.put(`/api/events/${slug}/hold/seats/${free.id}`);
  expect(second.status()).toBe(409);
  expect((await second.json()).code).toBe("SEAT_UNAVAILABLE");
});

test("geçersiz ödeme bilgisi alan hatası döndürür", async ({ page }) => {
  await openMatch(page, "ornek-mac-marmara");
  await settled(page);
  await clickAnyLowerBlock(page);
  await expect(page.getByTestId("pick-best")).toBeVisible();
  await page.getByTestId("pick-best").click();
  await expect(page.getByTestId("held-list").locator("li")).toHaveCount(2);
  await page.getByTestId("checkout").click();
  await page.getByTestId("confirm").click();
  await expect(page.locator(".field-error").first()).toBeVisible();
  await page.getByRole("button", { name: "Temizle" }).or(page.getByRole("button", { name: "Vazgeç" })).first().click();
});
