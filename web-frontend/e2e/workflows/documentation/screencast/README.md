# Event Workflow Schulungsvideo Assets

Dieses Verzeichnis enthält alle Assets für das BATbern Event-Workflow Schulungsvideo.

## Überblick

- **Testdatei**: `../screencast-event-workflow.spec.ts`
- **Dauer**: ~43 Minuten
- **Abdeckung**: Kompletter Event-Lebenszyklus (Phasen A-E)
- **Sprache**: Deutsch
- **Video-Qualität**: Full HD (1920x1080)

## Assets

- `script-de.md` - Vollständiges deutsches Narrationsskript (mit Timestamps)
- `script-for-tts-de.txt` - TTS-bereites Skript (plain text, ohne Markdown)
- `README.md` - Diese Dokumentation

## Video generieren

### Schritt 1: Test aufzeichnen

```bash
cd /Users/nissim/dev/bat/BATbern-feature/web-frontend
npm run test:e2e:screencast
```

Das Video wird gespeichert in:

```
test-results/screencast-event-workflow-screencast-training-chromium/video.webm
```

### Schritt 2: German Voice Narration generieren

⚠️ **WICHTIG**: macOS `say` Stimmen sind robotisch und nicht professionell genug!

**Siehe**: `VOICE-GENERATION-GUIDE.md` für professionelle Optionen.

**Empfohlene Optionen** (beste bis schlechteste Qualität):

1. **ElevenLabs** (Best Quality) - $22/month
   - Natural, menschlich klingende Stimme
   - Deutsche Stimme "Freya" empfohlen
   - https://elevenlabs.io/

2. **Google Cloud Text-to-Speech** (Very Good) - ~$1 für 43 Minuten
   - WaveNet-Stimmen sehr natürlich
   - Stimme `de-DE-Wavenet-F` empfohlen

3. **Amazon Polly** (Good) - KOSTENLOS (erstes Jahr)
   - Neural-Stimmen, gute Qualität
   - Stimme `Vicki` empfohlen

4. **Manuelle Aufnahme** (Beste Qualität, aber zeitaufwendig)
   - GarageBand + USB-Mikrofon
   - 2-3 Stunden Aufnahme + Bearbeitung

**Quick Start mit ElevenLabs** (Empfohlen):

```bash
# 1. Registrieren auf https://elevenlabs.io/
# 2. Creator Plan wählen ($22/month)
# 3. Text aus script-for-tts-de.txt kopieren
# 4. Stimme "Freya" wählen
# 5. Generate klicken und MP3 herunterladen
# 6. Konvertieren:
cd /Users/nissim/dev/bat/BATbern-feature/web-frontend/e2e/workflows/documentation/screencast
ffmpeg -i ~/Downloads/elevenlabs_*.mp3 -c:a aac -b:a 128k narration-raw-de.m4a
```

Detaillierte Anleitungen für alle Optionen: **siehe `VOICE-GENERATION-GUIDE.md`**

### Schritt 3: Untertitel automatisch generieren (SRT)

**Automatische Generierung** (Empfohlen):

Die SRT-Datei kann automatisch aus `timing-config.ts` und `NARRATION-MAPPING.md` generiert werden:

```bash
cd /Users/nissim/dev/bat/BATbern-feature/web-frontend/e2e/workflows/documentation/screencast/scripts
npx ts-node generate-subtitles.ts
```

Das Skript:

- Liest die exakten Timings aus `timing-config.ts`
- Extrahiert den deutschen Text aus `NARRATION-MAPPING.md`
- Entfernt Emotions-Marker (`[excited]`, `[playful]`, etc.)
- Teilt lange Texte in lesbare Untertitel-Chunks auf
- Generiert `subtitles-de.srt` mit 117 Einträgen (~12 Minuten)

**Manuelle Erstellung** (falls Anpassungen nötig):

Format:

```srt
1
00:00:00,000 --> 00:00:10,333
Willkommen zur BATbern Event-Management-Plattform!

2
00:00:10,333 --> 00:00:20,666
Heute zeige ich Ihnen, wie man ein Event plant, ohne dabei den Verstand zu verlieren.
```

**Timing-Tipps**:

- Schauen Sie das Video an und notieren Sie Zeitstempel
- Max. 120 Zeichen pro Untertitel für gute Lesbarkeit
- UTF-8 Encoding für deutsche Umlaute (ä, ö, ü, ß)

### Schritt 4: Video + Audio + Untertitel kombinieren

**Voraussetzung**: FFmpeg installieren

```bash
brew install ffmpeg
```

**Kombinieren**:

```bash
# Erstelle Output-Verzeichnis
mkdir -p /Users/nissim/dev/bat/BATbern-feature/docs/user-guide/assets/videos/workflow/raw

cd /Users/nissim/dev/bat/BATbern-feature/docs/user-guide/assets/videos/workflow

# Kopiere Quelldateien
cp /Users/nissim/dev/bat/BATbern-feature/web-frontend/test-results/screencast-event-workflow-screencast-training-chromium/video.webm raw/
cp /Users/nissim/dev/bat/BATbern-feature/web-frontend/e2e/workflows/documentation/screencast/narration-raw-de.m4a .
cp /Users/nissim/dev/bat/BATbern-feature/web-frontend/e2e/workflows/documentation/screencast/subtitles-de.srt .

# Kombiniere mit FFmpeg (Separate Subtitle Track)
ffmpeg -i raw/video.webm \
       -i narration-raw-de.m4a \
       -i subtitles-de.srt \
       -c:v libx264 \
       -preset medium \
       -crf 23 \
       -c:a aac \
       -b:a 128k \
       -c:s mov_text \
       -metadata:s:s:0 language=deu \
       -metadata:s:s:0 title="Deutsch" \
       -movflags +faststart \
       event-workflow-schulung-de.mp4
```

**Überprüfen**:

```bash
# Video-Eigenschaften anzeigen
ffmpeg -i event-workflow-schulung-de.mp4

# In VLC Player öffnen (Untertitel einschalten: View → Subtitles → Track 1)
open -a VLC event-workflow-schulung-de.mp4
```

## Video-Spezifikationen

- **Auflösung**: 1920x1080 (Full HD)
- **Format**: MP4 (H.264 + AAC)
- **Dauer**: ~43 Minuten
- **Dateigröße**: ~150-300 MB
- **Untertitel**: Separate Spur (ein-/ausschaltbar)
- **Stimme**: Shelley (Deutsch, weiblich)

## Video aktualisieren

Bei Workflow-Änderungen:

1. **Test anpassen**: `screencast-event-workflow.spec.ts` aktualisieren
2. **Neu aufzeichnen**: `npm run test:e2e:screencast`
3. **Skript anpassen**: `script-de.md` und `script-for-tts-de.txt` aktualisieren
4. **Narration neu generieren**: `say -v Shelley -o narration.aiff -f script-for-tts-de.txt`
5. **Untertitel anpassen**: `subtitles-de.srt` mit neuen Timestamps aktualisieren
6. **FFmpeg wiederholen**: Video neu kombinieren

## Troubleshooting

### Video-Qualität schlecht

- Stelle sicher, dass Playwright config `video.size: { width: 1920, height: 1080 }` hat
- Verwende `--headed` Mode: `npm run test:e2e:screencast:headed`

### Narration zu schnell/langsam

- Passe `slowMo: 500` in `playwright.config.ts` an (höher = langsamer)
- Oder bearbeite Audio mit Audacity (Tempo ändern ohne Pitch zu ändern)

### Untertitel nicht synchron

- Schaue Video + Audio zusammen an
- Notiere exakte Zeitstempel für jeden Satz
- Nutze Subtitle-Editor wie Aegisub für präzise Timing

### FFmpeg-Fehler

- Überprüfe, dass alle Input-Dateien existieren
- Stelle sicher, dass SRT-Datei UTF-8 kodiert ist: `file -I subtitles-de.srt`

## Distribution

Das finale Video kann gehostet werden auf:

- **Git Repository** (mit Git LFS für große Dateien)
- **Vimeo** (professionelles Video-Hosting mit Analytics)
- **YouTube** (unlisted/private, kostenlos, gute Embed-Optionen)
- **AWS S3 + CloudFront** (vollständige Kontrolle, CDN-Delivery)

## Kontakt

Bei Fragen zur Video-Erstellung wenden Sie sich an das Development-Team oder konsultieren Sie:

- `/docs/plans/screencast-training-video-plan.md` - Vollständiger Implementierungsplan
- `/docs/user-guide/` - User Guide Dokumentation
