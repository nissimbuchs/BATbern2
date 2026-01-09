#!/usr/bin/env python3
"""
Extract alignment data from ElevenLabs Forced Alignment API.

This script uses the ElevenLabs API to extract word-level timestamps
from the generated narration audio file by aligning it with the script text.

Requirements:
    pip install elevenlabs python-dotenv

Environment Variables:
    ELEVENLABS_API_KEY: Your ElevenLabs API key

Usage:
    cd web-frontend/e2e/workflows/documentation/screencast/scripts
    python extract-alignment.py
"""

import os
import json
from io import BytesIO
from elevenlabs.client import ElevenLabs
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

# Initialize ElevenLabs client
elevenlabs = ElevenLabs(api_key=os.getenv("ELEVENLABS_API_KEY"))

print("🎬 Extracting alignment data from ElevenLabs...")
print()

# Read audio file
audio_file = "../narration-raw-de.m4a"
print(f"📁 Reading audio file: {audio_file}")
with open(audio_file, "rb") as f:
    audio_data = BytesIO(f.read())
print(f"   ✓ Audio file loaded ({len(audio_data.getvalue()) / 1024 / 1024:.1f} MB)")

# Read narration script (funny version with emotion tags)
script_file = "../script-for-tts-de-funny-with-emotions.txt"
print(f"📄 Reading narration script: {script_file}")
with open(script_file, "r", encoding="utf-8") as f:
    script_text = f.read()
print(f"   ✓ Script loaded ({len(script_text)} characters)")

# Get alignment data with word-level timestamps
print()
print("🔍 Requesting forced alignment from ElevenLabs API...")
print("   (This may take 10-30 seconds depending on audio length)")

try:
    alignment = elevenlabs.forced_alignment.create(
        file=audio_data,
        text=script_text
    )
    print("   ✓ Alignment data received")
except Exception as e:
    print(f"   ✗ Error: {e}")
    print()
    print("Troubleshooting:")
    print("  1. Check that ELEVENLABS_API_KEY is set in .env file")
    print("  2. Verify the audio file exists and is not corrupted")
    print("  3. Ensure the script text matches the audio narration")
    exit(1)

# Convert to JSON-serializable format
print()
print("📊 Processing alignment data...")

# Convert alignment data to serializable format
words_data = []
if hasattr(alignment, 'words') and alignment.words:
    for word in alignment.words:
        # Extract word text - try multiple attribute names
        word_text = None
        if hasattr(word, 'text'):
            word_text = word.text
        elif hasattr(word, 'word'):
            word_text = word.word
        else:
            word_text = str(word)

        words_data.append({
            "word": word_text,
            "start": word.start if hasattr(word, 'start') else 0.0,
            "end": word.end if hasattr(word, 'end') else 0.0
        })

characters_data = []
if hasattr(alignment, 'characters') and alignment.characters:
    for char in alignment.characters:
        # Extract character text - try multiple attribute names
        char_text = None
        if hasattr(char, 'text'):
            char_text = char.text
        elif hasattr(char, 'character'):
            char_text = char.character
        else:
            char_text = str(char)

        characters_data.append({
            "character": char_text,
            "start": char.start if hasattr(char, 'start') else 0.0,
            "end": char.end if hasattr(char, 'end') else 0.0
        })

alignment_data = {
    "audio_duration": 740.7,  # Known duration from audio file
    "words": words_data,
    "characters": characters_data,
    "paragraphs": []  # Will be processed in next step
}

print(f"   ✓ Extracted {len(words_data)} words")
print(f"   ✓ Extracted {len(characters_data)} characters")

# Save to JSON file
output_file = "../alignment-data.json"
print()
print(f"💾 Saving alignment data to: {output_file}")
with open(output_file, "w", encoding="utf-8") as f:
    json.dump(alignment_data, f, indent=2, ensure_ascii=False)

print(f"   ✓ File saved ({os.path.getsize(output_file) / 1024:.1f} KB)")
print()
print("✅ Alignment extraction complete!")
print()
print("Next steps:")
print("  1. Review alignment-data.json to verify word timestamps")
print("  2. Run: npx ts-node generate-timing-config.ts")
