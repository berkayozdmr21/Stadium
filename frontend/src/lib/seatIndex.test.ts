import { describe, expect, it } from "vitest";

import type { BlockSpec } from "../api";
import { indexOf, localIndex, rowAndNumber } from "./seatIndex";

const block = { seatsPerRow: [3, 5, 4] } as BlockSpec;

describe("seatIndex", () => {
  it("sıra başlangıçlarını ve toplamı hesaplar", () => {
    const idx = indexOf(block);
    expect(idx.rowStart).toEqual([0, 3, 8]);
    expect(idx.total).toBe(12);
  });

  it("(sıra, numara) ile yerel indeks arasında gidip gelir", () => {
    const idx = indexOf(block);
    expect(localIndex(idx, 0, 1)).toBe(0);
    expect(localIndex(idx, 1, 1)).toBe(3);
    expect(localIndex(idx, 2, 4)).toBe(11);
    for (let i = 0; i < idx.total; i++) {
      const { rowIndex, number } = rowAndNumber(idx, i);
      expect(localIndex(idx, rowIndex, number)).toBe(i);
    }
  });

  it("sıra sınırlarında doğru sırayı bulur", () => {
    const idx = indexOf(block);
    expect(rowAndNumber(idx, 2)).toEqual({ rowIndex: 0, number: 3 });
    expect(rowAndNumber(idx, 3)).toEqual({ rowIndex: 1, number: 1 });
    expect(rowAndNumber(idx, 7)).toEqual({ rowIndex: 1, number: 5 });
    expect(rowAndNumber(idx, 8)).toEqual({ rowIndex: 2, number: 1 });
  });
});
