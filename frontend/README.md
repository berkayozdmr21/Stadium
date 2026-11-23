# Arayüz (frontend)

React 19 + TypeScript + Vite + three.js. Üst dizindeki [README](../README.md) projenin bütününü anlatır; burası arayüzün iç yapısıdır.

```powershell
npm install
npm run dev          # http://localhost:5173 (ya da 127.0.0.1:5173); /api ve /ws backend'e (8080) yönlenir
npm run build        # tsc + vite build
npm test             # birim testler (vitest)
npx playwright test  # uçtan uca (backend jar'ı ve sistemdeki Edge gerekir, bkz. playwright.config.ts)
```

## Yapı

| Yol | Ne yapar |
|---|---|
| `src/three/StadiumScene.ts` | 3B sahne: tribünler (blok başına bir `InstancedMesh`), çatı, cephe, saha, seyirci, seçim (ışın), renklendirme |
| `src/three/cameraRig.ts` | Üç kamera kipi (kuş bakışı, blok, koltuk) ve aralarındaki kavisli Bézier uçuşları |
| `src/three/textures.ts` | Çim, blok etiketleri vb. için üretilen dokular (görsel dosya yok) |
| `src/matchStore.ts` | Bir maçın canlı durumu: blok doluluğu, koltuk durumları, tutma, WebSocket; React'ten bağımsız dış depo |
| `src/lib/geometry.ts` | Stadyum geometrisi; backend'deki formülün birebir kopyası, Java referans değerleriyle test edilir |
| `src/components/` | `Stage` (tuval ve girdi), `Panel` (seçimler ve ödeme), `Ticket`, `Toasts`, küçük parçalar |
| `src/pages/` | Ana sayfa, maç sayfası, bilet sayfası |
| `src/router.tsx` | Küçük yönlendirici; sayfa geçişlerini View Transitions API ile sarar |
| `e2e/` | Playwright uçtan uca testleri |
| `scripts/` | Demo kaydı (`record-demo.mjs`) ve GIF üretimi (`make_gif.py`) |

## Tasarım kararları

- **Sahne React dışında çizilir.** React yalnızca komut verir (görünüm, odak, mod); kare döngüsü sahnenin kendisindedir. Böylece her yeniden çizimde sahne yeniden kurulmaz.
- **Dış depo + `useSyncExternalStore`.** Koltuk durumu değişimleri (saniyede onlarca) React'e değil, doğrudan sahneye akar; React yalnızca panelin ihtiyaç duyduğu özeti görür.
- **Seçim ucuz tutulur.** Kuş bakışında yalnızca 55 blok levhasına, blok görünümünde yalnızca o bloğun ~800 koltuğuna ışın atılır.
- **Yarış durumlarına karşı:** tutma her değiştiğinde bir sürüm sayacı artar; yolda olan eski özet yanıtı yerel tutmayı ezmez.
- **Erişilebilirlik:** klavye ile (Esc) geri dönülür, `prefers-reduced-motion` açıkken kamera geçişleri anında biter, WebGL yoksa açıklayıcı bir uyarı gösterilir.
- Geliştirme sürümünde sahne `window.__scene` olarak erişilebilir (uçtan uca testler ve demo kaydı kullanır); üretim derlemesinde yoktur.

## Demo kaydı

```powershell
node scripts/record-demo.mjs <klasör>     # <klasör>/demo.webm üretir (Playwright ffmpeg gerekir: npx playwright install ffmpeg)
```
