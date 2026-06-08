# Event Workflow Schulungsvideo — Screencast-Pipeline

Dieses Verzeichnis enthält alle Assets für das BATbern Event-Workflow Schulungsvideo.

## Überblick

- **Testdatei**: `../screencast-event-workflow.spec.ts` (button-getriebener Kanban-Workflow, ADR-009 — kein Drag-and-Drop)
- **Narration**: pro Segment via ElevenLabs API generiert (`eleven_v3`, Audio-Tags wie `[excited]`)
- **Synchronisation**: Das Video führt, das Audio folgt — die Testlaufzeit bestimmt die Audio-Offsets. Keine manuellen Timing-Dateien mehr.
- **Sprache**: Deutsch (`de`); Pipeline ist sprachparametrisiert (`SCREENCAST_LANG`), EN = neues Manifest, kein Code.
- **Video-Qualität**: Full HD (1920x1080)

## Die 3-Schritte-Pipeline

### Schritt 1: Narration generieren (Pre-Step)

Quelle der Wahrheit für alle Texte: `narration-manifest-de.json` (36 Segmente, Emotions-Tags).

```bash
cd web-frontend

# Mit ElevenLabs API — nur der Key ist nötig (text_to_speech-Permission reicht):
export ELEVENLABS_API_KEY=...
# Voice + Modell stehen im Manifest (de: ENNIAH WHaUUVTDq47Yqc9aDbkH, eleven_v3;
# en: Adeline 5l5f8iK3YPeGga21rQIX). Optionale Overrides:
# ELEVENLABS_VOICE_ID_{DE|EN} > ELEVENLABS_VOICE_ID > manifest.voiceId,
# ELEVENLABS_MODEL_ID > manifest.modelId.
# Achtung: Library-Voices via API brauchen einen bezahlten Plan (Starter reicht).
npm run screencast:narration                       # de (Default)
npm run screencast:narration -- --lang en

# Ohne API-Zugang (stille Platzhalter, für Dry-Runs der ganzen Pipeline):
npm run screencast:narration -- --fake
```

Ergebnis:

- `audio-cache/de/NARRATION_XX-{hash}.mp3` — ein MP3 pro Segment, **gecacht per Text-Hash**
  (unveränderte Segmente kosten bei Re-Runs keine API-Credits; `--force` erzwingt Neugenerierung)
- `narration-timings-de.json` — gemessene Dauern (ffprobe), vom Test konsumiert

### Schritt 2: Screencast aufzeichnen

Voraussetzung: lokale Dev-Umgebung läuft (`make dev-native-up`) und Organizer-Token ist gesetzt.

```bash
npm run test:e2e:screencast          # oder :headed
```

Der Test wartet pro Segment exakt die gemessene Audio-Dauer (+ Atempause,
`SCREENCAST_NARRATION_GAP_MS`, Default 700 ms) und schreibt am Ende
`narration-timeline-de.json` mit den **tatsächlichen Start-Offsets** jedes Segments.

Video: `test-results/screencast-event-workflow-…-screencast-training/video.webm`

### Schritt 3: Audio + Untertitel assemblieren (Post-Step)

```bash
npm run screencast:assemble
```

Platziert jedes Segment-MP3 exakt am aufgezeichneten Offset (ffmpeg adelay+amix) und erzeugt:

- `narration-de.m4a` — fertige, synchrone Tonspur
- `subtitles-de.srt` — Untertitel aus dem Manifest (Tags entfernt, ≤120 Zeichen pro Cue)

### Schritt 4: Muxen

```bash
ffmpeg -i video.webm -i narration-de.m4a -i subtitles-de.srt \
       -map 0:v -map 1:a -map 2:s \
       -c:v libx264 -preset medium -crf 23 \
       -c:a aac -b:a 128k -c:s mov_text \
       -metadata:s:s:0 language=deu -metadata:s:s:0 title="Deutsch" \
       -movflags +faststart \
       event-workflow-schulung-de.mp4
```

## Bei Workflow-Änderungen

1. Text in `narration-manifest-de.json` anpassen (nur geänderte Segmente werden neu generiert)
2. Test in `../screencast-event-workflow.spec.ts` anpassen (Narration-Kommentare = Kopien des Manifests)
3. Pipeline-Schritte 1–3 erneut ausführen — Timing synchronisiert sich automatisch

## Wichtige Hinweise

- **Keine echten E-Mails**: READY→ACCEPTED läuft über "Zusage im Namen erfassen" (keine
  Einladung). Promote legt SPEAKER-User namens **Bruno Test** an (CUMS leitet den Username
  aus `firstname.lastname` ab → `bruno.test*` wird vom Global-Teardown-Sweep aus der DB
  entfernt; die Karte zeigt den Brainstorm-Namen weiter als Untertitel). E-Mail:
  `@e2e.batbern.invalid` — nie zustellbar. Nur der **Cognito**-User (staging, Pattern N)
  wird von keinem Sweep entfernt — gelegentlich manuell aufräumen.
- **Generierte Artefakte** (`audio-cache/`, `narration-timings-*.json`,
  `narration-timeline-*.json`, `narration-*.m4a`, `subtitles-*.srt`) sind in `.gitignore`.
  Wer echte ElevenLabs-Audios versionieren will: `git add -f audio-cache/de`.
  Fake-Audio landet getrennt in `audio-cache/{lang}-fake/` — ein `--fake`-Lauf kann
  bezahlte ElevenLabs-MP3s nie überschreiben oder löschen.
- `--fake`-Timings führen zu einem stummen, aber vollständig durchlaufenden Dry-Run —
  der Test warnt laut, wenn er mit Fake-Timings läuft.

## Legacy-Assets

`ElevenLabs_BATbern_Workflow*.mp3`, `script-for-tts-*.txt`, `alignment-data.json`,
`narration-raw-de.m4a` stammen aus der manuellen v1-Pipeline (Web-UI-Aufnahme +
handgepflegte Timing-Configs) und sind nur noch Referenzmaterial. Die alten
`subtitles-*.srt` wurden entfernt — Untertitel sind jetzt generierte Artefakte.

## Video-Spezifikationen

- **Auflösung**: 1920x1080 (Full HD) · **Format**: MP4 (H.264 + AAC)
- **Untertitel**: separate Spur (ein-/ausschaltbar)

## Distribution

Git LFS, Vimeo, YouTube (unlisted) oder S3+CloudFront — siehe
`/docs/plans/screencast-training-video-plan.md`.
