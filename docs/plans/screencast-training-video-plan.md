# Screencast Training Video Implementation Plan

## Overview

Convert the E2E test workflow (`complete-event-workflow.spec.ts`) into a professional German-language screencast training video with voiceover narration and subtitles.

**User Preferences:**
- ✅ One continuous 43-minute video
- ✅ AI text-to-speech narration (macOS `say` command)
- ✅ Separate subtitle track (not burned in)
- ✅ German language

## Current State

**Test Structure:**
- Location: `web-frontend/e2e/workflows/documentation/complete-event-workflow.spec.ts` (1,303 lines)
- 6 phases (A-F) as separate serial tests
- Duration: ~43 minutes total
- Captures 94 screenshots using `createSequentialCapturer()`
- Viewport: 1920x1080 (Full HD)

**Playwright Configuration:**
- Video mode: `'retain-on-failure'` (only on failures)
- Documentation project already configured with proper viewport
- Global auth state loaded from `.playwright-auth-state.json`

## Implementation Approach

### Test Modification Strategy

**Create new dedicated test file** (not modify original):
- File: `web-frontend/e2e/workflows/documentation/screencast-event-workflow.spec.ts`
- Merge all 6 test phases into ONE continuous test function
- Remove ALL screenshot capture calls (`capturer()` calls)
- Remove `cleanupBeforeScreenshots()` from beforeAll
- Keep ALL UI interactions intact
- Add console log timing markers for subtitle synchronization
- Add strategic 2-second pauses after major actions (for narration pacing)

**Configuration:**
- Add new Playwright project: `screencast-training`
- Set video mode to `'on'` (always record)
- Set screenshot to `'off'` (not needed)
- Match viewport: 1920x1080
- Add `slowMo: 500` for clarity
- No retries (want one clean recording)

### German Voice Configuration

**macOS German Voices:**
- `Anna` (de_DE) - Female, high quality
- `Markus` (de_DE) - Male
- `Petra` (de_DE) - Female

**Recommended:** Use `Anna` voice for consistent, clear German narration.

## Step-by-Step Implementation

### Step 1: Create Screencast Test File (2-3 hours)

**1.1 Scaffold Test File**
- Copy `complete-event-workflow.spec.ts` to `screencast-event-workflow.spec.ts`
- Remove `test.describe.serial` wrapper
- Create single test function: `test('Vollständiger Event-Workflow von Erstellung bis Archivierung', async ({ page }) => {`
- Set timeout: `test.setTimeout(60 * 60 * 1000);` (60 minutes)

**1.2 Merge Test Phases**
```typescript
// Merge Phase A, B, B.5, C, D, E, F into one continuous flow
test('Vollständiger Event-Workflow', async ({ page }) => {
  test.setTimeout(60 * 60 * 1000);

  // Phase A: Event Setup
  console.log('[NARRATION_00:00] Phase A: Event-Einrichtung');
  // ... all Phase A code (no screenshots)
  await page.waitForTimeout(2000); // Pause for narration

  // Phase B: Speaker Outreach
  console.log('[NARRATION_10:00] Phase B: Referenten-Kontaktierung');
  // ... all Phase B code
  await page.waitForTimeout(2000);

  // Continue for all phases...
});
```

**1.3 Remove Screenshot Logic**
- Delete all lines with `createSequentialCapturer`
- Delete all lines with `await capturer(`
- Keep assertions and UI interactions

**1.4 Add Timing Markers**
Insert at each phase transition:
```typescript
console.log('[NARRATION_00:00] Willkommen zur BATbern Plattform');
console.log('[NARRATION_10:00] Phase B: Referenten-Kontaktierung');
console.log('[NARRATION_20:00] Phase C: Qualitätsprüfung');
// etc.
```

**1.5 Update Page Object Imports**
Keep all existing imports intact - page objects remain unchanged.

### Step 2: Configure Playwright Project (30 min)

**2.1 Add Screencast Project to `playwright.config.ts`**

Add after line 131 (after documentation-screenshots project):

```typescript
/* Screencast training video project - Continuous recording */
{
  name: 'screencast-training',
  use: {
    browserName: 'chromium',
    viewport: { width: 1920, height: 1080 },
    deviceScaleFactor: 1,
    isMobile: false,
    hasTouch: false,
    video: 'on', // ALWAYS record
    screenshot: 'off', // Not needed
    storageState: '.playwright-auth-state.json',
    launchOptions: {
      slowMo: 500, // Slow down for clarity
    },
  },
  testMatch: /screencast-event-workflow.spec.ts/,
  retries: 0, // No retries - want one clean recording
},
```

**2.2 Add NPM Script to `package.json`**

Add to scripts section:
```json
"test:e2e:screencast": "playwright test --project=screencast-training",
"test:e2e:screencast:headed": "playwright test --project=screencast-training --headed"
```

### Step 3: Record Initial Test Run (1 hour)

**3.1 Ensure Services Running**
```bash
cd /Users/nissim/dev/bat/BATbern-feature
make dev-native-status  # Verify all services running
# If not: make dev-native-up
```

**3.2 Run Screencast Test**
```bash
cd web-frontend
npm run test:e2e:screencast
```

**3.3 Locate Video Output**
```bash
find test-results -name "video.webm" -type f
# Expected: test-results/screencast-event-workflow-screencast-training-chromium/video.webm
```

**3.4 Verify Video**
- Duration: ~43 minutes
- Resolution: 1920x1080
- All phases captured sequentially
- Open in VLC/QuickTime to verify quality

**3.5 Extract Timing Markers**
```bash
grep "NARRATION" test-results/*/stdout.txt > timing-markers.txt
```

### Step 4: Write German Narration Script (4-5 hours)

**4.1 Create Asset Directory**
```bash
mkdir -p web-frontend/e2e/workflows/documentation/screencast
```

**4.2 Create `script-de.md`**

Structure:
```markdown
# BATbern Event-Workflow Schulungsvideo Skript

## Einleitung (0:00 - 0:30)
Willkommen zur BATbern Event-Management-Plattform. In diesem umfassenden Tutorial durchlaufen wir den kompletten Event-Lebenszyklus, von der Erstellung bis zur Archivierung.

## Phase A: Event-Einrichtung (0:30 - 10:00)

### Dashboard & Anmeldung (0:30 - 1:00)
Wir beginnen am Event-Dashboard, wo Sie alle bevorstehenden und vergangenen Events sehen können. Beachten Sie die übersichtliche Oberfläche mit Event-Karten, die wichtige Informationen anzeigen.

### Neues Event erstellen (1:00 - 3:00)
Klicken Sie auf den "Neues Event" Button, um das Event-Erstellungsformular zu öffnen. Hier geben wir wesentliche Details ein:
- Event-Nummer: Eine eindeutige Kennung für dieses Event
- Titel: Ein beschreibender Name, den die Teilnehmer sehen werden
- Event-Typ: Wählen Sie zwischen Abend, Nachmittag oder Ganztagsformaten
- Datum und Anmeldefrist
- Veranstaltungsort mit Name und Adresse

[Weiter für jede Phase...]

## Phase B: Referenten-Kontaktierung (10:00 - 20:00)
[...]

## Phase C: Qualitätsprüfung (20:00 - 25:00)
[...]

## Phase D: Slot-Zuweisung (25:00 - 33:00)
[...]

## Phase E: Veröffentlichung (33:00 - 38:00)
[...]

## Phase F: Archivierung (38:00 - 43:00)
[...]

## Abschluss (43:00)
Damit ist der vollständige Event-Workflow abgeschlossen. Vielen Dank fürs Zuschauen.
```

**4.3 Key Narration Guidelines**
- Explain WHY each action is performed (business logic)
- Highlight important UI elements ("Beachten Sie...")
- Point out workflow transitions ("Das System...")
- Keep pacing conversational (not too fast)
- Total script length: ~6,000-8,000 words for 43 minutes

### Step 5: Generate German Voice Narration (1-2 hours)

**5.1 Test German Voice**
```bash
# List available German voices
say -v ? | grep de_DE

# Test Anna voice
say -v Anna "Willkommen zur BATbern Plattform" -o test.aiff
afplay test.aiff  # Play to verify
```

**5.2 Prepare Script for TTS**

Create `script-for-tts-de.txt` from `script-de.md`:
- Remove markdown formatting
- Remove timestamps
- Keep only narration text
- Add pauses with `[[slnc 2000]]` for 2-second breaks

**5.3 Generate Narration**
```bash
cd web-frontend/e2e/workflows/documentation/screencast

# Generate with Anna voice (German)
say -v Anna -o narration-raw-de.aiff -f script-for-tts-de.txt

# Convert to M4A (AAC audio)
afconvert -f mp4f -d aac narration-raw-de.aiff narration-raw-de.m4a

# Verify output
afplay narration-raw-de.m4a
```

**5.4 Audio Quality Check**
- Clear pronunciation
- Consistent volume
- Proper pacing
- Duration matches script (~43 minutes)

### Step 6: Create German Subtitle File (3-4 hours)

**6.1 Create SRT File Structure**

Create `subtitles-de.srt`:

```srt
1
00:00:00,000 --> 00:00:05,000
Willkommen zur BATbern Event-Management-Plattform.

2
00:00:05,500 --> 00:00:10,000
In diesem Tutorial durchlaufen wir den kompletten Event-Lebenszyklus.

3
00:00:10,500 --> 00:00:15,000
Wir beginnen am Event-Dashboard mit der Anmeldung.

[Continue for entire 43 minutes...]
```

**6.2 Timing Synchronization**
- Watch video while listening to narration
- Note exact timestamps for each subtitle segment
- Keep subtitle duration: 3-7 seconds per line
- Max 42 characters per line (readability)
- Use German punctuation rules

**6.3 SRT Format Guidelines**
- Sequential numbering (1, 2, 3...)
- Timestamp format: `HH:MM:SS,mmm` (comma for milliseconds)
- Empty line between entries
- UTF-8 encoding for German umlauts (ä, ö, ü, ß)

**6.4 Validation**
```bash
# Check file encoding
file -I subtitles-de.srt
# Expected: text/plain; charset=utf-8

# Validate SRT syntax
head -20 subtitles-de.srt
```

### Step 7: Combine Video + Audio + Subtitles (1-2 hours)

**7.1 Install FFmpeg**
```bash
brew install ffmpeg
```

**7.2 Prepare Output Directory**
```bash
mkdir -p /Users/nissim/dev/bat/BATbern-feature/docs/user-guide/assets/videos/workflow/raw
cd /Users/nissim/dev/bat/BATbern-feature/docs/user-guide/assets/videos/workflow
```

**7.3 Copy Source Files**
```bash
# Copy video from test results
cp /Users/nissim/dev/bat/BATbern-feature/web-frontend/test-results/screencast-event-workflow-screencast-training-chromium/video.webm raw/

# Copy audio
cp /Users/nissim/dev/bat/BATbern-feature/web-frontend/e2e/workflows/documentation/screencast/narration-raw-de.m4a .

# Copy subtitles
cp /Users/nissim/dev/bat/BATbern-feature/web-frontend/e2e/workflows/documentation/screencast/subtitles-de.srt .
```

**7.4 Combine with FFmpeg (Separate Subtitle Track)**

User wants separate subtitle track (not burned in):

```bash
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

**FFmpeg Parameters:**
- `-c:v libx264`: H.264 video codec (universal)
- `-preset medium`: Balance encoding speed/quality
- `-crf 23`: Constant Rate Factor (quality: 18-28, 23 is good)
- `-c:a aac`: AAC audio codec
- `-b:a 128k`: Audio bitrate
- `-c:s mov_text`: Subtitle codec for MP4
- `-metadata:s:s:0 language=deu`: Mark subtitle as German
- `-movflags +faststart`: Optimize for streaming

**7.5 Verify Output**
```bash
# Check video properties
ffmpeg -i event-workflow-schulung-de.mp4

# Expected:
# Duration: 00:43:xx
# Video: h264, 1920x1080, 25-30 fps
# Audio: aac, stereo, 128 kb/s
# Subtitle: mov_text (deu)
# Size: ~150-300 MB
```

**7.6 Quality Check**
- Open in VLC Player
- Enable subtitle track (View → Subtitles → Track 1)
- Verify audio-video sync
- Check subtitle timing and readability
- Ensure smooth playback

### Step 8: Documentation & Organization (1 hour)

**8.1 Create Screencast README**

Create `web-frontend/e2e/workflows/documentation/screencast/README.md`:

```markdown
# Event Workflow Schulungsvideo

Dieses Verzeichnis enthält Assets für das BATbern Event-Workflow Schulungsvideo.

## Überblick

- **Testdatei**: `../screencast-event-workflow.spec.ts`
- **Dauer**: ~43 Minuten
- **Abdeckung**: Kompletter Event-Lebenszyklus (Phasen A-F)
- **Sprache**: Deutsch

## Assets

- `script-de.md` - Deutsches Narrationsskript
- `subtitles-de.srt` - Deutsche Untertitel
- `narration-raw-de.m4a` - Sprachaufnahme (Anna Stimme)
- `README.md` - Diese Dokumentation

## Video generieren

### 1. Test aufzeichnen
\`\`\`bash
cd /Users/nissim/dev/bat/BATbern-feature/web-frontend
npm run test:e2e:screencast
\`\`\`

### 2. Assets kombinieren
\`\`\`bash
cd /Users/nissim/dev/bat/BATbern-feature/docs/user-guide/assets/videos/workflow
ffmpeg -i raw/video.webm -i narration-raw-de.m4a -i subtitles-de.srt \
       -c:v libx264 -preset medium -crf 23 -c:a aac -b:a 128k \
       -c:s mov_text -metadata:s:s:0 language=deu \
       -movflags +faststart event-workflow-schulung-de.mp4
\`\`\`

## Video-Spezifikationen

- **Auflösung**: 1920x1080 (Full HD)
- **Format**: MP4 (H.264 + AAC)
- **Dauer**: ~43 Minuten
- **Dateigröße**: ~150-300 MB
- **Untertitel**: Separate Spur (ein-/ausschaltbar)

## Video aktualisieren

Bei Workflow-Änderungen:
1. `screencast-event-workflow.spec.ts` anpassen
2. Test neu aufzeichnen: `npm run test:e2e:screencast`
3. `script-de.md` aktualisieren
4. Narration neu generieren: `say -v Anna -o narration.aiff -f script.txt`
5. `subtitles-de.srt` mit neuen Timestamps aktualisieren
6. FFmpeg-Verarbeitung wiederholen
\`\`\`

**8.2 Update User Guide**

Add to `docs/user-guide/workflow/README.md`:

```markdown
# Event-Management Workflow

## Video-Tutorial

Schauen Sie sich das komplette Workflow-Tutorial an (43 Minuten):

- 🎥 [Event-Workflow Schulungsvideo (Deutsch)](../assets/videos/workflow/event-workflow-schulung-de.mp4)

Dieses Video behandelt alle Phasen von der Event-Erstellung bis zur Archivierung.

## Workflow-Phasen

- [Phase A: Event-Einrichtung](phase-a-setup.md)
- [Phase B: Referenten-Kontaktierung](phase-b-outreach.md)
- [Phase C: Qualitätsprüfung](phase-c-quality.md)
- [Phase D: Slot-Zuweisung](phase-d-assignment.md)
- [Phase E: Veröffentlichung](phase-e-publishing.md)
- [Phase F: Archivierung](phase-f-archival.md)
```

**8.3 File Organization Summary**

Final directory structure:
```
/Users/nissim/dev/bat/BATbern-feature/
├── web-frontend/
│   ├── e2e/
│   │   └── workflows/
│   │       └── documentation/
│   │           ├── complete-event-workflow.spec.ts    # Original test (keep)
│   │           ├── screencast-event-workflow.spec.ts  # NEW: Continuous test
│   │           └── screencast/                        # NEW: Screencast assets
│   │               ├── README.md
│   │               ├── script-de.md
│   │               ├── subtitles-de.srt
│   │               └── narration-raw-de.m4a
│   ├── playwright.config.ts                           # Modified: Add screencast-training project
│   ├── package.json                                   # Modified: Add test:e2e:screencast script
│   └── test-results/                                  # Playwright output (gitignored)
│       └── screencast-event-workflow-.../
│           └── video.webm
│
└── docs/
    └── user-guide/
        ├── workflow/
        │   └── README.md                              # Modified: Add video link
        └── assets/
            └── videos/                                # NEW: Training videos
                └── workflow/
                    ├── event-workflow-schulung-de.mp4 # Final video
                    └── raw/
                        └── video.webm                 # Raw Playwright video
```

**8.4 Gitignore Configuration**

Verify `.gitignore` excludes test artifacts:
```bash
# Already in .gitignore:
web-frontend/test-results/
web-frontend/playwright-report/
```

Consider using Git LFS for video files if committing to repo (optional).

## Alternative: Git LFS for Video Storage (Optional)

If you want to commit the video to the repository:

**Install Git LFS:**
```bash
brew install git-lfs
cd /Users/nissim/dev/bat/BATbern-feature
git lfs install
```

**Track Video Files:**
```bash
echo "*.mp4 filter=lfs diff=lfs merge=lfs -text" >> .gitattributes
echo "*.webm filter=lfs diff=lfs merge=lfs -text" >> .gitattributes
echo "*.m4a filter=lfs diff=lfs merge=lfs -text" >> .gitattributes

git add .gitattributes
git commit -m "chore: configure Git LFS for video assets"
```

**Add Video:**
```bash
git add docs/user-guide/assets/videos/workflow/event-workflow-schulung-de.mp4
git commit -m "docs: add German event workflow training video"
```

**Alternative:** Host video externally (Vimeo, YouTube unlisted, AWS S3) and link from documentation.

## Estimated Timeline

| Task | Duration | Total |
|------|----------|-------|
| Step 1: Create screencast test file | 2-3 hours | 2-3h |
| Step 2: Configure Playwright | 30 min | 2.5-3.5h |
| Step 3: Record initial test run | 1 hour | 3.5-4.5h |
| Step 4: Write German narration script | 4-5 hours | 7.5-9.5h |
| Step 5: Generate German voiceover | 1-2 hours | 8.5-11.5h |
| Step 6: Create German subtitle file | 3-4 hours | 11.5-15.5h |
| Step 7: Combine video/audio/subs | 1-2 hours | 12.5-17.5h |
| Step 8: Documentation & organization | 1 hour | 13.5-18.5h |
| **TOTAL** | **13.5-18.5 hours** | **~3 days** |

**Recommended Schedule:**
- **Day 1**: Steps 1-3 (Test file creation, configuration, initial recording)
- **Day 2**: Steps 4-5 (German script writing, voiceover generation)
- **Day 3**: Steps 6-8 (Subtitles, video combination, documentation)

## Critical Files

### Files to Create
1. `web-frontend/e2e/workflows/documentation/screencast-event-workflow.spec.ts` - Continuous test (no screenshots)
2. `web-frontend/e2e/workflows/documentation/screencast/script-de.md` - German narration script
3. `web-frontend/e2e/workflows/documentation/screencast/subtitles-de.srt` - German subtitles
4. `web-frontend/e2e/workflows/documentation/screencast/narration-raw-de.m4a` - German voice recording
5. `web-frontend/e2e/workflows/documentation/screencast/README.md` - Asset documentation
6. `docs/user-guide/assets/videos/workflow/event-workflow-schulung-de.mp4` - Final training video

### Files to Modify
1. `web-frontend/playwright.config.ts` - Add `screencast-training` project
2. `web-frontend/package.json` - Add `test:e2e:screencast` script
3. `docs/user-guide/workflow/README.md` - Add video link

## Quality Checklist

### Video Quality
- ✅ Resolution: 1920x1080
- ✅ Frame rate: 25-30 fps
- ✅ No stuttering or lag
- ✅ All UI elements visible and readable
- ✅ Duration: ~43 minutes

### Audio Quality (German)
- ✅ Clear pronunciation (Anna voice)
- ✅ Consistent volume level
- ✅ Narration synced with video
- ✅ No audio clipping
- ✅ Proper pacing (nicht zu schnell)

### Subtitle Quality (German)
- ✅ Accurate timing (synced with speech)
- ✅ Proper formatting (42 chars/line)
- ✅ No spelling errors (Rechtschreibung)
- ✅ Readable as separate track
- ✅ UTF-8 encoding (ä, ö, ü, ß)

### Content Quality
- ✅ Covers all workflow phases (A-F)
- ✅ Explains WHY (not just WHAT)
- ✅ Highlights key UI elements
- ✅ Mentions common pitfalls
- ✅ Professional German tone

## Verification Testing

After completing the video:

1. **Play in VLC Player**: Verify subtitle track works
2. **Play in QuickTime**: Verify macOS compatibility
3. **Play in browser**: Test HTML5 video player support
4. **Check file size**: Should be 150-300 MB
5. **Verify metadata**: German language tags present
6. **Test subtitle toggle**: Can turn subtitles on/off

## Future Enhancements (Phase 2)

Once the MVP video is complete, consider:

1. **Chapter Markers**: Add MP4 chapter metadata for navigation
2. **English Version**: Create English script/subtitles/narration
3. **French Version**: Swiss audience (Geneva, Lausanne)
4. **Short Clips**: Extract 5-minute clips per phase
5. **Callouts**: Add text overlays highlighting key actions (requires advanced editing)
6. **Interactive Quiz**: Add assessment questions (requires LMS integration)
7. **Premium Voice**: Use ElevenLabs for more natural voice
8. **Professional Editing**: Add intro/outro, transitions, music

## Distribution Options

Where to host the final video:

1. **Git Repository** (with Git LFS): Direct access, version controlled
2. **Vimeo**: Professional hosting, analytics, player customization
3. **YouTube (unlisted)**: Free hosting, easy embedding, subtitle support
4. **AWS S3 + CloudFront**: Full control, CDN delivery, scalable
5. **Company LMS**: Internal training platform integration
6. **Documentation Site**: Direct MP4 embed in user guide

**Recommendation**: Start with Git LFS or YouTube unlisted, then migrate to company infrastructure as needed.

---

## Summary

This plan provides a complete roadmap for converting the E2E test into a German-language training screencast with:

- ✅ **Single continuous 43-minute video** (no phase separations)
- ✅ **German AI voiceover** using macOS `say` command (Anna voice)
- ✅ **Separate German subtitle track** (not burned in, toggleable)
- ✅ **Professional MP4 output** (H.264 + AAC, 1920x1080, ~150-300 MB)
- ✅ **Fully automated** Playwright video recording (no manual screen recording)
- ✅ **Well-documented** process for future updates

**Key Technical Decisions:**
1. Playwright native video recording (not external screen recording)
2. macOS `say` with Anna voice for German TTS
3. FFmpeg for post-production (video + audio + subtitle merging)
4. MP4 with separate subtitle track (mov_text codec)
5. Sequential test phases merged into one continuous test

**Expected Output:**
- File: `event-workflow-schulung-de.mp4`
- Duration: ~43 minutes
- Size: 150-300 MB
- Quality: Full HD (1920x1080)
- Audio: German narration (AAC 128kbps)
- Subtitles: German (separate track, toggleable)

Ready for implementation! 🎬
