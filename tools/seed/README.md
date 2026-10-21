# Seed-Daten neu erzeugen

Die Übungsdatenbank (`core/database/src/main/assets/seed/exercises_v1.json`) stammt aus
[free-exercise-db](https://github.com/yuhonas/free-exercise-db) und wird in zwei Schritten erzeugt
(nur Python 3, Standardbibliothek):

```sh
cd tools/seed
python3 build_seed.py /tmp/exercises.json /tmp/exercises_en.json   # ohne Argumente: Download von GitHub
python3 merge_translations.py /tmp/exercises_en.json data/names_de.tsv \
    ../../core/database/src/main/assets/seed/exercises_v1.json
cp data/equipment_templates_v1.json ../../core/database/src/main/assets/seed/
python3 add_machines.py   # Kraftgeräte aus data/kraftgeraete_datenbank.csv ergänzen (idempotent)
```

- `data/names_de.tsv`: deutsche Namen (`slug<TAB>Name`, dazu `muscle:<key>` und `equipment:<key>`).
- `data/equipment_templates_v1.json`: Gerätevorlagen mit Einstellungs-Schlüsseln.
- Bei inhaltlichen Änderungen `schema_version` in der JSON-Datei erhöhen, sonst importiert die App
  den Bestand nicht erneut. Geänderte Namen der Muskel-/Geräte-Anzeige gehören zusätzlich in
  `core/data/src/main/res/values*/strings_catalog.xml`.

## Kraftgeräte (`add_machines.py`)

`data/kraftgeraete_datenbank.csv` (47 Geräte, eigene Inhalte von Jörg, CC0-1.0) wird von `add_machines.py` in
`exercises_v1.json` eingefügt (`source: gympulse`, `license: CC0-1.0`, `source_id: kraftgeraete:<ID>`, Slug aus dem
englischen Namen, `schema_version` 2). Das Skript entfernt vorher alle Einträge mit `source == gympulse` und ist daher
beliebig oft ausführbar. IDs bleiben über `exerciseId(slug)` stabil.

- **Duplikate:** 29 der 47 Geräte gibt es bereits (gleiche Bewegung, gleiches Equipment, z. B. Latzug, Beinstrecker,
  Butterfly, Hack Squat); sie stehen mit Ziel-Slug in `DUPLICATES` im Skript und werden übersprungen. 18 Geräte kommen
  neu hinzu (z. B. 45°-Beinpresse, Pendel-Kniebeuge, Belt Squat, Sitzendes Rudern, Tibialis-Maschine).
- **Gerätetyp:** der erste genannte Typ entscheidet: `selectorized`/`plate_loaded` -> `machine` (WEIGHT_REPS),
  `cable` -> `cable` (WEIGHT_REPS), `bodyweight` -> `bodyweight` (REPS mit `bodyweight_factor`). Keine neuen Equipment-Werte.
- **Muskelgruppen:** deutsche Texte werden per Teilstring auf die vorhandenen Keys abgebildet (Regeltabelle
  `MUSCLE_RULES`; unbekannte Texte brechen das Skript ab):

| Text enthält | Key |
| --- | --- |
| Brust, pectoralis | chest |
| Breiter Rückenmuskel, Latissimus | lats |
| Rautenmuskel, oberer/mittlerer Rücken, Rückenmuskulatur | middle_back |
| Rückenstrecker | lower_back |
| Trapez, Schulterblattheber | traps |
| Deltamuskel, Schulter, Infraspinatus | shoulders |
| Trizeps / Bizeps, Armbeuger | triceps / biceps |
| Unterarm, Griffmuskulatur | forearms |
| Gesäß | glutes |
| Tensor fasciae latae | abductors |
| Quadrizeps | quadriceps |
| Beinbeuger, hintere Oberschenkel | hamstrings |
| Adduktoren, innere Oberschenkel | adductors |
| Wade, Zwillingswaden, Schollenmuskel, Schienbein | calves |
| Bauch, Rumpf | abdominals |
| Sägemuskel, Hüftbeuger, Stabilisatoren, Ganzkörper, Keine | (nicht abgebildet) |

- **Einstellmöglichkeiten** werden nicht übernommen: das Seed-Schema hat kein Notiz-/Beschreibungsfeld (`notes` gehört
  dem Nutzer, `instructions` sind Übungsschritte). Die Texte bleiben in der CSV.
