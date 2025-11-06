#!/usr/bin/env python3
"""
Update sessions.json with extracted presentation data
"""
import json
import os

# Load sessions.json
sessions_file = '/Users/nissim/dev/bat/BATbern-main/apps/BATspa-old/src/api/sessions.json'
with open(sessions_file, 'r', encoding='utf-8') as f:
    sessions = json.load(f)

# Load extracted data files
extracted_data_dir = '/Users/nissim/dev/bat/BATbern-main/.ai/extracted-data'

# Build a mapping of BAT number + title to detailed info
presentations_map = {}
bat_numbers = set()

# Find all bat-*.json files in the directory
for filename in os.listdir(extracted_data_dir):
    if filename.startswith('bat-') and filename.endswith('.json'):
        try:
            # Extract BAT number from filename (e.g., bat-01.json -> 1)
            bat_num = int(filename[4:6])
            bat_numbers.add(bat_num)

            json_file = os.path.join(extracted_data_dir, filename)
            with open(json_file, 'r', encoding='utf-8') as f:
                data = json.load(f)

            for presentation in data.get('presentations', []):
                # Create a key based on BAT number and normalized title
                title_key = presentation['title'].lower().strip()
                key = (bat_num, title_key)
                presentations_map[key] = presentation
        except (ValueError, KeyError, json.JSONDecodeError) as e:
            print(f"Warning: Error processing {filename}: {e}")
            continue

bat_numbers = sorted(bat_numbers)

print(f"Found BAT events: {bat_numbers}")
print(f"Loaded {len(presentations_map)} presentations from extracted data")

# Helper function to convert company name to lowercase identifier
def company_to_identifier(company_name):
    """Convert company name to lowercase identifier"""
    # Remove common suffixes
    name = company_name.replace(' AG', '').replace(' GmbH', '').replace(' Inc', '').replace(' SA', '')
    name = name.replace(' Schweiz', '').replace(' Switzerland', '')

    # Handle special cases
    special_cases = {
        'Die Mobiliar': 'mobiliar',
        'Mobiliar Versicherungsgesellschaft': 'mobiliar',
        'SBB': 'sbb',
        'SBB Informatik': 'sbb',
        'PostFinance': 'postfinance',
        'Swisscom': 'swisscom',
        'Swisscom Fixnet': 'swisscom',
        'Credit Suisse': 'creditsuisse',
        'RTC': 'rtc',
        'BEKB': 'bekb',
        'BEKB | BCBE': 'bekb',
        'Bundesamt für Informatik und Telekommunikation BIT': 'bit',
        'BKW FMB Energie': 'bkw',
        'Hochschule Luzern': 'hslu',
        'SWITCH': 'switch',
        'Informatikstrategieorgan Bund ISB': 'isb',
    }

    # Check for special cases first
    for key, value in special_cases.items():
        if key in company_name:
            return value

    # Default: take first word, remove articles, lowercase
    words = name.strip().split()
    if words:
        first_word = words[0] if words[0] not in ['Die', 'Der', 'Das'] else (words[1] if len(words) > 1 else words[0])
        return first_word.lower().replace(',', '').replace('.', '')

    return 'unknown'

# Update sessions.json entries
updated_count = 0
for session in sessions:
    bat_num = session.get('bat')
    if bat_num not in bat_numbers:
        continue

    # Skip entries that are just program booklets
    if 'Programmheft' in session.get('title', ''):
        continue

    # Normalize the title for matching
    session_title = session.get('title', '').lower().strip()

    # Try to find a match in extracted data
    key = (bat_num, session_title)

    # Try fuzzy matching if exact match fails
    if key not in presentations_map:
        # Try matching by checking if one title contains the other
        for (pres_bat, pres_title), pres_data in presentations_map.items():
            if pres_bat == bat_num:
                if session_title in pres_title or pres_title in session_title:
                    key = (pres_bat, pres_title)
                    break

    if key in presentations_map:
        presentation = presentations_map[key]

        # Add abstract field
        session['abstract'] = presentation['abstract']

        # Add referenten field (speakers)
        session['referenten'] = []
        for speaker in presentation.get('speakers', []):
            company_id = company_to_identifier(speaker['company'])
            referent = {
                'name': f"{speaker['name']}, {speaker['company']}",
                'bio': speaker['bio'],
                'company': company_id
            }
            # Add portrait if available
            if 'portrait' in speaker:
                referent['portrait'] = speaker['portrait']

            session['referenten'].append(referent)

        # Remove authoren field since we now have referenten
        if 'authoren' in session:
            del session['authoren']

        updated_count += 1
        print(f"✓ Updated BAT {bat_num}: {session.get('title', 'Unknown')[:60]}")
    else:
        print(f"✗ No match for BAT {bat_num}: {session.get('title', 'Unknown')[:60]}")

print(f"\nUpdated {updated_count} session entries")

# Write updated sessions.json
with open(sessions_file, 'w', encoding='utf-8') as f:
    json.dump(sessions, f, ensure_ascii=False, indent=2)

print(f"✓ Saved updated sessions.json")
