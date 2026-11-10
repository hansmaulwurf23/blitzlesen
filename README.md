# Lesen

Blitzlesen für Kinder: Wörter als zufällige Karteikarten, zuerst die 100 häufigsten Wörter aus dem
Berliner Grundwortschatz. Eine Runde dauert eine Minute; nach jeder Karte tippt man **grün** („konnte
ich schon lesen“) oder **gelb** („hakte noch etwas“). Daraus entstehen die zwei Stapel, dazu kommen
die Lesezeit je Wort und eine Bestenliste.

**In erster Linie eine Web-App:** <https://hansmaulwurf23.github.io/blitzlesen/> — ohne Anmeldung,
alles bleibt im Browser, nach dem ersten Besuch auch offline, und auf dem Handy über *Zum
Startbildschirm hinzufügen* bzw. *App installieren* wie eine App. Daneben gibt es weiterhin die
Android-App (siehe unten), mit denselben Regeln, aber nur der einen Wortliste.

Eine bebilderte Kurzbeschreibung steht in [`docs/README.md`](docs/README.md).

## Bedienung

- **Startbildschirm:** die gewählte Wortliste, die beiden Stapel *Kann ich* (grün) und *Hakt noch*
  (gelb) mit der Anzahl Wörter, darunter, wie viele Wörter noch nie dran waren, und der Rekord. Ein
  Tipp auf einen Stapel zeigt seine Wörter, ein Tipp auf den Rekord die Bestenliste, ein Tipp auf
  die Wortliste wechselt sie (nur Web).
- **Runde:** nach einem Countdown „3, 2, 1, Los!“ kommt eine Karte nach der anderen. Grün schickt
  die Karte nach links auf den grünen Stapel, gelb nach rechts auf den gelben; die nächste springt
  sofort herein. Ist die Minute um, darf die offene Karte noch zu Ende gelesen und bewertet werden.
  Abbrechen über ✕ oder Zurück — eine angefangene Runde zählt nicht und ändert keinen Stapel. Im
  Browser gehen auch die Tasten ← bzw. 1 (grün), → bzw. 2 (gelb) und Escape (abbrechen); der
  Bildschirm bleibt während der Runde an, wo der Browser das erlaubt.
- **Zeit:** gemessen wird je Karte vom Aufdecken bis zum Tippen. Pausen (Bildschirm aus, App oder
  Tab gewechselt, Abbrechen-Dialog) zählen nicht; danach geht es mit einem neuen Countdown weiter,
  die Karte ist solange verdeckt.
- **Welche Karte kommt:** zufällig, aber gelbe und neue Wörter dreimal so oft wie grüne. Innerhalb
  einer Runde wiederholt sich kein Wort, bevor alle Wörter der Liste dran waren, und nie zweimal
  hintereinander.
- **Stapel:** jedes Wort liegt auf dem Stapel seiner **letzten** Bewertung. Ein grünes Wort, das
  später hakt, wandert also zurück auf Gelb, und umgekehrt. Das gilt über alle Wortlisten hinweg:
  Steht „und“ in zwei Listen, ist es dasselbe Wort.
- **Ergebnis:** Wörter in der Minute, davon grün und gelb, Platz in der Bestenliste, ggf. *Neuer
  Rekord!*, das schnellste grüne Wort und alle Karten der Runde mit ihrer Lesezeit.
- **Bestenliste** (Pokal-Symbol): alle Runden **der gewählten Wortliste** nach Anzahl Wörtern, bei
  Gleichstand nach grünen, dann die frühere zuerst. Die gerade gespielte Runde ist hervorgehoben.
  Rekord und Platz zählen ebenfalls je Liste, weil Listen unterschiedlich schwer sind.
- **Wörter** (Menü oder Tipp auf einen Stapel): die Wörter eines Stapels mit dem Durchschnitt der
  letzten fünf Lesezeiten und wie oft sie dran waren; ein Tipp auf ein Wort zeigt die Einzelheiten.
- **Design:** standardmäßig dunkel, *Menü → Dunkles Design* schaltet auf hell um (bleibt gespeichert).

## Web-App

Liegt in [`web/`](web/): eine einzige Seite aus HTML, CSS und JavaScript (ES-Module), ohne Framework
und ohne Build-Schritt.

- `lesen.js`: Runde, Kartenziehen, Stapel, Bestenliste und das JSON-Format — dieselben Regeln wie
  `game/Round`, `words/Picker` und `stats/History` der Android-App, ohne DOM.
- `app.js`: die Bildschirme. Navigation über `#/`, `#/runde`, `#/ergebnis/<id>`,
  `#/bestenliste`, `#/woerter/<stapel>`, damit Zurück im Browser und die Android-Zurück-Geste wie
  in der App funktionieren.
- `sw.js`: Service Worker. Lädt beim ersten Besuch alles inklusive der Wortlisten vor und fragt
  danach zuerst das Netz (neue Versionen und Listen kommen sofort an), ohne Netz oder nach 3 s den
  Cache.
- `listen/`: die Wortlisten, siehe unten.

**Gespeichert** wird nur im `localStorage` des Browsers: `lesen.runden` (alle Runden, im selben
Format wie der Export unten), `lesen.liste` (gewählte Liste) und `lesen.design`. Es gibt keinen
Server, kein Konto und keine Analyse. Nach der ersten Runde bittet die Seite den Browser, die Daten
dauerhaft aufzuheben (`navigator.storage.persist()`). Trotzdem gilt: Browserdaten löschen löscht
auch die Runden, und jeder Browser und jedes Gerät hat seine eigenen. Für Sicherung und
Gerätewechsel gibt es Export und Import im Menü; darauf weist auch ein Hinweis unter der Bestenliste
hin, mit einem Knopf zum Sichern. Auf dem iPhone sollte die Seite über *Teilen → Zum
Home-Bildschirm* installiert werden, sonst räumt Safari nach einigen Wochen ohne Besuch auf.

Lokal ausprobieren und testen (Node 22 oder neuer):

```bash
cd web && python3 -m http.server 8000     # dann http://localhost:8000
node --test web/test/*.test.mjs
```

Die Tests prüfen auch, dass jede Liste gültig ist und dass die 100 Wörter mit `words/Words.java`
der Android-App übereinstimmen.

**Veröffentlichung:** `.github/workflows/pages.yml` testet und veröffentlicht `web/` bei jedem Push
auf `main`, der `web/` betrifft, auf GitHub Pages. Dabei wird `__VERSION__` in `app.js` und `sw.js`
durch die Commit-Kennung ersetzt; der About-Dialog zeigt sie, und der Service Worker legt damit
einen frischen Cache an. In den Repo-Einstellungen muss unter *Pages* als Quelle *GitHub Actions*
eingestellt sein.

## Fortschritt exportieren und importieren

Für Sicherung und Gerätewechsel: *Menü → Fortschritt exportieren* speichert eine JSON-Datei (im
Browser als Download, in der App über den Android-Dateidialog), auf dem anderen Gerät
*importieren*. Web und Android-App verwenden **dasselbe Format**, der Fortschritt lässt sich also
auch von der App ins Web umziehen und zurück. Gespeichert sind nur die Runden. Stapel, Lesezeiten
und Bestenliste werden daraus berechnet und stimmen nach dem Import also auch. Der Import
**ergänzt**: schon bekannte Runden (gleiche `id`) werden übersprungen.

```json
{
  "format": "lesen-runden",
  "version": 1,
  "rounds": [
    {"id": "b0fa3f98-…", "timestamp": 1794126000000, "seconds": 60, "list": "grundwortschatz-100",
     "attempts": [{"word": "und", "millis": 840, "good": true},
                  {"word": "welche", "millis": 2310, "good": false}]}
  ]
}
```

`list` schreibt nur die Web-App; fehlt es (Runden aus der Android-App), gilt
`grundwortschatz-100`. Die Android-App liest Dateien mit `list` ohne Fehler, kennt aber keine
Listen: Runden anderer Listen landen dort in ihrer einen Bestenliste, und beim nächsten Export aus
der App fehlt ihr `list`. Solange es nur eine Liste gibt, ist das egal; mit mehreren sollte der
Fortschritt nur noch vom Web in die App gehen, nicht zurück.

In der App liegt das im selben Format in `files/rounds.json` (privater App-Speicher), im Browser
unter `lesen.runden` im `localStorage`. Das Wort selbst ist die Kennung; Wörter, die keine Liste mehr
kennt, bleiben gespeichert, zählen aber für keinen Stapel.

## Wortlisten

Die erste Liste, `web/listen/grundwortschatz-100.json`, enthält die 100 Häufigkeitswörter aus
*Grundwortschatz für Berlin – Planungshilfe für den Rechtschreibunterricht* (Fassung 2025,
S. 22–23), alphabetisch, „Zeit“ als Nomen groß. Die Auswahl und die Idee mit den zwei Stapeln
stammen aus der Wortkartei „Blitzlesen – 100 häufige Wörter“ von Die Lernheldin (@die.lernheldin);
darauf verweist auch der About-Dialog. Die Android-App hat dieselben Wörter fest in `words/Words`.

**Neue Liste hinzufügen:** eine Datei `web/listen/<id>.json` anlegen und die `id` in
`web/listen/index.json` eintragen (die Reihenfolge dort ist die Reihenfolge in der Auswahl). Mit
mehr als einer Liste zeigt die Web-App den Umschalter mit Pfeil und *Menü → Wortliste wechseln*.

```json
{
  "id": "beispiel-liste",
  "title": "Kurzer Name für die Auswahl",
  "description": "Ein Satz, woher die Wörter kommen",
  "source": "Quelle ausführlich",
  "words": ["Wort", "noch ein Wort"]
}
```

- Die `id` (Kleinbuchstaben, Ziffern, Bindestriche) **nie ändern**: Runden merken sich ihre Liste
  über die `id`.
- Ein Wort umzubenennen (auch nur Groß-/Kleinschreibung) trennt es von seinem bisherigen Verlauf.
  Wörter hinzufügen oder entfernen ist unproblematisch.
- `node --test web/test/*.test.mjs` prüft, dass jede Datei im Index steht, `id` und Dateiname
  zusammenpassen und kein Wort doppelt vorkommt.

## Android-App

Die ursprüngliche App, weiterhin gepflegt und mit Selbst-Update, aber nur mit der einen Wortliste. Der
About-Dialog verlinkt die Web-App.

- Java, Android Views (kein Compose), Material 3, minSdk 26 / targetSdk 34. Keine
  Google-Play-Dienste.
- `game/Round`, `words/` und `stats/History` haben keine Android-Abhängigkeiten und sind in
  `RoundTest`, `PickerTest` und `HistoryTest` getestet.
- Hochformat fest.

### Bauen und installieren

Gebraucht wird ein **JDK 17** (das JBR aus Android Studio ist zu neu):

```bash
JAVA_HOME=~/.jdks/jdk-17.0.20.1+1 ./gradlew testDebugUnitTest assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Auf die Handys gehört das **signierte Release-APK**, nicht das Debug-APK: nur dann passen spätere
Selbst-Updates darüber. Ein Debug-Build müsste vorher deinstalliert werden, und damit wären die Runden
weg.

### Selbst-Update

`update/AppUpdater`: beim Start höchstens alle 6 Stunden und über *Menü → Nach
Update suchen* fragt die App das neueste Release bei der GitHub-API ab
(`/repos/hansmaulwurf23/blitzlesen/releases/latest`), lädt das APK im Hintergrund, prüft die SHA-256
aus der angehängten `update.json` und bietet die Installation an. **Runden und Stapel bleiben
erhalten**, solange derselbe Signaturschlüssel verwendet wird und `versionCode` mit jedem Release
steigt.

Die API-Adresse steht in `app/build.gradle` (`UPDATE_API`). Ein Umbenennen des Repos leitet GitHub
weiter; wird es gelöscht oder privat, finden ausgelieferte Apps keine Updates mehr.

Bis Version 1.2 lag das Projekt auf gitlab.noisebleed.de (`pub/lesen`, Projekt-ID 63), und die Apps
fragten dort. Version 1.3 ist deshalb zusätzlich noch dort erschienen: Wer sie installiert, sucht ab
dann auf GitHub. Das GitLab-Projekt ist archiviert, seine Releases bleiben abrufbar.

### Download

Signierte APKs hängen an den [Releases](https://github.com/hansmaulwurf23/blitzlesen/releases).
Zum Installieren die Datei auf dem Handy öffnen. Android fragt einmalig nach der Erlaubnis, Apps aus
dieser Quelle zu installieren (bei Samsung pro App, z. B. für *Chrome* oder *Eigene Dateien*).

### Release

Signaturschlüssel und `keystore.properties` liegen **nicht im Repo**, sondern in
`~/Documents/Android/own/lesen/` (abweichender Pfad über `-Plesen.signing=…`).
Fehlt der Schlüssel, bleibt das Release-APK unsigniert.

Ohne diesen Schlüssel lässt sich kein Update über eine installierte Version schieben — Verlust
bedeutet, dass alle Nutzer erst deinstallieren (und den Fortschritt vorher exportieren) müssten.

`.github/workflows/release.yml` testet, baut, signiert und veröffentlicht, sobald ein Tag `v<Ziffer>`
gepusht wird, und hängt `update.json` neben das APK. Der Text des annotierten Tags (ohne die erste
Zeile) wird zur Release-Beschreibung. Zwei Prüfungen brechen absichtlich ab, statt ein kaputtes
Release zu veröffentlichen: ein unsigniertes APK und ein `versionName`, der nicht zum Tag passt.

Die Repository-Secrets `ANDROID_KEYSTORE_B64` (`base64 -w0 lesen-release.jks`),
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` (`lesen`) und `ANDROID_KEY_PASSWORD` sind gesetzt;
damit liegt eine zweite Kopie des Schlüssels bei GitHub.

```bash
# versionCode und versionName in app/build.gradle anheben, committen
git tag -a v1.4 -m "Lesen 1.4" -m "- Was sich geändert hat" && git push origin main v1.4
```

## Lizenz

Der Code steht unter der [MIT-Lizenz](LICENSE). Idee, Wortauswahl und Übungsweise stammen aus der
Wortkartei „Blitzlesen – 100 häufige Wörter“ von Die Lernheldin (@die.lernheldin), die
Veröffentlichung hier geschieht mit ihrem Einverständnis.
