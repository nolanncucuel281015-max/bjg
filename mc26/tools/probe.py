"""Sonde de l'API Minecraft/Fabric 26.3 (pour adapter le code) : lit mc26/tools/probe.txt.
  class <nom.complet.Classe> [regex]   -> signatures (javap -p), filtrées par regex
  find <regex>                         -> classes dont le chemin correspond
"""
import os, re, subprocess, sys, zipfile
home = os.path.expanduser("~")
jars = []
for root in (home + "/.gradle/caches", "mc26/.gradle"):
    for d, _, fs in os.walk(root):
        for f in fs:
            if f.endswith(".jar") and not f.endswith("-sources.jar"):
                p = os.path.join(d, f)
                if ("minecraft" in p and ("26.3" in p or "merged" in p or "common" in p or "client" in p)) or "fabric-api" in p or "net.fabricmc" in p:
                    jars.append(p)
index = {}
files = {}
for j in jars:
    try:
        with zipfile.ZipFile(j) as z:
            for n in z.namelist():
                if not n.endswith(".class") and (n.startswith("data/") or n.startswith("assets/minecraft/")):
                    files.setdefault(n, j)
                if n.endswith(".class") and (n.startswith("net/minecraft/") or n.startswith("net/fabricmc/fabric/api/") or n.startswith("com/mojang/")):
                    index.setdefault(n[:-6].replace("/", "."), j)
    except Exception:
        pass
print("classes indexées :", len(index))
for line in open("mc26/tools/probe.txt"):
    line = line.strip()
    if not line or line.startswith("#"):
        continue
    kind, _, rest = line.partition(" ")
    if kind == "find":
        hits = sorted(c for c in index if re.search(rest, c))
        print(f"== find {rest} ({len(hits)})"); print("\n".join(hits[:60]))
    elif kind == "files":
        hits = sorted(f for f in files if re.search(rest, f))
        print(f"== files {rest} ({len(hits)})"); print("\n".join(hits[:80]))
    elif kind == "cat":
        if rest not in files:
            print(f"== cat {rest} : INTROUVABLE"); continue
        with zipfile.ZipFile(files[rest]) as z:
            print(f"== cat {rest}"); print(z.read(rest).decode("utf-8", "replace")[:int(os.environ.get("PROBE_LIMIT", "6000")) if "noise_settings" not in rest else 400000])
    elif kind == "class":
        cls, _, rx = rest.partition(" ")
        if cls not in index:
            print(f"== class {cls} : INTROUVABLE"); continue
        out = subprocess.run(["javap", "-p", "-cp", index[cls], cls], capture_output=True, text=True).stdout
        lines = [l for l in out.splitlines() if not rx or re.search(rx, l, re.I)]
        print(f"== class {cls} /{rx}/ ({len(lines)})"); print("\n".join(lines[:80]))
