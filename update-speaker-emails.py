#!/usr/bin/env python3
"""
Update speaker JSON files with email addresses from CSV registration data.
Matches by name and companyKey/companyId when duplicates exist.
"""

import json
import csv
import sys
from pathlib import Path
from typing import Dict, List, Optional

def normalize_name(name: str) -> str:
    """Normalize name for matching (lowercase, remove extra spaces)."""
    return ' '.join(name.lower().split())

def load_csv_emails(csv_path: Path) -> Dict[str, List[Dict[str, str]]]:
    """
    Load CSV and create a lookup dictionary.
    Returns: {normalized_name: [{'email': ..., 'companyKey': ...}, ...]}
    """
    email_lookup = {}

    with open(csv_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        for row in reader:
            name = row.get('Name', '').strip()
            email = row.get('BestMail', '').strip()
            company_key = row.get('companyKey', '').strip()

            if not name:
                continue

            normalized = normalize_name(name)

            if normalized not in email_lookup:
                email_lookup[normalized] = []

            email_lookup[normalized].append({
                'email': email,
                'companyKey': company_key.lower() if company_key else '',
                'original_name': name
            })

    return email_lookup

def find_email(speaker_name: str, company_id: str, email_lookup: Dict) -> Optional[str]:
    """
    Find email for speaker by name and optionally company.
    Returns email or None if not found.
    """
    normalized = normalize_name(speaker_name)

    if normalized not in email_lookup:
        return None

    matches = email_lookup[normalized]

    # If only one match, return it (if email exists)
    if len(matches) == 1:
        return matches[0]['email'] if matches[0]['email'] else None

    # Multiple matches - try to disambiguate by company
    if company_id:
        normalized_company = company_id.lower()
        for match in matches:
            if match['companyKey'] == normalized_company and match['email']:
                return match['email']

    # If no company match, return first non-empty email
    for match in matches:
        if match['email']:
            return match['email']

    return None

def update_speaker_file(json_path: Path, email_lookup: Dict) -> tuple[int, int, int, int]:
    """
    Update speaker JSON file with email addresses (only if not already present).
    Returns: (total_speakers, emails_added, emails_skipped, emails_missing)
    """
    print(f"\nProcessing: {json_path.name}")

    # Read JSON
    with open(json_path, 'r', encoding='utf-8') as f:
        speakers = json.load(f)

    total = len(speakers)
    emails_added = 0
    emails_skipped = 0
    emails_missing = 0

    # Update each speaker
    for speaker in speakers:
        name = speaker.get('name', '')
        company_id = speaker.get('companyId', '')
        existing_email = speaker.get('email', '').strip()

        # Skip if speaker already has an email
        if existing_email:
            emails_skipped += 1
            print(f"  ⊘ {name} ({company_id}): KEEPING EXISTING {existing_email}")
            continue

        # Try to find email from CSV
        email = find_email(name, company_id, email_lookup)

        if email:
            speaker['email'] = email
            emails_added += 1
            print(f"  ✓ {name} ({company_id}): ADDED {email}")
        else:
            speaker['email'] = ""
            emails_missing += 1
            print(f"  ✗ {name} ({company_id}): NO EMAIL FOUND")

    # Write updated JSON (pretty printed)
    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump(speakers, f, indent=2, ensure_ascii=False)

    return total, emails_added, emails_skipped, emails_missing

def main():
    # Paths
    base_dir = Path('/Users/nissim/dev/bat/BATbern-main/apps/BATspa-old/src/api')
    csv_path = base_dir / 'anmeldungen.csv'

    json_files = [
        base_dir / 'speakers.json',
        base_dir / 'speakers-with-local-urls.json',
        base_dir / 'speakers-with-staging-urls.json'
    ]

    # Load email lookup from CSV
    print("Loading email data from CSV...")
    email_lookup = load_csv_emails(csv_path)
    print(f"Loaded {len(email_lookup)} unique names from CSV")

    # Update each JSON file
    grand_total = 0
    grand_added = 0
    grand_skipped = 0
    grand_missing = 0

    for json_path in json_files:
        if not json_path.exists():
            print(f"⚠️  Skipping {json_path.name} (not found)")
            continue

        total, added, skipped, missing = update_speaker_file(json_path, email_lookup)
        grand_total += total
        grand_added += added
        grand_skipped += skipped
        grand_missing += missing

    # Summary
    print("\n" + "="*60)
    print("SUMMARY")
    print("="*60)
    print(f"Total speakers processed: {grand_total}")
    print(f"Emails kept (already existed): {grand_skipped} ({100*grand_skipped//grand_total if grand_total else 0}%)")
    print(f"Emails added (new): {grand_added} ({100*grand_added//grand_total if grand_total else 0}%)")
    print(f"Emails missing (not found): {grand_missing} ({100*grand_missing//grand_total if grand_total else 0}%)")
    print("="*60)

    return 0 if grand_missing == 0 else 1

if __name__ == '__main__':
    sys.exit(main())
