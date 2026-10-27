"""Génère tout le contenu « données et ressources » des 7 Royaumes (src/main/resources) :
textures (recolorées à partir des textures astrales), modèles, états de blocs, objets, équipement,
dimensions, types de dimension, biomes, bruit, features, butins, recettes, tags et traductions.

Lancer depuis la racine du dépôt :  python3 tools/realms_gen.py  puis  python3 mc26/tools/convert_data.py
"""
import copy, json, pathlib, random
from PIL import Image

RES = pathlib.Path("src/main/resources")
ASSETS = RES / "assets/diamondaltar"
DATA = RES / "data/diamondaltar"
TEX = ASSETS / "textures"
NS = "diamondaltar"

# id, minerai, couleurs (sombre, moyen, clair) de la pierre, du sol et des gemmes/métal, ciel, brouillard
REALMS = [
    dict(id="ember", ore="pyronite", tier=0,
         stone=[(28, 12, 10), (78, 34, 26), (130, 60, 40)], soil=[(40, 34, 32), (95, 80, 74), (160, 120, 100)],
         metal=[(60, 12, 4), (220, 80, 20), (255, 215, 110)], sky="#3a0f05", fog="#6b200a", sky_light="#ff8a40",
         ambient=0.25, patch="minecraft:magma_block", skybox="overworld", water="#ff5a1a",
         thematic="minecraft:blaze_powder", monsters=[("minecraft:magma_cube", 10, 1, 3), ("minecraft:blaze", 6, 1, 2)]),
    dict(id="frost", ore="cryonite", tier=1,
         stone=[(40, 60, 90), (95, 135, 175), (170, 205, 230)], soil=[(180, 200, 220), (225, 238, 248), (255, 255, 255)],
         metal=[(20, 50, 90), (100, 200, 245), (240, 255, 255)], sky="#9fd8ff", fog="#d8f2ff", sky_light="#e0f6ff",
         ambient=0.3, patch="minecraft:packed_ice", skybox="overworld", water="#7fd4ff",
         thematic="minecraft:blue_ice", monsters=[("minecraft:stray", 10, 1, 3), ("minecraft:skeleton", 4, 1, 2)]),
    dict(id="storm", ore="fulgurite", tier=2,
         stone=[(22, 22, 36), (62, 64, 90), (120, 124, 160)], soil=[(45, 45, 55), (95, 95, 110), (150, 150, 170)],
         metal=[(70, 60, 10), (240, 210, 60), (255, 255, 200)], sky="#2a2f45", fog="#454c6b", sky_light="#fff2a0",
         ambient=0.15, patch="minecraft:tuff", skybox="overworld", water="#6b78a8",
         thematic="minecraft:lightning_rod", monsters=[("minecraft:creeper", 10, 1, 2), ("minecraft:phantom", 5, 1, 2)]),
    dict(id="sylvan", ore="verdite", tier=3,
         stone=[(30, 42, 28), (70, 92, 60), (120, 140, 100)], soil=[(30, 70, 20), (70, 150, 45), (150, 220, 90)],
         metal=[(10, 50, 15), (60, 200, 70), (210, 255, 150)], sky="#8fe08a", fog="#b8f0a0", sky_light="#d8ffb0",
         ambient=0.3, patch="minecraft:moss_block", skybox="overworld", water="#3fbf7a",
         thematic="minecraft:moss_block", monsters=[("minecraft:spider", 10, 1, 2), ("minecraft:zombie", 8, 1, 3)]),
    dict(id="abyss", ore="abyssite", tier=4,
         stone=[(8, 18, 40), (22, 50, 90), (50, 95, 140)], soil=[(30, 70, 80), (60, 130, 140), (120, 200, 200)],
         metal=[(5, 20, 70), (30, 110, 230), (130, 255, 240)], sky="#021030", fog="#0a2a5a", sky_light="#3df5e0",
         ambient=0.2, patch="minecraft:prismarine", skybox="end", water="#1e6bff",
         thematic="minecraft:prismarine_crystals", monsters=[("minecraft:drowned", 10, 1, 3)]),
    dict(id="shadow", ore="umbrite", tier=5,
         stone=[(8, 6, 12), (28, 20, 40), (55, 40, 75)], soil=[(22, 18, 28), (50, 42, 62), (90, 78, 108)],
         metal=[(20, 5, 40), (120, 45, 200), (220, 170, 255)], sky="#05020a", fog="#140a20", sky_light="#7a2bc8",
         ambient=0.05, patch="minecraft:blackstone", skybox="end", water="#2a0a3a",
         thematic="minecraft:echo_shard", monsters=[("minecraft:enderman", 10, 1, 2), ("minecraft:wither_skeleton", 6, 1, 2)]),
    dict(id="solar", ore="solarite", tier=6,
         stone=[(110, 80, 40), (190, 150, 80), (240, 215, 150)], soil=[(170, 130, 50), (230, 195, 100), (255, 240, 170)],
         metal=[(110, 60, 0), (255, 190, 40), (255, 255, 230)], sky="#ffd36b", fog="#fff0c0", sky_light="#ffffff",
         ambient=0.6, patch="minecraft:smooth_sandstone", skybox="overworld", water="#ffd040",
         thematic="minecraft:glowstone_dust", monsters=[("minecraft:husk", 10, 1, 3)]),
]

NAMES = {
    "fr": {
        "realm": ["Royaume de Braise", "Royaume de Givre", "Royaume des Tempêtes", "Royaume Sylvestre",
                  "Royaume des Abysses", "Royaume des Ombres", "Royaume du Soleil"],
        "of": ["de Braise", "de Givre", "des Tempêtes", "Sylvestre", "des Abysses", "des Ombres", "du Soleil"],
        "stone": ["Pierre de Braise", "Pierre de Givre", "Pierre Tempétueuse", "Pierre Sylvestre", "Pierre Abyssale",
                  "Pierre d'Ombre", "Pierre Solaire"],
        "soil": ["Cendres Ardentes", "Neige Éternelle", "Gravier Foudroyé", "Mousse Sylvestre", "Sable Abyssal",
                 "Poussière d'Ombre", "Sable Doré"],
        "ore": ["Pyronite", "Cryonite", "Fulgurite", "Verdite", "Abyssite", "Umbrite", "Solarite"],
        "boss": ["Seigneur des Braises", "Reine du Givre", "Titan de la Tempête", "Béhémoth Sylvestre",
                 "Léviathan des Abysses", "Roi des Ombres", "Paladin Solaire"],
        "set": ["Résistance au feu, Force, immunité au feu et à la lave",
                "Vitesse II, Résistance, immunité au gel",
                "Vitesse III, Saut III, Célérité II, immunité à la foudre et aux chutes",
                "Régénération II, saturation, immunité au poison",
                "Respiration aquatique, Grâce du dauphin, Force des conduits, Force II, ne se noie jamais",
                "Vision nocturne, Force II, immunité aux ténèbres et au flétrissement, invisible accroupi",
                "Résistance II, Régénération, Force II, Absorption IV toutes les 30 s"],
    },
    "en": {
        "realm": ["Ember Realm", "Frost Realm", "Storm Realm", "Sylvan Realm", "Abyssal Realm", "Shadow Realm", "Solar Realm"],
        "of": ["Ember", "Frost", "Storm", "Sylvan", "Abyssal", "Shadow", "Solar"],
        "stone": ["Ember Stone", "Frost Stone", "Storm Stone", "Sylvan Stone", "Abyssal Stone", "Shadow Stone", "Solar Stone"],
        "soil": ["Burning Ash", "Eternal Snow", "Thunderstruck Gravel", "Sylvan Moss", "Abyssal Sand", "Shadow Dust", "Golden Sand"],
        "ore": ["Pyronite", "Cryonite", "Fulgurite", "Verdite", "Abyssite", "Umbrite", "Solarite"],
        "boss": ["Ember Lord", "Frost Queen", "Storm Titan", "Sylvan Behemoth", "Abyssal Leviathan", "Shadow King", "Solar Paladin"],
        "set": ["Fire Resistance, Strength, immune to fire and lava",
                "Speed II, Resistance, immune to freezing",
                "Speed III, Jump Boost III, Haste II, immune to lightning and falls",
                "Regeneration II, saturation, immune to poison",
                "Water Breathing, Dolphin's Grace, Conduit Power, Strength II, never drowns",
                "Night Vision, Strength II, immune to darkness and wither, invisible while sneaking",
                "Resistance II, Regeneration, Strength II, Absorption IV every 30 s"],
    },
}
ARMOR = ["helmet", "chestplate", "leggings", "boots"]


def dump(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent="\t", ensure_ascii=False))


# ------------------------------------------------------------------ textures
def lum(px):
    r, g, b = px[:3]
    return (0.299 * r + 0.587 * g + 0.114 * b) / 255


def grad(pal, t):
    t = max(0.0, min(1.0, t))
    if t < 0.5:
        a, b, u = pal[0], pal[1], t / 0.5
    else:
        a, b, u = pal[1], pal[2], (t - 0.5) / 0.5
    return tuple(int(a[i] + (b[i] - a[i]) * u) for i in range(3))


def recolor(src, pal, contrast=1.0, mask=None):
    im = Image.open(src).convert("RGBA")
    out = im.copy()
    lums = [lum(im.getpixel((x, y))) for y in range(im.height) for x in range(im.width) if im.getpixel((x, y))[3] > 0]
    lo, hi = (min(lums), max(lums)) if lums else (0, 1)
    for y in range(im.height):
        for x in range(im.width):
            px = im.getpixel((x, y))
            if px[3] == 0 or (mask is not None and not mask(x, y)):
                continue
            t = (lum(px) - lo) / max(1e-6, hi - lo)
            t = 0.5 + (t - 0.5) * contrast
            out.putpixel((x, y), grad(pal, t) + (px[3],))
    return out


def save(img, rel, mcmeta_from=None):
    p = TEX / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    img.save(p)
    if mcmeta_from is not None:
        (p.parent / (p.name + ".mcmeta")).write_text(pathlib.Path(str(mcmeta_from) + ".mcmeta").read_text())


def ore_mask():
    stone = Image.open(TEX / "block/astral_stone.png").convert("RGBA")
    pts = set()
    for name in ("lunarite_ore", "astralite_ore"):
        o = Image.open(TEX / f"block/{name}.png").convert("RGBA")
        for y in range(16):
            for x in range(16):
                if o.getpixel((x, y)) != stone.getpixel((x, y)):
                    pts.add((x, y))
    return pts


KEY = [
    "................",
    ".....####.......",
    "....#oooo#......",
    "...#oo##oo#.....",
    "...#o#..#o#.....",
    "...#o#..#o#.....",
    "...#oo##oo#.....",
    "....#oooo#......",
    ".....#gg#.......",
    "......#o#.......",
    "......#o##......",
    "......#ooo#.....",
    "......#o##......",
    "......#oo#......",
    "......#o##......",
    ".......#........",
]


def key_texture(r):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(KEY):
        for x, c in enumerate(row):
            if c == "#":
                img.putpixel((x, y), r["metal"][0] + (255,))
            elif c == "o":
                img.putpixel((x, y), grad(r["metal"], 0.55 + 0.35 * ((x + y) % 3 == 0)) + (255,))
            elif c == "g":
                img.putpixel((x, y), r["metal"][2] + (255,))
    return img


def textures():
    mask = ore_mask()
    for r in REALMS:
        rid, ore = r["id"], r["ore"]
        save(recolor(TEX / "block/astral_stone.png", r["stone"]), f"block/{rid}_stone.png")
        save(recolor(TEX / "block/lunar_regolith.png", r["soil"]), f"block/{rid}_soil.png")
        stone = recolor(TEX / "block/astral_stone.png", r["stone"])
        gems = recolor(TEX / "block/astralite_ore.png", r["metal"], contrast=1.3)
        for (x, y) in mask:
            stone.putpixel((x, y), gems.getpixel((x, y)))
        save(stone, f"block/{ore}_ore.png")
        portal = recolor(TEX / "block/astral_portal.png", [r["metal"][0], r["metal"][1], r["metal"][2]])
        save(portal, f"block/realm_portal_{rid}.png", TEX / "block/astral_portal.png")
        save(recolor(TEX / "item/raw_astralite.png", r["metal"]), f"item/raw_{ore}.png")
        save(recolor(TEX / "item/astralite_ingot.png", r["metal"]), f"item/{ore}_ingot.png")
        save(recolor(TEX / "item/astralite_fragment.png", r["metal"], contrast=1.2), f"item/{rid}_shard.png")
        save(recolor(TEX / "item/guardian_heart.png", r["metal"]), f"item/{rid}_sigil.png")
        save(key_texture(r), f"item/{rid}_key.png")
        for piece in ARMOR:
            src = TEX / f"item/infused_diamond_{piece}.png"
            save(recolor(src, r["metal"]), f"item/{ore}_{piece}.png", src)
        for layer in ("humanoid", "humanoid_leggings"):
            save(recolor(TEX / f"entity/equipment/{layer}/infused_diamond.png", r["metal"]), f"entity/equipment/{layer}/{ore}.png")
    # Cadre des Royaumes : obsidienne noire veinée d'or.
    save(recolor(TEX / "block/astral_frame.png", [(10, 6, 16), (60, 30, 80), (255, 210, 90)], contrast=1.2), "block/realm_frame.png")
    # Bois sylvestre.
    save(recolor(TEX / "block/astral_log.png", [(40, 25, 12), (100, 70, 40), (170, 130, 80)]), "block/sylvan_log.png")
    save(recolor(TEX / "block/astral_log_top.png", [(50, 30, 15), (130, 95, 55), (200, 160, 100)]), "block/sylvan_log_top.png")
    save(recolor(TEX / "block/astral_leaves.png", [(15, 60, 20), (60, 170, 60), (200, 255, 130)]), "block/sylvan_leaves.png")
    # Épée des Sept Royaumes : dégradé arc-en-ciel des 7 royaumes le long de la lame.
    src = TEX / "item/infused_diamond_sword.png"
    im = Image.open(src).convert("RGBA")
    out = im.copy()
    cols = [r["metal"][1] for r in REALMS]
    for y in range(im.height):
        for x in range(im.width):
            px = im.getpixel((x, y))
            if px[3] == 0:
                continue
            fy = y % 16
            d = (x + (15 - fy)) / 30  # 0 = garde (bas gauche), 1 = pointe (haut droite)
            k = min(6, int(d * 7))
            base = cols[k]
            l = lum(px)
            c = tuple(int(min(255, base[i] * (0.35 + l * 1.1) + 60 * max(0, l - 0.75))) for i in range(3))
            out.putpixel((x, y), c + (px[3],))
    save(out, "item/seven_realms_sword.png", src)
    extra_textures()


AETHER = [(40, 50, 90), (150, 225, 255), (255, 250, 225)]


def extra_textures():
    # Aetherite : blanc nacré et cyan céleste.
    save(recolor(TEX / "item/astralite_ingot.png", AETHER), "item/aetherite_ingot.png")
    for piece in ARMOR + ["sword", "pickaxe"]:
        src = TEX / f"item/infused_diamond_{piece}.png"
        save(recolor(src, AETHER), f"item/aetherite_{piece}.png", src)
    for layer in ("humanoid", "humanoid_leggings"):
        save(recolor(TEX / f"entity/equipment/{layer}/infused_diamond.png", AETHER), f"entity/equipment/{layer}/aetherite.png")
    # Ailes de l'Aurore : plumes dorées (aile de l'élytre : boîte 10×20×2 en (22, 0) de la texture 64×32).
    wings = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    gold = [(150, 70, 10), (255, 190, 50), (255, 255, 230)]
    for y in range(22):
        for x in range(22, 46):
            feather = ((x - 22) % 3 == 0)
            t = 1 - y / 22 * 0.7 - (0.15 if feather else 0)
            wings.putpixel((x, y), grad(gold, t) + (255,))
    save(wings, "entity/equipment/wings/dawn_wings.png")
    icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    shape = ["................",
             ".##..........##.",
             ".#o#........#o#.",
             ".#oo#......#oo#.",
             ".#ooo#....#ooo#.",
             ".#oooo#..#oooo#.",
             ".#ooooo##ooooo#.",
             ".#oooooooooooo#.",
             "..#oooooooooo#..",
             "..#oooo##oooo#..",
             "...#oo#..#oo#...",
             "...#o#....#o#...",
             "....#......#....",
             "................",
             "................",
             "................"]
    for y, row in enumerate(shape):
        for x, c in enumerate(row):
            if c == "#":
                icon.putpixel((x, y), gold[0] + (255,))
            elif c == "o":
                icon.putpixel((x, y), grad(gold, 1 - y / 13 * 0.6 - (0.2 if x % 3 == 0 else 0)) + (255,))
    save(icon, "item/dawn_wings.png")
    # Perle des Royaumes : sphère aux 7 couleurs.
    pearl = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cols = [r["metal"][1] for r in REALMS]
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = (dx * dx + dy * dy) ** 0.5
            if d <= 6.2:
                base = cols[int((x + y) / 31 * 7) % 7]
                light = max(0.0, 1 - ((x - 5) ** 2 + (y - 5) ** 2) ** 0.5 / 7)
                c = tuple(int(min(255, base[i] * (0.55 + 0.3 * light) + 140 * light ** 3)) for i in range(3))
                pearl.putpixel((x, y), (c if d < 5.6 else tuple(int(v * 0.5) for v in c)) + (255,))
    save(pearl, "item/realm_pearl.png")


# ------------------------------------------------------------------ modèles et états de blocs
def cube(name, tex=None):
    dump(ASSETS / f"models/block/{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{tex or name}"}})
    dump(ASSETS / f"blockstates/{name}.json", {"variants": {"": {"model": f"{NS}:block/{name}"}}})
    dump(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}})


def flat_item(name, handheld=False):
    dump(ASSETS / f"models/item/{name}.json", {"parent": "minecraft:item/handheld" if handheld else "minecraft:item/generated",
                                              "textures": {"layer0": f"{NS}:item/{name}"}})
    dump(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})


def portal_models():
    variants = {}
    for i, r in enumerate(REALMS):
        for axis, shape, faces in (("x", "ns", ("north", "south")), ("z", "ew", ("east", "west"))):
            name = f"realm_portal_{r['id']}_{shape}"
            frm, to = ([0, 0, 6], [16, 16, 10]) if shape == "ns" else ([6, 0, 0], [10, 16, 16])
            dump(ASSETS / f"models/block/{name}.json", {
                "textures": {"particle": f"{NS}:block/realm_portal_{r['id']}", "portal": f"{NS}:block/realm_portal_{r['id']}"},
                "elements": [{"from": frm, "to": to, "faces": {f: {"uv": [0, 0, 16, 16], "texture": "#portal"} for f in faces}}]})
            variants[f"axis={axis},realm={i}"] = {"model": f"{NS}:block/{name}"}
    dump(ASSETS / "blockstates/realm_portal.json", {"variants": variants})


def assets():
    for r in REALMS:
        rid, ore = r["id"], r["ore"]
        cube(f"{rid}_stone")
        cube(f"{rid}_soil")
        cube(f"{ore}_ore")
        for item in (f"raw_{ore}", f"{ore}_ingot", f"{rid}_key", f"{rid}_sigil", f"{rid}_shard") + tuple(f"{ore}_{p}" for p in ARMOR):
            flat_item(item)
        dump(ASSETS / f"equipment/{ore}.json", {"layers": {
            "humanoid": [{"texture": f"{NS}:{ore}"}], "humanoid_leggings": [{"texture": f"{NS}:{ore}"}]}})
    cube("realm_frame")
    cube("sylvan_leaves")
    dump(ASSETS / "models/block/sylvan_log.json", {"parent": "minecraft:block/cube_column", "textures": {
        "end": f"{NS}:block/sylvan_log_top", "side": f"{NS}:block/sylvan_log"}})
    dump(ASSETS / "blockstates/sylvan_log.json", {"variants": {
        "axis=y": {"model": f"{NS}:block/sylvan_log"},
        "axis=z": {"model": f"{NS}:block/sylvan_log", "x": 90},
        "axis=x": {"model": f"{NS}:block/sylvan_log", "x": 90, "y": 90}}})
    dump(ASSETS / "items/sylvan_log.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/sylvan_log"}})
    flat_item("seven_realms_sword", handheld=True)
    for item in ["aetherite_ingot", "dawn_wings", "realm_pearl"] + [f"aetherite_{p}" for p in ARMOR]:
        flat_item(item)
    flat_item("aetherite_sword", handheld=True)
    flat_item("aetherite_pickaxe", handheld=True)
    dump(ASSETS / "equipment/aetherite.json", {"layers": {
        "humanoid": [{"texture": f"{NS}:aetherite"}], "humanoid_leggings": [{"texture": f"{NS}:aetherite"}]}})
    dump(ASSETS / "equipment/dawn_wings.json", {"layers": {"wings": [{"texture": f"{NS}:dawn_wings"}]}})
    portal_models()


# ------------------------------------------------------------------ monde
def dimensions():
    astral_noise = json.loads((DATA / "worldgen/noise_settings/astralis.json").read_text())
    astral_type = json.loads((DATA / "dimension_type/astralis.json").read_text())
    for r in REALMS:
        rid, ore = r["id"], r["ore"]
        dump(DATA / f"dimension/{rid}.json", {"type": f"{NS}:{rid}", "generator": {
            "type": "minecraft:noise", "biome_source": {"type": "minecraft:fixed", "biome": f"{NS}:{rid}_lands"},
            "settings": f"{NS}:{rid}"}})
        t = copy.deepcopy(astral_type)
        t["ambient_light"] = r["ambient"]
        t["skybox"] = r["skybox"]
        a = t["attributes"]
        a["minecraft:visual/fog_color"] = r["fog"]
        a["minecraft:visual/sky_color"] = r["sky"]
        a["minecraft:visual/sky_light_color"] = r["sky_light"]
        dump(DATA / f"dimension_type/{rid}.json", t)
        n = copy.deepcopy(astral_noise)
        n["default_block"] = {"Name": f"{NS}:{rid}_stone"}
        floor = {"type": "minecraft:stone_depth", "add_surface_depth": False, "offset": 0, "secondary_depth_range": 0, "surface_type": "floor"}
        deep = dict(floor, add_surface_depth=True)
        soil = {"type": "minecraft:block", "result_state": {"Name": f"{NS}:{rid}_soil"}}
        n["surface_rule"] = {"type": "minecraft:sequence", "sequence": [
            {"type": "minecraft:condition", "if_true": floor, "then_run": {"type": "minecraft:sequence", "sequence": [
                {"type": "minecraft:condition", "if_true": {"type": "minecraft:noise_threshold", "noise": "minecraft:surface",
                                                            "min_threshold": 0.3, "max_threshold": 1.0},
                 "then_run": {"type": "minecraft:block", "result_state": {"Name": r["patch"]}}},
                soil]}},
            {"type": "minecraft:condition", "if_true": deep, "then_run": soil}]}
        dump(DATA / f"worldgen/noise_settings/{rid}.json", n)
        empty = {k: [] for k in ("ambient", "axolotls", "creature", "misc", "underground_water_creature", "water_ambient", "water_creature")}
        dump(DATA / f"worldgen/biome/{rid}_lands.json", {
            "attributes": {"minecraft:visual/sky_color": r["sky"], "minecraft:visual/fog_color": r["fog"]},
            "carvers": [], "downfall": 0.0, "effects": {"water_color": r["water"]},
            "features": [[], [], [], [], [], [], [f"{NS}:ore_{ore}"], [], [], [f"{NS}:decor_{rid}"], []],
            "has_precipitation": False, "spawn_costs": {},
            "spawners": dict(empty, monster=[{"type": m, "maxCount": mx, "minCount": mn, "weight": w} for m, w, mn, mx in r["monsters"]]),
            "temperature": 0.5})
        dump(DATA / f"worldgen/configured_feature/ore_{ore}.json", {"type": "minecraft:ore", "config": {
            "discard_chance_on_air_exposure": 0.0, "size": 8, "targets": [{
                "state": {"Name": f"{NS}:{ore}_ore"},
                "target": {"predicate_type": "minecraft:block_match", "block": f"{NS}:{rid}_stone"}}]}})
        dump(DATA / f"worldgen/placed_feature/ore_{ore}.json", {"feature": f"{NS}:ore_{ore}", "placement": [
            {"type": "minecraft:count", "count": 14}, {"type": "minecraft:in_square"},
            {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 0},
                                                          "max_inclusive": {"absolute": 200}}},
            {"type": "minecraft:biome"}]})
        dump(DATA / f"worldgen/configured_feature/realm_decor_{rid}.json", {"type": f"{NS}:realm_decor_{rid}", "config": {}})
        dump(DATA / f"worldgen/placed_feature/decor_{rid}.json", {"feature": f"{NS}:realm_decor_{rid}", "placement": [
            {"type": "minecraft:count", "count": 4 if rid == "sylvan" else 2}, {"type": "minecraft:in_square"},
            {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"}, {"type": "minecraft:biome"}]})


# ------------------------------------------------------------------ butins, recettes, tags
def loot_self(name):
    dump(DATA / f"loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:item", "name": f"{NS}:{name}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


def loot_ore(ore):
    dump(DATA / f"loot_table/blocks/{ore}_ore.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": f"{NS}:{ore}_ore", "conditions": [{"condition": "minecraft:match_tool", "predicate": {
                "predicates": {"minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]},
            {"type": "minecraft:item", "name": f"{NS}:raw_{ore}", "functions": [
                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                {"function": "minecraft:explosion_decay"}]}]}]}]})


def shaped(name, pattern, key, result, count=1, category="misc"):
    dump(DATA / f"recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": category, "pattern": pattern,
                                        "key": key, "result": {"id": result, "count": count}})


def recipes_and_loot():
    for r in REALMS:
        rid, ore = r["id"], r["ore"]
        for b in (f"{rid}_stone", f"{rid}_soil"):
            loot_self(b)
        loot_ore(ore)
        ingot = f"{NS}:{ore}_ingot"
        for kind, cook in (("smelting", 200), ("blasting", 100)):
            dump(DATA / f"recipe/{ore}_ingot_from_{kind}.json", {"type": f"minecraft:{kind}", "category": "misc",
                                                                 "ingredient": f"{NS}:raw_{ore}", "result": {"id": ingot},
                                                                 "experience": 1.0 + r["tier"] * 0.5, "cookingtime": cook})
        prev = f"{NS}:astralite_ingot" if r["tier"] == 0 else f"{NS}:{REALMS[r['tier'] - 1]['ore']}_ingot"
        shaped(f"{rid}_key", [" T ", "PEP", " T "], {"T": r["thematic"], "P": prev, "E": "minecraft:ender_eye"}, f"{NS}:{rid}_key")
        shaped(f"{rid}_sigil", [" I ", "IDI", " I "], {"I": ingot, "D": "minecraft:diamond"}, f"{NS}:{rid}_sigil")
        for piece, pat in (("helmet", ["III", "I I"]), ("chestplate", ["I I", "III", "III"]),
                           ("leggings", ["III", "I I", "I I"]), ("boots", ["I I", "I I"])):
            shaped(f"{ore}_{piece}", pat, {"I": ingot}, f"{NS}:{ore}_{piece}", category="equipment")
    loot_self("realm_frame")
    loot_self("sylvan_log")
    dump(DATA / "loot_table/blocks/sylvan_leaves.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:item", "name": f"{NS}:sylvan_leaves"}], "conditions": [{"condition": "minecraft:match_tool",
                                                                                    "predicate": {"items": "minecraft:shears"}}]}]})
    shaped("realm_frame", ["OOO", "OAO", "OOO"], {"O": "minecraft:obsidian", "A": f"{NS}:astralite_ingot"}, f"{NS}:realm_frame", 8, "building")
    A = f"{NS}:aetherite_ingot"
    for piece, pat in (("helmet", ["III", "I I"]), ("chestplate", ["I I", "III", "III"]),
                       ("leggings", ["III", "I I", "I I"]), ("boots", ["I I", "I I"])):
        shaped(f"aetherite_{piece}", pat, {"I": A}, f"{NS}:aetherite_{piece}", category="equipment")
    shaped("aetherite_sword", ["I", "I", "S"], {"I": A, "S": "minecraft:stick"}, f"{NS}:aetherite_sword", category="equipment")
    shaped("aetherite_pickaxe", ["III", " S ", " S "], {"I": A, "S": "minecraft:stick"}, f"{NS}:aetherite_pickaxe", category="equipment")
    shaped("seven_realms_sword", ["ABC", "DSE", "FNG"], {
        "A": f"{NS}:ember_shard", "B": f"{NS}:frost_shard", "C": f"{NS}:storm_shard", "D": f"{NS}:sylvan_shard",
        "S": "minecraft:netherite_sword", "E": f"{NS}:abyss_shard", "F": f"{NS}:shadow_shard", "N": "minecraft:nether_star",
        "G": f"{NS}:solar_shard"}, f"{NS}:seven_realms_sword", category="equipment")


def tag_add(rel, values):
    p = RES / "data/minecraft/tags" / rel
    d = json.loads(p.read_text()) if p.exists() else {"replace": False, "values": []}
    for v in values:
        if v not in d["values"]:
            d["values"].append(v)
    dump(p, d)


def tags():
    stones = [f"{NS}:{r['id']}_stone" for r in REALMS]
    ores = [f"{NS}:{r['ore']}_ore" for r in REALMS]
    tag_add("block/mineable/pickaxe.json", stones + ores + [f"{NS}:realm_frame"])
    tag_add("block/mineable/shovel.json", [f"{NS}:{r['id']}_soil" for r in REALMS])
    tag_add("block/mineable/axe.json", [f"{NS}:sylvan_log"])
    tag_add("block/mineable/hoe.json", [f"{NS}:sylvan_leaves"])
    tag_add("block/leaves.json", [f"{NS}:sylvan_leaves"])
    tag_add("block/logs.json", [f"{NS}:sylvan_log"])
    tag_add("item/logs.json", [f"{NS}:sylvan_log"])
    tag_add("block/needs_iron_tool.json", ores[:3])
    tag_add("block/needs_diamond_tool.json", ores[3:] + [f"{NS}:realm_frame"])


# ------------------------------------------------------------------ traductions
def lang():
    for code, file in (("fr", "fr_fr.json"), ("en", "en_us.json")):
        n = NAMES[code]
        p = ASSETS / "lang" / file
        d = json.loads(p.read_text())
        fr = code == "fr"
        for i, r in enumerate(REALMS):
            rid, ore = r["id"], r["ore"]
            o = n["ore"][i]
            d[f"realm.{NS}.{rid}"] = n["realm"][i]
            d[f"block.{NS}.{rid}_stone"] = n["stone"][i]
            d[f"block.{NS}.{rid}_soil"] = n["soil"][i]
            d[f"block.{NS}.{ore}_ore"] = f"Minerai de {o}" if fr else f"{o} Ore"
            d[f"item.{NS}.raw_{ore}"] = f"{o} Brute" if fr else f"Raw {o}"
            d[f"item.{NS}.{ore}_ingot"] = f"Lingot de {o}" if fr else f"{o} Ingot"
            d[f"item.{NS}.{rid}_key"] = f"Clé {n['of'][i]}" if fr else f"{n['of'][i]} Key"
            d[f"item.{NS}.{rid}_sigil"] = f"Sceau {n['of'][i]}" if fr else f"{n['of'][i]} Sigil"
            d[f"item.{NS}.{rid}_shard"] = f"Éclat {n['of'][i]}" if fr else f"{n['of'][i]} Shard"
            pieces = (["Casque", "Plastron", "Jambières", "Bottes"] if fr else ["Helmet", "Chestplate", "Leggings", "Boots"])
            for k, piece in enumerate(ARMOR):
                d[f"item.{NS}.{ore}_{piece}"] = f"{pieces[k]} de {o}" if fr else f"{o} {pieces[k]}"
            d[f"entity.{NS}.{rid}_boss"] = n["boss"][i]
            d[f"{NS}.realm_armor.set.{rid}"] = n["set"][i]
        if fr:
            d.update({
                f"block.{NS}.realm_frame": "Cadre des Royaumes",
                f"block.{NS}.realm_portal": "Portail des Royaumes",
                f"block.{NS}.sylvan_log": "Bûche Sylvestre",
                f"block.{NS}.sylvan_leaves": "Feuilles Sylvestres",
                f"item.{NS}.seven_realms_sword": "Épée des Sept Royaumes",
                f"entity.{NS}.shade": "Ombre",
                f"{NS}.realm.enter": "~ %s ~",
                f"{NS}.realm_boss.arrives": "%s s'éveille !",
                f"{NS}.realm_boss.defeated": "%s est vaincu ! Ramasse son éclat.",
                f"{NS}.sigil.wrong_realm": "Ce sceau ne fonctionne que dans le %s.",
                f"{NS}.sigil.already": "Le boss de ce royaume est déjà là !",
                f"{NS}.sigil.desc": "Clic droit dans son royaume : invoque le boss.",
                f"{NS}.key.desc": "Clic droit dans un cadre de Cadres des Royaumes : ouvre le portail.",
                f"{NS}.realm_armor.set": "Armure complète :",
                f"{NS}.seven_realms.cooldown": "Jugement des Sept Royaumes : encore %s s",
                f"{NS}.seven_realms.hit": "Chaque coup : feu, gel, poison, flétrissement, lumière, et parfois la foudre",
                f"{NS}.seven_realms.use": "Clic droit : Jugement des Sept Royaumes (7 colonnes, recharge 8 s)",
                f"item.{NS}.aetherite_ingot": "Lingot d'Aetherite",
                f"item.{NS}.aetherite_helmet": "Casque en Aetherite",
                f"item.{NS}.aetherite_chestplate": "Plastron en Aetherite",
                f"item.{NS}.aetherite_leggings": "Jambières en Aetherite",
                f"item.{NS}.aetherite_boots": "Bottes en Aetherite",
                f"item.{NS}.aetherite_sword": "Épée en Aetherite",
                f"item.{NS}.aetherite_pickaxe": "Pioche en Aetherite",
                f"item.{NS}.dawn_wings": "Ailes de l'Aurore",
                f"item.{NS}.realm_pearl": "Perle des Royaumes",
                f"{NS}.aetherite.set": "Armure complète : Force III, Résistance II, Vitesse II, Régénération II, Résistance au feu, Respiration, Vision nocturne ; immunité au feu, aux chutes, au gel, à la foudre et à la noyade",
                f"{NS}.aetherite.rare": "Le métal le plus rare : seuls les boss les plus forts en lâchent",
                f"{NS}.wings.desc": "Des élytres incassables qui se propulsent toutes seules (accroupi : planer sans propulsion)",
                f"{NS}.pearl.desc": "Accroupi + clic droit : choisir le monde · Clic droit : s'y téléporter (recharge 20 s)",
                f"{NS}.pearl.selected": "Destination : %s",
                f"{NS}.pearl.cooldown": "Perle des Royaumes : encore %s s",
                f"{NS}.pearl.already": "Tu es déjà dans : %s",
                f"{NS}.pearl.overworld": "Overworld",
                f"{NS}.pearl.astral": "Dimension Astrale",
            })
        else:
            d.update({
                f"block.{NS}.realm_frame": "Realm Frame",
                f"block.{NS}.realm_portal": "Realm Portal",
                f"block.{NS}.sylvan_log": "Sylvan Log",
                f"block.{NS}.sylvan_leaves": "Sylvan Leaves",
                f"item.{NS}.seven_realms_sword": "Sword of the Seven Realms",
                f"entity.{NS}.shade": "Shade",
                f"{NS}.realm.enter": "~ %s ~",
                f"{NS}.realm_boss.arrives": "%s awakens!",
                f"{NS}.realm_boss.defeated": "%s is defeated! Pick up its shard.",
                f"{NS}.sigil.wrong_realm": "This sigil only works in the %s.",
                f"{NS}.sigil.already": "This realm's boss is already here!",
                f"{NS}.sigil.desc": "Right-click in its realm: summons the boss.",
                f"{NS}.key.desc": "Right-click inside a frame of Realm Frames: opens the portal.",
                f"{NS}.realm_armor.set": "Full set:",
                f"{NS}.seven_realms.cooldown": "Judgement of the Seven Realms: %s s left",
                f"{NS}.seven_realms.hit": "Every hit: fire, frost, poison, wither, light, and sometimes lightning",
                f"{NS}.seven_realms.use": "Right-click: Judgement of the Seven Realms (7 pillars, 8 s cooldown)",
                f"item.{NS}.aetherite_ingot": "Aetherite Ingot",
                f"item.{NS}.aetherite_helmet": "Aetherite Helmet",
                f"item.{NS}.aetherite_chestplate": "Aetherite Chestplate",
                f"item.{NS}.aetherite_leggings": "Aetherite Leggings",
                f"item.{NS}.aetherite_boots": "Aetherite Boots",
                f"item.{NS}.aetherite_sword": "Aetherite Sword",
                f"item.{NS}.aetherite_pickaxe": "Aetherite Pickaxe",
                f"item.{NS}.dawn_wings": "Wings of Dawn",
                f"item.{NS}.realm_pearl": "Realm Pearl",
                f"{NS}.aetherite.set": "Full set: Strength III, Resistance II, Speed II, Regeneration II, Fire Resistance, Water Breathing, Night Vision; immune to fire, falls, freezing, lightning and drowning",
                f"{NS}.aetherite.rare": "The rarest metal: only the strongest bosses drop it",
                f"{NS}.wings.desc": "Unbreakable elytra that propel themselves (sneak: glide without thrust)",
                f"{NS}.pearl.desc": "Sneak + right-click: choose the world · Right-click: teleport there (20 s cooldown)",
                f"{NS}.pearl.selected": "Destination: %s",
                f"{NS}.pearl.cooldown": "Realm Pearl: %s s left",
                f"{NS}.pearl.already": "You are already in: %s",
                f"{NS}.pearl.overworld": "Overworld",
                f"{NS}.pearl.astral": "Astral Dimension",
            })
        dump(p, d)


if __name__ == "__main__":
    textures()
    assets()
    dimensions()
    recipes_and_loot()
    tags()
    lang()
    print("ok")
