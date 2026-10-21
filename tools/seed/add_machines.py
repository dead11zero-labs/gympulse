#!/usr/bin/env python3
"""Ergaenzt die Kraftgeraete von Joerg (data/kraftgeraete_datenbank.csv) in der Seed-Datei.

Aufruf (nach merge_translations.py, siehe README):
    python3 add_machines.py [exercises_v1.json] [kraftgeraete_datenbank.csv]

Standardpfade: ../../core/database/src/main/assets/seed/exercises_v1.json und
data/kraftgeraete_datenbank.csv. Das Skript ist idempotent: vorhandene Eintraege mit
source == "gympulse" werden vor dem Einfuegen entfernt. Setzt schema_version auf SCHEMA_VERSION.
Nur Python 3, Standardbibliothek.
"""
import csv
import json
import os
import re
import sys

SCHEMA_VERSION = 2
SOURCE = "gympulse"
LICENSE = "CC0-1.0"

# CSV-ID -> bestehender Slug: Geraet ist bereits (gleiche Bewegung, gleiches Equipment) vorhanden.
DUPLICATES = {
    1: "leverage_chest_press",
    2: "leverage_incline_chest_press",
    3: "leverage_decline_chest_press",
    4: "butterfly",
    6: "machine_shoulder_military_press",
    8: "wide_grip_lat_pulldown",
    10: "leverage_high_row",
    12: "lying_t_bar_row",
    13: "reverse_machine_flyes",
    16: "hyperextensions_back_extensions",
    17: "leverage_shrug",
    18: "machine_preacher_curls",
    19: "dip_machine",
    20: "machine_triceps_extension",
    22: "leg_press",
    24: "hack_squat",
    29: "leg_extensions",
    30: "seated_leg_curl",
    31: "lying_leg_curls",
    32: "standing_leg_curl",
    33: "thigh_adductor",
    34: "thigh_abductor",
    37: "standing_calf_raises",
    38: "seated_calf_raise",
    39: "donkey_calf_raises",
    41: "ab_crunch_machine",
    45: "smith_machine_squat",
    46: "cable_crossover",
    47: "seated_cable_rows",
}

# Deutscher Muskeltext (Teilstring, kleingeschrieben) -> Muskel-Key; erste Regel gewinnt.
# None = bewusst nicht abgebildet (kein passender Key im Enum).
MUSCLE_RULES = [
    ("serratus", None),
    ("sägemuskel", None),
    ("hüftbeuger", None),
    ("stabilisator", None),
    ("keine", None),
    ("ganzkörper", None),
    ("tensor fasciae", "abductors"),
    ("gesäß", "glutes"),
    ("breiter rückenmuskel", "lats"),
    ("latissimus", "lats"),
    ("brust", "chest"),
    ("pectoralis", "chest"),
    ("rautenmuskel", "middle_back"),
    ("obere rückenmuskulatur", "middle_back"),
    ("mittlerer rücken", "middle_back"),
    ("rückenstrecker", "lower_back"),
    ("rückenmuskulatur", "middle_back"),
    ("trapez", "traps"),
    ("schulterblattheber", "traps"),
    ("infraspinatus", "shoulders"),
    ("deltamuskel", "shoulders"),
    ("schulter", "shoulders"),
    ("trizeps", "triceps"),
    ("bizeps", "biceps"),
    ("unterarm", "forearms"),
    ("armbeuger", "biceps"),
    ("griffmuskulatur", "forearms"),
    ("quadrizeps", "quadriceps"),
    ("beinbeuger", "hamstrings"),
    ("hintere oberschenkel", "hamstrings"),
    ("adduktoren", "adductors"),
    ("innere oberschenkel", "adductors"),
    ("wade", "calves"),
    ("zwillingswaden", "calves"),
    ("schollenmuskel", "calves"),
    ("schienbein", "calves"),
    ("bauch", "abdominals"),
    ("rumpf", "abdominals"),
]

# Bodyweight-Geraete: Anteil des Koerpergewichts (wie in build_seed.py, REPS-Tracking).
BODYWEIGHT_FACTOR = {"sissy_squat_bench": 0.7, "captains_chair": 0.35}


def slugify(s):
    return re.sub(r"[^a-z0-9]+", "_", s.lower().replace("'", "")).strip("_")


def split_top_level(text):
    """Teilt an Kommas ausserhalb von Klammern."""
    parts, depth, cur = [], 0, ""
    for ch in text:
        depth += (ch == "(") - (ch == ")")
        if ch == "," and depth == 0:
            parts.append(cur)
            cur = ""
        else:
            cur += ch
    parts.append(cur)
    return [p.strip() for p in parts if p.strip()]


def map_muscles(text, valid):
    keys = []
    for part in split_top_level(text):
        low = part.lower()
        for needle, key in MUSCLE_RULES:
            if needle in low:
                if key is not None and key not in keys:
                    keys.append(key)
                break
        else:
            raise SystemExit("Muskeltext nicht abgebildet: %r" % part)
    for k in keys:
        if k not in valid:
            raise SystemExit("Unbekannter Muskel-Key: " + k)
    return keys


def equipment_and_tracking(machine_type, slug):
    """Der erste genannte Geraetetyp entscheidet."""
    first = machine_type.split("/")[0].strip()
    if first in ("selectorized", "plate_loaded"):
        return "machine", "WEIGHT_REPS", None
    if first == "cable":
        return "cable", "WEIGHT_REPS", None
    if first == "bodyweight":
        return "bodyweight", "REPS", BODYWEIGHT_FACTOR[slug]
    raise SystemExit("Unbekannter Geraetetyp: " + machine_type)


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    seed_path = sys.argv[1] if len(sys.argv) > 1 else os.path.normpath(
        os.path.join(here, "..", "..", "core", "database", "src", "main", "assets", "seed", "exercises_v1.json"))
    csv_path = sys.argv[2] if len(sys.argv) > 2 else os.path.join(here, "data", "kraftgeraete_datenbank.csv")

    with open(seed_path, encoding="utf-8") as f:
        data = json.load(f)
    data["exercises"] = [e for e in data["exercises"] if e.get("source") != SOURCE]
    existing = {e["slug"] for e in data["exercises"]}
    valid_muscles = {m["key"] for m in data["muscle_groups"]}
    for target in DUPLICATES.values():
        if target not in existing:
            raise SystemExit("Duplikat-Ziel fehlt: " + target)

    added, skipped = [], []
    with open(csv_path, encoding="utf-8-sig", newline="") as f:
        for row in csv.DictReader(f):
            rid = int(row["ID"])
            if rid in DUPLICATES:
                skipped.append((rid, row["Geraetename_EN"], DUPLICATES[rid]))
                continue
            name_en = row["Geraetename_EN"].strip()
            slug = slugify(name_en.split(" / ")[0])
            if slug in existing or slug in {e["slug"] for e in added}:
                raise SystemExit("Slug-Kollision: " + slug)
            equipment, tracking, factor = equipment_and_tracking(row["Geraetetyp"], slug)
            primary = map_muscles(row["Primaere_Muskelgruppen"], valid_muscles)
            secondary = [m for m in map_muscles(row["Sekundaere_Muskelgruppen"], valid_muscles) if m not in primary]
            if not primary:
                raise SystemExit("Keine primaere Muskelgruppe: " + name_en)
            added.append({
                "slug": slug,
                "source_id": "kraftgeraete:%d" % rid,
                "name": {"en": name_en, "de": row["Geraetename_DE"].strip()},
                "category": "strength",
                "equipment_type": equipment,
                "tracking_type": tracking,
                "bodyweight_factor": factor,
                "level": None,
                "mechanic": None,
                "force": None,
                "primary_muscles": primary,
                "secondary_muscles": secondary,
                "instructions": {"en": []},
                "images": [],
                "source": SOURCE,
                "license": LICENSE,
            })

    data["exercises"] = sorted(data["exercises"] + added, key=lambda x: x["slug"])
    data["schema_version"] = SCHEMA_VERSION
    with open(seed_path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    print("hinzugefuegt: %d, uebersprungen (Duplikate): %d, gesamt: %d" % (len(added), len(skipped), len(data["exercises"])))
    for rid, en, target in skipped:
        print("  skip %2d %-45s -> %s" % (rid, en, target))


if __name__ == "__main__":
    main()
