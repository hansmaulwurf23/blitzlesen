# Lesen

Blitzlesen für Kinder auf Android: die 100 häufigsten Wörter aus dem Berliner Grundwortschatz als
zufällige Karteikarten. Eine Runde dauert eine Minute; nach jeder Karte tippt man **grün** („konnte
ich schon lesen“) oder **gelb** („hakte noch etwas“). Daraus entstehen die zwei Stapel, dazu kommen
die Lesezeit je Wort und eine Bestenliste.

Eine bebilderte Kurzbeschreibung steht in [`docs/README.md`](docs/README.md).

## Bedienung

- **Startbildschirm:** die beiden Stapel *Kann ich* (grün) und *Hakt noch* (gelb) mit der Anzahl
  Wörter, darunter, wie viele Wörter noch nie dran waren, und der Rekord. Ein Tipp auf einen Stapel
  zeigt seine Wörter, ein Tipp auf den Rekord die Bestenliste.
- **Runde:** nach einem Countdown „3, 2, 1, Los!“ kommt eine Karte nach der anderen. Grün schickt
  die Karte nach links auf den grünen Stapel, gelb nach rechts auf den gelben; die nächste springt
  sofort herein. Ist die Minute um, darf die offene Karte noch zu Ende gelesen und bewertet werden.
  Abbrechen über ✕ oder Zurück — eine angefangene Runde zählt nicht und ändert keinen Stapel.
- **Zeit:** gemessen wird je Karte vom Aufdecken bis zum Tippen. Pausen (Bildschirm aus, App
  gewechselt, Abbrechen-Dialog) zählen nicht; danach geht es mit einem neuen Countdown weiter, die
  Karte ist solange verdeckt.
- **Welche Karte kommt:** zufällig, aber gelbe und neue Wörter dreimal so oft wie grüne. Innerhalb
  einer Runde wiederholt sich kein Wort, bevor alle 100 dran waren, und nie zweimal hintereinander.
- **Stapel:** jedes Wort liegt auf dem Stapel seiner **letzten** Bewertung. Ein grünes Wort, das
  später hakt, wandert also zurück auf Gelb, und umgekehrt.
- **Ergebnis:** Wörter in der Minute, davon grün und gelb, Platz in der Bestenliste, ggf. *Neuer
  Rekord!*, das schnellste grüne Wort und alle Karten der Runde mit ihrer Lesezeit.
- **Bestenliste** (Pokal-Symbol): alle Runden nach Anzahl Wörtern, bei Gleichstand nach grünen,
  dann die frühere zuerst. Die gerade gespielte Runde ist hervorgehoben.
- **Wörter** (Menü oder Tipp auf einen Stapel): die Wörter eines Stapels mit dem Durchschnitt der
  letzten fünf Lesezeiten und wie oft sie dran waren; ein Tipp auf ein Wort zeigt die Einzelheiten.
- **Design:** standardmäßig dunkel, *Menü → Dunkles Design* schaltet auf hell um (bleibt gespeichert).

## Fortschritt exportieren und importieren

Für einen Gerätewechsel: *Menü → Fortschritt exportieren* speichert eine JSON-Datei über den
Android-Dateidialog, auf dem anderen Gerät *importieren*. Gespeichert sind nur die Runden. Stapel,
Lesezeiten und Bestenliste werden daraus berechnet und stimmen nach dem Import also auch. Der Import
**ergänzt**: schon bekannte Runden (gleiche `id`) werden übersprungen.

```json
{
  "format": "lesen-runden",
  "version": 1,
  "rounds": [
    {"id": "b0fa3f98-…", "timestamp": 1794126000000, "seconds": 60,
     "attempts": [{"word": "und", "millis": 840, "good": true},
                  {"word": "welche", "millis": 2310, "good": false}]}
  ]
}
```

Auf dem Gerät liegt das im selben Format in `files/rounds.json` (privater App-Speicher). Das Wort
selbst ist die Kennung; Wörter, die eine spätere Version nicht mehr kennt, bleiben gespeichert, zählen
aber für keinen Stapel.

## Wortliste

`words/Words` enthält die 100 Häufigkeitswörter aus *Grundwortschatz für Berlin – Planungshilfe für
den Rechtschreibunterricht* (Fassung 2025, S. 22–23), alphabetisch, „Zeit“ als Nomen groß. Die
Auswahl und die Idee mit den zwei Stapeln stammen aus der Wortkartei „Blitzlesen – 100 häufige
Wörter“ von Die Lernheldin (@die.lernheldin); darauf verweist auch der About-Dialog.

## Technik

- Java, Android Views (kein Compose), Material 3, minSdk 26 / targetSdk 34 — wie Zahlen und
  Dingsbums. Keine Google-Play-Dienste.
- `game/Round`, `words/` und `stats/History` haben keine Android-Abhängigkeiten und sind in
  `RoundTest`, `PickerTest` und `HistoryTest` getestet.
- Hochformat fest.

## Bauen und installieren

Gebraucht wird ein **JDK 17** (das JBR aus Android Studio ist zu neu):

```bash
JAVA_HOME=~/.jdks/jdk-17.0.20.1+1 ./gradlew testDebugUnitTest assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Auf die Handys gehört das **signierte Release-APK**, nicht das Debug-APK: nur dann passen spätere
Selbst-Updates darüber. Ein Debug-Build müsste vorher deinstalliert werden, und damit wären die Runden
weg.

## Selbst-Update

Wie bei Zahlen (`update/AppUpdater`): beim Start höchstens alle 6 Stunden und über *Menü → Nach
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

## Download

Signierte APKs hängen an den [Releases](https://github.com/hansmaulwurf23/blitzlesen/releases).
Zum Installieren die Datei auf dem Handy öffnen. Android fragt einmalig nach der Erlaubnis, Apps aus
dieser Quelle zu installieren (bei Samsung pro App, z. B. für *Chrome* oder *Eigene Dateien*).

## Release

Signaturschlüssel und `keystore.properties` liegen **nicht im Repo**, sondern in
`~/Documents/Android/own/lesen/` (wie bei Zahlen; abweichender Pfad über `-Plesen.signing=…`).
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
