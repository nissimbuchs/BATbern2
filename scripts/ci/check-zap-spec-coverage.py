#!/usr/bin/env python3
"""
Fails when an API spec in docs/api is not in the ZAP API scan matrix (#1155).

Six specs, the users API among them, were never scanned because the matrix in
.github/workflows/security-scan.yml is a hand-written list. This keeps it complete.
"""
import glob
import os
import sys

import yaml

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
workflow = yaml.safe_load(open(os.path.join(REPO, '.github', 'workflows', 'security-scan.yml'), encoding='utf-8'))
scanned = {os.path.basename(e['spec']) for e in workflow['jobs']['zap-api-scan']['strategy']['matrix']['include']}
specs = {os.path.basename(f) for f in glob.glob(os.path.join(REPO, 'docs', 'api', '*.openapi.yml'))
         if not os.path.basename(f).startswith('_')}

missing, stale = sorted(specs - scanned), sorted(scanned - specs)
for name in missing:
    print(f'NOT SCANNED: docs/api/{name} is missing from the zap-api-scan matrix in security-scan.yml')
for name in stale:
    print(f'STALE: security-scan.yml scans {name}, which no longer exists in docs/api')
print(f'ZAP matrix covers {len(specs & scanned)} of {len(specs)} API specs.')
sys.exit(1 if missing or stale else 0)
