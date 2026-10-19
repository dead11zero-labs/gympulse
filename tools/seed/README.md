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
```

- `data/names_de.tsv`: deutsche Namen (`slug<TAB>Name`, dazu `muscle:<key>` und `equipment:<key>`).
- `data/equipment_templates_v1.json`: Gerätevorlagen mit Einstellungs-Schlüsseln.
- Bei inhaltlichen Änderungen `schema_version` in der JSON-Datei erhöhen, sonst importiert die App
  den Bestand nicht erneut. Geänderte Namen der Muskel-/Geräte-Anzeige gehören zusätzlich in
  `core/data/src/main/res/values*/strings_catalog.xml`.
