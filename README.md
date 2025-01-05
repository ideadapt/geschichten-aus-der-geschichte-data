# Geschichten aus der Geschichte - Datenhub

Strukturierte Sammlung von Metadaten zu allen regulären Episoden (keine Feedback- oder Extra- / Bonus-Episoden) des
Podcasts "Geschichten aus der Geschichte" (aka
GAG), https://www.geschichte.fm.

Die Metadaten werden aus verschiedenen Quellen gewonnen. Zurzeit:

* GeschichteFM Podcast-Feed von Podigee.io https://geschichten-aus-der-geschichte.podigee.io/feed/mp3

## Metadaten

| Name                    | Quelle       | Format                                                               |
|-------------------------|--------------|----------------------------------------------------------------------|
| Title                   | Podcast-Feed | Text                                                                 |
| Erwähnte Episoden       | Podcast-Feed | Array<Number>                                                        |
| Erwähnte Zeitreferenzen | Podcast-Feed | Array<{ literal: string, normalized: string, from: date, to: date }> |
| Beschreibung            | Podcast-Feed | Text                                                                 |
| Dauer                   | Podcast-Feed | Number, in Sekunden                                                  |
| Link zur Audio-Datei    | Podcast-Feed | Text, URL                                                            |
| Erscheinungsdatum       | Podcast-Feed | Date                                                                 |
| Link zur Webseite       | Podcast-Feed | Text, URL                                                            |

Die Metadaten werden [automatisch](./.github/workflows/update-data.yaml) jede Woche aktualisiert und sind hier in diesem
Repository [abgelegt im JSONL Format](./data/episodes.jsonl) (menschen- und maschinenlesbar).

### Weitere Möglichkeiten / TODO

- Personen (aus Text via AI)
- Ortsangaben (inklusive Visualisierung auf Karte) (aus Text via AI)
- Themenbereiche (aus Text via AI, oder gag-network)

## Visualisierung

Auf der
Webseite [ideadapt.github.io/geschichten-aus-der-geschichte-data](https://ideadapt.github.io/geschichten-aus-der-geschichte-data)
werden gewisse Metadaten einfach zugänglich gemacht (Graphen, Diagramme, Tabellen).

# Verwandte Projekte

## gag-network

Die Themen der einzelnen Episoden übersichtlich und ansprechend als Graph dargestellt.

https://github.com/Dr-Lego/gag-network

### Design

Das Projekt verwendet als Datengrundlage Wikipedia dumps. Diese sind sehr gross. Es besteht das Risiko, dass die
Wikipedia Seite nicht aktuell / vollständig ist. Das verwendete Datenformat ist SQLite, welches nicht menschenlesbar
ist.

## gag-timeline

Schön aufbereitete Liste aller Episoden, sortierbar nach Erscheinungsdatum oder Themen-Jahr.

https://github.com/benlutz/gag-timeline

### Design

Das Projekt verwendet als Datengrundlage den RSS-Feed. Die Jahreszahlen wurden automatisch und manuell ermittelt.

## musixmatch podcasts

Index vieler Podcasts. AI angereicherte Metadaten.

### Design

Das Projekt verwendet den RSS-Feed und zusätzlich AI. Anhand von AI wird ein Transkript des Audio-Streams erstellt,
sowie Personen und Orte aus dem Transkript und der Beschreibung extrahiert.

API-Zugang auf Anfrage. Kosten unbekannt.

https://podcasts.musixmatch.com/podcast/geschichten-aus-der-geschichte-01h1nzad7068wrm8ac14dqgmm1

## Disclaimer

Sämtliche Inhalte wurden nach bestem Wissen und Gewissen sorgfältig erstellt. Es besteht jedoch keine Garantie auf
Vollständigkeit, Richtigkeit, Rückschlüsse und Schlussfolgerungen infolge Darstellung der Homepage und deren Inhalte.
