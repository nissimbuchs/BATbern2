#!/usr/bin/env python3
"""
Extract speaker portrait images from BAT PDFs and update JSON files
"""
import json
import os
import subprocess
import tempfile
import shutil
import re

def get_image_dimensions(filepath):
    """Get image dimensions using file command"""
    result = subprocess.run(['file', filepath], capture_output=True, text=True)
    output = result.stdout

    # Parse dimensions from output like "146x188"
    match = re.search(r'(\d+)x(\d+)', output)
    if match:
        width = int(match.group(1))
        height = int(match.group(2))
        return width, height
    return None, None

def is_portrait_image(filepath, min_height=80, max_height=250):
    """Determine if image is likely a portrait photo"""
    width, height = get_image_dimensions(filepath)
    if not width or not height:
        return False

    # Portrait orientation (taller than wide) and reasonable size
    is_portrait_orientation = height > width
    is_reasonable_size = min_height <= height <= max_height

    return is_portrait_orientation and is_reasonable_size

def sanitize_filename(name):
    """Convert speaker name to filename"""
    # Remove titles like Dr., Prof., etc.
    name = re.sub(r'\b(Dr\.|Prof\.|Mr\.|Mrs\.|Ms\.)\s*', '', name)

    # Get first and last name
    parts = name.split(',')[0].strip().split()
    if len(parts) >= 2:
        filename = f"{parts[0]}{parts[-1]}.jpg"
    else:
        filename = f"{parts[0]}.jpg" if parts else "Unknown.jpg"

    # Remove special characters
    filename = re.sub(r'[^\w\-.]', '', filename)

    return filename

def extract_portraits_from_pdf(pdf_path, bat_num, speakers):
    """Extract portrait images from PDF and match to speakers"""
    portraits = {}

    with tempfile.TemporaryDirectory() as tmpdir:
        # Extract all images from PDF
        prefix = os.path.join(tmpdir, f'bat{bat_num:02d}')
        subprocess.run(['pdfimages', '-j', pdf_path, prefix],
                      capture_output=True, check=True)

        # Find portrait images
        portrait_files = []
        for filename in sorted(os.listdir(tmpdir)):
            if filename.endswith('.jpg'):
                filepath = os.path.join(tmpdir, filename)
                if is_portrait_image(filepath):
                    portrait_files.append(filepath)

        print(f"  Found {len(portrait_files)} portrait images for {len(speakers)} speakers")

        # Match portraits to speakers (in order)
        portraits_dir = '/Users/nissim/dev/bat/BATbern-main/apps/BATspa-old/public/portraits'
        os.makedirs(portraits_dir, exist_ok=True)

        for idx, (portrait_file, speaker) in enumerate(zip(portrait_files, speakers)):
            speaker_name = speaker['name']
            portrait_filename = sanitize_filename(speaker_name)

            # Copy portrait to portraits directory
            dest_path = os.path.join(portraits_dir, portrait_filename)
            shutil.copy2(portrait_file, dest_path)

            portraits[speaker_name] = portrait_filename
            print(f"    → {speaker_name}: {portrait_filename}")

    return portraits

def process_bat_event(bat_num):
    """Process a single BAT event"""
    print(f"\nProcessing BAT {bat_num}...")

    # Load extracted JSON
    json_path = f'/Users/nissim/dev/bat/BATbern-main/.ai/extracted-data/bat-{bat_num:02d}.json'
    if not os.path.exists(json_path):
        print(f"  ⚠ JSON file not found, skipping")
        return

    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    # Check if this BAT has presentations
    presentations = data.get('presentations', [])
    if not presentations:
        print(f"  ⚠ No presentations found, skipping")
        return

    # Find PDF file
    pdf_mapping = {
        1: 'BAT_Nr.01.pdf',
        2: 'BAT_Nr.02.pdf',
        3: 'BAT_Nr.03.pdf',
        4: 'BAT_Nr.04.pdf',
        5: 'BAT_Nr.05.pdf',
        6: 'BAT_Nr.06.pdf',
        7: 'BAT_Nr.07.pdf',
        8: 'BAT_Nr.08.pdf',
        9: 'BAT_Nr.09.pdf',
        10: 'BAT_Nr.10.pdf',
        11: 'BAT_No11.pdf',
        12: 'BAT_No12.pdf',
        13: 'BAT_No13.pdf',
        14: 'BAT_No14.pdf',
        15: 'BAT_No15.pdf',
        16: 'BAT_No16.pdf',
        17: 'BAT_No17.pdf',
        18: 'BAT_No18.pdf',
        19: 'BAT_No19.pdf',
        20: 'BAT_No20.pdf',
        21: 'BAT_No21_v1.1.pdf',
        22: 'BAT_No22_v1.1.pdf',
        23: 'BAT_No23_V1.1.pdf',
        24: 'BAT_No24_V1.0.pdf',
        25: 'BAT_No25.pdf',
        26: 'BAT_No26.pdf',
        27: 'BAT_No27.pdf',
        28: 'BAT_No28.pdf',
        29: 'BAT_No29.pdf',
        30: 'BAT_No30.pdf',
        31: 'BAT_No31.pdf',
        32: 'BAT32_Programmheft.pdf',
        33: 'BAT33_Programmheft.pdf',
        34: 'BAT34_Programmheft.pdf',
        35: 'BAT35_Programmheft.pdf',
        36: 'BAT36_Programmheft.pdf',
        37: 'BAT37_Programmheft.pdf',
        38: 'BAT38_Programmheft.pdf',
        39: 'BAT39_Programmheft.pdf',
        40: 'BAT40_00_Programmheft.pdf',
        41: 'BAT41_Programmheft.pdf',
    }

    pdf_filename = pdf_mapping.get(bat_num)
    if not pdf_filename:
        print(f"  ⚠ No PDF mapping found, skipping")
        return

    pdf_path = f'/Users/nissim/dev/bat/BATbern-main/apps/BATspa-old/docs/moderations/{pdf_filename}'
    if not os.path.exists(pdf_path):
        print(f"  ⚠ PDF not found: {pdf_path}")
        return

    # Collect all speakers
    all_speakers = []
    for presentation in presentations:
        speakers = presentation.get('speakers', [])
        all_speakers.extend(speakers)

    if not all_speakers:
        print(f"  ⚠ No speakers found, skipping")
        return

    # Extract portraits
    try:
        portraits = extract_portraits_from_pdf(pdf_path, bat_num, all_speakers)

        # Update JSON with portrait filenames
        for presentation in presentations:
            for speaker in presentation.get('speakers', []):
                speaker_name = speaker['name']
                if speaker_name in portraits:
                    speaker['portrait'] = portraits[speaker_name]

        # Save updated JSON
        with open(json_path, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)

        print(f"  ✓ Updated JSON with {len(portraits)} portrait(s)")

    except Exception as e:
        print(f"  ✗ Error: {e}")

if __name__ == '__main__':
    import sys

    if len(sys.argv) > 1:
        # Process specific BAT events from command line
        bat_numbers = [int(arg) for arg in sys.argv[1:]]
    else:
        # Process all BAT events 1-41 by default
        bat_numbers = range(1, 42)

    total = len(bat_numbers)
    success_count = 0

    for idx, bat_num in enumerate(bat_numbers, 1):
        print(f"\n[{idx}/{total}] ", end='')
        try:
            process_bat_event(bat_num)
            success_count += 1
        except Exception as e:
            print(f"Processing BAT {bat_num}...")
            print(f"  ✗ Unexpected error: {e}")

    print(f"\n{'='*60}")
    print(f"Completed: {success_count}/{total} BAT events processed successfully")
    print(f"{'='*60}")
