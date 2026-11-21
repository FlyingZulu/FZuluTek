#!/usr/bin/env python3
# tools/make_icon.py — génère les visuels de l'APK zuluTek en PIXEL ART fin
# (style console portable 16 bits type Game Gear, pas le gros pixel Game Boy) :
# REMIX Ghetto Blaster x Nyan Cat : corps pop-tart rose (croûte + sprinkles), platine
# cassette devenue visage de chat, oreilles, poignée, antenne, HP, traînée étoilée.
# - icônes launcher : master 120x120 -> nearest vers mipmap 48..192
# - splash : master 240x240 -> drawable-nodpi/splash_boombox.png (480 px, fond transparent)
# Redimensionnement au plus proche voisin : les pixels restent nets.
import zlib, struct, os

ROOT = os.path.join(os.path.dirname(__file__), "..", "app-zulutek", "src", "main", "res")

def write_png(path, w, h, rgba):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        raw += rgba[y * w * 4:(y + 1) * w * 4]
    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "wb").write(png)

class Px:  # grille basse résolution, pixels durs (pas d'antialiasing)
    def __init__(self, s):
        self.s = s
        self.p = bytearray(s * s * 4)
    def set(self, x, y, c):
        x, y = int(x), int(y)
        if 0 <= x < self.s and 0 <= y < self.s:
            i = (y * self.s + x) * 4
            self.p[i:i + 4] = bytes(c)
    def rect(self, x0, y0, x1, y1, c, r=0):
        for y in range(int(y0), int(y1)):
            for x in range(int(x0), int(x1)):
                if r > 0 and (x - min(max(x, x0 + r), x1 - r - 1)) ** 2 + (y - min(max(y, y0 + r), y1 - r - 1)) ** 2 > r * r:
                    continue
                self.set(x, y, c)
    def circle(self, cx, cy, r, c):
        for y in range(int(cy - r), int(cy + r) + 1):
            for x in range(int(cx - r), int(cx + r) + 1):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                    self.set(x, y, c)
    def tri(self, x0, y0, x1, y1, x2, y2, c):
        d = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
        if d == 0:
            return
        for y in range(int(min(y0, y1, y2)), int(max(y0, y1, y2)) + 1):
            for x in range(int(min(x0, x1, x2)), int(max(x0, x1, x2)) + 1):
                a = ((y1 - y2) * (x - x2) + (x2 - x1) * (y - y2)) / d
                b = ((y2 - y0) * (x - x2) + (x0 - x2) * (y - y2)) / d
                if a >= 0 and b >= 0 and 1 - a - b >= 0:
                    self.set(x, y, c)
    def line(self, x0, y0, x1, y1, w, c):
        n = int(max(abs(x1 - x0), abs(y1 - y0))) * 2 + 1
        for i in range(n + 1):
            t = i / n
            self.circle(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, w / 2, c)
    def nearest(self, s):
        out = bytearray(s * s * 4)
        for y in range(s):
            sy = y * self.s // s
            for x in range(s):
                i = (sy * self.s + x * self.s // s) * 4
                o = (y * s + x) * 4
                out[o:o + 4] = self.p[i:i + 4]
        return out

RAINBOW = [(255, 48, 48, 255), (255, 160, 0, 255), (255, 238, 0, 255),
           (0, 200, 60, 255), (0, 160, 255, 255), (170, 60, 255, 255)]
PINK = (255, 158, 222, 255)      # pop-tart rose
PINK_D = (226, 74, 168, 255)
DARK = (22, 23, 30, 255)
GRAY = (200, 204, 214, 255)

def draw(cv, bg=True, tag="ZT", accent=(255, 45, 160, 255), base=(10, 10, 16, 255)):
    # REMIX GHETTO BLASTER x NYAN CAT (drawing in 120 units, scaled to the grid)
    # le walkman-cat : corps pop-tart rose a croote + sprinkles, platine cassette devenue
    # visage de chat (yeux, joues roses, sourire, moustaches), oreilles, poignee, antenne,
    # deux HP, trainee arc-en-ciel etoilee derriere.
    k = cv.s / 120.0
    def X(v):
        return v * k
    if bg:
        cv.rect(0, 0, cv.s, cv.s, base, r=9 * k)
    # trainee arc-en-ciel etoilee derriere le boombox
    bh, y0 = 6, 54
    for i, c in enumerate(RAINBOW):
        cv.rect(0, X(y0 + i * bh), X(52), X(y0 + (i + 1) * bh), c)
    for sx, sy in ((8, 40), (16, 84), (26, 32), (34, 92), (44, 46), (12, 66)):
        cv.line(X(sx - 2), X(sy), X(sx + 2), X(sy), 1 * k, (255, 255, 255, 255))
        cv.line(X(sx), X(sy - 2), X(sx), X(sy + 2), 1 * k, (255, 255, 255, 255))
    # antenne
    cv.line(X(96), X(30), X(114), X(10), 2.2 * k, GRAY)
    cv.circle(X(114), X(10), 2.6 * k, GRAY)
    # poignee entre les oreilles
    cv.rect(X(52), X(26), X(74), X(29), PINK_D)
    cv.rect(X(52), X(36), X(74), X(39), PINK_D)
    cv.rect(X(52), X(26), X(55), X(39), PINK_D)
    cv.rect(X(71), X(26), X(74), X(39), PINK_D)
    # oreilles de chat (contour sombre puis rose)
    cv.tri(X(40), X(38), X(36), X(19), X(54), X(38), DARK)
    cv.tri(X(88), X(38), X(74), X(38), X(92), X(19), DARK)
    cv.tri(X(42), X(37), X(39), X(23), X(52), X(37), PINK)
    cv.tri(X(86), X(37), X(77), X(37), X(89), X(23), PINK)
    # corps du ghetto blaster : contour sombre, croote pop-tart, interieur rose
    cv.rect(X(26), X(36), X(106), X(104), DARK, r=8 * k)
    cv.rect(X(28.5), X(38.5), X(103.5), X(101.5), PINK_D)
    cv.rect(X(30), X(40), X(102), X(100), PINK, r=6 * k)
    # sprinkles sur la pop-tart (hors platine/HP)
    SPR = [(255, 255, 255, 255), (255, 238, 0, 255), (0, 255, 224, 255),
           (255, 48, 48, 255), (0, 160, 255, 255), (170, 60, 255, 255)]
    for si, (sx, sy) in enumerate(((34, 44), (40, 42), (58, 43), (70, 43), (84, 44), (95, 42), (102, 74), (103, 88))):
        cv.circle(X(sx), X(sy), 1.3 * k, SPR[si % 6])
    # platine cassette = visage du chat (grande fenetre centrale)
    cv.rect(X(46), X(46), X(94), X(77), DARK, r=4 * k)
    cv.rect(X(48), X(48), X(92), X(75), (36, 37, 48, 255), r=3 * k)
    cv.circle(X(59), X(58), 2.6 * k, (255, 255, 255, 255))       # oeil gauche
    cv.circle(X(81), X(58), 2.6 * k, (255, 255, 255, 255))       # oeil droit
    cv.circle(X(59.6), X(58.6), 1.1 * k, DARK)
    cv.circle(X(81.6), X(58.6), 1.1 * k, DARK)
    cv.circle(X(54), X(66), 2.2 * k, (255, 120, 160, 255))       # joue rose
    cv.circle(X(86), X(66), 2.2 * k, (255, 120, 160, 255))
    cv.tri(X(58), X(66), X(70), X(66), X(64), X(71), (255, 255, 255, 255))   # sourire
    cv.tri(X(70), X(66), X(82), X(66), X(76), X(71), (255, 255, 255, 255))
    cv.line(X(49), X(55), X(57), X(56), 1 * k, (255, 255, 255, 255))          # moustaches
    cv.line(X(49), X(61), X(57), X(60), 1 * k, (255, 255, 255, 255))
    cv.line(X(91), X(55), X(83), X(56), 1 * k, (255, 255, 255, 255))
    cv.line(X(91), X(61), X(83), X(60), 1 * k, (255, 255, 255, 255))
    # haut-parleurs (cones)
    for cx in (40, 92):
        cv.circle(X(cx), X(89), 9.5 * k, DARK)
        cv.circle(X(cx), X(89), 8 * k, (46, 48, 60, 255))
        cv.circle(X(cx), X(89), 4.5 * k, DARK)
        cv.circle(X(cx), X(89), 2 * k, PINK_D)
    # bouton + LED sous la platine
    cv.rect(X(62), X(84), X(70), X(87), (46, 48, 60, 255), r=1 * k)
    cv.circle(X(64.5), X(92), 1.4 * k, (255, 60, 60, 255))
    cv.circle(X(68.5), X(92), 1.4 * k, (255, 238, 0, 255))
    if bg and tag:
        draw_tag(cv, tag, accent)

# --- initiales d'identité (charte) : UN SEUL DESSIN pour tout l'écosystème Zulu, mais
# chaque app porte ses initiales dans un petit cartouche coloré à son accent, pour qu'on
# ne confonde pas les icônes : ZT (ZuluTek) rose fluo, ZK (Zulu Kontrol) turquoise,
# ZD (Drums) jaune fluo. La variation de teinte du fond est volontairement imperceptible
# seule, repérable côte à côte. Voir docs/ZULUTEK-UI-STYLE.md.
GLYPHS = {
    "Z": ("###", "..#", ".#.", "#..", "###"),
    "T": ("###", ".#.", ".#.", ".#.", ".#."),
    "K": ("#.#", "#.#", "###", "#.#", "#.#"),
    "D": ("##.", "#.#", "#.#", "#.#", "##."),
}

def draw_tag(cv, tag, accent):
    """Cartouche d'initiales en bas à droite (style autocollant), pixel art 3x5."""
    u = cv.s / 240.0
    cv.rect(190 * u, 196 * u, 236 * u, 230 * u, accent, r=4 * u)
    cv.rect(192 * u, 198 * u, 234 * u, 228 * u, (14, 15, 20, 255), r=3 * u)
    px = 4.0 * u
    x = 199.0 * u
    y = 203.0 * u
    for ch in tag:
        g = GLYPHS[ch]
        for ry in range(5):
            for rx in range(3):
                if g[ry][rx] == "#":
                    cv.rect(x + rx * px, y + ry * px, x + (rx + 1) * px, y + (ry + 1) * px, (255, 255, 255, 255))
        x += 4 * px

master = Px(240)   # master fin (style Game Gear) ; les mipmap sont un nearest depuis 240
draw(master, bg=True)
for name, s in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    px = master.nearest(s)
    write_png(os.path.join(ROOT, "mipmap-" + name, "ic_launcher.png"), s, s, px)
    print("wrote mipmap-%s/ic_launcher.png (%d px)" % (name, s))

splash = Px(240)
draw(splash, bg=False)
px = splash.nearest(480)
write_png(os.path.join(ROOT, "drawable-nodpi", "splash_boombox.png"), 480, 480, px)
print("wrote drawable-nodpi/splash_boombox.png (480 px, art 240x240)")

# décor "intérieur du FM-1" : PCB sombre, pistes cuivre, chip AC791N épinglé, câbles colorés,
# condensateurs, LED — le Nyan Cat évolue dedans (splash + veille), pixel art 240x240
def draw_pcb(cv):
    s = cv.s
    cv.rect(0, 0, s, s, (8, 14, 9, 255))                       # PCB vert très sombre
    cu, cu2 = (120, 80, 40, 255), (80, 55, 28, 255)
    for i in range(0, s, 20):
        cv.line(i, 0, i, s, 1, cu2)
    for i in range(10, s, 26):
        cv.line(0, i, s, i, 1, cu2)
    for y in range(10, s, 26):
        for x in range(0, s, 20):
            cv.circle(x, y, 1.6, cu)                            # vias
    # chip CPU central (AC791N) : contour, corps, die, broches 4 côtés, point de broche 1
    cv.rect(84, 80, 156, 152, (14, 15, 19, 255), r=6)
    cv.rect(88, 84, 152, 148, (40, 42, 52, 255), r=4)
    cv.rect(104, 100, 136, 132, (22, 24, 30, 255), r=2)
    for x in range(94, 150, 9):
        cv.rect(x, 76, x + 4, 84, (190, 195, 205, 255))
        cv.rect(x, 148, x + 4, 156, (190, 195, 205, 255))
    for y in range(90, 146, 9):
        cv.rect(80, y, 88, y + 4, (190, 195, 205, 255))
        cv.rect(152, y, 160, y + 4, (190, 195, 205, 255))
    cv.circle(95, 91, 2.5, (220, 220, 230, 255))
    # câbles colorés qui serpentent
    def wire(pts, w, c):
        for i in range(len(pts) - 1):
            cv.line(pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], w, c)
        for p in pts:
            cv.circle(p[0], p[1], w / 2, c)
    wire([(0, 205), (60, 205), (60, 180), (150, 180), (150, 162), (240, 162)], 4, PINK)
    wire([(0, 60), (40, 60), (40, 92), (200, 92), (200, 70), (240, 70)], 4, (255, 238, 0, 255))
    wire([(0, 122), (30, 122), (30, 140), (78, 140)], 3, (0, 255, 224, 255))
    wire([(240, 28), (182, 28), (182, 50), (128, 50)], 3, (255, 70, 70, 255))
    # condensateurs cylindriques
    for cx, cy in ((36, 44), (206, 214)):
        cv.rect(cx - 7, cy, cx + 7, cy + 16, (150, 155, 165, 255))
        cv.circle(cx, cy, 7, (200, 205, 215, 255))
        cv.rect(cx - 7, cy + 5, cx + 7, cy + 8, (60, 64, 74, 255))
    # résistance
    cv.rect(58, 168, 80, 174, (180, 140, 90, 255))
    cv.rect(63, 168, 66, 174, (200, 60, 60, 255))
    cv.rect(69, 168, 72, 174, (255, 238, 0, 255))
    # LED verte qui clignote (halo en cercles décroissants)
    cv.circle(220, 20, 9, (0, 60, 30, 255))
    cv.circle(220, 20, 7, (0, 100, 48, 255))
    cv.circle(220, 20, 4, (0, 200, 90, 255))
    cv.circle(220, 20, 2, (180, 255, 200, 255))
    # vis aux coins
    for vx, vy in ((8, 8), (s - 8, 8), (8, s - 8), (s - 8, s - 8)):
        cv.circle(vx, vy, 4, (60, 62, 72, 255))
        cv.line(vx - 3, vy, vx + 3, vy, 1.4, (20, 21, 26, 255))

pcb = Px(240)
draw_pcb(pcb)
px = pcb.nearest(480)
write_png(os.path.join(ROOT, "drawable-nodpi", "pcb_scene.png"), 480, 480, px)
print("wrote drawable-nodpi/pcb_scene.png (480 px, art 240x240)")

# ---- icône du module drums (app-zulutek-drums) : tambour pixel art sur fond sombre ----
ROOT_DRUMS = os.path.join(os.path.dirname(__file__), "..", "app-zulutek-drums", "src", "main", "res")

def draw_drum(cv):
    s = cv.s
    cv.rect(0, 0, s, s, (10, 10, 16, 255), r=int(s * 0.075))
    k = s / 120.0
    def X(v):
        return v * k
    RED = (255, 70, 70, 255)
    RED_D = (200, 40, 45, 255)
    SKIN = (255, 240, 210, 255)
    WOOD = (200, 150, 80, 255)
    # baguettes
    cv.line(X(38), X(30), X(56), X(62), 2.4 * k, WOOD)
    cv.line(X(82), X(30), X(64), X(62), 2.4 * k, WOOD)
    cv.circle(X(56), X(62), 2.6 * k, WOOD)
    cv.circle(X(64), X(62), 2.6 * k, WOOD)
    # corps du tambour (cylindre vu de face légèrement de biais)
    cv.rect(X(30), X(58), X(90), X(102), RED_D)                       # ombre bas
    cv.rect(X(28), X(52), X(92), X(98), RED)                          # fût
    for x in (40, 60, 80):                                            # cerclages
        cv.line(X(x), X(52), X(x), X(98), 1.6 * k, RED_D)
    cv.rect(X(28), X(52), X(92), X(60), RED_D)                        # cerclage haut
    cv.rect(X(28), X(94), X(92), X(98), RED_D)                        # cerclage bas
    # peau : ellipse aplatie (on écrase verticalement)
    for yy in range(int(X(30)), int(X(82))):
        for xx in range(int(X(28)), int(X(92))):
            dy = (yy - X(56)) / (26 * k)
            if (xx - X(60)) ** 2 / (32 * k) ** 2 + dy * dy <= 1.0:
                cv.set(xx, yy, SKIN)
    # reflet sur la peau + ombre portée
    cv.line(X(42), X(44), X(54), X(38), 2 * k, (255, 255, 255, 255))
    cv.rect(X(34), X(102), X(86), X(106), (0, 0, 0, 120))

drum = Px(240)
draw_drum(drum)
draw_tag(drum, "ZD", (255, 238, 0, 255))
for name, s in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    px = drum.nearest(s)
    write_png(os.path.join(ROOT_DRUMS, "mipmap-" + name, "ic_launcher.png"), s, s, px)
    print("wrote [drums] mipmap-%s/ic_launcher.png (%d px)" % (name, s))
