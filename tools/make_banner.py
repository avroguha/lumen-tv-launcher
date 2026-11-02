"""Write app/src/main/res/drawable/banner.png: a 320x180 dark-glass gradient with a light diamond."""
import struct, zlib, sys, os

W, H = 320, 180
def px(x, y):
    t = y / H
    r = int(11 + 20 * t); g = int(16 + 30 * t); b = int(32 + 70 * t)
    # soft light blob top-left
    dx, dy = (x - 70) / 120, (y - 40) / 90
    glow = max(0.0, 1 - (dx * dx + dy * dy)) * 0.35
    r = int(r + 79 * glow); g = int(g + 124 * glow); b = int(b + 255 * glow)
    # diamond mark, centre-left
    cx, cy, s = 60, 90, 34
    if abs(x - cx) + abs(y - cy) <= s:
        a = 0.85 if y <= cy else 0.55
        r = int(r * (1 - a) + 255 * a); g = int(g * (1 - a) + 255 * a); b = int(b * (1 - a) + 255 * a)
    return min(r, 255), min(g, 255), min(b, 255)

raw = b"".join(b"\x00" + b"".join(bytes(px(x, y)) for x in range(W)) for y in range(H))
def chunk(tag, data):
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff)
png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "drawable", "banner.png")
open(out, "wb").write(png)
print("wrote", os.path.abspath(out), len(png), "bytes")
