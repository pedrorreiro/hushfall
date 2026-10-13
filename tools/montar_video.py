"""
Monta o vídeo de demonstração a partir da gravação do DemoReel.

    ./gradlew runClientGameTest -PdemoReel=/caminho/gravacao
    python3 tools/montar_video.py /caminho/gravacao branding/video/hushfall_demo.mp4

A gravação tem frames/NNNNN.png (60 fps: 3 quadros por tick, 6 nos trechos em câmera lenta) e
events.tsv (início de cada tomada e cada som tocado no jogo). O script corta as tomadas, aplica os
efeitos (zoom, tremor, flashes, aberração cromática, faixas de cinema animadas, dessaturação), põe as
legendas, a pausa no preto e a tela final, e mixa o áudio com os sons originais (as mesmas .ogg que o
jogo tocou, com volume pela distância até a câmera) mais os baques, o riser e o silêncio dramático.
"""
import json
import math
import os
import shutil
import subprocess
import sys
import tempfile
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from scipy.signal import lfilter

FPS = 60
W, H = 1920, 1080
SR = 48000
ROOT = Path(__file__).resolve().parent.parent
# Fonte do sistema (SF Pro, macOS), com o peso escolhido pelo nome.
FONT_FILE = os.environ.get("HUSHFALL_FONT", "/System/Library/Fonts/SFNS.ttf")
ASSETS = Path.home() / ".gradle/caches/fabric-loom/assets"
ACCENT = (232, 122, 58)
DANGER = (226, 52, 44)
TEXT = (244, 245, 248)
BG = (10, 11, 15)
BAR = 118  # faixas pretas das tomadas de cinema (fica ~2.35:1)

# Tomadas na ordem: (nome, de, até [s], cinema?). "black" e "outro" são telas geradas aqui.
SEGS = [
    ("A", 0.3, 4.8, True),
    ("B", 0.0, 4.7, True),
    ("C1", 0.3, 3.9, True),
    ("C2", 0.0, 3.0, False),
    ("D", 0.3, 6.7, False),
    ("E1", 0.0, 2.5, True),
    ("E2", 0.0, 2.5, False),
    ("F1", 0.0, 1.3, False),
    ("F2", 0.0, 7.2, True),
    ("black", 0.0, 2.6, False),
    ("outro", 0.0, 3.0, False),
]
# Onde a câmera lenta começa dentro da tomada (tempo de saída, 60 fps).
SLOW = {"E1": 1.1, "F2": 1.8}
# Trechos esticados na montagem para dar tempo de ler as legendas longas:
# (a partir de que segundo da gravação, velocidade). Quadros intermediários saem de mistura.
STRETCH = {"B": (3.0, 0.6), "D": (4.3, 0.65), "F2": (1.8, 0.5)}

# (estilo, texto, (tomada, entra), (tomada, sai)). "sub" vai embaixo, palavra por palavra.
CAPS = [
    ("sub", "Suas noites no Minecraft nunca mais serão as mesmas...", ("A", 0.6), ("A", 4.3)),
    ("kicker", "Conheça", ("B", 0.7), ("B", 6.4)),
    ("title", "O OUVINTE", ("B", 1.6), ("B", 6.4)),
    ("tag", "uma criatura cega que caça qualquer barulho.", ("B", 2.3), ("B", 6.4)),
    ("sub", "Ele não consegue te ver...", ("C1", 0.3), ("C1", 3.5)),
    ("sub", "...mas ouve cada passo que você dá.", ("C2", 0.15), ("C2", 2.9)),
    ("sub", "Fique de olho no medidor de ruído.\nCada movimento pode ser fatal.", ("D", 0.2), ("D", 5.0)),
    ("sub", "Mas, se você fizer barulho demais...", ("D", 5.2), ("D", 7.6)),
    ("impact", "ELE VAI SABER\nONDE VOCÊ ESTÁ!", ("E1", 0.03), ("E1", 2.5)),
    ("sub", "Use o Sino Ensurdecedor para atordoá-lo\ne ganhar alguns segundos.", ("F1", 0.3), ("F2", 5.2)),
    ("sub", "Porque, contra O Ouvinte, um segundo pode ser\na diferença entre escapar...", ("F2", 5.4), ("F2", 12.5)),
    ("end", "...e nunca mais voltar.", ("black", 0.7), ("black", 2.5)),
]

_fonts = {}


def font(weight, size):
    if (weight, size) not in _fonts:
        f = ImageFont.truetype(FONT_FILE, size)
        f.set_variation_by_name(weight)
        _fonts[weight, size] = f
    return _fonts[weight, size]


def ease(t):
    t = min(max(t, 0.0), 1.0)
    return 1 - (1 - t) ** 3


def smooth(t):
    t = min(max(t, 0.0), 1.0)
    return t * t * (3 - 2 * t)


# ---------------------------------------------------------------- legendas

CURSOR = "\x01"  # marca no fim do texto: desenhar o cursor de digitação


def typing_times(text, seed):
    """Quando cada letra aparece (s, a partir da entrada da legenda): ritmo de alguém digitando,
    irregular, com respiros na pontuação e nas reticências."""
    rng = np.random.default_rng(seed)
    times, t = [], 0.15
    for i, c in enumerate(text):
        times.append(t)
        nxt = text[i + 1] if i + 1 < len(text) else ""
        t += 0.038 * rng.uniform(0.6, 1.5)
        if c == "\n":
            t += 0.22
        elif c in ",;":
            t += 0.16
        elif c in ".!?" and nxt != ".":
            t += 0.28
        elif c == " " and rng.random() < 0.15:
            t += 0.08
    return times


def shadowed(size, draw_fn, blur=12, strength=210, glow=None):
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    mask = Image.new("L", size, 0)
    draw_fn(ImageDraw.Draw(mask), strength)
    soft = mask.filter(ImageFilter.GaussianBlur(blur))
    layer.paste(Image.new("RGBA", size, (glow or (0, 0, 0)) + (255,)), (0, 0), soft)
    return layer


def render_sub(text):
    fnt = font("Bold", 58)
    full = CAPTION_FULL.get(text, text)
    cursor = text.endswith(CURSOR)
    text = text.rstrip(CURSOR)
    lines = text.split("\n")
    line_h = 74
    width = int(max(fnt.getlength(line) for line in full.split("\n"))) + 120
    height = line_h * len(full.split("\n")) + 60

    def draw(d, fill):
        for i, line in enumerate(lines):
            d.text((32, 22 + i * line_h), line, font=fnt, fill=fill)

    layer = shadowed((width, height), draw)
    d = ImageDraw.Draw(layer)
    d.rectangle((6, 34, 11, 22 + line_h * len(full.split("\n")) - 4), fill=ACCENT + (255,))
    for i, line in enumerate(lines):
        d.text((28, 18 + i * line_h), line, font=fnt, fill=TEXT + (255,))
    if cursor:
        x = 28 + fnt.getlength(lines[-1]) + 6
        y = 18 + (len(lines) - 1) * line_h
        d.rectangle((x, y + 14, x + 26, y + 70), fill=ACCENT + (255,))
    return layer


CAPTION_FULL = {}


def render_centered(text, fnt, color, tracking=0, glow=None, blur=14, strength=230):
    lines = text.split("\n")
    asc, desc = fnt.getmetrics()
    line_h = int((asc + desc) * 1.05)

    def line_w(line):
        return sum(fnt.getlength(c) for c in line) + tracking * max(0, len(line) - 1)

    width = int(max(line_w(line) for line in lines)) + 160
    height = line_h * len(lines) + 120

    def draw_all(d, fill):
        for i, line in enumerate(lines):
            x = (width - line_w(line)) / 2
            for c in line:
                d.text((x, 60 + i * line_h), c, font=fnt, fill=fill)
                x += fnt.getlength(c) + tracking

    layer = shadowed((width, height), draw_all, blur=blur, strength=strength, glow=glow)
    draw_all(ImageDraw.Draw(layer), color + (255,))
    return layer


def render_caption(style, text):
    if style == "sub":
        return render_sub(text)
    if style == "kicker":
        return render_centered(text.upper(), font("Semibold", 40), (200, 205, 215), tracking=14)
    if style == "title":
        return render_centered(text, font("Black", 150), TEXT, tracking=18, glow=(120, 50, 20), blur=26, strength=255)
    if style == "tag":
        return render_centered(text, font("Semibold", 46), ACCENT)
    if style == "impact":
        return render_centered(text, font("Black", 112), TEXT, tracking=4, glow=(150, 10, 10), blur=30, strength=255)
    if style == "end":
        return render_centered(text, font("Bold", 76), TEXT, blur=20)
    raise ValueError(style)


def with_alpha(img, a):
    if a >= 0.999:
        return img
    out = img.copy()
    out.putalpha(img.getchannel("A").point(lambda v: int(v * a)))
    return out


def scaled(img, s):
    if abs(s - 1) < 0.003:
        return img
    return img.resize((max(1, int(img.width * s)), max(1, int(img.height * s))), Image.BICUBIC)


# ---------------------------------------------------------------- efeitos

def vignette(strength=120, color=(0, 0, 0), start=0.75):
    y, x = np.mgrid[0:H, 0:W]
    d = np.sqrt(((x - W / 2) / (W / 2)) ** 2 + ((y - H / 2) / (H / 2)) ** 2)
    alpha = np.clip((d - start) / 0.7, 0, 1) ** 1.5 * strength
    img = np.zeros((H, W, 4), np.uint8)
    img[..., :3] = color
    img[..., 3] = alpha.astype(np.uint8)
    return Image.fromarray(img)


def outro(t, logo):
    img = Image.new("RGBA", (W, H), BG + (255,))
    draw = ImageDraw.Draw(img)
    cx, cy = W // 2, 420
    # Anéis de som saindo do logo, como na capa.
    for r0 in range(3):
        r = ((t * 260 + r0 * 170) % 510) + 70
        fade = max(0.0, 1 - r / 580) * ease(t / 0.3)
        col = tuple(int(BG[i] + (ACCENT[i] - BG[i]) * fade * 0.85) for i in range(3))
        draw.ellipse((cx - r, cy - r, cx + r, cy + r), outline=col, width=4)
    img = img.filter(ImageFilter.GaussianBlur(1.2))
    a = ease(t / 0.4)
    scale = 1.25 + 0.12 * (1 - ease(t / 0.9))
    lg = logo.resize((int(logo.width * scale), int(logo.height * scale)), Image.LANCZOS)
    img.alpha_composite(with_alpha(lg, a), (cx - lg.width // 2, cy - lg.height // 2))
    d = ImageDraw.Draw(img)
    for text, fnt, color, y, start in [
        ("Baixe grátis no CurseForge e no Modrinth", font("Bold", 54), ACCENT, 610, 0.45),
    ]:
        b = ease((t - start) / 0.35)
        w = d.textlength(text, font=fnt)
        d.text((cx - w / 2, y + (1 - b) * 18), text, font=fnt, fill=color + (int(255 * b),))
    # Entra saindo do preto com um flash curto.
    if t < 0.25:
        img = Image.blend(Image.new("RGBA", img.size, (0, 0, 0, 255)), img, ease(t / 0.25))
    return img


# ---------------------------------------------------------------- quadros

_ctx = {}


def init_worker(rec, logo_path, full):
    _ctx["rec"] = Path(rec)
    _ctx["logo"] = Image.open(logo_path).convert("RGBA")
    small = _ctx["logo"].resize((int(_ctx["logo"].width * 0.32), int(_ctx["logo"].height * 0.32)), Image.LANCZOS)
    _ctx["mark"] = with_alpha(small, 0.6)
    _ctx["vignette"] = vignette()
    _ctx["red"] = vignette(235, DANGER, 0.45)
    _ctx["captions"] = {}
    CAPTION_FULL.update(full)
    rng = np.random.default_rng(7)
    _ctx["grain"] = [rng.normal(0, 1, (H // 2, W // 2)).astype(np.float32) for _ in range(6)]


def caption(style, text):
    key = (style, text)
    if key not in _ctx["captions"]:
        _ctx["captions"][key] = render_caption(style, text)
    return _ctx["captions"][key]


def apply_camera(img, fx):
    """Zoom, tremor e aberração cromática sobre o quadro do jogo."""
    z = fx.get("zoom", 1.0)
    dx, dy = fx.get("shake", (0.0, 0.0))
    if z > 1.0005 or dx or dy:
        z = max(z, 1.0 + (abs(dx) + abs(dy)) * 2.4 / W)
        cw, ch = W / z, H / z
        cx, cy = fx.get("focus", (0.5, 0.5))
        x0 = min(max(cx * W - cw / 2 + dx, 0), W - cw)
        y0 = min(max(cy * H - ch / 2 + dy, 0), H - ch)
        img = img.resize((W, H), Image.BICUBIC, box=(x0, y0, x0 + cw, y0 + ch))
    arr = None
    ab = fx.get("aberr", 0)
    desat = fx.get("desat", 0)
    if ab >= 1 or desat > 0.01:
        arr = np.asarray(img.convert("RGB")).astype(np.float32)
        if desat > 0.01:
            luma = arr[..., 0] * 0.299 + arr[..., 1] * 0.587 + arr[..., 2] * 0.114
            cool = np.stack([luma * 0.92, luma * 0.97, luma * 1.06], -1)
            arr = arr * (1 - desat) + cool * desat
        if ab >= 1:
            s = int(ab)
            arr[:, s:, 0] = arr[:, :-s, 0].copy()
            arr[:, :-s, 2] = arr[:, s:, 2].copy()
        img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)).convert("RGBA")
    return img


def grain(img, amount, idx):
    if amount <= 0:
        return img
    arr = np.asarray(img.convert("RGB")).astype(np.float32)
    g = _ctx["grain"][idx % len(_ctx["grain"])]
    g = np.repeat(np.repeat(g, 2, 0), 2, 1)[..., None]
    arr += g * amount
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)).convert("RGBA")


def draw_caps(img, caps, bar):
    for style, text, a, k, t in caps:
        if style == "sub":
            cap = caption(style, k)
            rise = (1 - ease(t / 0.35)) * 22
            n = text.count("\n") + 1
            y = H - max(bar, 0) - (74 * n + 60) - 44 + int(rise)
            img.alpha_composite(with_alpha(cap, a), (84, y))
            continue
        cap = caption(style, text)
        if style == "kicker":
            s, y = 1.0, 560
        elif style == "title":
            s = 1 + 0.45 * (1 - ease(t / 0.28))
            y = 610
        elif style == "tag":
            s, y = 1.0, 790
        elif style == "impact":
            s = 1 + 0.6 * (1 - ease(t / 0.22))
            y = H // 2 - 170
        else:  # end
            s = 1.06 - 0.06 * ease(t / 1.6)
            y = H // 2 - 110
        c = scaled(cap, s)
        jx = jy = 0
        if style == "impact":
            amp = 7 * (1 - ease(t / 1.4)) + 1.5
            jx, jy = amp * math.sin(t * 91.0), amp * math.cos(t * 77.0)
        img.alpha_composite(with_alpha(c, a), (int(W / 2 - c.width / 2 + jx), int(y + cap.height / 2 - c.height / 2 + jy)))
    return img


def build_frame(job):
    kind, data = job
    if kind == "outro":
        img = outro(data["t"], _ctx["logo"])
    elif kind == "black":
        img = Image.new("RGBA", (W, H), (0, 0, 0, 255))
        img = draw_caps(img, data["caps"], 0)
        img = grain(img, 5, data["i"])
    else:
        fx = data
        a = int(fx["src"])
        img = Image.open(_ctx["rec"] / "frames" / f"{a:05d}.png").convert("RGBA")
        mix = fx["src"] - a
        if mix > 0.02 and (_ctx["rec"] / "frames" / f"{a + 1:05d}.png").exists():
            nxt = Image.open(_ctx["rec"] / "frames" / f"{a + 1:05d}.png").convert("RGBA")
            img = Image.blend(img, nxt, mix)
        img = apply_camera(img, fx)
        img.alpha_composite(_ctx["vignette"])
        red = fx.get("red", 0)
        if red > 0.01:
            img.alpha_composite(with_alpha(_ctx["red"], min(red, 1.0)))
        img = grain(img, 6, fx["i"])
        flash = fx.get("flash")
        if flash and flash[1] > 0.01:
            img = Image.blend(img, Image.new("RGBA", img.size, flash[0] + (255,)), min(flash[1], 1.0))
        ring = fx.get("ring")
        if ring:
            r, a = ring
            ov = Image.new("RGBA", (W, H), (0, 0, 0, 0))
            d = ImageDraw.Draw(ov)
            for k, wdt in ((0, 10), (0.6, 5)):
                rr = r * (1 - 0.25 * k)
                d.ellipse((W / 2 - rr, H / 2 - rr, W / 2 + rr, H / 2 + rr), outline=(255, 244, 214, int(255 * a * (1 - k * 0.5))), width=wdt)
            img.alpha_composite(ov.filter(ImageFilter.GaussianBlur(3)))
        bar = fx["bar"]
        if bar > 0:
            d = ImageDraw.Draw(img)
            d.rectangle((0, 0, W, bar), fill=(0, 0, 0, 255))
            d.rectangle((0, H - bar, W, H), fill=(0, 0, 0, 255))
        mark = _ctx["mark"]
        img.alpha_composite(mark, (W - mark.width - 44, max(34, (bar - mark.height) // 2)))
        img = draw_caps(img, fx["caps"], bar)
        if fx.get("fade", 1) < 1:
            img = Image.blend(Image.new("RGBA", img.size, (0, 0, 0, 255)), img, max(fx["fade"], 0))
    return img.convert("RGB").tobytes()


# ---------------------------------------------------------------- áudio

def load_index():
    index = sorted((ASSETS / "indexes").glob("*.json"))[-1]
    return json.loads(index.read_text())["objects"]


# Só os sons da cena; fica de fora o que tocou durante a preparação do mundo (lava, música...).
KEEP = ("silenciototal:", "minecraft:block.grass.step", "minecraft:block.campfire", "minecraft:entity.player.hurt",
        "minecraft:block.bell", "minecraft:entity.player.attack")

_cache = {}


def decode(path):
    if path not in _cache:
        raw = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", str(path), "-f", "f32le", "-ac", "1", "-ar", str(SR), "-"],
                             capture_output=True, check=True).stdout
        _cache[path] = np.frombuffer(raw, np.float32)
    return _cache[path]


def sound_file(objects, location):
    ns, path = location.split(":", 1)
    entry = objects.get(f"{ns}/sounds/{path}.ogg")
    if not entry:
        return None
    h = entry["hash"]
    return ASSETS / "objects" / h[:2] / h


def place(out, data, at_seconds, gain):
    start = int(at_seconds * SR)
    if start >= len(out) or start < 0:
        return
    end = min(len(out), start + len(data))
    out[start:end] += data[:end - start] * gain


def mix_game(events, frame_map, seconds, objects):
    out = np.zeros(int(seconds * SR) + SR, np.float32)
    for src_frame, ident, location, vol, pitch, x, y, z, cx, cy, cz, relative in events:
        if src_frame not in frame_map or not ident.startswith(KEEP):
            continue
        path = sound_file(objects, location)
        if path is None:
            continue
        reach = (64 if "nearby_close" in location else 16) * max(1.0, vol)
        dist = 0 if relative else math.dist((x, y, z), (cx, cy, cz))
        gain = max(0.0, 1 - dist / reach) * min(vol, 1.0)
        if gain < 0.02:
            continue
        data = decode(path)
        if abs(pitch - 1) > 0.01:
            n = int(len(data) / pitch)
            data = np.interp(np.linspace(0, len(data) - 1, n), np.arange(len(data)), data).astype(np.float32)
        place(out, data, frame_map[src_frame] / FPS, gain * 0.8)
    return out


def drone(seconds):
    """Fundo grave e baixo, só para a noite não ficar muda."""
    n = int(seconds * SR)
    rng = np.random.default_rng(3)
    k = 2400
    low = np.convolve(rng.standard_normal(n).astype(np.float32), np.ones(k) / k, mode="same")
    low /= np.abs(low).max() + 1e-9
    t = np.arange(n) / SR
    hum = 0.5 * np.sin(2 * np.pi * 55 * t) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.1 * t))
    return (low * 0.07 + hum * 0.03).astype(np.float32)


def riser(seconds):
    """Ruído filtrado que sobe de volume e de brilho: a tensão antes do corte para o silêncio."""
    n = int(seconds * SR)
    rng = np.random.default_rng(11)
    noise = rng.standard_normal(n).astype(np.float32)
    # O filtro vai abrindo aos poucos: aplica em pedaços com o corte subindo.
    out = np.concatenate([lowpass(chunk, 0.02 + 0.5 * (i / 32) ** 2) for i, chunk in enumerate(np.array_split(noise, 32))])
    t = np.arange(n) / SR
    tone = np.sin(2 * np.pi * (70 + 160 * (t / seconds) ** 2) * t)
    env = (t / seconds) ** 2.2
    return ((out * 0.9 + tone * 0.25) * env).astype(np.float32)


def lowpass(x, alpha):
    """Filtro de um polo: abafa o som (y += alpha * (x - y))."""
    return lfilter([alpha], [1, alpha - 1], x).astype(np.float32)


def envelope(points, n):
    """Ganho por amostra a partir de pares (segundos, ganho), interpolando em linha reta."""
    ts = np.arange(n) / SR
    xs, ys = zip(*points)
    return np.interp(ts, xs, ys).astype(np.float32)


# ---------------------------------------------------------------- montagem

def main():
    rec = Path(sys.argv[1])
    dest = Path(sys.argv[2])
    shots = {}
    sounds = []
    for line in (rec / "events.tsv").read_text().splitlines():
        p = line.split("\t")
        if p[0] == "shot":
            shots[p[1]] = int(p[2])
        elif p[0] == "sound":
            sounds.append((int(p[1]), p[2], p[3], float(p[4]), float(p[5]), float(p[6]), float(p[7]), float(p[8]),
                           float(p[9]), float(p[10]), float(p[11]), p[12] == "true"))

    # Linha do tempo: onde cada tomada começa na saída.
    starts = {}
    frames = []  # (tomada, t local, quadro de origem ou None)
    for name, t0, t1, cinema in SEGS:
        starts[name] = len(frames) / FPS
        s0, speed = STRETCH.get(name, (t1, 1.0))
        k = 0
        while True:
            t = k / FPS
            rt = t0 + t if t0 + t < s0 else s0 + (t0 + t - s0) * speed
            if rt >= t1 - 1e-9:
                break
            src = shots[name] + rt * FPS if name in shots else None
            frames.append((name, t, src))
            k += 1
    seconds = len(frames) / FPS
    at = {name: starts[name] for name in starts}
    frame_map = {}
    for i, (_, _, src) in enumerate(frames):
        if src is not None:
            frame_map.setdefault(round(src), i)

    def when(anchor):
        return at[anchor[0]] + anchor[1]

    # Momentos que vêm do próprio jogo: pancadas e o sino.
    hits, bell = [], None
    for src_frame, ident, *_ in sounds:
        if src_frame in frame_map:
            g = frame_map[src_frame] / FPS
            if ident.startswith("minecraft:entity.player.hurt") and at["E1"] <= g < at["F1"]:
                hits.append(g)
            elif ident.startswith("minecraft:block.bell") and bell is None:
                bell = g
    hits = sorted(set(round(h, 3) for h in hits))
    if bell is None:
        bell = at["F1"] + 0.3
    title_at = when(("B", 1.6))

    caps = [(style, text, when(a), when(b)) for style, text, a, b in CAPS]
    full = {}
    typed = {}
    for j, (style, text, *_) in enumerate(caps):
        if style == "sub":
            typed[text] = typing_times(text, j)
            for k in range(len(text) + 1):
                full[text[:k]] = full[text[:k] + CURSOR] = text

    def captions_at(g):
        out = []
        for style, text, g_in, g_out in caps:
            if not (g_in <= g < g_out):
                continue
            t = g - g_in
            fade_out = 1 - ease((g - (g_out - 0.25)) / 0.25)
            if style == "sub":
                times = typed[text]
                k = sum(1 for x in times if x <= t)
                # Cursor fixo enquanto digita; depois pisca.
                done = t - times[-1] if k == len(text) else -1
                blink = done < 0.25 or int((done - 0.25) / 0.45) % 2 == 1
                shown = text[:k] + (CURSOR if blink else "")
                out.append((style, text, ease(t / 0.2) * fade_out, shown, t))
            elif style in ("title", "impact"):
                out.append((style, text, min(1, t / 0.08) * fade_out, 0, t))
            else:
                out.append((style, text, ease(t / 0.35) * fade_out, 0, t))
        return out

    seg_info = {name: (cinema, i) for i, (name, _, _, cinema) in enumerate(SEGS)}
    seg_len = {name: sum(1 for f in frames if f[0] == name) / FPS for name, *_ in SEGS}
    rng = np.random.default_rng(5)

    def shake(amount, g):
        return (amount * math.sin(g * 83.1 + rng.random() * 0.6), amount * math.cos(g * 71.7 + rng.random() * 0.6))

    work = Path(tempfile.mkdtemp(prefix="hushfall_video_"))
    jobs = []
    for i, (name, t, src) in enumerate(frames):
        g = i / FPS
        if name == "outro":
            jobs.append(("outro", {"t": t}))
            continue
        if name == "black":
            jobs.append(("black", {"caps": captions_at(g), "i": i}))
            continue
        cinema, si = seg_info[name]
        prev_cinema = seg_info[SEGS[si - 1][0]][0] if si > 0 else cinema
        target = BAR if cinema else 0
        bar = target
        if prev_cinema != cinema:
            b = ease(t / (0.18 if name == "E1" else 0.4))
            bar = (BAR if prev_cinema else 0) + (target - (BAR if prev_cinema else 0)) * b
        fx = {"src": src, "i": i, "bar": int(bar), "caps": captions_at(g)}
        dur = seg_len[name]
        sx = sy = 0.0
        flash_a, flash_c = 0.0, (255, 255, 255)
        aberr = 0.0

        if name == "A":
            fx["zoom"] = 1.0 + 0.05 * smooth(t / dur)
            fx["fade"] = ease(t / 1.0) * (1 - smooth((t - (dur - 0.22)) / 0.22))
        elif name == "B":
            fx["fade"] = smooth(t / 0.18)
            fx["zoom"] = 1.05 - 0.05 * smooth(t / dur)
            if 0.3 <= t <= 2.5:
                amp = 9 * math.sin(math.pi * (t - 0.3) / 2.2) ** 0.6
                sx, sy = shake(amp, g)
        elif name == "C1":
            fx["zoom"] = 1.0 + 0.07 * smooth(t / dur)
            fx["focus"] = (0.55, 0.45)
        elif name == "C2":
            fx["zoom"] = 1.0 + 0.05 * smooth(t / dur)
        elif name == "D":
            # Medidor em destaque e, no fim, a câmera fecha e o mundo perde a cor antes do corte.
            tension = smooth((t - 5.0) / 2.6)
            fx["zoom"] = 1.0 + 0.14 * tension
            fx["desat"] = 0.55 * tension
            fx["red"] = 0.35 * tension * (0.6 + 0.4 * math.sin(t * 9))
            fx["fade"] = 1 - smooth((t - (dur - 0.12)) / 0.12)
        elif name == "E1":
            aberr = 14 * (1 - ease(t / 0.5))
            flash_c, flash_a = DANGER, 0.75 * (1 - ease(t / 0.35))
            slow = t >= SLOW["E1"]
            amp = 10 * (1 - ease(t / 1.2)) + (3 if slow else 5)
            sx, sy = shake(amp, g)
            fx["zoom"] = 1.03 + (0.06 * smooth((t - SLOW["E1"]) / 1.2) if slow else 0)
            fx["desat"] = 0.35 * smooth((t - SLOW["E1"]) / 0.6)
        elif name == "E2":
            fx["red"] = 0.25
            fx["desat"] = 0.2
        elif name == "F2":
            slow = smooth((t - SLOW["F2"]) / 0.8)
            fx["desat"] = 0.65 * slow
            fx["zoom"] = 1.0 + 0.08 * smooth((t - SLOW["F2"]) / (dur - SLOW["F2"]))

        # Cada pancada: tremor, aberração e o vermelho pulsando nas bordas.
        for h in hits:
            dt = g - h
            if 0 <= dt < 0.6:
                k = 1 - ease(dt / 0.6)
                ax, ay = shake(22 * k, g)
                sx, sy = sx + ax, sy + ay
                aberr = max(aberr, 12 * k)
                fx["red"] = max(fx.get("red", 0), 0.9 * k + 0.25)
                flash_c, flash_a = DANGER, max(flash_a, 0.35 * (1 - ease(dt / 0.15)))
        # O sino: flash branco, onda de choque e o mundo tremendo.
        dt = g - bell
        if 0 <= dt < 0.9:
            flash_c, flash_a = (255, 250, 235), max(flash_a, 0.85 * (1 - ease(dt / 0.4)))
            fx["ring"] = (60 + 1500 * ease(dt / 0.9), 1 - ease(dt / 0.9))
            ax, ay = shake(14 * (1 - ease(dt / 0.6)), g)
            sx, sy = sx + ax, sy + ay
            aberr = max(aberr, 10 * (1 - ease(dt / 0.5)))
        # O título entrando: um baque visual.
        dt = g - title_at
        if 0 <= dt < 0.4:
            flash_a = max(flash_a, 0.35 * (1 - ease(dt / 0.25)))
            aberr = max(aberr, 9 * (1 - ease(dt / 0.4)))

        fx["shake"] = (sx, sy)
        fx["aberr"] = aberr
        fx["flash"] = (flash_c, flash_a)
        jobs.append(("shot", fx))

    # ------------------------------------------------------------ áudio
    objects = load_index()
    n = int(seconds * SR)
    game = mix_game(sounds, frame_map, seconds, objects)[:n]
    # Câmera lenta: o som do jogo fica abafado e distante.
    slow_e1, slow_f2 = at["E1"] + SLOW["E1"], at["F2"] + SLOW["F2"]
    muffled = lowpass(game, 0.06)
    wet = envelope([(0, 0), (slow_e1, 0), (slow_e1 + 0.2, 0.7), (at["E2"], 0.7), (at["E2"] + 0.05, 0),
                    (slow_f2, 0), (slow_f2 + 0.6, 0.9), (seconds, 0.9)], n)
    game = game * (1 - wet) + muffled * wet
    # O silêncio: some tudo no fim do medidor, e no preto antes da frase final.
    cut = at["E1"]
    silence = envelope([(0, 1), (cut - 0.9, 1), (cut - 0.45, 0), (cut, 0), (cut + 0.01, 1),
                        (at["black"] - 0.05, 1), (at["black"], 0), (seconds, 0)], n)
    audio = game * silence
    bed = drone(seconds)[:n] * envelope([(0, 1), (cut - 0.9, 1), (cut - 0.45, 0), (cut, 0), (cut + 0.01, 1),
                                          (at["black"], 1), (at["black"] + 0.02, 0), (at["black"] + 0.7, 0),
                                          (at["black"] + 1.4, 0.8), (seconds, 0.8)], n)
    audio += bed
    rise = riser(1.6)
    place(audio, rise, cut - 0.45 - 1.6, 0.35)

    thump_path = sound_file(objects, "minecraft:mob/warden/heartbeat_1")
    if thump_path:
        thump = decode(thump_path)
        place(audio, thump, 0.1, 0.9)
        place(audio, thump, title_at, 1.2)
        place(audio, thump, at["E1"], 1.3)
        # Coração batendo na câmera lenta do final.
        t = slow_f2 + 0.2
        while t < at["black"] - 0.3:
            place(audio, thump, t, 0.9)
            t += 0.82
        place(audio, thump, at["black"] + 0.7, 1.1)
    # Uma tecla por letra das legendas de narração.
    # Teclas gravadas de um teclado de verdade (tools/teclado), sorteadas sem repetir a anterior.
    keys = [decode(f) for f in sorted((ROOT / "tools" / "teclado").glob("*.wav"))]
    keys = [k / np.abs(k).max() for k in keys]
    krng = np.random.default_rng(11)
    last = -1
    for style, text, g_in, g_out in caps:
        if style != "sub":
            continue
        for c, t in zip(text, typed[text]):
            if c == "\n" or g_in + t >= g_out:
                continue
            j = int(krng.integers(len(keys) - 1))
            j = j + 1 if j >= last else j
            last = j
            place(audio, keys[j], g_in + t, krng.uniform(0.32, 0.42) * (1.15 if c == " " else 1.0))
    growl = sound_file(objects, "minecraft:mob/warden/nearby_close_1")
    if growl:
        place(audio, decode(growl), at["outro"] + 0.05, 0.7)
    fade = int(0.6 * SR)
    audio[-fade:] *= np.linspace(1, 0, fade)
    wav = work / "audio.f32"
    audio.astype(np.float32).tofile(wav)

    # Os quadros vão direto para o ffmpeg, sem passar pelo disco (são ~6 MB cada).
    dest.parent.mkdir(parents=True, exist_ok=True)
    ffmpeg = subprocess.Popen([
        "ffmpeg", "-loglevel", "error", "-y",
        "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-framerate", str(FPS), "-i", "-",
        "-f", "f32le", "-ar", str(SR), "-ac", "1", "-i", str(wav),
        "-vf", "format=yuv420p",
        "-c:v", "libx264", "-preset", "slow", "-crf", "18", "-profile:v", "high", "-r", str(FPS),
        "-af", "loudnorm=I=-16:TP=-1.5:LRA=11",
        "-c:a", "aac", "-b:a", "192k", "-ac", "2",
        "-movflags", "+faststart", "-shortest", str(dest),
    ], stdin=subprocess.PIPE)
    with ProcessPoolExecutor(initializer=init_worker,
                             initargs=(str(rec), str(ROOT / "tools" / "video_logo.png"), full)) as pool:
        # Em lotes, para não acumular quadros prontos na memória enquanto o ffmpeg codifica.
        for k in range(0, len(jobs), 96):
            for frame in pool.map(build_frame, jobs[k:k + 96], chunksize=4):
                ffmpeg.stdin.write(frame)
    ffmpeg.stdin.close()
    if ffmpeg.wait() != 0:
        raise SystemExit("ffmpeg falhou")
    shutil.rmtree(work)
    print(f"{dest} ({seconds:.1f} s, {FPS} fps)")


if __name__ == "__main__":
    main()
