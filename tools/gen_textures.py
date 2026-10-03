"""Gera as texturas do mod (Ouvinte, ovo, orelha, adaga e ícone). Rode: python3 tools/gen_textures.py"""
import os
import random
from PIL import Image, ImageDraw

R = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "src/main/resources/assets/silenciototal") + "/"
random.seed(7)

SKIN = (122, 126, 118)
SKIN_DARK = (92, 96, 90)
SKIN_LIGHT = (148, 151, 141)
FLESH = (150, 74, 78)
FLESH_DARK = (104, 44, 52)
MOUTH = (28, 14, 16)
TOOTH = (214, 206, 180)

img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
px = img.load()

def mottled(x0, y0, w, h, base=SKIN, spread=6):
    for x in range(x0, x0 + w):
        for y in range(y0, y0 + h):
            r = random.random()
            if r < 0.05:
                c = SKIN_DARK
            elif r < 0.08:
                c = SKIN_LIGHT
            else:
                j = random.randint(-spread, spread) // 2
                c = tuple(max(0, min(255, v + j)) for v in base)
            px[x, y] = c + (255,)

def box(u, v, w, h, d):
    """Pinta todas as faces de um cubo com pele manchada e devolve as regiões."""
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

# Cabeça 7x7x7 em (0,0): sem olhos, cicatrizes onde estariam, boca rasgada.
head = box(0, 0, 7, 7, 7)
fx, fy, fw, fh = head["front"]
for x in (fx + 1, fx + 2, fx + 4, fx + 5):
    px[x, fy + 2] = SKIN_DARK + (255,)          # pálpebras costuradas
px[fx + 1, fy + 3] = (80, 82, 77, 255)
px[fx + 5, fy + 3] = (80, 82, 77, 255)
for x in range(fx, fx + fw):
    px[x, fy + 5] = MOUTH + (255,)
    px[x, fy + 6] = MOUTH + (255,)
for x in range(fx, fx + fw, 2):
    px[x, fy + 5] = TOOTH + (255,)

# Orelha 1x9x6 em (28,0): lado interno de carne com veias, externo de pele.
ear = box(28, 0, 1, 9, 6)
for name in ("right", "left"):
    x0, y0, w, h = ear[name]
    inner = name == "right"
    for x in range(x0, x0 + w):
        for y in range(y0, y0 + h):
            edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
            if inner and not edge:
                px[x, y] = (FLESH if random.random() > 0.25 else FLESH_DARK) + (255,)
    if inner:
        for y in range(y0 + 1, y0 + h - 1):
            px[x0 + 2 + (y % 2), y] = FLESH_DARK + (255,)

# Tronco 8x12x5 em (0,14): costelas aparecendo.
body = box(0, 14, 8, 12, 5)
bx, by, bw, bh = body["front"]
for y in range(by + 2, by + 8, 2):
    for x in range(bx + 1, bx + bw - 1):
        if x != bx + bw // 2 and x != bx + bw // 2 - 1:
            px[x, y] = SKIN_DARK + (255,)
for y in range(by, by + bh):
    px[bx + bw // 2, y] = SKIN_DARK + (255,)

# Mandíbula 6x2x6 em (0,31): dentes na frente.
jaw = box(0, 31, 6, 2, 6)
jx, jy, jw, jh = jaw["front"]
for x in range(jx, jx + jw):
    px[x, jy] = (TOOTH if x % 2 == 0 else MOUTH) + (255,)
    px[x, jy + 1] = MOUTH + (255,)
jx, jy, jw, jh = jaw["top"]
for x in range(jx, jx + jw):
    for y in range(jy, jy + jh):
        px[x, y] = (90, 30, 36, 255)

# Braço 3x17x3 em (28,15): garras escuras na ponta.
arm = box(28, 15, 3, 17, 3)
for name in ("front", "left", "right", "back"):
    x0, y0, w, h = arm[name]
    for x in range(x0, x0 + w):
        for y in range(y0 + h - 3, y0 + h):
            px[x, y] = (40, 38, 36, 255) if y == y0 + h - 1 or x % 2 == 0 else SKIN_DARK + (255,)

# Perna 3x11x3 em (40,15).
box(40, 15, 3, 11, 3)

img.save(R + "textures/entity/listener/listener.png")

# Ovo de spawn 16x16.
egg = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
d = ImageDraw.Draw(egg)
d.ellipse((3, 1, 12, 15), fill=SKIN + (255,), outline=(52, 54, 50, 255))
for (x, y) in [(5, 5), (9, 4), (7, 9), (10, 11), (5, 12), (8, 6)]:
    egg.putpixel((x, y), FLESH + (255,))
egg.save(R + "textures/item/listener_spawn_egg.png")

# Ícone do mod 128x128: orelha em leque e ondas de som num fundo escuro.
icon = Image.new("RGBA", (128, 128), (14, 18, 26, 255))
d = ImageDraw.Draw(icon)
for i, r in enumerate((56, 44, 32)):
    d.arc((64 - r, 64 - r, 64 + r, 64 + r), 200, 340, fill=(60 + i * 30, 90 + i * 30, 120 + i * 30, 255), width=4)
d.polygon([(40, 112), (52, 40), (76, 40), (88, 112)], fill=SKIN + (255,))
d.polygon([(48, 104), (57, 50), (71, 50), (80, 104)], fill=FLESH + (255,))
for y in range(56, 100, 8):
    d.line((60, y, 68, y + 4), fill=FLESH_DARK + (255,), width=2)
icon.save(R + "icon.png")
print("ok")

# Orelha do Ouvinte 16x16: leque de pele com o interior de carne.
ear = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
d = ImageDraw.Draw(ear)
d.polygon([(3, 15), (5, 1), (11, 1), (13, 15)], fill=SKIN + (255,), outline=(60, 62, 58, 255))
d.polygon([(5, 13), (6, 3), (10, 3), (11, 13)], fill=FLESH + (255,))
for y in range(4, 13, 3):
    d.line((7, y, 9, y + 1), fill=FLESH_DARK + (255,))
d.line((4, 15, 12, 15), fill=(70, 30, 34, 255))
ear.save(R + "textures/item/listener_ear.png")
print("ear ok")

# Adaga Silenciosa 16x16: lâmina curta de ferro na diagonal, cabo enrolado em lã cinza.
dag = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = dag.load()
BLADE = (214, 218, 224, 255); BLADE_DARK = (150, 156, 166, 255); EDGE = (60, 62, 70, 255)
FELT_C = (120, 116, 110, 255); FELT_D = (86, 82, 78, 255); POMMEL = (70, 50, 36, 255)
for i in range(7):            # lâmina de (8,7) até (14,1)
    x, y = 8 + i, 7 - i
    px[x, y] = BLADE
    if y + 1 < 16: px[x, y + 1] = BLADE_DARK
    if x - 1 >= 0 and i > 0: px[x - 1, y] = EDGE if i == 6 else BLADE
px[15, 0] = EDGE; px[14, 0] = EDGE
for (x, y) in [(6, 7), (7, 8), (8, 9), (9, 8), (7, 6)]:   # guarda
    px[x, y] = EDGE
for i in range(5):            # cabo enrolado em lã
    x, y = 6 - i, 9 + i
    px[x, y] = FELT_C if i % 2 == 0 else FELT_D
    px[x + 1, y] = FELT_D if i % 2 == 0 else FELT_C
px[1, 14] = POMMEL; px[1, 15] = POMMEL; px[0, 15] = POMMEL
dag.save(R + "textures/item/silent_dagger.png")
print("dagger ok")

# Sino Ensurdecedor 16x16: sino dourado com a orelha (carne) no lugar do badalo.
bell = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
d = ImageDraw.Draw(bell)
GOLD = (233, 183, 54, 255); GOLD_D = (176, 120, 26, 255); GOLD_L = (255, 232, 130, 255)
d.rectangle((7, 0, 8, 2), fill=(110, 78, 48, 255))                 # alça (graveto)
d.polygon([(4, 3), (11, 3), (12, 10), (13, 12), (2, 12), (3, 10)], fill=GOLD, outline=GOLD_D)
d.line((5, 4, 4, 10), fill=GOLD_L)                                  # brilho
d.rectangle((2, 12, 13, 13), fill=GOLD_D)                           # boca do sino
d.ellipse((6, 13, 9, 15), fill=FLESH + (255,), outline=FLESH_DARK + (255,))  # badalo de orelha
bell.save(R + "textures/item/deafening_bell.png")
print("bell ok")
