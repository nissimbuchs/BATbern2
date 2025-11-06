#!/usr/bin/env python3
"""
Extract speaker portrait images from BAT PDFs using page-based matching
"""
import json
import os
import re
import fitz  # PyMuPDF

def sanitize_filename(name):
    """Convert speaker name to firstname.lastname.jpg format (lowercase)"""
    # Remove titles like Dr., Prof., etc.
    name = re.sub(r'\b(Dr\.|Prof\.|Mr\.|Mrs\.|Ms\.)\s*', '', name)

    # Get first and last name
    parts = name.split(',')[0].strip().split()

    if len(parts) >= 2:
        # Format: firstname.lastname.jpg (all lowercase)
        firstname = parts[0].lower()
        lastname = parts[-1].lower()
        filename = f"{firstname}.{lastname}.jpg"
    elif len(parts) == 1:
        filename = f"{parts[0].lower()}.jpg"
    else:
        filename = "unknown.jpg"

    # Remove special characters except dots
    filename = re.sub(r'[^\w\-.]', '', filename)

    return filename

def find_speaker_page(pdf_doc, speaker_name, speaker_bio):
    """Find which page contains the speaker's information using bio matching"""
    if not speaker_bio:
        return None

    # Extract last name for matching
    last_name = speaker_name.split()[-1] if speaker_name.split() else speaker_name

    # Use first 50-100 words of bio for matching (more distinctive)
    bio_words = speaker_bio.split()
    bio_snippet = ' '.join(bio_words[:50]) if len(bio_words) > 50 else speaker_bio

    # Try to find page with best match score
    best_page = None
    best_score = 0

    for page_num in range(len(pdf_doc)):
        page = pdf_doc[page_num]
        text = page.get_text()

        score = 0

        # Check if last name appears
        if last_name in text:
            score += 1

        # Check how much of the bio appears on this page
        # Split bio into chunks and count matches
        bio_chunks = [bio_snippet[i:i+30] for i in range(0, len(bio_snippet), 30)]
        matching_chunks = sum(1 for chunk in bio_chunks if chunk in text)

        if matching_chunks > 0:
            score += matching_chunks * 10  # Bio text is more important

        # Update best match if this page scores higher
        if score > best_score:
            best_score = score
            best_page = page_num

    # Only return if we found a reasonably good match
    return best_page if best_score > 5 else None

def extract_portrait_from_page(pdf_doc, page_num, speaker_name, output_dir, shared_xrefs=None, used_xrefs=None, speaker_index=0, adjacent_pages_xrefs=None):
    """Extract portrait image from a specific page using spatial matching to speaker name"""
    page = pdf_doc[page_num]
    images = page.get_images(full=True)
    image_info_list = page.get_image_info()

    if not images or not image_info_list:
        return None, None

    if shared_xrefs is None:
        shared_xrefs = set()

    if used_xrefs is None:
        used_xrefs = set()

    if adjacent_pages_xrefs is None:
        adjacent_pages_xrefs = {}

    # Find the position of the speaker's name on the page
    last_name = speaker_name.split()[-1] if speaker_name.split() else speaker_name
    text_instances = page.search_for(last_name)

    if not text_instances:
        return None, None

    # Use the first occurrence of the name
    name_bbox = text_instances[0]
    name_y = (name_bbox.y0 + name_bbox.y1) / 2  # Vertical center of name

    # Collect all valid portrait images with their positions
    portrait_candidates = {}

    for img_index, (img, img_info) in enumerate(zip(images, image_info_list)):
        xref = img[0]

        # Skip shared/template portraits that appear on non-adjacent pages
        if xref in shared_xrefs:
            continue

        # Skip portraits that have already been used for other speakers
        if xref in used_xrefs:
            continue

        # Skip if we've already processed this image
        if xref in portrait_candidates:
            continue

        try:
            # Get image position from image_info
            bbox = img_info['bbox']
            img_rect = fitz.Rect(bbox)

            base_image = pdf_doc.extract_image(xref)
            image_bytes = base_image["image"]
            image_ext = base_image["ext"]
            width = base_image["width"]
            height = base_image["height"]

            # Check if it's portrait-oriented and reasonable size
            if height > width and 80 <= height <= 300 and 60 <= width <= 250:
                # Calculate distance from speaker's name
                img_y = (img_rect.y0 + img_rect.y1) / 2
                distance = abs(img_y - name_y)

                portrait_candidates[xref] = {
                    'bytes': image_bytes,
                    'ext': image_ext,
                    'width': width,
                    'height': height,
                    'distance': distance,
                    'xref': xref,
                    'position': img_y
                }

        except Exception as e:
            # Skip problematic images
            continue

    if not portrait_candidates:
        return None, None

    # Check if we're in a two-page spread scenario:
    # Multiple portraits on adjacent pages shared with other speakers
    # In this case, match by vertical position order, not proximity
    is_two_page_spread = len(portrait_candidates) > 1 and any(
        len(adjacent_pages_xrefs.get(xref, [])) > 1
        for xref in portrait_candidates.keys()
    )

    if is_two_page_spread:
        # Two-page spread: match portraits by vertical position to speaker index
        # Sort portraits by vertical position (top to bottom)
        sorted_portraits = sorted(portrait_candidates.values(), key=lambda x: x['position'])

        # Pick the portrait corresponding to this speaker's index
        if speaker_index < len(sorted_portraits):
            best_portrait = sorted_portraits[speaker_index]
            print(f"      [Two-page spread detected: using position-based matching, speaker #{speaker_index}]")
        else:
            # Fallback if index out of range
            best_portrait = sorted_portraits[0]

    elif len(portrait_candidates) == 1:
        best_portrait = list(portrait_candidates.values())[0]
    else:
        # Score each candidate by quality and proximity
        for candidate in portrait_candidates.values():
            quality_score = candidate['width'] * candidate['height']
            distance = candidate['distance']

            if distance < 100:
                candidate['final_score'] = quality_score * (100 / (distance + 1))
            else:
                candidate['final_score'] = quality_score / (distance / 10)

        best_portrait = max(portrait_candidates.values(), key=lambda x: x['final_score'])

    filename = sanitize_filename(speaker_name)
    filepath = os.path.join(output_dir, filename)

    with open(filepath, 'wb') as f:
        f.write(best_portrait['bytes'])

    if len(portrait_candidates) == 1:
        print(f"      ✓ Extracted portrait: {filename} ({best_portrait['width']}x{best_portrait['height']})")
    else:
        print(f"      ✓ Extracted portrait: {filename} ({best_portrait['width']}x{best_portrait['height']}) [best of {len(portrait_candidates)} candidates, distance={best_portrait['distance']:.1f}]")

    return filename, best_portrait['xref']

def get_portrait_xrefs_by_page(pdf_doc):
    """Get portrait xrefs for each page and identify template portraits"""
    xref_to_pages = {}  # xref -> list of page numbers

    for page_num in range(len(pdf_doc)):
        page = pdf_doc[page_num]
        images = page.get_images(full=True)

        for img in images:
            xref = img[0]

            try:
                base_image = pdf_doc.extract_image(xref)
                width = base_image["width"]
                height = base_image["height"]

                # Check if it's a portrait
                if height > width and 80 <= height <= 300 and 60 <= width <= 250:
                    if xref not in xref_to_pages:
                        xref_to_pages[xref] = []
                    xref_to_pages[xref].append(page_num)
            except:
                pass

    # Identify template xrefs that appear on NON-ADJACENT pages
    # (portraits on adjacent pages are likely part of the same session/spread)
    template_xrefs = set()

    for xref, pages in xref_to_pages.items():
        if len(pages) > 1:
            # Check if all pages are adjacent
            pages_sorted = sorted(pages)
            all_adjacent = all(
                pages_sorted[i+1] - pages_sorted[i] == 1
                for i in range(len(pages_sorted) - 1)
            )

            # If NOT all adjacent, it's a template
            if not all_adjacent:
                template_xrefs.add(xref)

    return xref_to_pages, template_xrefs

def process_bat_event(bat_num):
    """Process a single BAT event"""
    print(f"\nProcessing BAT {bat_num}...")

    # Load extracted JSON
    json_path = f'/Users/nissim/dev/bat/BATbern-main/.ai/extracted-data/bat-{bat_num:02d}.json'
    if not os.path.exists(json_path):
        print(f"  ⚠ JSON file not found")
        return 0

    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    presentations = data.get('presentations', [])
    if not presentations:
        print(f"  ⚠ No presentations found")
        return 0

    # PDF mapping
    pdf_mapping = {
        1: 'BAT_Nr.01.pdf', 2: 'BAT_Nr.02.pdf', 3: 'BAT_Nr.03.pdf',
        4: 'BAT_Nr.04.pdf', 5: 'BAT_Nr.05.pdf', 6: 'BAT_Nr.06.pdf',
        7: 'BAT_Nr.07.pdf', 8: 'BAT_Nr.08.pdf', 9: 'BAT_Nr.09.pdf',
        10: 'BAT_Nr.10.pdf', 11: 'BAT_No11.pdf', 12: 'BAT_No12.pdf',
        13: 'BAT_No13.pdf', 14: 'BAT_No14.pdf', 15: 'BAT_No15.pdf',
        16: 'BAT_No16.pdf', 17: 'BAT_No17.pdf', 18: 'BAT_No18.pdf',
        19: 'BAT_No19.pdf', 20: 'BAT_No20.pdf', 21: 'BAT_No21_v1.1.pdf',
        22: 'BAT_No22_v1.1.pdf', 23: 'BAT_No23_V1.1.pdf', 24: 'BAT_No24_V1.0.pdf',
        25: 'BAT_No25.pdf', 26: 'BAT_No26.pdf', 27: 'BAT_No27.pdf',
        28: 'BAT_No28.pdf', 29: 'BAT_No29.pdf', 30: 'BAT_No30.pdf',
        31: 'BAT_No31.pdf', 32: 'BAT32_Programmheft.pdf', 33: 'BAT33_Programmheft.pdf',
        34: 'BAT34_Programmheft.pdf', 35: 'BAT35_Programmheft.pdf',
        36: 'BAT36_Programmheft.pdf', 37: 'BAT37_Programmheft.pdf',
        38: 'BAT38_Programmheft.pdf', 39: 'BAT39_Programmheft.pdf',
        40: 'BAT40_00_Programmheft.pdf', 41: 'BAT41_Programmheft.pdf',
    }

    pdf_filename = pdf_mapping.get(bat_num)
    if not pdf_filename:
        print(f"  ⚠ No PDF mapping")
        return 0

    pdf_path = f'/Users/nissim/dev/bat/BATbern-main/apps/BATspa-old/docs/moderations/{pdf_filename}'
    if not os.path.exists(pdf_path):
        print(f"  ⚠ PDF not found")
        return 0

    # Open PDF
    try:
        pdf_doc = fitz.open(pdf_path)
    except Exception as e:
        print(f"  ✗ Error opening PDF: {e}")
        return 0

    # Identify shared/template portraits that appear on multiple pages
    adjacent_pages_xrefs, shared_xrefs = get_portrait_xrefs_by_page(pdf_doc)

    # Save portraits to archiv/X/ folder
    portraits_dir = f'/Users/nissim/dev/bat/BATbern-main/apps/BATspa-old/src/archiv/{bat_num}'
    os.makedirs(portraits_dir, exist_ok=True)

    portrait_count = 0
    used_xrefs = set()  # Track which portrait xrefs have been used
    speaker_global_index = 0  # Track speaker index across all presentations

    # Process each presentation and speaker
    for pres_idx, presentation in enumerate(presentations):
        speakers = presentation.get('speakers', [])

        for speaker_idx, speaker in enumerate(speakers):
            speaker_name = speaker['name']
            speaker_bio = speaker.get('bio', '')

            print(f"  Speaker: {speaker_name}")

            # Find page containing this speaker
            page_num = find_speaker_page(pdf_doc, speaker_name, speaker_bio)

            if page_num is None:
                print(f"    ⚠ Could not find speaker on any page")
                speaker_global_index += 1
                continue

            print(f"    → Found on page {page_num + 1}")

            # Extract portrait from that page
            portrait_filename, portrait_xref = extract_portrait_from_page(
                pdf_doc, page_num, speaker_name, portraits_dir,
                shared_xrefs, used_xrefs, speaker_global_index, adjacent_pages_xrefs
            )

            if portrait_filename and portrait_xref:
                speaker['portrait'] = portrait_filename
                used_xrefs.add(portrait_xref)  # Mark this portrait as used
                portrait_count += 1
            else:
                print(f"    ⚠ No portrait image found on page")

            speaker_global_index += 1

    pdf_doc.close()

    # Save updated JSON
    if portrait_count > 0:
        with open(json_path, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        print(f"  ✓ Extracted {portrait_count} portrait(s)")

    return portrait_count

if __name__ == '__main__':
    import sys

    if len(sys.argv) > 1:
        bat_numbers = [int(arg) for arg in sys.argv[1:]]
    else:
        bat_numbers = range(1, 42)

    total = len(bat_numbers)
    total_portraits = 0

    for idx, bat_num in enumerate(bat_numbers, 1):
        print(f"\n{'='*60}")
        print(f"[{idx}/{total}] BAT Event {bat_num}")
        print(f"{'='*60}")
        portraits = process_bat_event(bat_num)
        total_portraits += portraits

    print(f"\n{'='*60}")
    print(f"COMPLETE: {total_portraits} total portraits extracted from {total} events")
    print(f"{'='*60}")
