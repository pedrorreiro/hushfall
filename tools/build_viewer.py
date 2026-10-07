"""Gera o site (site/index.html e tools/visualizador.html): o Ouvinte em 3D com os sons, os itens com receitas e o guia.

Rode de novo depois de mudar texturas ou sons:  python3 tools/build_viewer.py
A geometria do Ouvinte espelha ListenerModel.java (mesmas caixas, pivôs e animações) e os sons
saem de assets/silenciototal/sounds.json (mesmos arquivos e pitch do jogo).
"""
import base64, glob, io, json, os, zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MOD_ASSETS = os.path.join(ROOT, "src/main/resources/assets/silenciototal")
LOOM = os.path.expanduser("~/.gradle/caches/fabric-loom")
MAX_VARIANTS = 1


def data_url(raw, mime):
    return f"data:{mime};base64," + base64.b64encode(raw).decode()


def read(path):
    with open(path, "rb") as f:
        return f.read()


def vanilla_sounds():
    """Lê os sons do índice de assets que o Loom já baixou (os arquivos ficam com nome de hash)."""
    index = sorted(glob.glob(os.path.join(LOOM, "assets/indexes/*.json")))[-1]
    objects = json.load(open(index))["objects"]
    events = json.load(open(os.path.join(MOD_ASSETS, "sounds.json")))
    out = {}
    for event, spec in events.items():
        variants = []
        for sound in spec["sounds"][:MAX_VARIANTS]:
            name = sound["name"].split(":", 1)[1]
            entry = objects.get(f"minecraft/sounds/{name}.ogg")
            if entry is None:
                continue
            h = entry["hash"]
            path = os.path.join(LOOM, "assets/objects", h[:2], h)
            if os.path.exists(path):
                variants.append({"src": data_url(read(path), "audio/ogg"), "pitch": sound.get("pitch", 1.0)})
        out[event.removeprefix("entity.listener.")] = variants
    return out


def vanilla_raw(name):
    jar = glob.glob(os.path.join(LOOM, "*/minecraft-client.jar"))[0]
    with zipfile.ZipFile(jar) as z:
        return z.read("assets/minecraft/textures/" + name)


def png_url(image):
    buf = io.BytesIO()
    image.save(buf, "PNG")
    return data_url(buf.getvalue(), "image/png")


def iso_block(face_png, size=64):
    """Ícone de bloco isométrico (como no inventário) a partir da textura de uma face."""
    tex = Image.open(io.BytesIO(face_png)).convert("RGBA")
    tw, th = tex.size
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    s = size / 32
    faces = [  # origem, eixo u, eixo v, brilho
        ((16, 1), (15, 7.5), (-15, 7.5), 1.0),
        ((1, 8.5), (15, 7.5), (0, 15), 0.8),
        ((16, 16), (15, -7.5), (0, 15), 0.62),
    ]
    for py in range(size):
        for px in range(size):
            x, y = (px + 0.5) / s, (py + 0.5) / s
            for (ox, oy), (ux, uy), (vx, vy), light in faces:
                det = ux * vy - uy * vx
                dx, dy = x - ox, y - oy
                u = (dx * vy - dy * vx) / det
                v = (ux * dy - uy * dx) / det
                if 0 <= u < 1 and 0 <= v < 1:
                    r, g, b, a = tex.getpixel((min(tw - 1, int(u * tw)), min(th - 1, int(v * th))))
                    out.putpixel((px, py), (int(r * light), int(g * light), int(b * light), a))
                    break
    return png_url(out)


def tinted(base_png, overlay_png, rgb):
    base = Image.open(io.BytesIO(base_png)).convert("RGBA")
    px = base.load()
    for yy in range(base.height):
        for xx in range(base.width):
            r, g, b, a = px[xx, yy]
            px[xx, yy] = (r * rgb[0] // 255, g * rgb[1] // 255, b * rgb[2] // 255, a)
    base.alpha_composite(Image.open(io.BytesIO(overlay_png)).convert("RGBA"))
    return png_url(base)


WOOLS = ["white", "light_gray", "gray", "black", "brown", "red", "lime", "light_blue"]
ITEMS = {
    "wool": {"name": "Qualquer lã", "icons": [iso_block(vanilla_raw(f"block/{c}_wool.png")) for c in WOOLS]},
    "leather": {"name": "Couro", "icons": [data_url(vanilla_raw("item/leather.png"), "image/png")]},
    "iron": {"name": "Barra de Ferro", "icons": [data_url(vanilla_raw("item/iron_ingot.png"), "image/png")]},
    "stick": {"name": "Graveto", "icons": [data_url(vanilla_raw("item/stick.png"), "image/png")]},
    "felt_boots": {"name": "Botas de Feltro", "icons": [tinted(vanilla_raw("item/leather_boots.png"),
                   vanilla_raw("item/leather_boots_overlay.png"), (0x6E, 0x6B, 0x66))]},
    "silent_dagger": {"name": "Adaga Silenciosa", "icons": [data_url(read(os.path.join(MOD_ASSETS, "textures/item/silent_dagger.png")), "image/png")]},
    "listener_ear": {"name": "Orelha do Ouvinte", "icons": [data_url(read(os.path.join(MOD_ASSETS, "textures/item/listener_ear.png")), "image/png")]},
    "gold": {"name": "Barra de Ouro", "icons": [data_url(vanilla_raw("item/gold_ingot.png"), "image/png")]},
    "deafening_bell": {"name": "Sino Ensurdecedor", "icons": [data_url(read(os.path.join(MOD_ASSETS, "textures/item/deafening_bell.png")), "image/png")]},
    "listener_spawn_egg": {"name": "Ovo Gerador de Ouvinte", "icons": [data_url(read(os.path.join(MOD_ASSETS, "textures/item/listener_spawn_egg.png")), "image/png")]},
}

def item(name):
    return data_url(vanilla_raw(f"item/{name}.png"), "image/png")


def mod_item(name):
    return data_url(read(os.path.join(MOD_ASSETS, f"textures/item/{name}.png")), "image/png")


# Ícones soltos usados nas seções do site (data-icon="...").
ICONS = {
    "moon": item("clock_32"),
    "ear": mod_item("listener_ear"),
    "heart": data_url(vanilla_raw("gui/sprites/hud/heart/full.png"), "image/png"),
    "iron_door": item("iron_door"),
    "oak_door": item("oak_door"),
    "bed": data_url(vanilla_raw("block/red_bed_head_up.png"), "image/png"),
    "bell": item("bell"),
    "note_block": iso_block(vanilla_raw("block/note_block.png")),
    "rabbit_foot": item("rabbit_foot"),
    "pickaxe": item("iron_pickaxe"),
    "sword": item("iron_sword"),
    "bow": item("bow"),
    "goat_horn": item("goat_horn"),
    "gravel": iso_block(vanilla_raw("block/gravel.png")),
    "feather": item("feather"),
    "wool": iso_block(vanilla_raw("block/white_wool.png")),
    "snowball": item("snowball"),
    "water_bucket": item("water_bucket"),
    "felt_boots": ITEMS["felt_boots"]["icons"][0],
    "dagger": mod_item("silent_dagger"),
    "deafening_bell": mod_item("deafening_bell"),
}


textures = {
    "listener": data_url(read(os.path.join(MOD_ASSETS, "textures/entity/listener/listener.png")), "image/png"),
}

html = open(os.path.join(ROOT, "tools/viewer_template.html"), encoding="utf-8").read()
html = html.replace("__TEXTURES__", json.dumps(textures)).replace("__ITEMS__", json.dumps(ITEMS))
html = html.replace("__ICONS__", json.dumps(ICONS)).replace("__SOUNDS__", json.dumps(vanilla_sounds()))
# tools/visualizador.html para abrir localmente; site/index.html é o que a Vercel publica.
for out in (os.path.join(ROOT, "tools/visualizador.html"), os.path.join(ROOT, "site/index.html")):
    os.makedirs(os.path.dirname(out), exist_ok=True)
    open(out, "w", encoding="utf-8").write(html)
    print(out, f"{os.path.getsize(out) // 1024} KB")
