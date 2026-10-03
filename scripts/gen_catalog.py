#!/usr/bin/env python3
"""
Generador de catálogo Fieldwatch.
Combina: OUI IEEE, Bluetooth SIG, Fast Pair, BLE-DB, Wireshark.
Salida: fieldwatch_catalog.json (para app/src/main/assets/)
"""

import csv, json, re, sys, urllib.request, urllib.error
from pathlib import Path

OUT = Path("fieldwatch_catalog.json")
ASSETS_DIR = Path("app/src/main/assets")
CACHE = Path("cache")
CACHE.mkdir(exist_ok=True)

# URLs validadas (2026-10-03)
SOURCES = {
    # OUI Master Database (combina IEEE, Wireshark, Nmap, HDM)
    "oui_master_csv": "https://raw.githubusercontent.com/Ringmast4r/OUI-Master-Database/main/LISTS/master_oui.csv",
    "oui_master_txt": "https://raw.githubusercontent.com/Ringmast4r/OUI-Master-Database/main/LISTS/master_oui.txt",

    # Bluetooth SIG Company IDs (oficial)
    "bluetooth_sig": "https://bitbucket.org/bluetooth-SIG/public/raw/HEAD/assigned_numbers/company_identifiers/company_identifiers.yaml",

    # Fast Pair model IDs (lista de SpectrixDev)
    "fast_pair": "https://raw.githubusercontent.com/SpectrixDev/DIY_WhisperPair/main/parser/model_ids.py",

    # BLE-DB (nombres de dispositivos reportados)
    "ble_db": "https://raw.githubusercontent.com/simondankelmann/BLE-DB/main/ble_db.json",

    # Wireshark manuf (respaldo de OUIs)
    "wireshark_manuf": "https://www.wireshark.org/download/automated/data/manuf",
}

def download(url, name):
    dest = CACHE / name
    if dest.exists() and dest.stat().st_size > 100:
        print(f"  [cache] {name}")
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

def parse_oui_master_csv(path):
    """CSV: Registry,Assignment,Organization Name,Organization Address"""
    out = {}
    try:
        with open(path, newline='', encoding='utf-8', errors='ignore') as f:
            reader = csv.DictReader(f)
            for r in reader:
                oui = (r.get('Assignment') or '').strip().upper()
                if len(oui) != 6 or not re.fullmatch(r'[0-9A-F]{6}', oui):
                    continue
                name = (r.get('Organization Name') or '').strip()
                if not name:
                    continue
                out[f"oui:{oui}"] = {"name": name, "type": "manufacturer", "source": "IEEE/OUI-Master"}
    except Exception as e:
        print(f"  WARN OUI CSV: {e}")
    return out

def parse_oui_master_txt(path):
    """TXT: OUI<tab>Manufacturer"""
    out = {}
    try:
        with open(path, encoding='utf-8', errors='ignore') as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith('#'):
                    continue
                parts = line.split('\t')
                if len(parts) < 2:
                    continue
                oui = parts[0].strip().upper().replace(':', '')
                name = parts[1].strip()
                if len(oui) != 6 or not re.fullmatch(r'[0-9A-F]{6}', oui):
                    continue
                if name:
                    out[f"oui:{oui}"] = {"name": name, "type": "manufacturer", "source": "OUI-Master"}
    except Exception as e:
        print(f"  WARN OUI TXT: {e}")
    return out

def parse_bluetooth_sig(path):
    """YAML: - Company: <Nombre>\n  Value: 0x004C"""
    out = {}
    try:
        text = path.read_text(encoding='utf-8', errors='ignore')
        pattern = re.compile(r'-\s*Company:\s*(.+?)\n\s*Value:\s*(0x[0-9A-Fa-f]+|\d+)', re.MULTILINE)
        for m in pattern.finditer(text):
            name = m.group(1).strip().strip('"').strip("'")
            val = m.group(2)
            try:
                cid = int(val, 16) if val.startswith('0x') else int(val)
            except ValueError:
                continue
            out[f"company:{cid:04X}"] = {"name": name, "type": "manufacturer-ble", "source": "Bluetooth SIG"}
    except Exception as e:
        print(f"  WARN Bluetooth SIG: {e}")
    return out

def parse_fast_pair(path):
    """Python: 0xCD8256: 'Sony WF-1000XM4'"""
    out = {}
    try:
        text = path.read_text(encoding='utf-8', errors='ignore')
        pattern = re.compile(r'0x([0-9A-Fa-f]{6})\s*:\s*[\'"]([^\'"]+)[\'"]')
        for m in pattern.finditer(text):
            model_id = m.group(1).upper()
            name = m.group(2).strip()
            out[f"fastpair:{model_id}"] = {
                "name": name, "type": "model", "category": "audio/wearable", "source": "Fast Pair"
            }
    except Exception as e:
        print(f"  WARN Fast Pair: {e}")
    return out

def parse_ble_db(path):
    """JSON: {deviceId: name} o [{id, name}]"""
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
                if len(key) in (6, 8):
                    out[f"bledb:{key}"] = {"name": name, "type": "model", "source": "BLE-DB"}
    except Exception as e:
        print(f"  WARN BLE-DB: {e}")
    return out

# ---------- Curación manual ----------

CURATED = {
    # Fast Pair (IDs de modelo)
    "fastpair:CD8256": ("Sony WF-1000XM4", "audio"),
    "fastpair:0E30C3": ("Sony WH-1000XM5", "audio"),
    "fastpair:821F66": ("Sony LinkBuds S", "audio"),
    "fastpair:F52494": ("JBL Tune Buds", "audio"),
    "fastpair:9D3F8A": ("Anker Soundcore Liberty 4", "audio"),
    "fastpair:0600FC": ("Google Pixel Buds", "audio"),
    "fastpair:D800AA": ("Google Pixel Buds Pro", "audio"),
    "fastpair:30018E": ("Google Pixel Buds Pro 2", "audio"),
    "fastpair:1312F3": ("Samsung Galaxy Buds2 Pro", "audio"),
    "fastpair:00000B": ("Google Gphones", "phone"),
    "fastpair:000047": ("Arduino 101", "dev"),
    "fastpair:0000F0": ("Bose QuietComfort 35 II", "audio"),

    # Rastreadores (por nombre lógico)
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

    # Autos
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
        elif key.startswith(("tracker:", "camera:", "car:")):
            out[key] = {"name": name, "type": "model", "category": cat, "source": "curado"}
    return out

# ---------- Main ----------

def main():
    print("=== Generador de catálogo Fieldwatch ===")
    catalog = {}

    # 1. OUI Master (CSV y TXT como respaldo)
    p = download(SOURCES["oui_master_csv"], "master_oui.csv")
    if p:
        d = parse_oui_master_csv(p)
        catalog.update(d)
        print(f"  OUIs (CSV):       {len(d):>7,}")
    if not catalog:
        p = download(SOURCES["oui_master_txt"], "master_oui.txt")
        if p:
            d = parse_oui_master_txt(p)
            catalog.update(d)
            print(f"  OUIs (TXT):       {len(d):>7,}")

    # 2. Bluetooth SIG
    p = download(SOURCES["bluetooth_sig"], "bluetooth_sig.yaml")
    if p:
        d = parse_bluetooth_sig(p)
        catalog.update(d)
        print(f"  Company IDs BLE:  {len(d):>7,}")

    # 3. Fast Pair
    p = download(SOURCES["fast_pair"], "model_ids.py")
    if p:
        d = parse_fast_pair(p)
        catalog.update(d)
        print(f"  Fast Pair:        {len(d):>7,}")

    # 4. BLE-DB
    p = download(SOURCES["ble_db"], "ble_db.json")
    if p:
        d = parse_ble_db(p)
        catalog.update(d)
        print(f"  BLE-DB:           {len(d):>7,}")

    # 5. Curados
    add_curated(catalog)
    print(f"  Curados:          {len(CURATED):>7,}")

    print(f"\n=== TOTAL: {len(catalog):,} entradas ===")

    # Asegurar directorio de assets
    ASSETS_DIR.mkdir(parents=True, exist_ok=True)

    # Escribir JSON
    with open(OUT, 'w', encoding='utf-8') as f:
        json.dump(catalog, f, ensure_ascii=False, separators=(',', ':'))

    mb = OUT.stat().st_size / 1024 / 1024
    print(f"Guardado: {OUT} ({mb:.1f} MB)")

    # Copiar a assets
    import shutil
    shutil.copy(OUT, ASSETS_DIR / OUT.name)
    print(f"Copiado a: {ASSETS_DIR / OUT.name}")

    # Resumen por tipo
    from collections import Counter
    types = Counter(v.get('type', 'unknown') for v in catalog.values())
    print("\nPor tipo:")
    for t, n in types.most_common():
        print(f"  {t:20s} {n:>7,}")

if __name__ == "__main__":
    main()