#!/usr/bin/env python3
"""Fuehrt die deutschen Namen (names_de.tsv) in die Seed-Daten zusammen.

Aufruf:
    python3 merge_translations.py <exercises_v1.json> <names_de.tsv> <ausgabe.json>

Zeilen "slug<TAB>Name" werden zu name.de der Uebung; "muscle:<key>" und
"equipment:<key>" zu name.de der Muskelgruppen bzw. Geraetetypen.
Die Ausgabe ist kompaktes UTF-8-JSON ohne Einrueckung.
"""
import json
import sys


def main(src, tsv, dst):
    with open(src, encoding="utf-8") as f:
        data = json.load(f)
    de = {}
    with open(tsv, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            key, name = line.split("\t", 1)
            de[key] = name.strip()

    missing = []
    for e in data["exercises"]:
        if e["slug"] in de:
            e["name"]["de"] = de[e["slug"]]
        else:
            missing.append(e["slug"])
    for m in data["muscle_groups"]:
        m["name"]["de"] = de["muscle:" + m["key"]]
    for t in data["equipment_types"]:
        t["name"]["de"] = de["equipment:" + t["key"]]

    if missing:
        print("Ohne deutschen Namen: %d (z. B. %s)" % (len(missing), missing[:3]), file=sys.stderr)
    with open(dst, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    print("geschrieben:", dst)


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(*sys.argv[1:])
