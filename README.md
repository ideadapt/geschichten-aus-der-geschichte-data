# Geschichten aus der Geschichte - Datenhub

Strukturierte Sammlung von Metadaten zu allen regulären Episoden (keine Feedback- oder Extra- / Bonus-Episoden) des
Podcasts "Geschichten aus der Geschichte" (aka
GAG), https://www.geschichte.fm.

Die Metadaten werden aus verschiedenen Quellen gewonnen. Zurzeit:

* GeschichteFM Podcast-Feed von Podigee.io https://geschichten-aus-der-geschichte.podigee.io/feed/mp3

## Metadaten

| Name                    | Quelle            | Format                                                                             |
|-------------------------|-------------------|------------------------------------------------------------------------------------|
| Title                   | Podcast-Feed      | string                                                                             |
| Erwähnte Episoden       | Podcast-Feed      | Array<number>                                                                      |
| Erwähnte Zeitreferenzen | Podcast-Feed      | Array<{ literal: string, mode: string, normalized: string, from: date, to: date }> |
| Erwähnte Orte           | Podcast-Feed (AI) | Array<{ name: string, longitude: number, latitude: number }>                       |
| Beschreibung            | Podcast-Feed      | string                                                                             |
| Dauer                   | Podcast-Feed      | Number, in Sekunden                                                                |
| Link zur Audio-Datei    | Podcast-Feed      | string, URL                                                                        |
| Erscheinungsdatum       | Podcast-Feed      | string, Date                                                                       |
| Link zur Webseite       | Podcast-Feed      | string, URL                                                                        |

Beispiel:

`{"id":157,"checksum":1570449493,"title":"Salpeter – Aufstieg und Fall einer chemischen Verbindung","date":"2018-09-26T08:25:19Z","durationInSeconds":2160,"websiteUrl":"https://gadg.fm/157","audioUrl":"https://audio.podigee-cdn.net/543427-m-937b92fc738d6fbaa7b40f2b2d6f5a3a.mp3?source=feed","description":"Wir springen etwa 100 Jahre zurück und beschäftigen uns mit Chemiegeschichte: Es geht um Salpeter. Ein Stoff, Kaliumnitrat, von dem Europa im 19. Jahrhundert abhängig war, denn er war Hauptbestandteil von Schießpulver und Basis von Düngemittel für die Landwirtschaft. Mitte des 19. Jahrhunderts begann dann der industrielle Abbau von Salpeter. Nach dem Salpeterkrieg, ab 1884, sicherte sich Chile praktisch das Monopol auf den Handel mit Salpeter, das vor allem in der Atacama-Wüste abgebaut wurde. Doch der Boom währte nur kurz: Mit dem Haber-Bosch-Verfahren stand bald eine Alternative bereit, die dazu führte, dass der Handel mit Salpeter in den 1920er Jahren zusammenbrach und die vielen Fabriken in der Atacama-Wüste zu Geisterstädten wurden – die heute Teil des UNESCO Weltkultur-Erbes sind.","episodeLinks":[],"temporalLinks":[{"literal":"1920er Jahren","mode":"DecadeAbsolute","normalized":"1920er Jahre","start":"1920-01-01T00:00:00Z","end":"1929-12-31T23:59:59Z"}],"locations":[{"name":"Chile","latitude":-35.6751,"longitude":-71.5375},{"name":"Atacama-Wüste","latitude":-24.8742,"longitude":-69.2557}]}`

Die Metadaten werden [automatisch](./.github/workflows/update-data.yaml) jede Woche aktualisiert und sind hier in diesem
Repository [abgelegt im JSONL Format](./data/episodes.jsonl) (menschen- und maschinenlesbar).

### Weitere Möglichkeiten / TODO

- Personen (aus Text via AI)
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

## GAG-Podcast-Episodes-Chronological-Order

Extrahiert Jahreszahlen aus der Episodenbeschreibung, mithilfe von AI.

https://github.com/JakobAtTUM/GAG-Podcast-Episodes-Chronological-Order

### Design

Das Projekt verwendet als Datengrundlage das HTML Podcast-Archiv. Die Jahreszahlen werden via AI aus dem semantischen
Kontext abgeleitet.
Dadurch sind praktisch für jede Episode Jahreszahlen verfügbar. Die Ergebnisse sind in einem öffentlichen Google Sheet
gespeichert.

## musixmatch podcasts

Index vieler Podcasts. AI angereicherte Metadaten.

### Design

Das Projekt verwendet den RSS-Feed und zusätzlich AI. Anhand von AI wird ein Transkript des Audio-Streams erstellt,
sowie Personen und Orte aus dem Transkript und der Beschreibung extrahiert.

API-Zugang auf Anfrage. Kosten unbekannt.

https://podcasts.musixmatch.com/podcast/geschichten-aus-der-geschichte-01h1nzad7068wrm8ac14dqgmm1

## shuffle-gag

Das Projekt verwendet die Podcast-Feeds verschiedener Plattformen (z.B. Spotify, Apple) um deren Episoden URLs
zu speichern. Danach kann man, z.B. auf dem iPhone, eine zufällige Folge hören.

https://github.com/simonneutert/shuffle-gag

## Disclaimer

Sämtliche Inhalte wurden nach bestem Wissen und Gewissen sorgfältig erstellt. Es besteht jedoch keine Garantie auf
Vollständigkeit, Richtigkeit, Rückschlüsse und Schlussfolgerungen infolge Darstellung der Homepage und deren Inhalte.
