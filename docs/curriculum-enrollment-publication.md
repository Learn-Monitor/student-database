# Jahrgangszuordnung, WPF und Freischaltung

Stand: 12.09.2026. Ergänzung des bestehenden Curriculum-Editors.

Admins können reguläre Fächer für alle aktuellen Kinder eines Jahrgangs einem
Halbjahr zuordnen. Pro Klasse/Fach wird die zuständige Lehrkraft explizit
angegeben; die Oberfläche wählt nur bei genau einer passenden Lehrkraft vor.
Der Server ermittelt Klassen und Kinder frisch und prüft die vollständige
Zuordnungsmatrix. Sämtliche Zuweisungen teilen eine Schreibtransaktion:
Ein fehlender Kontext, ein fremder Lehrer, eine veränderte historische Stufe
oder ein Konflikt mit Abschlüssen verwirft die ganze Änderung. Die Operation
ist additiv und idempotent, sie entfernt keine anderen Fachzuordnungen.

WPF werden als Fachart markiert und sind von der Sammelzuordnung ausgeschlossen.
Eine gesonderte Klassenliste ordnet je Kind und Halbjahr genau ein WPF zu
(Datenbank-Primärschlüssel student/semester). Drag-and-drop und die zugängliche
Auswahl mit Speichern verwenden denselben Endpunkt. expectedSubjectId schützt
vor veralteten oder gleichzeitigen Änderungen. Vorhandene Arbeiten verhindern
einen stillen WPF-Wechsel. Alte Kontextzeilen und Leistungen bleiben erhalten.
Die bisherige Leistungsübernahme bleibt für Wechsel innerhalb desselben Fachs
verfügbar; ein fachübergreifender WPF-Wechsel mit Arbeit ist hier bewusst gesperrt.

Lehrkräfte können für ihren Fach-/Klassen-/Halbjahreskontext ganze zentrale
Themen oder einzelne Etappen freischalten. Neue Freischaltungen sind zunächst
inaktiv. Die Themenfreigabe ist der Standard auch für spätere Etappen dieses
Themas; eine einzelne Etappe kann davon abweichend freigegeben/gesperrt werden.
Eine erneute Freigabe/Sperre des ganzen Themas löscht nur dessen Einzel-Ausnahmen.
Sie verändert keine Etappen, Aufgabenstatistiken oder verdienten Münzen.

Der Schülerkatalog enthält nur freigeschaltete zentrale Inhalte sowie bereits
abgeschlossene Historie. Ein späteres Sperren nimmt keine Münzen weg. Offene
Aufgaben können ohne Freischaltung auch nicht direkt über die Aufgabenendpunkte
begonnen werden. Die Legacy-Leser /tasks, /topic-list und /current-topic filtern
semestergebundene zentrale Inhalte entsprechend. Archivierte Legacy-Themen ohne
Halbjahr werden durch diesen Umbau nicht neu definiert.

Das volle Budget bleibt unverändert gültig (100 regulär, 105 hart). Der additive
Katalogwert planned.unreleasedCentralTokens erklärt die Differenz zwischen dem
vollen zentralen Plan und den sichtbaren Aufgaben; das Schüler-Overlay prüft
diese Summe weiterhin exakt. Fortschritt zählt unverändert alle gültigen
Abschlüsse im zugewiesenen Kontext.

/mySubjects (tatsächlicher Pfad /mysubjects) verwendet bei verwalteten Kindern
frisch deren Zuordnungen im konfigurierten aktuellen Halbjahr. Dadurch nutzen
Dashboard und Schatzkammer dieselbe Fachauswahl. Unverwaltete Legacy-Kinder
behalten ihre bisherige Fachliste; explizit als WPF markierte Fächer erscheinen
aber nur bei individueller WPF-Auswahl. Die Zuordnung ändert nicht automatisch
das global konfigurierte aktuelle Halbjahr.

## Endpunkte (JSON POST)

- /curriculum-enrollment-catalog: Admin; Facharten, Klassen, Halbjahre, Lehrkräfte,
  mögliche Lehrer/Klassen/Fach-Kombinationen und bisherige Jahrgangsfächer.
- /set-curriculum-subject-type: Admin; subjectId, wpf (Boolean).
- /assign-grade-curriculum: Admin; grade, semesterId, subjectIds und teaching.
  Teilzuordnungen sind erlaubt: übermittelte Zuordnungen mit
  classId/subjectId/teacherId werden additiv gespeichert bzw. aktualisiert,
  leere Kombinationen bleiben unverändert. Vorhandene Zuordnungen werden im
  UI semesterbezogen wieder angezeigt.
- /curriculum-wpf-roster: Admin; classId, semesterId. Minimaler Schülername,
  ID, aktuelle WPF- und Lehrer-ID; keine Zugangsdaten.
- /assign-curriculum-wpf: Admin; studentId, subjectId, classId, teacherId,
  semesterId, expectedSubjectId (bei Erstzuordnung null).
- /curriculum-releases: Lehrkraft/Admin; Standard-Kontextfelder.
- /set-curriculum-release: Lehrkraft/Admin; Kontext, active (Boolean) und genau
  eines von topicId oder taskId.

PM-Funktionen: curriculum_manage_enrollment (Admin), curriculum_publish
(Lehrkraft/Admin); beide benötigen curriculum_view und erlauben ausschließlich
POST. Kernrollen- und Besitzprüfungen bleiben unabhängig davon wirksam.

Sechs additive Tabellen, keine Bestandsmigration und keine automatische
Jahrgangs-/WPF-Zuordnung vorhandener Konten. Semesterbezogene Klassenkontexte
werden weiterhin historisch festgehalten.
# Halbjahresfolge

Das Admin-Dashboard legt über „Nächstes Halbjahr anlegen“ automatisch den
chronologischen Nachfolger an: `YYYY_YY_HJ1` gefolgt von `YYYY_YY_HJ2`, danach
`(YYYY+1)_(YY+1)_HJ1`. Die Bezeichnung wird ausschließlich serverseitig aus den
vorhandenen Halbjahreslabels bestimmt; freie oder doppelte Eingaben sind für
diesen Ablauf nicht erforderlich.

Beim Anlegen werden die Rahmendaten des letzten logisch benannten Halbjahres in
das neue Halbjahr übernommen: Jahrgang-/Fach-Matrix, Schüler-Jahrgangskontexte,
Schüler-Fachkontexte und bestehende individuelle WPF-Zuordnungen. Leistungen,
Themen, Etappen und Freischaltungen werden nicht kopiert. Änderungen, vor allem
WPF-Wechsel, werden anschließend gezielt über die bestehenden Bearbeitungen
eingetragen.
