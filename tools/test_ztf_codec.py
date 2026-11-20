#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later — Copyright (C) 2026 FlyingZulu
"""Test host du codec ZTF côté APK (miroir Python 1:1 de ZtfCodec.kt / ZtfTransport.kt).

    python3 tools/test_ztf_codec.py              # tests unitaires seuls
    python3 tools/test_ztf_codec.py --with-c     # + cross-check contre le VRAI code firmware
                                                  #   (harnais C compilé depuis l'arbre patch 0010 ;
                                                  #    vecteurs écrits dans /tmp/ztf_vectors)

Le cross-check --with-c fait tourner zml_slot_load/zml_slot_save, zmac_load/zmac_save,
zml_ring_import et zlk_sysex (patch 0010) sur des vecteurs produits ici, et vérifie que
les octets reviennent identiques (ZLS1/ZMC1), que le ring vit (ZSM1), que la trame PONG
est conforme, et que pack7 == cp_pack7 de fm1-base/device/companion.c.
"""
import os
import random
import struct
import subprocess
import sys
import tempfile
import zlib

# ---- miroir de ZtfCodec.kt ----------------------------------------------------

def pack7(data: bytes) -> bytes:
    out, acc, nb = bytearray(), 0, 0
    for b in data:
        acc |= b << nb
        nb += 8
        while nb >= 7:
            out.append(acc & 0x7F)
            acc >>= 7
            nb -= 7
    if nb:
        out.append(acc & 0x7F)
    return bytes(out)

def unpack7(s: bytes) -> bytes:
    out, acc, nb = bytearray(), 0, 0
    for b in s:
        acc |= (b & 0x7F) << nb
        nb += 7
        while nb >= 8:
            out.append(acc & 0xFF)
            acc >>= 8
            nb -= 8
    return bytes(out)

def frame(cmd: int, payload: bytes = b"") -> bytes:
    body = bytearray([0, 0x59, cmd, len(payload) & 0xFF, (len(payload) >> 8) & 0xFF, 0]) + payload
    body.append(~sum(payload) & 0xFF)
    return b"\xF0" + pack7(bytes(body)) + b"\xF7"

def parse_frame(pkt: bytes):
    if len(pkt) < 9 or pkt[0] != 0xF0 or pkt[-1] != 0xF7:
        return None
    u = unpack7(pkt[1:-1])
    if len(u) < 7 or u[0] != 0 or u[1] != 0x59:
        return None
    n = u[3] | u[4] << 8 | u[5] << 16
    if len(u) < n + 7 or ~sum(u[6:6 + n]) & 0xFF != u[6 + n]:
        return None
    return u[2], bytes(u[6:6 + n])

def is_ztf(pkt: bytes) -> bool:
    return len(pkt) >= 5 and pkt[0] == 0xF0 and pkt[1] == 0x7D and pkt[2] == ord('Z') and pkt[3] == ord('T')

def ztf_frame(cmd: str, args: bytes = b"") -> bytes:
    return bytes([0xF0, 0x7D, ord('Z'), ord('T'), ord(cmd)]) + args + b"\xF7"

def parse_ztf(pkt: bytes):
    if not is_ztf(pkt) or len(pkt) < 6 or pkt[-1] != 0xF7:
        return None
    return chr(pkt[4]), pkt[5:-1]

def linear_to_ulaw(pcm: int) -> int:
    # identique à ml_ulaw_enc (dsp/midi_looper.c) : ~(sign|exp<<4|mant), sign=0x80 si négatif
    sign = (pcm >> 8) & 0x80
    if sign:
        pcm = -pcm
    if pcm > 32635:
        pcm = 32635
    pcm += 0x84
    exp, mask = 7, 0x4000
    while exp > 0 and not (pcm & mask):
        mask >>= 1
        exp -= 1
    mant = (pcm >> (exp + 3)) & 0x0F
    return (~(sign | (exp << 4) | mant)) & 0xFF

def ulaw_to_linear(u: int) -> int:
    # identique à ml_ulaw_dec, échelle int16 (sample/32768 en float côté C)
    uu = (~u) & 0xFF
    t = ((uu & 0x0F) << 3) + 0x84
    t <<= (uu >> 4) & 0x07
    return (0x84 - t) if (uu & 0x80) else (t - 0x84)

def encode_zsm1(sr: int, fmt: int, samples) -> bytes:
    payload = bytes(linear_to_ulaw(s) for s in samples) if fmt == 1 \
        else b"".join(struct.pack("<h", s) for s in samples)
    return b"ZSM1" + struct.pack("<II", sr, len(samples)) + bytes([fmt]) + payload

def decode_zsm1(b: bytes):
    if len(b) < 13 or b[:4] != b"ZSM1":
        return None
    sr, ln = struct.unpack_from("<II", b, 4)
    fmt = b[12]
    need = ln if fmt == 1 else ln * 2
    if len(b) < 13 + need:
        return None
    if fmt == 1:
        s = [ulaw_to_linear(b[13 + i]) for i in range(ln)]
    else:
        s = list(struct.unpack_from("<%dh" % ln, b, 13))
    return sr, fmt, s

def encode_zls1(z) -> bytes:
    # z = dict(pitch,pos,mode,mut,src,fx,vol,len,len_set,on,notes=[(t,note,vel,dur)],pars=[(t,key,vf32)])
    out = bytearray(b"ZLS1")
    out.append(z["pitch"] & 0xFF)
    out += struct.pack("<H", z["pos"])
    for k in ("mode", "mut", "src", "fx", "vol"):
        out.append(z[k] & 0xFF)
    out += struct.pack("<H", z["len"])
    out.append(1 if z["len_set"] else 0)
    out.append(1 if z["on"] else 0)
    out += struct.pack("<H", len(z["notes"]))
    for (t, note, vel, dur) in z["notes"]:
        out += struct.pack("<HBBB", t, note, vel, dur)
    out += struct.pack("<H", len(z["pars"]))
    for (t, key, v) in z["pars"]:
        out += struct.pack("<HBB", t, key, 0) + struct.pack("<f", v)
    return bytes(out)

def decode_zls1(b: bytes):
    if len(b) < 18 or b[:4] != b"ZLS1":
        return None
    z = {"pitch": struct.unpack_from("<b", b, 4)[0], "pos": struct.unpack_from("<H", b, 5)[0]}
    z["mode"], z["mut"], z["src"], z["fx"], z["vol"] = b[7], b[8], b[9], b[10], b[11]
    z["len"] = struct.unpack_from("<H", b, 12)[0]
    z["len_set"] = b[14] != 0
    z["on"] = b[15] != 0
    nn = struct.unpack_from("<H", b, 16)[0]
    if len(b) < 18 + nn * 5 + 2:
        return None
    p = 18
    z["notes"] = [struct.unpack_from("<HBBB", b, p + 5 * i) for i in range(nn)]
    p += nn * 5
    np_ = struct.unpack_from("<H", b, p)[0]
    p += 2
    if len(b) < p + np_ * 8:
        return None
    z["pars"] = []
    for i in range(np_):
        t, key = struct.unpack_from("<HB", b, p + 8 * i)
        v = struct.unpack_from("<f", b, p + 8 * i + 4)[0]
        z["pars"].append((t, key, v))
    return z

def encode_zmc1(slots) -> bytes:
    # zmac_save (ztek_macro.c) : 10 octets par slot {dst i16, val f32, used u8, pad x3}
    out = bytearray(b"ZMC1")
    for (dst, v, used) in slots:
        out += struct.pack("<h", dst) + struct.pack("<f", v) + bytes([1 if used else 0]) + b"\x00\x00\x00"
    return bytes(out)

def decode_zmc1(b: bytes):
    if len(b) < 4 + 16 * 10 or b[:4] != b"ZMC1":
        return None
    out = []
    for i in range(16):
        dst = struct.unpack_from("<h", b, 4 + 10 * i)[0]
        v = struct.unpack_from("<f", b, 6 + 10 * i)[0]
        used = b[10 + 10 * i] != 0
        out.append((dst, v, used))
    return out

# ---- tests ----------------------------------------------------------------------

FAILS = []
def check(cond, label):
    if not cond:
        FAILS.append(label)
        print("FAIL:", label)

def main():
    rng = random.Random(20261120)

    # CRC-32 (zlib == java.util.zip.CRC32 == plat) : vecteur connu
    check(zlib.crc32(b"123456789") == 0xCBF43926, "crc32 vecteur connu")

    # pack7 : vecteur connu + round-trip aléatoire
    check(pack7(bytes([0xFF])) == bytes([0x7F, 0x01]), "pack7 [0xFF] -> 7F 01")
    for _ in range(200):
        d = bytes(rng.randrange(256) for _ in range(rng.randrange(1, 700)))
        check(unpack7(pack7(d)) == d, "pack7 round-trip")
        # contrainte trame : <= 640 o entre F0/F7 (pack7 compris) -> payload <= 553
        if len(d) <= 553:
            check(len(frame(0x61, d)) - 2 <= 640, "taille trame <= 640")

    # frame companion : structure + parse
    f = frame(0x60)
    check(f[0] == 0xF0 and f[-1] == 0xF7 and not is_ztf(f), "frame hello bornes")
    check(parse_frame(f) == (0x60, b""), "parse_frame hello")
    body = bytes(range(256)) * 2
    check(parse_frame(frame(0x61, body)) == (0x61, body), "frame 512 o round-trip")
    bad = bytearray(frame(0x62, b"abc")); bad[5] ^= 0xFF
    check(parse_frame(bytes(bad)) is None, "checksum rejeté")

    # ZTF brut
    p = ztf_frame('W', struct.pack("<BI", 3, 800))
    check(p == bytes([0xF0, 0x7D, 0x5A, 0x54, ord('W'), 3, 0x20, 0x03, 0, 0, 0xF7]), "ztf W bytes")
    check(is_ztf(p) and parse_ztf(p) == ('W', struct.pack("<BI", 3, 800)), "parse_ztf W")
    check(parse_frame(p) is None, "une trame ZTF n'est PAS du companion")
    check(parse_ztf(frame(0x60)) is None, "une trame companion n'est PAS ZTF")

    # µ-law : convention firmware ml_ulaw_enc/ml_ulaw_dec (0 -> 0xFF, -32768 -> 0x00 -> -32124)
    check(linear_to_ulaw(0) == 0xFF, "µ-law 0 -> 0xFF")
    check(linear_to_ulaw(-32768) == 0x00 and ulaw_to_linear(0x00) == -32124, "µ-law min -> 0x00 -> -32124")
    check(ulaw_to_linear(0xFF) == 0, "µ-law 0xFF -> 0")
    for v in (-32768, -8000, -1, 0, 1, 8000, 32767):
        u = linear_to_ulaw(v)
        d = ulaw_to_linear(u)
        check(abs(d - v) <= max(257, abs(v) // 8 + 2), f"µ-law round-trip {v} -> {u} -> {d}")

    # ZSM1
    samples = [int(20000 * __import__('math').sin(i * 0.1)) for i in range(1000)]
    for fmt in (0, 1):
        blob = encode_zsm1(44100, fmt, samples)
        sr, f2, s2 = decode_zsm1(blob)
        check(sr == 44100 and f2 == fmt and len(s2) == 1000, f"ZSM1 fmt{fmt} header/len")
        if fmt == 0:
            check(s2 == samples, "ZSM1 int16 exact")
        else:
            check(all(abs(a - b) <= abs(b) // 16 + 400 for a, b in zip(s2, samples)), "ZSM1 µ-law proche")
        check(len(blob) == 13 + (len(samples) if fmt == 1 else 2 * len(samples)), "ZSM1 taille")
    check(decode_zsm1(b"XXXX") is None, "ZSM1 magic rejeté")

    # ZLS1 (format ztek_loop.c patch 0010 : 18 + 5*n_note + 2 + 8*n_par)
    z = {"pitch": -12, "pos": 200, "mode": 2, "mut": 1, "src": 3, "fx": 0, "vol": 200,
         "len": 64, "len_set": True, "on": True,
         "notes": [(0, 60, 100, 8), (16, 64, 90, 8), (32, 67, 80, 16)],
         "pars": [(4, 26, 0.5), (8, 30, 0.25)]}
    blob = encode_zls1(z)
    check(len(blob) == 18 + 3 * 5 + 2 + 2 * 8, "ZLS1 taille 18+5n+2+8p")
    z2 = decode_zls1(blob)
    check(z2 is not None and z2["pitch"] == -12 and z2["notes"] == z["notes"] and
          len(z2["pars"]) == 2 and abs(z2["pars"][0][2] - 0.5) < 1e-6, "ZLS1 round-trip")

    # ZMC1 (16 slots x 10 o + 'ZMC1' = 164)
    slots = [(i * 3 - 20, i / 16.0, i % 2 == 0) for i in range(16)]
    zmc = encode_zmc1(slots)
    check(len(zmc) == 164, "ZMC1 taille 164")
    check(decode_zmc1(zmc) == slots, "ZMC1 round-trip")

    # ---- vecteurs pour le cross-check C (--with-c) ----
    vecdir = "/tmp/ztf_vectors"
    if "--with-c" in sys.argv:
        os.makedirs(vecdir, exist_ok=True)
        with open(vecdir + "/vec_zls1.bin", "wb") as f: f.write(encode_zls1(z))
        with open(vecdir + "/vec_zmc1.bin", "wb") as f: f.write(zmc)
        with open(vecdir + "/vec_zsm1.bin", "wb") as f: f.write(encode_zsm1(44100, 1, samples[:2000]))
        pack_in = bytes(rng.randrange(256) for _ in range(560))
        with open(vecdir + "/vec_pack_in.bin", "wb") as f: f.write(pack_in)
        r = subprocess.run(["/tmp/ztf_vec", vecdir], capture_output=True, text=True)
        print(r.stdout.strip())
        if r.returncode != 0:
            check(False, "harnais C rc=%d" % r.returncode)
        else:
            rt = open(vecdir + "/vec_zls1_rt.bin", "rb").read()
            check(rt == encode_zls1(z), "C zml_slot_load/save == Python encode_zls1 (octets identiques)")
            rt = open(vecdir + "/vec_zmc1_rt.bin", "rb").read()
            check(rt == zmc, "C zmac_load/save == Python encode_zmc1")
            pong = open(vecdir + "/vec_pong.bin", "rb").read()
            # F0 7D 'Z' 'T' + body 13 o ('P' + "zuluTek" + ver + free u32) + F7 = 18 o
            check(pong[:5] == bytes([0xF0, 0x7D, 0x5A, 0x54, ord('P')]) and pong[5:12] == b"zuluTek"
                  and pong[-1] == 0xF7 and len(pong) == 18 and pong[12] == 1,
                  "PONG conforme (zuluTek v1 free u32)")
            pk = open(vecdir + "/vec_pack_out.bin", "rb").read()
            check(pk == pack7(pack_in), "cp_pack7 (companion.c) == pack7 Python")
            for line in r.stdout.splitlines():
                if line.startswith("ZSM1"):
                    check("rc=0" in line and "energie=" in line, "ZSM1 ring import rc=0")
                    e = float(line.split("energie=")[1])
                    check(e > 0.001, f"ring vivant après import ZSM1 (énergie {e:.2f})")

    if FAILS:
        print(f"ECHEC: {len(FAILS)} checks en échec")
        return 1
    print("test_ztf_codec: OK (tous les checks passent)")
    return 0

if __name__ == "__main__":
    sys.exit(main())
