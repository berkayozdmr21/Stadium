import type { BlockSpec } from "../api";

/**
 * Bir bloktaki koltukların yerel sırası: sıralar 0'dan, her sırada koltuk numaraları 1'den artar.
 * Backend koltukları aynı sırayla üretir ve aynı sırayla döner, bu yüzden (sıra, numara) -> örnek indeksi eşlemesi
 * tablolara ihtiyaç duymadan hesaplanır.
 */
export interface BlockIndex {
  /** rowStart[r] = r. sıranın ilk koltuğunun yerel indeksi. */
  rowStart: number[];
  total: number;
}

export function indexOf(block: BlockSpec): BlockIndex {
  const rowStart: number[] = [];
  let sum = 0;
  for (const n of block.seatsPerRow) {
    rowStart.push(sum);
    sum += n;
  }
  return { rowStart, total: sum };
}

/** @param number 1'den başlayan koltuk numarası */
export function localIndex(index: BlockIndex, rowIndex: number, number: number): number {
  return index.rowStart[rowIndex] + number - 1;
}

/** Yerel indeksten (sıra, numara): ters eşleme, ikili arama. */
export function rowAndNumber(index: BlockIndex, local: number): { rowIndex: number; number: number } {
  let lo = 0;
  let hi = index.rowStart.length - 1;
  while (lo < hi) {
    const mid = (lo + hi + 1) >>> 1;
    if (index.rowStart[mid] <= local) lo = mid;
    else hi = mid - 1;
  }
  return { rowIndex: lo, number: local - index.rowStart[lo] + 1 };
}
