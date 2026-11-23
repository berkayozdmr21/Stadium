import { describe, expect, it } from "vitest";

import type { BlockSpec } from "./api";
import { indexOf } from "./lib/seatIndex";
import { bestRun } from "./matchStore";

// bestRun yalnızca saf mantıktır; MatchStore'un ağ kısmı uçtan uca testlerle sınanır.
const index = indexOf({ seatsPerRow: [6, 6, 6] } as BlockSpec);
const free = () => new Uint8Array(18);

describe("bestRun", () => {
  it("sahaya en yakın sıranın ortasını seçer", () => {
    expect(bestRun(index, free(), 2)).toEqual([2, 3]);
  });

  it("dolu koltukları atlar", () => {
    const s = free();
    s[2] = 2; // 1. sıra 3. koltuk satıldı
    s[3] = 1; // 1. sıra 4. koltuk tutuluyor
    expect(bestRun(index, s, 2)).toEqual([0, 1]);
  });

  it("ilk sıra yetmezse sonraki sıraya geçer", () => {
    const s = free();
    for (let i = 0; i < 6; i += 2) s[i] = 2; // 1. sırada yan yana iki boş yer kalmadı
    expect(bestRun(index, s, 2)).toEqual([6 + 2, 6 + 3]);
  });

  it("hiçbir yerde yan yana yer yoksa null döner", () => {
    const s = new Uint8Array(18).fill(2);
    s[0] = 0;
    expect(bestRun(index, s, 2)).toBeNull();
    expect(bestRun(index, free(), 0)).toBeNull();
  });

  it("kendi koltuğumuzu (3) boş saymaz", () => {
    const s = free();
    s[2] = 3;
    s[3] = 3;
    expect(bestRun(index, s, 2)).toEqual([0, 1]);
  });
});
