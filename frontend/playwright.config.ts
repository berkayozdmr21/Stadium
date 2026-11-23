import { defineConfig } from "@playwright/test";

/**
 * Uçtan uca testler gerçek backend'e (bellek içi H2) ve Vite sunucusuna karşı çalışır.
 * WebGL için tarayıcıyı yazılım çizimiyle (SwiftShader) açar; ekran kartı gerekmez.
 *
 * Önce backend jar'ı üretilmiş olmalı:  cd ../backend && ./mvnw -DskipTests package
 * Tarayıcı:  PW_CHANNEL=msedge (varsayılan) ya da PW_CHANNEL=chrome; kurulu tarayıcı yoksa  npx playwright install chromium
 */
const channel = process.env.PW_CHANNEL ?? "msedge";

export default defineConfig({
  testDir: "e2e",
  timeout: 90_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [["list"]],
  use: {
    baseURL: "http://localhost:5173",
    channel: channel === "chromium" ? undefined : channel,
    launchOptions: { args: ["--use-gl=angle", "--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--ignore-gpu-blocklist"] },
    viewport: { width: 1280, height: 800 },
    trace: "retain-on-failure",
  },
  webServer: [
    {
      command: "java -jar ../backend/target/stadyum-backend-1.0.0.jar",
      url: "http://localhost:8080/api/events",
      reuseExistingServer: true,
      timeout: 120_000,
    },
    {
      command: "npm run dev",
      url: "http://localhost:5173",
      reuseExistingServer: true,
      timeout: 60_000,
    },
  ],
});
