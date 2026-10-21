# Datenschutzerklärung – GymPulse

Stand: [DATUM] · Version 0.x

> **Entwurf.** Diese Erklärung beschreibt den technischen Stand der App. Sie ist noch nicht rechtlich geprüft und muss vor der Veröffentlichung im Play Store von Jörg rechtlich geprüft und ergänzt werden.

## 1. Verantwortliche Stelle

[ANBIETER – Name und Anschrift von Jörg ergänzen]

Kontakt für alle Fragen zum Datenschutz: [KONTAKT-EMAIL]

## 2. Grundprinzip: alles bleibt auf deinem Gerät

GymPulse ist eine offline-first App. Alle Daten, die du in der App erfasst, werden in einer lokalen Datenbank auf deinem Android-Gerät gespeichert. Die App

- benötigt kein Benutzerkonto,
- überträgt deine Trainingsdaten, Pläne, Übungen, Körpermaße und Einstellungen nicht an einen Server des Anbieters – außer du schaltest das optionale Cloud-Backup ein und löst eine Sicherung selbst aus (Abschnitt 7),
- funktioniert im Training auch ohne Internetverbindung.

## 3. Welche Daten in der App gespeichert werden

Auf deinem Gerät speichert GymPulse abhängig von deiner Nutzung:

- **Profil und Einstellungen:** Name, optional Körpergröße, Körpergewicht, Wochenziel, Wochenstart, Pausentimer, Design und Sprache,
- **Training:** Workouts mit Datum, Dauer, Übungen, Sätzen, Gewichten, Wiederholungen und Notizen,
- **Pläne und Kalender:** Trainingspläne, Wochenplan und Ausnahmen (z. B. verschobene Trainingstage),
- **Bibliothek:** Übungen und Geräte inklusive selbst angelegter Einträge und Einstellungen,
- **Körpermaße:** Gewicht, Körperfett und Umfänge, jeweils mit Datum.

Du entscheidest selbst, welche Angaben du machst. Pflichtfelder sind auf das Nötige beschränkt.

## 4. Internetverbindung und Drittanbieter

- **Übungsbilder:** Wenn du die Detailansicht einer Übung öffnest, lädt die App das zugehörige Demonstrationsbild von einem Server von GitHub (`raw.githubusercontent.com`). Dabei sendet dein Gerät technisch notwendig seine IP-Adresse an GitHub. Für diese Verbindung gilt die Datenschutzerklärung von GitHub. GymPulse selbst erhält von diesen Abrufen keine Daten. Bilder können lokal zwischengespeichert werden.
- Nach dem derzeitigen Stand enthält GymPulse keine Werbe-, Tracking- oder Absturzberichts-Dienste und sendet keine Nutzungsstatistiken.

## 5. Berechtigungen

- **Internet:** zum Laden der Übungsbilder (siehe Abschnitt 4) und, nur wenn du es einschaltest, für Anmeldung und Cloud-Backup (siehe Abschnitt 7).
- **Benachrichtigungen:** zeigen während eines laufenden Workouts eine Benachrichtigung an, damit die Sitzung nicht unterbrochen wird.
- **Vordergrunddienst:** hält das laufende Workout und den Pausentimer aktiv, solange du trainierst.

## 6. Export und Import

Mit der Funktion „Export und Import“ kannst du deine Daten als JSON-Datei sichern und wieder einlesen. Den Speicherort wählst du über die Dateiauswahl von Android. Die Datei verlässt das Gerät nur, wenn du sie selbst weitergibst oder an einem Ort speicherst, auf den andere Zugriff haben.

## 7. Cloud-Backup (optional, Opt-in)

Das Cloud-Backup ist standardmäßig **ausgeschaltet**. Es wird erst aktiv, wenn du es in den Einstellungen unter „Cloud-Backup“ selbst einschaltest und dich mit deinem Google-Konto anmeldest. Ohne diese Schritte überträgt GymPulse keine Daten an einen Server.

- **Anmeldung:** Die Anmeldung läuft über „Mit Google anmelden“ (Android Credential Manager). Google übermittelt dabei ein Anmelde-Token mit deiner Google-Konto-ID, deiner E-Mail-Adresse sowie gegebenenfalls Name und Profilbild an unseren Backend-Dienst Supabase. Dort wird ein Benutzerkonto mit dieser E-Mail-Adresse und einer zufälligen Nutzer-ID angelegt.
- **Was gesichert wird:** Wenn du „Jetzt sichern“ antippst, lädt die App dieselbe JSON-Datei hoch, die auch der Export erzeugt (Abschnitt 6): Profil und Einstellungen, Workouts, Pläne, Kalender, eigene Übungen und Geräte sowie Körpermaße. Pro Konto wird nur die jeweils letzte Sicherung gespeichert, zusammen mit dem Zeitpunkt des Hochladens. Fortschrittsfotos werden nicht hochgeladen.
- **Kein automatischer Upload:** Gesichert wird nur, wenn du es auslöst. „Wiederherstellen“ lädt die Sicherung nach einer Bestätigung zurück auf dein Gerät.
- **Zugriffsschutz:** Die Übertragung ist per HTTPS verschlüsselt. Eine Zugriffsregel in der Datenbank (Row Level Security) stellt sicher, dass nur dein angemeldetes Konto deine Sicherung lesen, ändern oder löschen kann.
- **Dienstleister:** Die Daten werden bei Supabase (Supabase Inc.) gespeichert, die Anmeldung erfolgt über Google (Google Ireland Limited). [Von Jörg zu prüfen: Region des Supabase-Projekts, Auftragsverarbeitungsvertrag, Rechtsgrundlage (Einwilligung, Art. 6 Abs. 1 lit. a DSGVO bzw. Art. 9 Abs. 2 lit. a DSGVO für Gesundheitsdaten wie Körpermaße) und Hinweise zu Übermittlungen in Drittländer.]
- **Widerruf und Löschung:** Du kannst die Sicherung jederzeit in den Einstellungen mit „Cloud-Sicherung löschen“ entfernen, dich abmelden und das Cloud-Backup ausschalten. Ausschalten oder Abmelden löscht eine vorhandene Sicherung nicht automatisch.

## 8. Android-Systemsicherung

GymPulse ist für die Android-Systemsicherung konfiguriert. Wenn du in den Android-Einstellungen eine Sicherung deiner App-Daten aktiviert hast (z. B. über dein Google-Konto), kann Android die Daten der App nach den Regeln des Betriebssystems sichern. Diese Sicherung wird von Android bzw. Google durchgeführt, nicht von GymPulse. [Von Jörg zu prüfen: Sicherungsregeln festlegen oder bewusst zulassen.]

## 9. Keine Weitergabe, keine Werbung

GymPulse verkauft keine Daten, gibt keine Daten an Dritte weiter und zeigt keine Werbung. Ausgenommen sind der technisch notwendige Abruf der Übungsbilder bei GitHub (Abschnitt 4) sowie – nur bei eingeschaltetem Cloud-Backup – Google als Anmeldedienst und Supabase als Hosting-Dienstleister (Abschnitt 7).

## 10. Geplante Funktionen (noch nicht verfügbar)

Die folgenden Funktionen sind geplant, aber in der aktuellen Version **nicht** enthalten. Sobald sie veröffentlicht werden, wird diese Erklärung ergänzt. Sie sind optional und werden erst nach deiner Zustimmung aktiviert.

- **Health Connect:** Workouts an Health Connect schreiben und Körperdaten (z. B. Gewicht, Körperfett, Größe) von dort lesen. Jede Berechtigung kann einzeln erteilt und widerrufen werden. Samsung Health soll über Health Connect angebunden werden.
- **Automatische Synchronisation:** fortlaufender Abgleich der Daten zwischen mehreren Geräten. Das heutige Cloud-Backup (Abschnitt 7) arbeitet nur manuell.

## 11. Speicherdauer und Löschung

Deine Daten bleiben auf deinem Gerät, bis du sie in der App löschst (einzelne Workouts, Übungen, Pläne oder Messwerte). Eine Cloud-Sicherung bleibt gespeichert, bis du sie in den Einstellungen mit „Cloud-Sicherung löschen“ entfernst oder durch eine neue Sicherung ersetzt; zur Löschung deines Kontos wende dich an [KONTAKT-EMAIL]. Wenn du GymPulse deinstallierst, werden die lokal gespeicherten Daten der App entfernt. Eine eventuelle Sicherung durch Android (Abschnitt 8) bleibt davon unberührt und ist in den Android-Einstellungen zu verwalten.

## 12. Deine Rechte

Da der Anbieter deine Trainingsdaten nicht erhält, liegen sie vollständig bei dir. Wenn du dennoch Fragen zum Datenschutz hast oder ein Anliegen zu Daten hast, die du an uns übermittelt hast (z. B. per E-Mail), wende dich an [KONTAKT-EMAIL]. Dir stehen die gesetzlichen Rechte auf Auskunft, Berichtigung, Löschung, Einschränkung und Widerspruch sowie das Beschwerderecht bei einer Datenschutzaufsichtsbehörde zu. [Von Jörg zu prüfen: Formulierung und Umfang.]

## 13. Kinder

[Von Jörg zu prüfen: Hinweis zur Nutzung durch Minderjährige.]

## 14. Änderungen dieser Erklärung

Wenn sich die Funktionen oder die Datenverarbeitung ändern, wird diese Erklärung angepasst. Das Datum oben zeigt den Stand an.
