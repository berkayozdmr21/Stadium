"""Numaralı PNG karelerinden GIF üretir.

    python scripts/make_gif.py <png-klasörü> <çıktı.gif> [kare-hızı] [renk-sayısı] [kare-atlama]

Kareleri webm'den Playwright'ın kendi ffmpeg'iyle çıkarabilirsin:

    ffmpeg -i demo.webm -r 12 -s 520x303 png/%04d.png

Gereken: Pillow.
"""
import sys
from pathlib import Path

from PIL import Image

src = Path(sys.argv[1])
dst = Path(sys.argv[2])
fps = float(sys.argv[3]) if len(sys.argv) > 3 else 12
colors = int(sys.argv[4]) if len(sys.argv) > 4 else 64
stride = int(sys.argv[5]) if len(sys.argv) > 5 else 1

files = sorted(src.glob("*.png"))[::stride]
images = [
    Image.open(f).convert("RGB").quantize(colors=colors, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    for f in files
]
frame_ms = round(1000 / fps * stride)
images[0].save(dst, save_all=True, append_images=images[1:], duration=frame_ms, loop=0, optimize=True, disposal=1)
print(f"{len(images)} kare, {len(images) * frame_ms / 1000:.1f} sn, {dst.stat().st_size / 1e6:.1f} MB")
