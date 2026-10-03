#!/usr/bin/env python3
"""
Generador de catálogo Fieldwatch (URLs verificadas el 2026-10-03).

Fuentes (en orden de prioridad con fallbacks):
  - OUI Master Database (rama "master")           → OUIs MA-L/M/S
  - Wireshark manuf                                → respaldo de OUIs
  - Nordic bluetooth-numbers-database              → Company IDs BLE (JSON limpio)
  - Bluetooth SIG YAML oficial                     → respaldo de Company IDs
  - BLE-DB fastpair.json + samsung.json            → modelos Fast Pair/Samsung
  - BLE-DB README.md                               → respaldo
  - Curación manual                                → huecos puntuales

Salida: fieldwatch_catalog.json  →  app/src/main/assets/

Claves:
  oui:<hex>        6 hex = MA-L, 7 hex = MA-M, 9 hex = MA-S/IAB
  company:XXXX     Company ID BLE
  fastpair:XXXXXX  Model ID Google Fast Pair
  samsung:XXXX     Model ID Samsung
"""

import csv, json, re, shutil, sys, urllib.request
from collections import Counter
from pathlib import Path

OUT = Path("fieldwatch_catalog.json")
ASSETS_DIR = Path("app/src/main/assets")
CACHE = Path("cache")
CACHE.mkdir(exist_ok=True)

OUI_BASE = "https://raw.githubusercontent.com/Ringmast4r/OUI-Master-Database/master/LISTS"
BLEDB_BASE = "https://raw.githubusercontent.com/simondankelmann/BLE-DB/main"
NORDIC_BASE = "https://raw.githubusercontent.com/NordicSemiconductor/bluetooth-numbers-database/master/v1"

SOURCES = {
    # OUIs: OUI-Master primero, Wireshark respaldo
    "oui_csv":      (f"{OUI_BASE}/master_oui.csv",  "master_oui.csv"),
    "oui_txt":      (f"{OUI_BASE}/master_oui.txt",  "master_oui.txt"),
    "wireshark":    ("https://www.wireshark.org/download/automated/data/manuf", "wireshark_manuf"),

    # Company IDs BLE: Nordic primero, SIG YAML respaldo
    "company_ids":  (f"{NORDIC_BASE}/company_ids.json", "company_ids.json"),
    "sig_yaml":     ("https://bitbucket.org/bluetooth-SIG/public/raw/HEAD/assigned_numbers/company_identifiers/company_identifiers.yaml", "bluetooth_sig.yaml"),

    # BLE-DB: JSON dedicados primero, README respaldo
    "fastpair":     (f"{BLEDB_BASE}/fastpair.json", "fastpair.json"),
    "samsung":      (f"{BLEDB_BASE}/samsung.json",  "samsung.json"),
    "bledb_readme": (f"{BLEDB_BASE}/README.md",     "bledb_readme.md"),
}

FORCE_REFRESH = "--refresh" in sys.argv


def download(key):
    url, name = SOURCES[key]
    dest = CACHE / name
    if not FORCE_REFRESH and dest.exists() and dest.stat().st_size > 100:
        print(f"  [cache] {name}")
        return dest
    print(f"  Descargando {name}...")
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "FieldwatchCatalog/1.0"})
        with urllib.request.urlopen(req, timeout=60) as r:
            data = r.read()
        head = data[:200].lstrip().lower()
        if head.startswith(b"<!doctype html") or head.startswith(b"<html"):
            print(f"  ERROR: respuesta HTML (no se guarda) para {name}")
            return None
        dest.write_bytes(data)
        return dest
    except Exception as e:
        print(f"  ERROR ({name}): {e}")
        return None


def read(path):
    return path.read_text(encoding="utf-8", errors="ignore")


def oui_key(prefix):
    """'C8:5C:E2' o 'C85CE27' o 'C8:5C:E2:7' → 'oui:C85CE27'."""
    p = prefix.strip().upper().split("/")[0].replace(":", "").replace("-", "")
    if re.fullmatch(r"[0-9A-F]{6}|[0-9A-F]{7}|[0-9A-F]{9}", p):
        return f"oui:{p}"
    return None


# ---------- Parsers: OUIs ----------

def parse_oui_csv(path):
    """Acepta columnas IEEE ('Assignment','Organization Name') o formato OUI-Master
    ('oui','manufacturer','device_type')."""
    out = {}
    with open(path, newline="", encoding="utf-8", errors="ignore") as f:
        reader = csv.DictReader(f)
        if not reader.fieldnames:
            return out
        cols = {c.lower().strip() for c in reader.fieldnames}
        # Detectar nombres de columnas
        col_key = next((c for c in reader.fieldnames
                        if c.lower().strip() in ("assignment", "oui", "registry")), None)
        col_name = next((c for c in reader.fieldnames
                         if c.lower().strip() in ("organization name", "manufacturer", "organization")), None)
        col_type = next((c for c in reader.fieldnames if c.lower().strip() in ("device_type", "type")), None)
        if not col_key or not col_name:
            print(f"  WARN OUI CSV: columnas inesperadas: {reader.fieldnames}")
            return out
        for r in reader:
            k = oui_key(r.get(col_key) or "")
            name = (r.get(col_name) or "").strip()
            if not k or not name:
                continue
            entry = {"name": name, "type": "manufacturer", "source": "OUI-Master"}
            if col_type and r.get(col_type):
                entry["category"] = r[col_type].strip()
            out[k] = entry
    return out


def parse_oui_txt(path):
    """TXT: OUI<tab>Manufacturer."""
    out = {}
    for line in read(path).splitlines():
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) < 2:
            continue
        k = oui_key(parts[0])
        if k and parts[1].strip():
            out[k] = {"name": parts[1].strip(), "type": "manufacturer", "source": "OUI-Master"}
    return out


def parse_wireshark(path):
    """manuf: OUI<tab>Short<tab>Long."""
    out = {}
    for line in read(path).splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) < 2:
            continue
        k = oui_key(parts[0])
        if k and parts[1].strip():
            out[k] = {"name": parts[1].strip(), "type": "manufacturer", "source": "Wireshark"}
    return out


# ---------- Parsers: Company IDs BLE ----------

def parse_company_ids_json(path):
    """Nordic: [{"code": 76, "name": "Apple, Inc."}, ...]"""
    out = {}
    for it in json.loads(read(path)):
        try:
            cid = int(it["code"])
        except (KeyError, ValueError, TypeError):
            continue
        out[f"company:{cid:04X}"] = {
            "name": str(it["name"]).strip(),
            "type": "manufacturer-ble",
            "source": "Bluetooth SIG",
        }
    return out


def parse_sig_yaml(path):
    """YAML oficial SIG: ' - value: 0x004C\\n   name: "Apple, Inc."'"""
    out = {}
    text = read(path)
    pat = re.compile(r"value:\s*(0x[0-9A-Fa-f]+|\d+)\s*\n\s*name:\s*['\"]?(.+?)['\"]?\s*$", re.M)
    for m in pat.finditer(text):
        v = m.group(1)
        try:
            cid = int(v, 16) if v.lower().startswith("0x") else int(v)
        except ValueError:
            continue
        out[f"company:{cid:04X}"] = {
            "name": m.group(2).strip(),
            "type": "manufacturer-ble",
            "source": "Bluetooth SIG",
        }
    return out


# ---------- Parsers: Fast Pair / Samsung ----------

def parse_fastpair_json(path):
    """fastpair.json: {0x000006, "Google Pixel Buds"},\n..."""
    out = {}
    for m in re.finditer(r'\{0x([0-9A-Fa-f]{6})\s*,\s*"([^"]+)"\}', read(path)):
        name = m.group(2).strip()
        if name and "****" not in name:
            out[f"fastpair:{m.group(1).upper()}"] = {
                "name": name, "type": "model",
                "category": "audio/wearable", "source": "BLE-DB",
            }
    return out


def parse_samsung_json(path):
    """samsung.json: {"hex_code": "XXXX", ..., "modelName": "..."} (puede estar malformado)."""
    out = {}
    text = read(path)
    pat = re.compile(r'"hex_code"\s*:\s*"([0-9A-Fa-f]+)".*?"modelName"\s*:\s*"([^"]*)"', re.S)
    for m in pat.finditer(text):
        name = m.group(2).strip()
        if name:
            out[f"samsung:{m.group(1).upper()}"] = {
                "name": name, "type": "model",
                "category": "phone/wearable", "source": "BLE-DB",
            }
    return out


def parse_bledb_readme(path):
    """README como respaldo: líneas {0xXXXXXX, "Name"} y {0xXXXX, "Name"}."""
    out = {}
    text = read(path)
    for m in re.finditer(r'\{0x([0-9A-Fa-f]{6})\s*,\s*"([^"]+)"\}', text):
        name = m.group(2).strip()
        if name and "****" not in name:
            out[f"fastpair:{m.group(1).upper()}"] = {
                "name": name, "type": "model",
                "category": "audio/wearable", "source": "BLE-DB",
            }
    for m in re.finditer(r'\{0x([0-9A-Fa-f]{4})\s*,\s*"([^"]+)"\}', text):
        name = m.group(2).strip()
        if name and "****" not in name:
            out[f"samsung:{m.group(1).upper()}"] = {
                "name": name, "type": "model",
                "category": "phone/wearable", "source": "BLE-DB",
            }
    return out


# ---------- Curación manual (solo huecos) ----------

CURATED = {
    # Fast Pair IDs verificados
    "fastpair:CD8256":       ("Bose NC 700", "audio"),
    "fastpair:0E30C3":       ("Sony WH-1000XM5", "audio"),
    "fastpair:821F66":       ("Sony LinkBuds S", "audio"),
    "fastpair:F52494":       ("JBL Tune Buds", "audio"),
    "fastpair:9D3F8A":       ("Anker Soundcore Liberty 4", "audio"),
    "fastpair:0600FC":       ("Google Pixel Buds", "audio"),
    "fastpair:D800AA":       ("Google Pixel Buds Pro", "audio"),
    "fastpair:30018E":       ("Google Pixel Buds Pro 2", "audio"),
    "fastpair:1312F3":       ("Samsung Galaxy Buds2 Pro", "audio"),
    "fastpair:00000B":       ("Google Gphones", "phone"),
    "fastpair:000047":       ("Arduino 101", "dev"),
    "fastpair:0000F0":       ("Bose QuietComfort 35 II", "audio"),
    "fastpair:000006":       ("Google Pixel Buds", "audio"),
    "fastpair:000007":       ("Android Auto", "vehicle"),
    # Rastreadores
    "tracker:airtag":        ("Apple AirTag", "tracker"),
    "tracker:smarttag2":     ("Samsung Galaxy SmartTag2", "tracker"),
    "tracker:tile-mate":     ("Tile Mate (2024)", "tracker"),
    "tracker:chipolo-one":   ("Chipolo ONE", "tracker"),
    "tracker:pebblebee":     ("Pebblebee Clip", "tracker"),
    "tracker:findhub":       ("Google Find Hub tag", "tracker"),
    # Cámaras
    "camera:ring-doorbell":  ("Ring Video Doorbell", "camera"),
    "camera:arlo-pro5":      ("Arlo Pro 5", "camera"),
    "camera:eufy-cam3":      ("Eufy Cam 3", "camera"),
    "camera:wyze-cam-v3":    ("Wyze Cam v3", "camera"),
    "camera:reolink-argus":  ("Reolink Argus", "camera"),
    "camera:flock-safety":   ("Flock Safety ALPR", "surveillance"),
    # Vehículos
    "car:tesla-model3":      ("Tesla Model 3", "vehicle"),
    "car:ford-f150":         ("Ford F-150", "vehicle"),
    "car:bmw-ix":            ("BMW iX", "vehicle"),
}


def add_curated(catalog):
    added = 0
    for key, (name, cat) in CURATED.items():
        if key not in catalog:
            catalog[key] = {"name": name, "type": "model", "category": cat, "source": "curado"}
            added += 1
    return added


# ---------- Main ----------

def load(label, key, parser, catalog, only_new=False, skip_if_nonempty=False):
    if skip_if_nonempty and catalog:
        return 0
    p = download(key)
    if not p:
        return 0
    try:
        d = parser(p)
    except Exception as e:
        print(f"  WARN {label}: {e}")
        return 0
    if only_new:
        d = {k: v for k, v in d.items() if k not in catalog}
    catalog.update(d)
    print(f"  {label:<28}{len(d):>8,}")
    return len(d)


def main():
    print("=== Generador de catálogo Fieldwatch ===")
    catalog = {}

    # --- OUIs: OUI-Master (CSV → TXT) → Wireshark ---
    n = load("OUIs (OUI-Master CSV)", "oui_csv", parse_oui_csv, catalog)
    if n == 0:
        n = load("OUIs (OUI-Master TXT)", "oui_txt", parse_oui_txt, catalog)
    load("OUIs (Wireshark)", "wireshark", parse_wireshark, catalog, only_new=True)

    # --- Company IDs BLE: Nordic → SIG YAML ---
    load("Company IDs (Nordic)", "company_ids", parse_company_ids_json, catalog)
    load("Company IDs (SIG)", "sig_yaml", parse_sig_yaml, catalog, only_new=True)

    # --- Fast Pair / Samsung: JSON dedicado → README ---
    n_fp = load("Fast Pair (JSON)", "fastpair", parse_fastpair_json, catalog)
    n_sam = load("Samsung (JSON)", "samsung", parse_samsung_json, catalog)
    if n_fp == 0 and n_sam == 0:
        load("Fast Pair/Samsung (README)", "bledb_readme", parse_bledb_readme, catalog)

    # --- Curados ---
    n = add_curated(catalog)
    print(f"  {'Curados (nuevos)':<28}{n:>8,}")

    print(f"\n=== TOTAL: {len(catalog):,} entradas ===")
    if len(catalog) < 1000:
        print("ERROR: catálogo casi vacío (<1000). No se escribe.")
        sys.exit(1)

    ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(catalog, f, ensure_ascii=False, separators=(",", ":"))
    print(f"Guardado: {OUT} ({OUT.stat().st_size / 1024 / 1024:.1f} MB)")
    shutil.copy(OUT, ASSETS_DIR / OUT.name)
    print(f"Copiado a: {ASSETS_DIR / OUT.name}")

    print("\nPor tipo:")
    for t, c in Counter(v.get("type", "unknown") for v in catalog.values()).most_common():
        print(f"  {t:20s}{c:>8,}")


if __name__ == "__main__":
    main()