# Professional Voice Generation Guide

## Option 1: ElevenLabs (Recommended - Best Quality)

**Pros:**

- Natural, human-like voices
- Excellent German support
- Professional quality
- Fine control over emotion, pace, stability

**Cons:**

- Paid service (free tier: 10,000 characters/month)
- Requires account signup

### Setup:

1. **Sign up**: https://elevenlabs.io/
2. **Free Tier**: 10,000 characters/month (enough for ~7 minutes)
3. **Paid Plans**:
   - Starter: $5/month - 30,000 characters (~20 minutes)
   - Creator: $22/month - 100,000 characters (~70 minutes) ← **Recommended for 43-minute video**
   - Pro: $99/month - 500,000 characters

### Usage:

**Method A: Web Interface (Easiest)**

1. Go to https://elevenlabs.io/speech-synthesis
2. Select Voice: Choose a German voice (e.g., "Freya" - German Female, Professional)
3. Paste text from `script-for-tts-de.txt`
4. Adjust settings:
   - Stability: 50-60 (balanced)
   - Clarity + Similarity: 75 (clear speech)
5. Click "Generate"
6. Download as MP3
7. Convert to M4A:
   ```bash
   ffmpeg -i elevenlabs-output.mp3 -c:a aac -b:a 128k narration-raw-de.m4a
   ```

**Method B: API (for automation)**

```bash
# Install ElevenLabs CLI
pip install elevenlabs

# Set API key (get from https://elevenlabs.io/api)
export ELEVEN_API_KEY="your_api_key_here"

# Generate speech
elevenlabs text-to-speech \
  --voice "Freya" \
  --text "$(cat script-for-tts-de.txt)" \
  --output-format mp3_44100_128 \
  --model-id eleven_multilingual_v2 \
  > narration-elevenlabs.mp3

# Convert to M4A
ffmpeg -i narration-elevenlabs.mp3 -c:a aac -b:a 128k narration-raw-de.m4a
```

**Best German Voices on ElevenLabs:**

- **Freya** - German Female, Professional, Clear
- **Liam** - German Male, Warm, Authoritative
- **Charlotte** - German Female, Conversational
- **Daniel** - German Male, Narrative style

---

## Option 2: Google Cloud Text-to-Speech (Very Good Quality)

**Pros:**

- High-quality WaveNet voices
- Excellent German support
- Pay-as-you-go (no monthly fee)
- Official Google quality

**Cons:**

- Requires Google Cloud account
- Credit card required (even for free tier)
- More complex setup

### Pricing:

- WaveNet voices: $16 per 1 million characters
- For 43-minute video (~8,500 words = ~60,000 characters): **~$0.96**

### Setup:

1. **Create Google Cloud Account**: https://cloud.google.com/
2. **Enable Text-to-Speech API**: https://console.cloud.google.com/apis/library/texttospeech.googleapis.com
3. **Create Service Account Key**: Download JSON key
4. **Install Google Cloud SDK**:
   ```bash
   brew install google-cloud-sdk
   ```

### Usage:

```bash
# Authenticate
gcloud auth activate-service-account --key-file=path/to/key.json

# Generate speech (Python script)
cat > generate_voice_google.py << 'EOF'
from google.cloud import texttospeech
import os

os.environ['GOOGLE_APPLICATION_CREDENTIALS'] = 'path/to/key.json'

client = texttospeech.TextToSpeechClient()

# Read script
with open('script-for-tts-de.txt', 'r') as f:
    text = f.read()

# Configure request
synthesis_input = texttospeech.SynthesisInput(text=text)

voice = texttospeech.VoiceSelectionParams(
    language_code="de-DE",
    name="de-DE-Wavenet-F",  # Female, professional
    ssml_gender=texttospeech.SsmlVoiceGender.FEMALE
)

audio_config = texttospeech.AudioConfig(
    audio_encoding=texttospeech.AudioEncoding.MP3,
    speaking_rate=0.95,  # Slightly slower for training
    pitch=0.0
)

# Generate speech
response = client.synthesize_speech(
    input=synthesis_input,
    voice=voice,
    audio_config=audio_config
)

# Save to file
with open('narration-google.mp3', 'wb') as out:
    out.write(response.audio_content)

print("Audio saved to narration-google.mp3")
EOF

python3 generate_voice_google.py

# Convert to M4A
ffmpeg -i narration-google.mp3 -c:a aac -b:a 128k narration-raw-de.m4a
```

**Best German Voices:**

- `de-DE-Wavenet-F` - Female, Clear, Professional ← **Recommended**
- `de-DE-Wavenet-E` - Male, Authoritative
- `de-DE-Neural2-F` - Female, Modern (newer model)
- `de-DE-Neural2-D` - Male, Warm

---

## Option 3: Amazon Polly (Good Quality)

**Pros:**

- Good quality Neural voices
- AWS integration (if already using AWS)
- Free tier: 5 million characters/month for 12 months

**Cons:**

- AWS account required
- Slightly less natural than ElevenLabs

### Pricing:

- Neural voices: $16 per 1 million characters
- Free tier: 5 million characters/month (first 12 months)
- For 43-minute video: **FREE** (under free tier limit)

### Setup:

```bash
# Install AWS CLI
brew install awscli

# Configure AWS credentials
aws configure

# Generate speech
aws polly synthesize-speech \
    --output-format mp3 \
    --voice-id Vicki \
    --engine neural \
    --language-code de-DE \
    --text file://script-for-tts-de.txt \
    narration-polly.mp3

# Convert to M4A
ffmpeg -i narration-polly.mp3 -c:a aac -b:a 128k narration-raw-de.m4a
```

**Best German Voices:**

- `Vicki` - Female, News-style, Clear
- `Daniel` - Male, Conversational

---

## Option 4: Manual Voice Recording (Highest Quality)

**Pros:**

- Most natural and authentic
- Complete control over tone, pace, emotion
- No word/character limits

**Cons:**

- Time-consuming (2-3 hours recording + editing)
- Requires good microphone
- Requires audio editing skills

### Tools:

**macOS (Free):**

- **GarageBand** - Built-in, professional features
- **QuickTime Player** - Simple recording
- **Audacity** - Free, cross-platform, powerful

**Paid:**

- **Logic Pro** - Professional DAW ($199 one-time)
- **Adobe Audition** - Industry standard

### Process:

1. **Setup**:
   - Quiet room
   - Good microphone (USB mic like Blue Yeti ~$100)
   - Pop filter (reduces plosives)

2. **Recording**:

   ```bash
   # Open GarageBand
   # Create new Voice project
   # Hit Record and read script-de.md
   # Take breaks every 5-10 minutes
   # Re-record difficult sections
   ```

3. **Editing**:
   - Remove breaths, clicks, mistakes
   - Normalize volume
   - Add slight compression
   - Export as M4A (AAC 128kbps)

4. **Tips**:
   - Read slowly and clearly
   - Emphasize key words
   - Pause naturally between sections
   - Stay consistent in tone and energy

---

## Recommendation Matrix

| Use Case              | Recommended Option | Cost              | Quality    |
| --------------------- | ------------------ | ----------------- | ---------- |
| **Quick MVP**         | Google Cloud TTS   | ~$1               | ⭐⭐⭐⭐   |
| **Best Quality (AI)** | ElevenLabs         | $22/month         | ⭐⭐⭐⭐⭐ |
| **AWS Users**         | Amazon Polly       | Free (first year) | ⭐⭐⭐⭐   |
| **Authentic Voice**   | Manual Recording   | Time + Mic        | ⭐⭐⭐⭐⭐ |
| **Free (acceptable)** | macOS `say`        | Free              | ⭐⭐       |

---

## Quick Start: ElevenLabs (Recommended)

**5-Minute Setup:**

1. Sign up: https://elevenlabs.io/sign-up
2. Choose "Creator" plan ($22/month) for 100k characters
3. Go to Speech Synthesis: https://elevenlabs.io/speech-synthesis
4. Select voice: "Freya" (German, Female, Professional)
5. Paste content from `script-for-tts-de.txt`
6. Settings:
   - Stability: 55
   - Clarity: 75
   - Style: 0 (neutral for training)
7. Click "Generate"
8. Download MP3
9. Convert:
   ```bash
   cd /Users/nissim/dev/bat/BATbern-feature/web-frontend/e2e/workflows/documentation/screencast
   ffmpeg -i ~/Downloads/elevenlabs_*.mp3 -c:a aac -b:a 128k narration-raw-de.m4a
   ```

**Done!** You now have professional-quality German narration ready for Step 7 (combining with video).

---

## Comparison Audio Samples

To hear the difference, test with this sentence:

```bash
# macOS say (robotic)
say -v Shelley "Willkommen zur BATbern Event-Management-Plattform" -o test-macos.aiff
afplay test-macos.aiff

# Then compare with ElevenLabs sample (natural, human-like)
# Upload same text to elevenlabs.io and listen
```

The difference is night and day. ElevenLabs sounds like a real person.

---

## Support

- ElevenLabs Docs: https://docs.elevenlabs.io/
- Google Cloud TTS: https://cloud.google.com/text-to-speech/docs
- Amazon Polly: https://docs.aws.amazon.com/polly/

---

**Next Steps:**

1. Choose your voice generation method above
2. Generate `narration-raw-de.m4a`
3. Proceed to Step 6 (creating subtitles)
4. Then Step 7 (combine with FFmpeg)
