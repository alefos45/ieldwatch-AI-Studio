#!/usr/bin/env python3
"""
Generador de catálogo Fieldwatch.
Combina: OUI IEEE, Bluetooth SIG, Fast Pair, BLE-DB, AirHound, DeFlock.
Salida: fieldwatch_catalog.json (para app/src/main/assets/)
"""

import csv, json, re, sys, urllib.request, urllib.error
from pathlib import Path

OUT = Path("fieldwatch_catalog.json")
CACHE = Path("cache")
CACHE.mkdir(exist_ok=True)

SOURCES = {
    # OUIs IEEE completos (90k fabricantes)
    "oui_master": "https://raw.githubusercontent.com/ringmast4r/OUI-Master-Database/main/data/csv/master_oui.csv",

    # Bluetooth SIG Company IDs (3k fabricantes BLE)
    "bluetooth_sig": "https://bitbucket.org/bluetooth-SIG/public/raw/main/assigned_numbers/company_identifiers/company_identifiers.yaml",

    # Fast Pair model IDs (300+ modelos exactos)
    "fast_pair": "https://raw.githubusercontent.com/SpectrixDev/DIY_WhisperPair/main/parser/fast_pair_models.py",

    # BLE-DB (nombres reales reportados por usuarios)
    "ble_db": "https://raw.githubusercontent.com/simondankelmann/BLE-DB/main/ble_db.json",
}


def download(url, name):
    dest = CACHE / name
    if dest.exists() and dest.stat().st_size > 100:
        return dest
    print(f"  Descargando {name}...")
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "FieldwatchCatalog/1.0"})
        with urllib.request.urlopen(req, timeout=30) as r:
            dest.write_bytes(r.read())
        return dest
    except Exception as e:
        print(f"  ERROR: {e}")
        return None


# ---------- Parsers ----------

def parse_oui_master(path):
    """CSV: Registry,Assignment,Organization Name,Organization Address"""
    out = {}
    try:
        with open(path, newline='', encoding='utf-8', errors='ignore') as f:
            reader = csv.DictReader(f)
            for r in reader:
                oui = (r.get('Assignment') or '').strip().upper()
                if len(oui) != 6:
                    continue
                if not re.fullmatch(r'[0-9A-F]{6}', oui):
                    continue
                name = (r.get('Organization Name') or '').strip()
                if not name:
                    continue
                out[f"oui:{oui}"] = {
                    "name": name,
                    "type": "manufacturer",
                    "source": "IEEE"
                }
    except Exception as e:
        print(f"  WARN OUI: {e}")
    return out


def parse_bluetooth_sig(path):
    """YAML: - Company: <Nombre>\\n  Value: 0x004C"""
    out = {}
    try:
        text = path.read_text(encoding='utf-8', errors='ignore')
        # Parser simple, sin dependencias YAML
        pattern = re.compile(
            r'-\s*Company:\s*(.+?)\n\s*Value:\s*(0x[0-9A-Fa-f]+|\d+)',
            re.MULTILINE
        )
        for m in pattern.finditer(text):
            name = m.group(1).strip().strip('"').strip("'")
            val = m.group(2)
            try:
                cid = int(val, 16) if val.startswith('0x') else int(val)
            except ValueError:
                continue
            out[f"company:{cid:04X}"] = {
                "name": name,
                "type": "manufacturer-ble",
                "source": "Bluetooth SIG"
            }
    except Exception as e:
        print(f"  WARN Bluetooth SIG: {e}")
    return out


def parse_fast_pair(path):
    """Python: MODEL_IDS = { 0xCD8256: 'Sony WF-1000XM4', ... }"""
    out = {}
    try:
        text = path.read_text(encoding='utf-8', errors='ignore')
        # Tolerante: acepta "0xXXXXXX: '...'" o "0xXXXXXX: \"...\""
        pattern = re.compile(r'0x([0-9A-Fa-f]{6})\s*:\s*[\'"]([^\'"]+)[\'"]')
        for m in pattern.finditer(text):
            model_id = m.group(1).upper()
            name = m.group(2).strip()
            out[f"fastpair:{model_id}"] = {
                "name": name,
                "type": "model",
                "category": "audio/wearable",
                "source": "Google Fast Pair"
            }
    except Exception as e:
        print(f"  WARN Fast Pair: {e}")
    return out


def parse_ble_db(path):
    """JSON genérico: {deviceId: name} o [{id, name}]"""
    out = {}
    try:
        data = json.loads(path.read_text(encoding='utf-8', errors='ignore'))
        items = data if isinstance(data, list) else data.get('devices', [])
        for item in items:
            if isinstance(item, dict):
                did = item.get('id') or item.get('deviceId') or item.get('uuid')
                name = item.get('name') or item.get('label')
                if not did or not name:
                    continue
                key = str(did).upper().replace('0X', '').replace(':', '')
                if len(key) == 6 or len(key) == 8:
                    out[f"bledb:{key}"] = {
                        "name": name,
                        "type": "model",
                        "source": "BLE-DB"
                    }
    except Exception as e:
        print(f"  WARN BLE-DB: {e}")
    return out


# ---------- Curación manual ----------

# Modelos adicionales curados manualmente: TVs, cámaras, rastreadores, teléfonos, autos.
# Estos NO están en fuentes públicas como JSON, así que los agregamos a mano.
CURATED = {
    # TVs (por nombre BT / Fast Pair donde aplica)
    "fastpair:00000B": ("Google TV / Chromecast", "tv"),
    "fastpair:0000F0": ("Google Nest Hub", "smart-display"),

    # Auriculares / audio
    "fastpair:0000FC": ("Google Pixel Buds Pro", "audio"),
    "fastpair:0600FC": ("Google Pixel Buds A", "audio"),
    "fastpair:CD8256": ("Sony WF-1000XM4", "audio"),
    "fastpair:0E30C3": ("Sony WH-1000XM5", "audio"),
    "fastpair:821F66": ("Sony LinkBuds S", "audio"),
    "fastpair:F52494": ("JBL Tune Buds", "audio"),
    "fastpair:9D3F8A": ("Anker Soundcore Liberty 4", "audio"),
    "fastpair:E06116": ("Sony LinkBuds S", "audio"),
    "fastpair:E020C1": ("Anker soundcore Motion 300", "audio"),
    "fastpair:0xE5440B": ("TAG Heuer Calibre E4", "wearable"),
    "fastpair:0xE57363": ("Oladance Wearable Stereo", "audio"),

    # Wearables
    "fastpair:00000F": ("Galaxy Watch", "wearable"),

    # Rastreadores (por UUID, ya en DefaultCatalog — acá por nombre)
    "tracker:airtag": ("Apple AirTag", "tracker"),
    "tracker:smarttag2": ("Samsung Galaxy SmartTag2", "tracker"),
    "tracker:tile-mate": ("Tile Mate (2024)", "tracker"),
    "tracker:chipolo-one": ("Chipolo ONE", "tracker"),
    "tracker:pebblebee": ("Pebblebee Clip", "tracker"),
    "tracker:findhub": ("Google Find Hub tag", "tracker"),

    # Cámaras (algunas emiten BLE con nombre)
    "camera:ring-doorbell": ("Ring Video Doorbell", "camera"),
    "camera:arlo-pro5": ("Arlo Pro 5", "camera"),
    "camera:eufy-cam3": ("Eufy Cam 3", "camera"),
    "camera:wyze-cam-v3": ("Wyze Cam v3", "camera"),
    "camera:reolink-argus": ("Reolink Argus", "camera"),
    "camera:flock-safety": ("Flock Safety ALPR", "surveillance"),

    # Autos (por nombre BT/company ID)
    "car:tesla-model3": ("Tesla Model 3", "vehicle"),
    "car:ford-f150": ("Ford F-150", "vehicle"),
    "car:bmw-ix": ("BMW iX", "vehicle"),
}


def add_curated(out):
    for key, (name, cat) in CURATED.items():
        if key.startswith("fastpair:"):
            out[key.upper().replace(":0X", ":")] = {
                "name": name, "type": "model", "category": cat, "source": "curado"
            }
        elif key.startswith("tracker:") or key.startswith("camera:") or key.startswith("car:"):
            out[key] = {
                "name": name, "type": "model", "category": cat, "source": "curado"
            }
    return out


# ---------- Main ----------

def main():
    print("=== Generador de catálogo Fieldwatch ===")
    catalog = {}

    p = download(SOURCES["oui_master"], "oui_master.csv")
    if p:
        d = parse_oui_master(p)
        catalog.update(d)
        print(f"  OUIs IEEE:        {len(d):>7,}")

    p = download(SOURCES["bluetooth_sig"], "bluetooth_sig.yaml")
    if p:
        d = parse_bluetooth_sig(p)
        catalog.update(d)
        print(f"  Company IDs BLE:  {len(d):>7,}")

    p = download(SOURCES["fast_pair"], "fast_pair.py")
    if p:
        d = parse_fast_pair(p)
        catalog.update(d)
        print(f"  Fast Pair:        {len(d):>7,}")

    p = download(SOURCES["ble_db"], "ble_db.json")
    if p:
        d = parse_ble_db(p)
        catalog.update(d)
        print(f"  BLE-DB:           {len(d):>7,}")

    d = add_curated(catalog)
    print(f"  Curados:          {len(CURATED):>7,}")

    print(f"\n=== TOTAL: {len(catalog):,} entradas ===")

    # Escribir JSON
    with open(OUT, 'w', encoding='utf-8') as f:
        json.dump(catalog, f, ensure_ascii=False, separators=(',', ':'))

    mb = OUT.stat().st_size / 1024 / 1024
    print(f"Guardado: {OUT} ({mb:.1f} MB)")

    # Resumen por tipo
    from collections import Counter
    types = Counter(v.get('type', 'unknown') for v in catalog.values())
    print("\nPor tipo:")
    for t, n in types.most_common():
        print(f"  {t:20s} {n:>7,}")


if __name__ == "__main__":
    main()