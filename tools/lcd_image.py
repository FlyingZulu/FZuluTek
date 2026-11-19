#!/usr/bin/env python3
# tools/lcd_image.py — convertit une image PNG en buffer LCD du FM-1 (240x240, RGB565, ST7789).
# Usage : python3 tools/lcd_image.py entree.png sortie [--w 240 --h 240] [--bin] [--header]
#   - redimensionne au plus proche voisin si la taille diffère (pixel art net)
#   - sortie.bin  : brut RGB565 little-endian (à flasher/copier tel quel)
#   - sortie.h    : const uint16_t img[W*H] = { ... } prêt à inclure dans le firmware ATEK303
# Pur Python (zlib), PNG 8 bits non entrelacé (RGB ou RGBA).
import zlib, struct, sys, os

def read_png(path):
    d = open(path, "rb").read()
    assert d[:8] == b"\x89PNG\r\n\x1a\n", "pas un PNG"
    pos = 8
    w = h = None
    bitdepth = ctype = None
    idat = b""
    while pos < len(d):
        ln, = struct.unpack(">I", d[pos:pos + 4])
        typ = d[pos + 4:pos + 8]
        data = d[pos + 8:pos + 8 + ln]
        if typ == b"IHDR":
            w, h, bitdepth, ctype = struct.unpack(">IIBB", data[:10])
        elif typ == b"IDAT":
            idat += data
        elif typ == b"IEND":
            break
        pos += 12 + ln
    assert bitdepth == 8 and ctype in (2, 6), "PNG 8 bits RGB/RGBA uniquement"
    ch = 3 if ctype == 2 else 4
    raw = zlib.decompress(idat)
    stride = w * ch
    out = bytearray(w * h * ch)
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        f = raw[p]; p += 1
        line = bytearray(raw[p:p + stride]); p += stride
        if f == 1:
            for i in range(ch, stride):
                line[i] = (line[i] + line[i - ch]) & 0xFF
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif f == 3:
            for i in range(stride):
                a = line[i - ch] if i >= ch else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif f == 4:
            for i in range(stride):
                a = line[i - ch] if i >= ch else 0
                b = prev[i]
                c = prev[i - ch] if i >= ch else 0
                pp = a + b - c
                pa, pb, pc = abs(pp - a), abs(pp - b), abs(pp - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out[y * stride:(y + 1) * stride] = line
        prev = line
    return w, h, ch, out

def resize_nearest(w, h, ch, px, W, H):
    out = bytearray(W * H * ch)
    for y in range(H):
        sy = y * h // H
        for x in range(W):
            i = (sy * w + x * w // W) * ch
            o = (y * W + x) * ch
            out[o:o + ch] = px[i:i + ch]
    return out

def to_rgb565(w, h, ch, px):
    out = bytearray(w * h * 2)
    for i in range(w * h):
        r, g, b = px[i * ch], px[i * ch + 1], px[i * ch + 2]
        v = ((r & 0xF8) << 8) | ((g & 0xFC) << 3) | (b >> 3)
        out[i * 2] = v & 0xFF
        out[i * 2 + 1] = (v >> 8) & 0xFF
    return out

def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)
    src, dst = sys.argv[1], sys.argv[2]
    W = H = 240
    args = sys.argv[3:]
    if "--w" in args:
        W = int(args[args.index("--w") + 1])
    if "--h" in args:
        H = int(args[args.index("--h") + 1])
    w, h, ch, px = read_png(src)
    if (w, h) != (W, H):
        px = resize_nearest(w, h, ch, px, W, H)
        print("redimensionne %dx%d -> %dx%d (nearest)" % (w, h, W, H))
    buf = to_rgb565(W, H, ch, px)
    if "--bin" in args or True:
        open(dst + ".bin", "wb").write(buf)
        print("wrote %s.bin (%d octets, RGB565 LE, %dx%d)" % (dst, len(buf), W, H))
    if "--header" in args or True:
        with open(dst + ".h", "w") as f:
            f.write("/* %s — %dx%d RGB565, généré par tools/lcd_image.py */\n" % (os.path.basename(src), W, H))
            f.write("#include <stdint.h>\n#define IMG_W %d\n#define IMG_H %d\n" % (W, H))
            f.write("const uint16_t img[%d] = {\n" % (W * H))
            for i in range(0, W * H, 12):
                row = ", ".join("0x%04X" % (buf[j * 2] | (buf[j * 2 + 1] << 8)) for j in range(i, min(i + 12, W * H)))
                f.write("  " + row + ",\n")
            f.write("};\n")
        print("wrote %s.h (const uint16_t img[%d])" % (dst, W * H))

if __name__ == "__main__":
    main()
