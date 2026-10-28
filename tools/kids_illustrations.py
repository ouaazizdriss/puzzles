"""Generates the illustrated Kids puzzles.

Usage: python3 tools/kids_illustrations.py <Fredoka.ttf> <out_dir>
Fredoka (OFL): https://github.com/google/fonts/tree/main/ofl/fredoka
"""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

FONT, OUT = sys.argv[1], sys.argv[2]
W, H = 1600, 1200
S = 2  # supersampling


def font(size, weight="Bold"):
    f = ImageFont.truetype(FONT, int(size * S))
    f.set_variation_by_name(weight)
    return f


def sc(v):
    return [x * S for x in v] if isinstance(v, (list, tuple)) else v * S


class Canvas:
    def __init__(self, bg):
        self.img = Image.new("RGB", (W * S, H * S), bg)
        self.shadow = Image.new("L", (W * S, H * S), 0)
        self.d = ImageDraw.Draw(self.img)
        self.sd = ImageDraw.Draw(self.shadow)

    def flush_shadow(self, off=10, blur=14, alpha=0.22):
        """Composites accumulated shadow shapes under whatever is drawn next."""
        sh = self.shadow.filter(ImageFilter.GaussianBlur(blur * S))
        moved = Image.new("L", sh.size, 0)
        moved.paste(sh, (off * S, off * S))
        dark = Image.new("RGB", self.img.size, (0, 0, 0))
        self.img = Image.composite(dark, self.img, moved.point(lambda p: int(p * alpha)))
        self.d = ImageDraw.Draw(self.img)
        self.shadow = Image.new("L", self.img.size, 0)
        self.sd = ImageDraw.Draw(self.shadow)

    # Each primitive takes design-space coordinates (1600x1200) and can cast a shadow.
    def ellipse(self, box, fill, outline=None, width=0, shadow=False):
        if shadow:
            self.sd.ellipse(sc(box), fill=255)
        else:
            self.d.ellipse(sc(box), fill=fill, outline=outline, width=max(1, int(round(sc(width)))))

    def rrect(self, box, r, fill, outline=None, width=0, shadow=False):
        if shadow:
            self.sd.rounded_rectangle(sc(box), sc(r), fill=255)
        else:
            self.d.rounded_rectangle(sc(box), sc(r), fill=fill, outline=outline, width=max(1, int(round(sc(width)))))

    def poly(self, pts, fill, outline=None, width=0, shadow=False):
        p = [(x * S, y * S) for x, y in pts]
        if shadow:
            self.sd.polygon(p, fill=255)
        else:
            self.d.polygon(p, fill=fill, outline=outline, width=max(1, int(round(sc(width)))))

    def line(self, pts, fill, width):
        self.d.line([(x * S, y * S) for x, y in pts], fill=fill, width=max(1, int(round(sc(width)))), joint="curve")
        r = width / 2
        for x, y in (pts[0], pts[-1]):
            self.ellipse((x - r, y - r, x + r, y + r), fill)

    def arc(self, box, start, end, fill, width):
        self.d.arc(sc(box), start, end, fill=fill, width=max(1, int(round(sc(width)))))

    def text(self, xy, s, size, fill, anchor="mm", weight="Bold", stroke=0, stroke_fill=None):
        self.d.text(sc(xy), s, font=font(size, weight), fill=fill, anchor=anchor,
                    stroke_width=sc(stroke), stroke_fill=stroke_fill)

    def gradient(self, top, bottom, box=(0, 0, W, H)):
        x0, y0, x1, y1 = box
        for y in range(y0 * S, y1 * S):
            t = (y - y0 * S) / max(1, (y1 - y0) * S - 1)
            c = tuple(int(a + (b - a) * t) for a, b in zip(top, bottom))
            self.d.line([(x0 * S, y), (x1 * S, y)], fill=c)

    def save(self, name):
        self.img.resize((W, H), Image.LANCZOS).save(os.path.join(OUT, name), "WEBP", quality=90, method=6)


def star_pts(cx, cy, r_out, r_in, n=5, rot=-90):
    pts = []
    for i in range(n * 2):
        r = r_out if i % 2 == 0 else r_in
        a = math.radians(rot + i * 180 / n)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def heart_pts(cx, cy, size):
    pts = []
    for i in range(120):
        t = 2 * math.pi * i / 120
        x = 16 * math.sin(t) ** 3
        y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        pts.append((cx + x * size / 32, cy - y * size / 32 - size * 0.05))
    return pts


def dots_bg(c, color, step=80, r=6):
    for yi, y in enumerate(range(40, H, step)):
        for x in range(40 + (step // 2 if yi % 2 else 0), W, step):
            c.ellipse((x - r, y - r, x + r, y + r), color)


def tile_grid(cols, rows, margin=60, gap=40, top=60):
    tw = (W - 2 * margin - (cols - 1) * gap) / cols
    th = (H - top - margin - (rows - 1) * gap) / rows
    for r in range(rows):
        for col in range(cols):
            x = margin + col * (tw + gap)
            y = top + r * (th + gap)
            yield (x, y, x + tw, y + th)


BRIGHT = [(239, 71, 111), (255, 159, 28), (255, 209, 102), (6, 214, 160), (17, 138, 178),
          (131, 56, 236), (255, 99, 196), (58, 134, 255), (251, 86, 7)]


def darker(c, f=0.75):
    return tuple(int(v * f) for v in c)


# ---------- 1. Shapes ----------
def shapes():
    c = Canvas((255, 246, 222))
    dots_bg(c, (255, 234, 190))
    items = [("Circle", (239, 71, 111)), ("Square", (58, 134, 255)), ("Triangle", (6, 214, 160)),
             ("Star", (255, 190, 11)), ("Heart", (255, 99, 196)), ("Diamond", (131, 56, 236))]
    boxes = list(tile_grid(3, 2, margin=70, gap=50, top=70))

    def draw(i, shadow):
        name, col = items[i]
        x0, y0, x1, y1 = boxes[i]
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2 - 40
        r = 140
        if name == "Circle":
            c.ellipse((cx - r, cy - r, cx + r, cy + r), col, shadow=shadow)
        elif name == "Square":
            c.rrect((cx - r * 0.9, cy - r * 0.9, cx + r * 0.9, cy + r * 0.9), 26, col, shadow=shadow)
        elif name == "Triangle":
            c.poly([(cx, cy - r * 1.05), (cx + r * 1.1, cy + r * 0.85), (cx - r * 1.1, cy + r * 0.85)], col, shadow=shadow)
        elif name == "Star":
            c.poly(star_pts(cx, cy + 10, r * 1.15, r * 0.5), col, shadow=shadow)
        elif name == "Heart":
            c.poly(heart_pts(cx, cy + 10, r * 2.1), col, shadow=shadow)
        else:
            c.poly([(cx, cy - r * 1.1), (cx + r * 0.85, cy), (cx, cy + r * 1.1), (cx - r * 0.85, cy)], col, shadow=shadow)

    for x0, y0, x1, y1 in boxes:
        c.rrect((x0, y0, x1, y1), 40, None, shadow=True)
    c.flush_shadow(off=8, blur=12, alpha=0.15)
    for i, (x0, y0, x1, y1) in enumerate(boxes):
        c.rrect((x0, y0, x1, y1), 40, (255, 255, 255))
    for i in range(6):
        draw(i, True)
    c.flush_shadow(off=10, blur=10, alpha=0.25)
    for i in range(6):
        draw(i, False)
        x0, y0, x1, y1 = boxes[i]
        c.text(((x0 + x1) / 2, y1 - 55), items[i][0], 58, darker(items[i][1], 0.8))
    c.save("k_shapes.webp")


# ---------- 2. Numbers ----------
def numbers():
    c = Canvas((232, 246, 255))
    dots_bg(c, (210, 234, 252))
    boxes = list(tile_grid(3, 3, margin=60, gap=36, top=60))
    for b in boxes:
        c.rrect(b, 44, None, shadow=True)
    c.flush_shadow(off=10, blur=12, alpha=0.2)
    for i, (x0, y0, x1, y1) in enumerate(boxes):
        n = i + 1
        col = BRIGHT[i]
        c.rrect((x0, y0, x1, y1), 44, col)
        c.rrect((x0 + 10, y0 + 10, x1 - 10, y1 - 10), 36, None, outline=(255, 255, 255), width=5)
        w, h = x1 - x0, y1 - y0
        c.text((x0 + w * 0.3, y0 + h * 0.5), str(n), 200, (255, 255, 255), stroke=6, stroke_fill=darker(col, 0.7))
        # n dots in a 3-column grid so children can count
        r, gap = 22, 62
        rows = (n + 2) // 3
        gx, gy = x0 + w * 0.72, y0 + h * 0.5 - (rows - 1) * gap / 2
        for k in range(n):
            row, colk = k // 3, k % 3
            in_row = min(3, n - row * 3)
            dx = gx + (colk - (in_row - 1) / 2) * gap
            dy = gy + row * gap
            c.ellipse((dx - r, dy - r, dx + r, dy + r), (255, 255, 255))
    c.save("k_numbers.webp")


# ---------- 3. ABC ----------
def abc():
    c = Canvas((255, 240, 245))
    dots_bg(c, (255, 222, 233))
    letters = "ABCDEFGHIJKL"
    boxes = list(tile_grid(4, 3, margin=60, gap=34, top=60))
    for b in boxes:
        c.rrect(b, 40, None, shadow=True)
    c.flush_shadow(off=10, blur=12, alpha=0.2)
    for i, (x0, y0, x1, y1) in enumerate(boxes):
        col = BRIGHT[(i * 4) % len(BRIGHT)]
        c.rrect((x0, y0, x1, y1), 40, col)
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        c.text((cx, cy + 6), letters[i] + letters[i].lower(), 170, (255, 255, 255), stroke=6, stroke_fill=darker(col, 0.7))
    c.save("k_abc.webp")


# ---------- fruit drawings ----------
def apple(c, cx, cy, r, col=(230, 57, 70), shadow=False):
    c.ellipse((cx - r, cy - r * 0.85, cx + r * 0.15, cy + r), col, shadow=shadow)
    c.ellipse((cx - r * 0.15, cy - r * 0.85, cx + r, cy + r), col, shadow=shadow)
    if shadow:
        return
    c.ellipse((cx - r * 0.55, cy - r * 0.45, cx - r * 0.25, cy - r * 0.05), (255, 140, 150))
    c.line([(cx, cy - r * 0.7), (cx + r * 0.08, cy - r * 1.15)], (110, 70, 40), r * 0.12)
    c.poly([(cx + r * 0.1, cy - r * 1.0), (cx + r * 0.6, cy - r * 1.25), (cx + r * 0.55, cy - r * 0.95)], (56, 176, 0))


def banana(c, cx, cy, r, shadow=False):
    outer = [(cx + r * 1.15 * math.cos(math.radians(t)), cy - r * 0.35 + r * 1.0 * math.sin(math.radians(t)))
             for t in range(10, 171, 4)]
    inner = [(cx + r * 1.0 * math.cos(math.radians(t)), cy - r * 0.75 + r * 0.95 * math.sin(math.radians(t)))
             for t in range(165, 14, -4)]
    c.poly(outer + inner, (255, 214, 10), shadow=shadow)
    if shadow:
        return
    hx, hy = outer[0]
    c.line([(hx, hy), (hx + r * 0.12, hy - r * 0.25)], (120, 90, 30), r * 0.16)
    tx, ty = outer[-1]
    c.ellipse((tx - r * 0.08, ty - r * 0.08, tx + r * 0.08, ty + r * 0.08), (90, 60, 20))


def orange(c, cx, cy, r, shadow=False):
    c.ellipse((cx - r, cy - r, cx + r, cy + r), (255, 140, 0), shadow=shadow)
    if shadow:
        return
    c.ellipse((cx - r * 0.55, cy - r * 0.55, cx - r * 0.25, cy - r * 0.25), (255, 190, 90))
    c.poly([(cx, cy - r * 0.95), (cx + r * 0.5, cy - r * 1.25), (cx + r * 0.45, cy - r * 0.9)], (56, 176, 0))


def strawberry(c, cx, cy, r, shadow=False):
    pts = heart_pts(cx, cy, r * 2.2)
    pts = [(x, 2 * cy - y + r * 0.25) for x, y in pts]  # upside-down heart
    c.poly(pts, (230, 30, 60), shadow=shadow)
    if shadow:
        return
    for i in range(3):
        for j in range(3 - (i == 2)):
            sx = cx - r * 0.45 + j * r * 0.45 + (r * 0.22 if i == 1 else 0) + (r * 0.22 if i == 2 else 0)
            sy = cy - r * 0.25 + i * r * 0.38
            c.ellipse((sx - r * 0.05, sy - r * 0.08, sx + r * 0.05, sy + r * 0.08), (255, 230, 120))
    c.poly(star_pts(cx, cy - r * 0.72, r * 0.5, r * 0.18, n=5, rot=90), (56, 176, 0))


def watermelon(c, cx, cy, r, shadow=False):
    box = (cx - r * 1.15, cy - r * 1.0, cx + r * 1.15, cy + r * 1.3)
    if shadow:
        c.sd.pieslice(sc(box), 0, 180, fill=255)
        return
    c.d.pieslice(sc(box), 0, 180, fill=(46, 160, 67))
    inset = r * 0.14
    c.d.pieslice(sc((box[0] + inset, box[1] + inset, box[2] - inset, box[3] - inset)), 0, 180, fill=(255, 240, 200))
    inset = r * 0.22
    c.d.pieslice(sc((box[0] + inset, box[1] + inset, box[2] - inset, box[3] - inset)), 0, 180, fill=(255, 70, 90))
    mid = (box[1] + box[3]) / 2
    for i, (dx, dy) in enumerate([(-0.5, 0.25), (0, 0.4), (0.5, 0.25), (-0.25, 0.65), (0.25, 0.65)]):
        x, y = cx + dx * r, mid + dy * r
        c.ellipse((x - r * 0.05, y - r * 0.09, x + r * 0.05, y + r * 0.09), (40, 30, 30))


def pear(c, cx, cy, r, shadow=False):
    col = (164, 222, 2)
    c.ellipse((cx - r * 0.85, cy - r * 0.2, cx + r * 0.85, cy + r * 1.0), col, shadow=shadow)
    c.ellipse((cx - r * 0.5, cy - r * 0.95, cx + r * 0.5, cy + r * 0.2), col, shadow=shadow)
    if shadow:
        return
    c.line([(cx, cy - r * 0.9), (cx + r * 0.05, cy - r * 1.25)], (110, 70, 40), r * 0.1)
    c.ellipse((cx - r * 0.5, cy + r * 0.05, cx - r * 0.25, cy + r * 0.3), (210, 245, 120))


def grapes(c, cx, cy, r, shadow=False):
    br = r * 0.27
    rows = [4, 3, 2, 1]
    for i, n in enumerate(rows):
        for j in range(n):
            x = cx - (n - 1) * br + j * 2 * br
            y = cy - r * 0.55 + i * br * 1.7
            c.ellipse((x - br, y - br, x + br, y + br), (130, 60, 180), shadow=shadow)
            if not shadow:
                c.ellipse((x - br * 0.5, y - br * 0.5, x - br * 0.1, y - br * 0.1), (180, 120, 220))
    if not shadow:
        c.line([(cx, cy - r * 0.8), (cx + r * 0.1, cy - r * 1.15)], (110, 70, 40), r * 0.1)
        c.poly([(cx + r * 0.05, cy - r * 0.95), (cx + r * 0.6, cy - r * 1.15), (cx + r * 0.5, cy - r * 0.8)], (56, 176, 0))


def cherries(c, cx, cy, r, shadow=False):
    for dx in (-0.45, 0.4):
        x, y = cx + dx * r, cy + r * 0.45
        c.ellipse((x - r * 0.42, y - r * 0.42, x + r * 0.42, y + r * 0.42), (200, 16, 46), shadow=shadow)
    if shadow:
        return
    for dx in (-0.45, 0.4):
        x, y = cx + dx * r, cy + r * 0.45
        c.ellipse((x - r * 0.25, y - r * 0.25, x - r * 0.05, y - r * 0.05), (255, 120, 130))
        c.line([(x, y - r * 0.35), (cx + r * 0.05, cy - r * 0.9)], (90, 140, 40), r * 0.08)
    c.poly([(cx + r * 0.05, cy - r * 0.9), (cx + r * 0.6, cy - r * 1.05), (cx + r * 0.5, cy - r * 0.75)], (56, 176, 0))


def fruits():
    c = Canvas((240, 255, 236))
    dots_bg(c, (220, 245, 214))
    items = [("Apple", apple), ("Banana", banana), ("Orange", orange), ("Strawberry", strawberry),
             ("Watermelon", watermelon), ("Pear", pear), ("Grapes", grapes), ("Cherries", cherries)]
    boxes = list(tile_grid(4, 2, margin=60, gap=40, top=60))
    for b in boxes:
        c.rrect(b, 44, None, shadow=True)
    c.flush_shadow(off=8, blur=12, alpha=0.15)
    for b in boxes:
        c.rrect(b, 44, (255, 255, 255))
    for (name, fn), (x0, y0, x1, y1) in zip(items, boxes):
        fn(c, (x0 + x1) / 2, (y0 + y1) / 2 - 40, 115, shadow=True)
    c.flush_shadow(off=8, blur=8, alpha=0.22)
    for (name, fn), (x0, y0, x1, y1) in zip(items, boxes):
        fn(c, (x0 + x1) / 2, (y0 + y1) / 2 - 40, 115)
        c.text(((x0 + x1) / 2, y1 - 50), name, 50, (70, 70, 90))
    c.save("k_fruits.webp")


# ---------- scenes ----------
def cloud(c, x, y, s, shadow=False, col=(255, 255, 255)):
    for dx, dy, r in [(-0.9, 0.2, 0.55), (-0.3, -0.2, 0.75), (0.45, -0.05, 0.65), (1.0, 0.25, 0.45)]:
        c.ellipse((x + (dx - r) * s, y + (dy - r) * s, x + (dx + r) * s, y + (dy + r) * s), col, shadow=shadow)
    c.rrect((x - 1.2 * s, y + 0.05 * s, x + 1.3 * s, y + 0.7 * s), 0.3 * s, col, shadow=shadow)


def face(c, cx, cy, r, col=(80, 50, 30)):
    for dx in (-0.35, 0.35):
        c.ellipse((cx + dx * r - r * 0.09, cy - r * 0.25, cx + dx * r + r * 0.09, cy - r * 0.05), col)
    c.arc((cx - r * 0.4, cy - r * 0.2, cx + r * 0.4, cy + r * 0.45), 20, 160, col, r * 0.08)
    for dx in (-0.55, 0.55):
        c.ellipse((cx + dx * r - r * 0.13, cy + r * 0.05, cx + dx * r + r * 0.13, cy + r * 0.25), (255, 150, 150))


def sun(c, cx, cy, r):
    for i in range(12):
        a = math.radians(i * 30)
        c.line([(cx + math.cos(a) * r * 1.25, cy + math.sin(a) * r * 1.25),
                (cx + math.cos(a) * r * 1.6, cy + math.sin(a) * r * 1.6)], (255, 190, 11), r * 0.14)
    c.ellipse((cx - r, cy - r, cx + r, cy + r), (255, 210, 40))
    face(c, cx, cy, r)


def rainbow():
    c = Canvas((0, 0, 0))
    c.gradient((120, 200, 255), (210, 240, 255))
    cols = [(239, 71, 111), (255, 159, 28), (255, 214, 10), (6, 214, 160), (58, 134, 255), (131, 56, 236)]
    cx, cy, R, band = 800, 1000, 640, 62
    for i, col in enumerate(cols):
        rr = R - i * band
        c.arc((cx - rr, cy - rr, cx + rr, cy + rr), 180, 360, col, band + 2)
    c.d.rectangle(sc((0, 950, W, H)), fill=(120, 200, 90))
    c.ellipse((-200, 880, 900, 1400), (100, 185, 75))
    c.ellipse((700, 900, 1900, 1450), (130, 210, 95))
    cloud(c, 230, 960, 120, shadow=True)
    cloud(c, 1370, 960, 120, shadow=True)
    cloud(c, 1180, 230, 80, shadow=True)
    c.flush_shadow(off=8, blur=10, alpha=0.18)
    cloud(c, 230, 960, 120)
    cloud(c, 1370, 960, 120)
    cloud(c, 1180, 230, 80)
    sun(c, 230, 220, 110)
    for x, col in [(520, (255, 99, 196)), (640, (255, 214, 10)), (980, (131, 56, 236)), (1100, (239, 71, 111))]:
        y = 1110
        c.line([(x, y), (x, y + 70)], (60, 150, 50), 10)
        for k in range(5):
            a = math.radians(k * 72)
            c.ellipse((x + math.cos(a) * 22 - 18, y + math.sin(a) * 22 - 18, x + math.cos(a) * 22 + 18, y + math.sin(a) * 22 + 18), col)
        c.ellipse((x - 14, y - 14, x + 14, y + 14), (255, 240, 120))
    c.save("k_rainbow.webp")


def fish(c, x, y, s, col, flip=False, shadow=False):
    d = -1 if flip else 1
    c.poly([(x - d * s * 0.9, y), (x - d * s * 1.5, y - s * 0.55), (x - d * s * 1.5, y + s * 0.55)], darker(col, 0.85), shadow=shadow)
    c.ellipse((x - s, y - s * 0.65, x + s, y + s * 0.65), col, shadow=shadow)
    if shadow:
        return
    c.poly([(x - d * s * 0.2, y - s * 0.6), (x + d * s * 0.3, y - s * 0.95), (x + d * s * 0.45, y - s * 0.5)], darker(col, 0.85))
    ex = x + d * s * 0.5
    c.ellipse((ex - s * 0.17, y - s * 0.3, ex + s * 0.17, y + s * 0.04), (255, 255, 255))
    c.ellipse((ex - s * 0.08 + d * s * 0.04, y - s * 0.2, ex + s * 0.08 + d * s * 0.04, y - s * 0.04), (30, 30, 50))
    for k in range(2):
        sx = x - d * s * (0.1 + k * 0.3)
        c.arc((sx - s * 0.3, y - s * 0.5, sx + s * 0.3, y + s * 0.5), 300 if not flip else 120, 60 if not flip else 240, (255, 255, 255), s * 0.06)


def sea():
    c = Canvas((0, 0, 0))
    c.gradient((80, 200, 240), (10, 90, 170))
    for i in range(7):
        x = 120 + i * 230
        pts = []
        for k in range(30):
            pts.append((x + math.sin(k / 3) * 25, 1150 - k * 14))
        c.line(pts, (40, 170, 90), 26)
    c.d.rectangle(sc((0, 1080, W, H)), fill=(245, 215, 150))
    c.ellipse((-100, 1030, 700, 1250), (245, 215, 150))
    c.ellipse((900, 1040, 1800, 1250), (240, 205, 135))
    fishes = [(400, 350, 120, (255, 140, 0), False), (1150, 300, 100, (255, 99, 196), True),
              (800, 640, 140, (255, 214, 10), False), (300, 800, 90, (6, 214, 160), True),
              (1300, 760, 110, (131, 56, 236), True)]
    for f in fishes:
        fish(c, *f[:4], flip=f[4], shadow=True)
    c.flush_shadow(off=10, blur=12, alpha=0.2)
    for f in fishes:
        fish(c, *f[:4], flip=f[4])
    c.poly(star_pts(1350, 1110, 70, 32), (255, 110, 80))
    c.poly(star_pts(260, 1130, 50, 22, rot=-70), (255, 190, 60))
    for x, y, r in [(560, 200, 24), (590, 140, 16), (620, 95, 11), (980, 470, 20), (1000, 420, 13), (200, 560, 18), (215, 515, 11)]:
        c.ellipse((x - r, y - r, x + r, y + r), None, outline=(220, 245, 255), width=5)
    c.save("k_sea.webp")


def house():
    c = Canvas((0, 0, 0))
    c.gradient((140, 210, 255), (225, 245, 255))
    c.ellipse((-300, 760, 1200, 1500), (120, 200, 90))
    c.ellipse((500, 820, 2000, 1500), (100, 185, 75))
    sun(c, 1380, 200, 105)
    cloud(c, 380, 190, 85)
    # house
    c.rrect((470, 560, 1010, 1000), 10, None, shadow=True)
    c.flush_shadow(off=12, blur=12, alpha=0.2)
    c.rrect((470, 560, 1010, 1000), 10, (255, 214, 120))
    c.poly([(420, 590), (740, 300), (1060, 590)], (230, 70, 70))
    c.rrect((860, 330, 930, 450), 6, (160, 90, 70))
    c.rrect((680, 760, 800, 1000), 50, (140, 80, 50))
    c.ellipse((775, 870, 793, 888), (255, 214, 10))
    for x0 in (530, 860):
        c.rrect((x0, 660, x0 + 100, 760), 10, (130, 210, 255), outline=(255, 255, 255), width=10)
        c.line([(x0 + 50, 665), (x0 + 50, 755)], (255, 255, 255), 8)
    c.ellipse((700, 400, 780, 480), (130, 210, 255), outline=(255, 255, 255), width=8)
    # tree
    c.rrect((1220, 700, 1290, 1000), 20, (140, 90, 50))
    for dx, dy, r in [(0, -60, 150), (-110, 30, 110), (110, 30, 110), (0, 80, 120)]:
        c.ellipse((1255 + dx - r, 640 + dy - r, 1255 + dx + r, 640 + dy + r), (60, 170, 70))
    for x, y in [(1180, 600), (1320, 560), (1250, 700), (1330, 690)]:
        apple(c, x, y, 26)
    # path & flowers
    c.poly([(690, 1000), (790, 1000), (900, H), (580, H)], (230, 200, 160))
    for x, col in [(200, (255, 99, 196)), (320, (255, 214, 10)), (1450, (131, 56, 236)), (1100, (239, 71, 111))]:
        y = 1050
        c.line([(x, y), (x, y + 80)], (60, 150, 50), 10)
        for k in range(5):
            a = math.radians(k * 72)
            c.ellipse((x + math.cos(a) * 24 - 20, y + math.sin(a) * 24 - 20, x + math.cos(a) * 24 + 20, y + math.sin(a) * 24 + 20), col)
        c.ellipse((x - 15, y - 15, x + 15, y + 15), (255, 240, 120))
    c.save("k_house.webp")


def space():
    c = Canvas((0, 0, 0))
    c.gradient((20, 24, 70), (60, 30, 110))
    import random
    rnd = random.Random(3)
    for _ in range(90):
        x, y, r = rnd.uniform(0, W), rnd.uniform(0, H), rnd.uniform(2, 5)
        c.ellipse((x - r, y - r, x + r, y + r), (255, 255, 230))
    for x, y in [(200, 180), (1450, 600), (700, 120), (1100, 1050), (330, 950)]:
        c.poly(star_pts(x, y, 34, 14), (255, 220, 80))
    # moon
    c.ellipse((1150, 90, 1450, 390), (255, 240, 170))
    for x, y, r in [(1230, 180, 30), (1350, 270, 22), (1260, 310, 16)]:
        c.ellipse((x - r, y - r, x + r, y + r), (235, 215, 140))
    # planet with ring
    c.ellipse((140, 420, 520, 800), (255, 140, 90))
    c.ellipse((190, 520, 470, 570), (255, 180, 130))
    c.arc((60, 560, 600, 680), 160, 380, (255, 214, 10), 22)
    # rocket
    cx, cy = 900, 620
    c.poly([(cx - 90, cy + 140), (cx - 190, cy + 300), (cx - 90, cy + 260)], (239, 71, 111))
    c.poly([(cx + 90, cy + 140), (cx + 190, cy + 300), (cx + 90, cy + 260)], (239, 71, 111))
    c.poly([(cx - 60, cy + 270), (cx, cy + 420), (cx + 60, cy + 270)], (255, 159, 28))
    c.poly([(cx - 35, cy + 270), (cx, cy + 360), (cx + 35, cy + 270)], (255, 230, 90))
    c.rrect((cx - 100, cy - 160, cx + 100, cy + 290), 90, (240, 240, 250))
    c.poly([(cx - 98, cy - 100), (cx, cy - 330), (cx + 98, cy - 100)], (239, 71, 111))
    c.ellipse((cx - 60, cy - 60, cx + 60, cy + 60), (130, 210, 255), outline=(58, 134, 255), width=16)
    c.rrect((cx - 100, cy + 150, cx + 100, cy + 190), 0, (58, 134, 255))
    c.save("k_space.webp")


def vehicles():
    c = Canvas((0, 0, 0))
    c.gradient((150, 215, 255), (220, 245, 255))
    cloud(c, 1250, 170, 80)
    cloud(c, 350, 140, 60)
    c.d.rectangle(sc((0, 700, W, H)), fill=(120, 200, 90))
    c.d.rectangle(sc((0, 820, W, 1080)), fill=(90, 90, 105))
    for x in range(0, W, 200):
        c.rrect((x + 30, 940, x + 150, 960), 10, (255, 255, 255))
    def wheel(x, y, r):
        c.ellipse((x - r, y - r, x + r, y + r), (40, 40, 50))
        c.ellipse((x - r * 0.45, y - r * 0.45, x + r * 0.45, y + r * 0.45), (200, 200, 210))
    # car
    c.rrect((140, 760, 620, 900), 50, (239, 71, 111))
    c.rrect((240, 650, 520, 790), 60, (239, 71, 111))
    c.rrect((275, 680, 375, 770), 20, (190, 230, 255))
    c.rrect((395, 680, 490, 770), 20, (190, 230, 255))
    c.ellipse((585, 790, 615, 820), (255, 230, 120))
    wheel(250, 905, 55)
    wheel(510, 905, 55)
    # bus
    c.rrect((820, 600, 1460, 920), 40, (255, 190, 11))
    for k in range(4):
        c.rrect((860 + k * 140, 640, 980 + k * 140, 740), 16, (190, 230, 255))
    c.rrect((820, 780, 1460, 810), 0, (230, 120, 20))
    c.ellipse((1430, 850, 1460, 880), (255, 255, 255))
    wheel(950, 925, 60)
    wheel(1330, 925, 60)
    # traffic light
    c.rrect((680, 300, 720, 820), 10, (70, 70, 80))
    c.rrect((630, 230, 770, 560), 30, (50, 50, 60))
    for k, col in enumerate([(239, 71, 111), (255, 190, 11), (6, 214, 160)]):
        c.ellipse((660, 250 + k * 103, 740, 330 + k * 103), col)
    c.save("k_vehicles.webp")


def colors():
    c = Canvas((255, 252, 245))
    items = [("Red", (230, 40, 50)), ("Orange", (255, 140, 0)), ("Yellow", (255, 210, 0)), ("Green", (40, 180, 70)),
             ("Blue", (40, 110, 230)), ("Purple", (130, 60, 200)), ("Pink", (255, 105, 180)), ("Brown", (140, 90, 50))]
    boxes = list(tile_grid(4, 2, margin=70, gap=50, top=70))
    import random
    rnd = random.Random(5)
    for (name, col), (x0, y0, x1, y1) in zip(items, boxes):
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2 - 30
        pts = []
        for k in range(36):
            a = 2 * math.pi * k / 36
            r = 140 * (1 + 0.12 * math.sin(3 * a + rnd.uniform(0, 6)) + 0.06 * math.sin(5 * a))
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
        c.poly(pts, col, shadow=True)
    c.flush_shadow(off=8, blur=10, alpha=0.2)
    rnd = random.Random(5)
    for (name, col), (x0, y0, x1, y1) in zip(items, boxes):
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2 - 30
        pts = []
        for k in range(36):
            a = 2 * math.pi * k / 36
            r = 140 * (1 + 0.12 * math.sin(3 * a + rnd.uniform(0, 6)) + 0.06 * math.sin(5 * a))
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
        c.poly(pts, col)
        c.ellipse((cx - 70, cy - 70, cx - 30, cy - 30), tuple(min(255, v + 70) for v in col))
        c.text((cx, y1 - 30), name, 60, darker(col, 0.8) if name == "Yellow" else col)
    c.save("k_colors.webp")


os.makedirs(OUT, exist_ok=True)
for fn in (shapes, numbers, abc, fruits, rainbow, sea, house, space, vehicles, colors):
    fn()
    print("ok", fn.__name__)
