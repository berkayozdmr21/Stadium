import { describe, expect, it } from "vitest";

import { outline, outwardNormal, perimeter, pointAt } from "./geometry";

const N = 3; // backend: StadiumGeometry.EXPONENT

/** Backend'deki `StadiumGeometry` ile elde edilen referans değerler (GeometryProbe çıktısı). */
describe("geometri, backend ile aynı sonucu verir", () => {
  const references: [number, number, number, number][] = [
    [0, 0, 52.5, 0.0],
    [10, 0.1, 51.90863262172218, 33.13568211174882],
    [10, 0.25, 0, 44.0],
    [27.5, 0.37, -56.295908608973974, 53.31557790425957],
    [45, 0.6, -83.68862950773928, -56.59211107602134],
    [52, 0.93, 99.42784144219152, -44.5124042913798],
    [5, -0.2, 16.358821336734234, -38.6983108341087],
  ];

  it.each(references)("pointAt(%d, %d)", (d, u, x, z) => {
    const p = pointAt(d, u, N);
    // 5 hane = 0,01 mm; iki tarafın kayan nokta işlemleri sıfıra yakın değerlerde 1e-6 oynayabilir
    expect(p.x).toBeCloseTo(x, 5);
    expect(p.z).toBeCloseTo(z, 5);
  });

  it.each([
    [0, 293.7650504398102],
    [10, 360.8278329147606],
    [30, 495.2731148934188],
    [52, 643.3844764414847],
  ])("perimeter(%d)", (d, expected) => {
    expect(perimeter(d, N)).toBeCloseTo(expected, 6);
  });
});

describe("çevre üzerindeki yardımcılar", () => {
  it("u = 1, u = 0 ile aynı noktadır", () => {
    const a = pointAt(10, 0, N);
    const b = pointAt(10, 1, N);
    expect(b.x).toBeCloseTo(a.x, 6);
    expect(b.z).toBeCloseTo(a.z, 6);
  });

  it("dışa bakan normal merkezden uzağa bakar ve birim uzunluktadır", () => {
    const o = outline(20, N);
    for (const u of [0, 0.05, 0.13, 0.25, 0.4, 0.5, 0.62, 0.75, 0.9]) {
      const n = outwardNormal(o, u);
      const p = pointAt(20, u, N);
      expect(Math.hypot(n.x, n.z)).toBeCloseTo(1, 6);
      expect(n.x * p.x + n.z * p.z).toBeGreaterThan(0);
    }
  });

  it("uzun kenarın ortasında normal düz dışarıyı gösterir", () => {
    const n = outwardNormal(outline(20, N), 0.25); // güney kenarın ortası
    expect(n.z).toBeCloseTo(1, 3);
    expect(Math.abs(n.x)).toBeLessThan(0.01);
  });
});
