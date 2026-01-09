# Screencast Narration Synchronization Guide

This guide explains how to synchronize the Playwright screencast with ElevenLabs voiceover narration.

## Problem

The Playwright screencast executes in ~5-10 minutes, but the German voiceover audio is 740.7 seconds (~12.3 minutes). We need to add strategic delays to synchronize the visual actions with the narration.

## Solution Architecture

**3-Tier Approach:**

1. **Extract timestamps** from ElevenLabs using Forced Alignment API (Python)
2. **Generate timing config** mapping narration markers to delays (TypeScript)
3. **Inject delays** into Playwright script using helper function (TypeScript)

## Prerequisites

- Python 3.8+ with pip
- Node.js 20+ with npm
- ElevenLabs API key (get from https://elevenlabs.io)
- Audio file: `narration-raw-de.m4a` (generated from ElevenLabs TTS)
- Script file: `script-for-tts-de-funny-with-emotions.txt`

## Setup

### 1. Install Python Dependencies

```bash
cd web-frontend/e2e/workflows/documentation/screencast/scripts
pip install elevenlabs python-dotenv
```

### 2. Configure Environment

Create a `.env` file in the `scripts` directory:

```bash
ELEVENLABS_API_KEY=your_api_key_here
```

Or set the environment variable:

```bash
export ELEVENLABS_API_KEY=your_api_key_here
```

## Step-by-Step Process

### Step 1: Extract Alignment Data

Extract word-level timestamps from the audio file using ElevenLabs Forced Alignment API:

```bash
cd web-frontend/e2e/workflows/documentation/screencast/scripts
python extract-alignment.py
```

**Output:** `alignment-data.json` with word timestamps

**What it does:**

- Reads `narration-raw-de.m4a` audio file
- Reads `script-for-tts-de-funny-with-emotions.txt` narration script
- Calls ElevenLabs API to align audio with text
- Saves word-level timestamps to JSON

**Expected output:**

```
🎬 Extracting alignment data from ElevenLabs...
📁 Reading audio file: ../narration-raw-de.m4a
   ✓ Audio file loaded (X.X MB)
📄 Reading narration script: ../script-for-tts-de-funny-with-emotions.txt
   ✓ Script loaded (XXXX characters)
🔍 Requesting forced alignment from ElevenLabs API...
   ✓ Alignment data received
📊 Processing alignment data...
   ✓ Extracted XXX words
💾 Saving alignment data to: ../alignment-data.json
   ✓ File saved (XX.X KB)
✅ Alignment extraction complete!
```

### Step 2: Generate Timing Configuration

Generate TypeScript timing configuration from alignment data:

```bash
npx ts-node generate-timing-config.ts
```

**Output:** `timing-config.ts` with calculated delays

**What it does:**

- Reads `alignment-data.json` (ElevenLabs timestamps)
- Reads `script-for-tts-de-funny-with-emotions.txt` (narration script)
- Maps 27 narration markers to script paragraphs
- Calculates delays = narration duration + UI buffer (2.5s)
- Generates TypeScript configuration file

**Expected output:**

```
🎬 Generating timing configuration...
📁 Reading alignment data: ../alignment-data.json
   ✓ Loaded XXX words with timestamps
📄 Reading narration script: ../script-for-tts-de-funny-with-emotions.txt
   ✓ Loaded XX paragraphs
📊 Processing XX narration markers...

=== TIMING SUMMARY ===
Total calculated delays: XXX.Xs
Target audio duration: 740.7s
Difference: X.Xs (X.X%)
✅ Timing within acceptable range

💾 Generated timing config: ../timing-config.ts
   XX markers configured
```

### Step 3: Verify Timing

Run the verification test to check timing accuracy:

```bash
cd web-frontend
npm run test:e2e -- verify-timing.spec.ts
```

**What it does:**

- Checks that total delays ≈ 740.7s (within 5% tolerance)
- Verifies minimum/maximum delay bounds
- Ensures all narration markers are configured

**Expected output:**

```
=== TIMING VERIFICATION ===
Configured segments: 27
Total delay: XXX.Xs
Target duration: 740.7s
Difference: X.Xs (X.X%)
✅ Timing within acceptable range
```

### Step 4: Modify Playwright Script (Optional)

If you want to update the Playwright script to use the new timing helpers:

**Before:**

```typescript
console.log('[NARRATION_00:00] Willkommen zur BATbern Event-Management-Plattform');
await page.waitForTimeout(2000); // Narration pause
```

**After:**

```typescript
import { waitForNarration, logNarration } from './screencast/timing-helper';

logNarration('NARRATION_00:00', 'Willkommen zur BATbern Event-Management-Plattform');
await waitForNarration('NARRATION_00:00', page);
```

### Step 5: Record Screencast

Record the screencast with synchronized timing:

```bash
npm run test:e2e:screencast
```

The test will now pause at each narration marker for the calculated duration.

### Step 6: Combine Video and Audio

Combine the recorded video with the narration audio:

```bash
ffmpeg -i test-results/*/video.webm -i narration-raw-de.m4a -c:v copy -c:a aac output.mp4
```

## File Structure

```
screencast/
├── script-for-tts-de-funny-with-emotions.txt  # Narration script with emotion tags
├── narration-raw-de.m4a                       # Audio file from ElevenLabs
├── alignment-data.json                         # Word timestamps (generated)
├── timing-config.ts                           # Timing configuration (generated)
├── timing-helper.ts                           # Helper functions
├── verify-timing.spec.ts                      # Verification test
├── SYNCHRONIZATION-GUIDE.md                   # This file
└── scripts/
    ├── extract-alignment.py                   # Step 1: Extract timestamps
    └── generate-timing-config.ts              # Step 2: Generate config
```

## Troubleshooting

### Error: "alignment-data.json not found"

**Solution:** Run `python extract-alignment.py` first.

### Error: "ELEVENLABS_API_KEY not set"

**Solution:** Set the environment variable or create `.env` file in `scripts/` directory.

### Warning: "Timing difference exceeds 5% tolerance"

**Solution:** Manually adjust delays in `timing-config.ts`:

1. Open `timing-config.ts`
2. Find segments with unusually long/short delays
3. Adjust `delayAfter` values
4. Re-run verification test

### Error: "Module not found: timing-config"

**Solution:** Run `npx ts-node scripts/generate-timing-config.ts` to generate the config file.

## Manual Timing Adjustments

If automatic timing doesn't work perfectly, you can manually adjust delays in `timing-config.ts`:

```typescript
export const timingConfig: NarrationSegment[] = [
  {
    marker: 'NARRATION_00:00',
    targetTime: 0,
    narrationDuration: 15.5,
    delayAfter: 18000, // Adjust this value (in milliseconds)
    description: 'Willkommen zur BATbern Event-Management-Plattform...',
  },
  // ... more segments
];
```

**Tips:**

- Add 1000-2000ms for phase transitions
- Add 2500ms UI buffer for user to see actions complete
- Keep delays between 3000-60000ms (3-60 seconds)

## Timing Configuration

**Delay Calculation:**

```
delayAfter = (narrationDuration * 1000) + uiBuffer + phaseBuffer

Where:
- narrationDuration: Actual speech duration from ElevenLabs (seconds)
- uiBuffer: 2500ms (time for UI actions to complete visually)
- phaseBuffer: 2000ms for phase transitions, 0ms otherwise
```

**Tolerance:**

- Target: Within 5% of 740.7s (37 seconds)
- Acceptable range: 703-778 seconds
- If outside range, manually adjust `timing-config.ts`

## Next Steps

After synchronization:

1. Record final screencast with synchronized timing
2. Combine video + audio using ffmpeg
3. Add subtitles if desired (use alignment data for precise timing)
4. Review and publish training video

## Links

- ElevenLabs API Docs: https://elevenlabs.io/docs
- Forced Alignment: https://elevenlabs.io/docs/api-reference/forced-alignment
- Playwright Documentation: https://playwright.dev
