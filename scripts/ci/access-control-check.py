#!/usr/bin/env python3
"""
Access-control DAST: every API operation in docs/api/*.openapi.yml that is not on the reviewed
public allowlist must reject an anonymous caller with 401 or 403 (#1155).

Generic scanners (ZAP, Sonar, CodeQL) cannot find a missing authorization rule: they do not know
which endpoints are meant to be public. This check does, because the allowlist states it. New
operations in the specs are covered automatically and fail closed until someone decides.

Safe against production by construction. Nothing a probe sends can change data even where an
endpoint is wrongly open:
  - path parameters are filled with ids that do not exist;
  - write requests carry a text/plain body, which every JSON endpoint refuses with 415 before the
    controller runs;
  - writes with neither a path parameter nor a request body are NOT sent (they could act on
    their own); they are listed as "not probed" instead.
A 415 or 404 on a non-public write still counts as a finding: it proves the request passed the
security layer.

Usage:
  scripts/ci/access-control-check.py [--base-url URL] [--only-get] [--list]
  scripts/ci/access-control-check.py --check-allowlist     # offline, for PRs
Exit code 1 if any non-public operation answered an anonymous caller with anything but 401/403,
or if an allowlist entry no longer matches an operation in the specs.
"""
import argparse
import glob
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

import yaml

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
ALLOWLIST = os.path.join(os.path.dirname(__file__), 'access-control-public.txt')
DEFAULT_BASE = 'https://api.batbern.ch/api/v1'
WRITE_METHODS = {'POST', 'PUT', 'PATCH', 'DELETE'}
PROBE_ID = 'acl-probe-nonexistent'
PROBE_UUID = '00000000-0000-4000-8000-00000000ac1e'
PROBE_INT = '999999999'


def load_operations(spec_dir):
    ops = []
    for spec in sorted(glob.glob(os.path.join(spec_dir, '*.openapi.yml'))):
        if os.path.basename(spec).startswith('_'):
            continue
        doc = yaml.safe_load(open(spec, encoding='utf-8'))
        components = (doc.get('components') or {}).get('parameters') or {}
        for path, item in (doc.get('paths') or {}).items():
            shared_params = item.get('parameters') or []
            for method, op in item.items():
                if method.upper() not in {'GET'} | WRITE_METHODS:
                    continue
                params = [resolve(p, components) for p in shared_params + (op.get('parameters') or [])]
                ops.append({
                    'spec': os.path.basename(spec),
                    'method': method.upper(),
                    'path': path,
                    'has_body': bool(op.get('requestBody')),
                    'path_params': {p['name']: schema_type(p) for p in params if p.get('in') == 'path'},
                    # Required query/header parameters are sent too, so a request reaches the
                    # authorization check instead of failing argument binding first.
                    'query': {p['name']: probe_value(p) for p in params
                              if p.get('in') == 'query' and p.get('required')},
                    'headers': {p['name']: probe_value(p) for p in params
                                if p.get('in') == 'header' and p.get('required')
                                and p['name'].lower() != 'authorization'},
                })
    return ops


def resolve(param, components):
    if '$ref' in param:
        return components.get(param['$ref'].split('/')[-1], {})
    return param


def schema_type(param):
    schema = param.get('schema') or {}
    return schema.get('type'), schema.get('format')


def probe_value(param):
    schema = param.get('schema') or {}
    if schema.get('enum'):
        return str(schema['enum'][0])
    typ, fmt = schema_type(param)
    if fmt == 'uuid':
        return PROBE_UUID
    if typ in ('integer', 'number'):
        return PROBE_INT
    if typ == 'boolean':
        return 'false'
    return PROBE_ID


def load_allowlist(path):
    entries = []
    for n, line in enumerate(open(path, encoding='utf-8'), 1):
        line = line.split('#', 1)[0].strip()
        if not line:
            continue
        method, path = line.split(None, 1)
        entries.append((method.upper(), path.strip(), n))
    return entries


def concrete_path(op):
    def fill(match):
        typ, fmt = op['path_params'].get(match.group(1), (None, None))
        if fmt == 'uuid':
            return PROBE_UUID
        if typ in ('integer', 'number'):
            return PROBE_INT
        return PROBE_ID
    return re.sub(r'\{([^}]+)\}', fill, op['path'])


def probe(base, op):
    url = base.rstrip('/') + concrete_path(op)
    if op['query']:
        url += '?' + urllib.parse.urlencode(op['query'])
    data, headers = None, {'Accept': 'application/json', 'User-Agent': 'batbern-access-control-check'}
    headers.update(op['headers'])
    if op['method'] in WRITE_METHODS:
        data = b'access-control-probe'
        headers['Content-Type'] = 'text/plain'
    for attempt in range(5):
        req = urllib.request.Request(url, data=data, method=op['method'], headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=20) as r:
                status = r.status
        except urllib.error.HTTPError as e:
            status = e.code
        except (urllib.error.URLError, TimeoutError) as e:
            return f'ERR {e}'
        if status != 429:
            return status
        time.sleep(5 * (attempt + 1))
    return 429


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument('--base-url', default=os.environ.get('ACCESS_CONTROL_BASE_URL', DEFAULT_BASE))
    ap.add_argument('--only-get', action='store_true', help='probe GET operations only')
    ap.add_argument('--list', action='store_true', help='print every probe, not just findings')
    ap.add_argument('--delay', type=float, default=0.2, help='seconds between requests')
    ap.add_argument('--spec-dir', default=os.path.join(REPO, 'docs', 'api'))
    ap.add_argument('--allowlist', default=ALLOWLIST)
    ap.add_argument('--check-allowlist', action='store_true',
                    help='offline: only verify every allowlist entry still matches an operation')
    args = ap.parse_args(argv)

    ops = load_operations(args.spec_dir)
    allow = load_allowlist(args.allowlist)
    allowed = {(m, p) for m, p, _ in allow}
    known = {(o['method'], o['path']) for o in ops}

    stale = [(m, p, n) for m, p, n in allow if (m, p) not in known]
    if args.check_allowlist:
        for m, p, n in stale:
            print(f"STALE allowlist entry, line {n}: {m} {p} matches no operation in docs/api")
        print(f"Allowlist: {len(allow)} entries, {len(stale)} stale.")
        return 1 if stale else 0
    findings, not_probed, results = [], [], []

    for op in ops:
        if (op['method'], op['path']) in allowed:
            continue
        if args.only_get and op['method'] != 'GET':
            continue
        if op['method'] in WRITE_METHODS and not op['path_params'] and not op['has_body']:
            not_probed.append(op)
            continue
        status = probe(args.base_url, op)
        results.append((op, status))
        if status not in (401, 403):
            findings.append((op, status))
        time.sleep(args.delay)

    if args.list:
        for op, status in results:
            print(f"{status}\t{op['method']}\t{op['path']}\t{op['spec']}")

    print(f"\nOperations in specs: {len(ops)}, public by allowlist: {len(allowed & known)}, "
          f"probed anonymously: {len(results)}, not probed (write without id or body): {len(not_probed)}")
    for op in not_probed:
        print(f"  not probed: {op['method']} {op['path']} ({op['spec']})")
    for m, p, n in stale:
        print(f"STALE allowlist entry, line {n}: {m} {p} matches no operation in docs/api")
    for op, status in findings:
        print(f"FINDING: anonymous {op['method']} {op['path']} -> {status}, expected 401/403 ({op['spec']})")

    if findings or stale:
        print(f"\n{len(findings)} finding(s), {len(stale)} stale allowlist entr(y/ies).")
        print('Either the endpoint must require authentication, or it is public by design and belongs in '
              'scripts/ci/access-control-public.txt with a reason.')
        return 1
    print('\nAccess control OK: every non-public operation rejected the anonymous caller.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
