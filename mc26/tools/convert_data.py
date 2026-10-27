"""Génère les données 26.3 (mc26/overrides/main/resources/data) à partir des données 1.21.11 (src/main/resources/data).
Lancer depuis la racine du dépôt : python3 mc26/tools/convert_data.py
"""
import json, os, pathlib, shutil
SRC = pathlib.Path("src/main/resources/data")
OUT = pathlib.Path("mc26/overrides/main/resources/data")
REMOVED = pathlib.Path("mc26/overrides/removed.txt")
removed = []

def write(rel, obj):
    p = OUT / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(obj, indent="\t", ensure_ascii=False))

def state_str(st):
    if isinstance(st, str):
        return st
    s = st["Name"]
    if st.get("Properties"):
        s += "[" + ",".join(f"{k}={v}" for k, v in st["Properties"].items()) + "]"
    return s

# ---- tables de butin : "conditions" -> "condition", "functions" -> "modifier", clés "condition"/"function" -> "type"
def cond(c):
    t = c.get("condition")
    if t == "minecraft:match_tool":
        txt = json.dumps(c)
        if "shears" in txt:
            return "minecraft:tool/can_shear"
        if "silk_touch" in txt:
            return "minecraft:tool/can_silk_touch"
    out = {"type": t}
    for k, v in c.items():
        if k == "condition":
            continue
        if k == "term":
            v = cond(v)
        elif k == "terms":
            v = [cond(x) for x in v]
        out[k] = loot(v)
    return out

def conds(lst):
    lst = [cond(c) for c in lst]
    return lst[0] if len(lst) == 1 else {"type": "minecraft:all_of", "terms": lst}

def loot(o):
    if isinstance(o, list):
        return [loot(x) for x in o]
    if not isinstance(o, dict):
        return o
    out = {}
    for k, v in o.items():
        if k == "conditions":
            out["condition"] = conds(v)
        elif k == "functions":
            out["modifier"] = [{("type" if kk == "function" else kk): loot(vv) if kk != "conditions" else None for kk, vv in f.items() if kk != "conditions"}
                               | ({"condition": conds(f["conditions"])} if "conditions" in f else {}) for f in v]
        else:
            out[k] = loot(v)
    return out

for f in (SRC / "diamondaltar/loot_table").rglob("*.json"):
    write(f.relative_to(SRC), loot(json.loads(f.read_text())))

# ---- biomes : apparitions dans les attributs
for f in (SRC / "diamondaltar/worldgen/biome").glob("*.json"):
    d = json.loads(f.read_text())
    sp = d.pop("spawners", {}); costs = d.pop("spawn_costs", {})
    cats = {k: [{"type": e["type"], "count": e.get("maxCount", 1), "weight": e["weight"]} for e in v] for k, v in sp.items() if v}
    d.setdefault("attributes", {})["minecraft:gameplay/natural_mob_spawns"] = {
        "argument": {"spawn_costs": costs, "spawns_by_category": cats}, "modifier": "overlay"}
    write(f.relative_to(SRC), d)

# ---- configured_feature -> feature (la config est mise à plat)
for f in (SRC / "diamondaltar/worldgen/configured_feature").glob("*.json"):
    d = json.loads(f.read_text())
    flat = {"type": d["type"]}
    flat.update(d.get("config", {}))
    for t in flat.get("targets", []):
        t["state"] = state_str(t["state"])
    write(pathlib.Path("diamondaltar/worldgen/feature") / f.name, flat)
    removed.append(f"main/resources/data/diamondaltar/worldgen/configured_feature/{f.name}")

# ---- réglages de bruit : ceux de floating_islands 26.3, retexturés, + règle de matériaux (une par dimension)
base0 = json.loads(pathlib.Path("mc26/tools/vanilla_floating_islands.json").read_text())

def rule(r):
    if isinstance(r, list):
        return [rule(x) for x in r]
    if not isinstance(r, dict):
        return r
    out = {}
    for k, v in r.items():
        out[k] = state_str(v) if k == "result_state" else rule(v)
    return out

for f in sorted((SRC / "diamondaltar/worldgen/noise_settings").glob("*.json")):
    old = json.loads(f.read_text())
    base = json.loads(json.dumps(base0))
    base["default_block"] = state_str(old["default_block"])
    base["default_fluid"] = "minecraft:air"
    base["material_rule"] = f"diamondaltar:{f.stem}"
    base.pop("debug_functions", None)
    write(f"diamondaltar/worldgen/noise_settings/{f.name}", base)
    write(f"diamondaltar/worldgen/material_rule/{f.name}", rule(old["surface_rule"]))

# ---- types de dimension
for f in sorted((SRC / "diamondaltar/dimension_type").glob("*.json")):
    d = json.loads(f.read_text())
    d["default_clock"] = "minecraft:the_end"
    d["has_ender_dragon_fight"] = False
    bed = d.get("attributes", {}).get("minecraft:gameplay/bed_rule")
    if bed and "explodes" in bed:
        bed["destroy_on_use"] = bed.pop("explodes")
    write(f"diamondaltar/dimension_type/{f.name}", d)

# ---- liste des fichiers 1.21.11 à ne pas copier en 26.3
lines = [l for l in REMOVED.read_text().splitlines() if not l.startswith("main/resources/data/")] if REMOVED.exists() else []
REMOVED.write_text("\n".join(lines + sorted(removed)) + "\n")
print("ok :", len(list(OUT.rglob('*.json'))), "fichiers 26.3")
