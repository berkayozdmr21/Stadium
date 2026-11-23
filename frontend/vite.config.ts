import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

// Geliştirmede /api ve /ws istekleri backend'e yönlendirilir; tarayıcı için her şey tek kaynaktır.
const backend = process.env.STADYUM_BACKEND ?? "http://localhost:8080";

export default defineConfig({
  plugins: [react()],
  // Uçtan uca testler birden çok arayüz sunucusu çalıştırır; önbellekleri çakışmasın diye ayrılabilir.
  cacheDir: process.env.STADYUM_VITE_CACHE ?? "node_modules/.vite",
  build: {
    // three.js büyüktür; ayrı bir parçada tutulur ki arayüz kodu değişince yeniden indirilmesin
    rollupOptions: { output: { manualChunks: { three: ["three"] } } },
    chunkSizeWarningLimit: 900,
  },
  server: {
    port: 5173,
    host: true, // hem IPv4 (127.0.0.1) hem IPv6 (::1) dinlensin; "localhost" tarayıcıya göre ikisinden birine çözülür
    proxy: {
      "/api": { target: backend, changeOrigin: true },
      "/ws": { target: backend, ws: true, changeOrigin: true },
    },
  },
  test: { environment: "node", include: ["src/**/*.test.ts"] },
});
