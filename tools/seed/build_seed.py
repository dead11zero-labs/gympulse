#!/usr/bin/env python3
"""Convert free-exercise-db (Unlicense) into GymPulse's seed format.

Usage:
    python3 build_seed.py <input.json> <output.json>
    python3 build_seed.py            # downloads the input from GitHub

Defaults when arguments are omitted: input is downloaded from
https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json
and output is ../../seed-data/exercises_v1.json (relative to this script).

Also writes names_en.tsv next to the output file (slug<TAB>english name),
including "muscle:<key>" and "equipment:<key>" rows, for later translation.

Deterministic: exercises are sorted by slug, no timestamps, stable key order.
Python 3 standard library only.
"""
import json
import os
import re
import sys
import urllib.request

SRC_URL = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json"
IMG_PREFIX = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"

EQUIPMENT = {  # source value -> (key, English name)
    "barbell": ("barbell", "Barbell"),
    "dumbbell": ("dumbbell", "Dumbbell"),
    "cable": ("cable", "Cable"),
    "machine": ("machine", "Machine"),
    "kettlebells": ("kettlebell", "Kettlebell"),
    "bands": ("band", "Band"),
    "medicine ball": ("medicine_ball", "Medicine Ball"),
    "exercise ball": ("exercise_ball", "Exercise Ball"),
    "foam roll": ("foam_roll", "Foam Roll"),
    "e-z curl bar": ("ez_bar", "EZ Bar"),
    "body only": ("bodyweight", "Bodyweight"),
    "other": ("other", "Other"),
    None: ("other", "Other"),
}

HOLD_RE = re.compile(r"\b(plank|planks|hold|wall sit)\b")
DISTANCE_RE = re.compile(r"\b(run|running|sprint|sprints|jog|jogging|walk|walking|bike|biking|bicycling|cycling|stairmaster|rowing|row machine|treadmill)\b")


def slugify(s):
    return re.sub(r"[^a-z0-9]+", "_", s.lower()).strip("_")


def norm(name):
    return re.sub(r"\s+", " ", name.lower().replace("-", " ")).strip()


def tracking_type(e):
    n = norm(e["name"])
    if e["category"] == "stretching" or HOLD_RE.search(n):
        return "DURATION"
    if e["category"] == "cardio":
        return "DISTANCE" if DISTANCE_RE.search(n) else "DURATION"
    if e["equipment"] == "body only" and e["category"] in ("strength", "plyometrics"):
        return "REPS"
    return "WEIGHT_REPS"


# Ordered: first match wins.
FACTOR_RULES = [
    (r"\bpistol", 0.9),
    (r"\b(pull ?ups?|chin ?ups?|muscle ?ups?|dips?)\b", 1.0),
    (r"\b(knee|incline) push ?ups?\b", 0.5),
    (r"\bdecline push ?ups?\b", 0.75),
    (r"\bpush ?ups?\b", 0.65),
    (r"\binverted row", 0.6),
    (r"\b(squat|squats|lunge|lunges)\b", 0.7),
    (r"\b(sit ?ups?|crunch|crunches)\b", 0.3),
    (r"\bleg raises?\b", 0.35),
    (r"\b(glute bridge|hip thrust)", 0.5),
]


def bodyweight_factor(e):
    if e["equipment"] != "body only" or e["category"] != "strength":
        return None
    n = norm(e["name"])
    for pat, f in FACTOR_RULES:
        if re.search(pat, n):
            return f
    return 0.5


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    args = sys.argv[1:]
    out_path = args[1] if len(args) > 1 else os.path.normpath(
        os.path.join(here, "..", "..", "seed-data", "exercises_v1.json"))
    if args:
        with open(args[0], encoding="utf-8") as f:
            data = json.load(f)
    else:
        with urllib.request.urlopen(SRC_URL) as r:
            data = json.loads(r.read().decode("utf-8"))

    muscles = set()
    used = set()
    exercises = []
    for e in sorted(data, key=lambda x: x["id"]):
        base = slugify(e["id"]) or slugify(e["name"])
        slug, i = base, 2
        while slug in used:
            slug = "%s_%d" % (base, i)
            i += 1
        used.add(slug)
        prim = [slugify(m) for m in e.get("primaryMuscles") or []]
        sec = [slugify(m) for m in e.get("secondaryMuscles") or []]
        muscles.update(prim + sec)
        exercises.append({
            "slug": slug,
            "source_id": e["id"],
            "name": {"en": e["name"]},
            "category": slugify(e["category"]),
            "equipment_type": EQUIPMENT[e.get("equipment")][0],
            "tracking_type": tracking_type(e),
            "bodyweight_factor": bodyweight_factor(e),
            "level": e.get("level"),
            "mechanic": e.get("mechanic"),
            "force": e.get("force"),
            "primary_muscles": prim,
            "secondary_muscles": sec,
            "instructions": {"en": list(e.get("instructions") or [])},
            "images": [IMG_PREFIX + p for p in e.get("images") or []],
            "source": "free-exercise-db",
            "license": "Unlicense",
        })
    exercises.sort(key=lambda x: x["slug"])

    muscle_groups = [{"key": m, "name": {"en": m.replace("_", " ").title()}} for m in sorted(muscles)]
    seen, equipment_types = set(), []
    for key, name in EQUIPMENT.values():
        if key not in seen:
            seen.add(key)
            equipment_types.append({"key": key, "name": {"en": name}})
    equipment_types.sort(key=lambda x: x["key"])

    out = {
        "schema_version": 1,
        "source": {"name": "free-exercise-db",
                   "url": "https://github.com/yuhonas/free-exercise-db",
                   "license": "Unlicense"},
        "muscle_groups": muscle_groups,
        "equipment_types": equipment_types,
        "exercises": exercises,
    }
    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
        f.write("\n")

    tsv = os.path.join(os.path.dirname(os.path.abspath(out_path)), "names_en.tsv")
    with open(tsv, "w", encoding="utf-8", newline="\n") as f:
        for m in muscle_groups:
            f.write("muscle:%s\t%s\n" % (m["key"], m["name"]["en"]))
        for q in equipment_types:
            f.write("equipment:%s\t%s\n" % (q["key"], q["name"]["en"]))
        for x in exercises:
            f.write("%s\t%s\n" % (x["slug"], x["name"]["en"].replace("\t", " ").replace("\n", " ")))
    print("wrote %d exercises to %s" % (len(exercises), out_path))


if __name__ == "__main__":
    main()
