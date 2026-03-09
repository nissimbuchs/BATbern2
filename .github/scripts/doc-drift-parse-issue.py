#!/usr/bin/env python3
"""
Parses a doc-drift issue body and outputs JSON with the list of
flagged commits and their suggested docs.

Input: issue body markdown (stdin or --file)
Output: JSON to stdout
  [{"sha": "75f20a87", "message": "feat(10-17): ...", "docs": ["docs/arch/...", ...]}, ...]
"""

import json
import re
import sys
import argparse


def parse_issue_body(text: str) -> list[dict]:
    entries = []

    # Match table rows: | `sha` | message | `doc1` `doc2` ... |
    row_re = re.compile(r'^\|\s*`([0-9a-f]{6,12})`\s*\|\s*(.+?)\s*\|\s*(.+?)\s*\|', re.MULTILINE)

    for m in row_re.finditer(text):
        sha = m.group(1).strip()
        message = m.group(2).strip()
        docs_raw = m.group(3).strip()

        # Extract backtick-quoted doc paths
        docs = re.findall(r'`([^`]+)`', docs_raw)
        # Filter to only paths that look like docs
        docs = [d for d in docs if d.startswith('docs/')]

        if sha and message:
            entries.append({"sha": sha, "message": message, "docs": docs})

    return entries


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--file', help='Issue body file (default: stdin)')
    args = parser.parse_args()

    if args.file:
        with open(args.file) as f:
            text = f.read()
    else:
        text = sys.stdin.read()

    entries = parse_issue_body(text)
    print(json.dumps(entries, indent=2))


if __name__ == '__main__':
    main()
