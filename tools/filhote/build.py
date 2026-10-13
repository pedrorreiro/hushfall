"""Gera a textura do Filhote (32x32), no mod e aqui, e a prévia 3D (preview.html) com o Ouvinte ao lado para escala.

Rode: python3 tools/filhote/build.py src/main/resources/assets/silenciototal/
"""
import base64, io, os, random, sys
from PIL import Image

MOD = sys.argv[1]
OUT = os.path.dirname(os.path.abspath(__file__))
random.seed(11)

# Mesma paleta do Ouvinte, um pouco mais clara e rosada (filhote).
SKIN = (138, 140, 131)
SKIN_DARK = (104, 107, 100)
SKIN_LIGHT = (164, 165, 155)
BELLY = (160, 150, 144)
FLESH = (170, 88, 92)
FLESH_DARK = (120, 54, 62)
MOUTH = (34, 16, 18)
TOOTH = (220, 212, 188)
CLAW = (48, 46, 44)

img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
px = img.load()


def mottled(x0, y0, w, h, base=SKIN, spread=6):
    for x in range(x0, x0 + w):
        for y in range(y0, y0 + h):
            r = random.random()
            if r < 0.05:
                c = SKIN_DARK
            elif r < 0.09:
                c = SKIN_LIGHT
            else:
                j = random.randint(-spread, spread) // 2
                c = tuple(max(0, min(255, v + j)) for v in base)
            px[x, y] = c + (255,)


def box(u, v, w, h, d):
    faces = {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }
    for (x, y, fw, fh) in faces.values():
        mottled(x, y, fw, fh)
    return faces


# Cabeça 6x5x5 em (0,0): sem olhos, pálpebras costuradas, boquinha.
head = box(0, 0, 6, 5, 5)
fx, fy, fw, fh = head["front"]
for x in (fx + 1, fx + 2, fx + 3, fx + 4):
    if x in (fx + 1, fx + 4):
        px[x, fy + 1] = SKIN_DARK + (255,)
for x in (fx + 1, fx + 4):
    px[x, fy + 2] = (84, 86, 80, 255)
for x in range(fx + 1, fx + fw - 1):
    px[x, fy + 4] = MOUTH + (255,)
px[fx + 2, fy + 4] = TOOTH + (255,)
px[fx + 3, fy + 4] = TOOTH + (255,)

# Mandíbula 5x1x4 em (0,10).
jaw = box(0, 10, 5, 1, 4)
jx, jy, jw, jh = jaw["front"]
for x in range(jx, jx + jw):
    px[x, jy] = MOUTH + (255,)
jx, jy, jw, jh = jaw["top"]
for x in range(jx, jx + jw):
    for y in range(jy, jy + jh):
        px[x, y] = (96, 34, 40, 255)

# Orelha 1x6x4 em (22,0): interno de carne com veias.
ear = box(22, 0, 1, 6, 4)
for name in ("right", "left"):
    x0, y0, w, h = ear[name]
    for x in range(x0, x0 + w):
        for y in range(y0, y0 + h):
            edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
            if name == "right" and not edge:
                px[x, y] = (FLESH if random.random() > 0.25 else FLESH_DARK) + (255,)
    if name == "right":
        for y in range(y0 + 1, y0 + h - 1, 2):
            px[x0 + 1, y] = FLESH_DARK + (255,)

# Tronco 5x4x6 em (0,16): barriga clara, costelas nos lados.
body = box(0, 16, 5, 4, 6)
bx, by, bw, bh = body["bottom"]
for x in range(bx, bx + bw):
    for y in range(by, by + bh):
        px[x, y] = BELLY + (255,)
for side in ("left", "right"):
    sx, sy, sw, sh = body[side]
    for x in range(sx + 1, sx + sw - 1, 2):
        px[x, sy + 1] = SKIN_DARK + (255,)
        px[x, sy + 2] = SKIN_DARK + (255,)

# Braço 2x6x2 em (22,10): garrinhas escuras.
arm = box(22, 10, 2, 6, 2)
for name in ("front", "left", "right", "back"):
    x0, y0, w, h = arm[name]
    for x in range(x0, x0 + w):
        px[x, y0 + h - 1] = CLAW + (255,)

# Perna 2x3x2 em (22,18).
box(22, 18, 2, 3, 2)

img.save(os.path.join(OUT, "listener_pup.png"))
os.makedirs(os.path.join(MOD, "textures/entity/listener_pup"), exist_ok=True)
img.save(os.path.join(MOD, "textures/entity/listener_pup/listener_pup.png"))
big = img.resize((256, 256), Image.NEAREST)
big.save(os.path.join(OUT, "listener_pup_x8.png"))


def url(path_or_img):
    if isinstance(path_or_img, Image.Image):
        buf = io.BytesIO()
        path_or_img.save(buf, "PNG")
        raw = buf.getvalue()
    else:
        raw = open(path_or_img, "rb").read()
    return "data:image/png;base64," + base64.b64encode(raw).decode()


template = open(os.path.join(OUT, "template.html")).read()
html = (template
        .replace("__PUP__", url(img))
        .replace("__LISTENER__", url(os.path.join(MOD, "textures/entity/listener/listener.png"))))
open(os.path.join(OUT, "preview.html"), "w").write(html)
print("ok")
