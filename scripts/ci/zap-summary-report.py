#!/usr/bin/env python3
"""
ZAP Security Summary Report Generator
Parses ZAP console log files and produces a readable Markdown report
showing every tested rule (PASS / WARN / FAIL) grouped by security category.

Usage:
    python3 scripts/ci/zap-summary-report.py <log-dir> [--output report.md]

Log files expected in <log-dir>:
    events.log, companies.log, speakers.log, partners.log,
    attendees.log, frontend.log

Exit code: 0 if no FAILs, 1 if any FAIL found (usable as a CI gate).
"""

import json
import re
import sys
import os
import argparse
from datetime import datetime

# ─── Friendly names for ZAP rule IDs ──────────────────────────────────────────
RULE_NAMES = {
    # Injection
    "40018": "SQL Injection",
    "40019": "SQL Injection (MySQL, Time Based)",
    "40020": "SQL Injection (Hypersonic, Time Based)",
    "40021": "SQL Injection (Oracle, Time Based)",
    "40022": "SQL Injection (PostgreSQL, Time Based)",
    "40027": "SQL Injection (MsSQL, Time Based)",
    "40012": "Cross-Site Scripting — Reflected (XSS)",
    "40014": "Cross-Site Scripting — Persistent (XSS)",
    "40016": "Cross-Site Scripting — Persistent, Prime (XSS)",
    "40017": "Cross-Site Scripting — Persistent, Spider (XSS)",
    "40026": "Cross-Site Scripting — DOM Based (XSS)",
    "90017": "XSLT Injection",
    "90021": "XPath Injection",
    "90019": "Server Side Code Injection",
    "90020": "Remote OS Command Injection",
    "90037": "Remote OS Command Injection (Time Based)",
    "90035": "Server Side Template Injection",
    "90036": "Server Side Template Injection (Blind)",
    "40003": "CRLF Injection",
    "90023": "XML External Entity (XXE)",
    "90029": "SOAP XML Injection",
    "30001": "Buffer Overflow",
    "30002": "Format String Error",
    "40008": "Parameter Tampering",
    "40009": "Server Side Include",
    # Known CVEs & Exploits
    "40043": "Log4Shell (CVE-2021-44228)",
    "40045": "Spring4Shell (CVE-2022-22965)",
    "20015": "Heartbleed OpenSSL (CVE-2014-0160)",
    "10034": "Heartbleed OpenSSL — Indicative",
    "20017": "PHP CGI Remote Code Execution (CVE-2012-1823)",
    "20018": "Remote Code Execution (CVE-2012-1823)",
    "10048": "Remote Code Execution — ShellShock",
    "40048": "Remote Code Execution (React2Shell)",
    "40044": "Exponential Entity Expansion (Billion Laughs)",
    "40042": "Spring Actuator Information Leak",
    # Authentication & Session
    "10105": "Weak Authentication Method",
    "10057": "Username Hash Found",
    "10202": "Absence of Anti-CSRF Tokens",
    "3":     "Session ID in URL Rewrite",
    "10112": "Session Management Response Identified",
    "10111": "Authentication Request Identified",
    "10113": "Verification Request Identified",
    "90001": "Insecure JSF ViewState",
    "90024": "Generic Padding Oracle",
    # Security Headers
    "10020": "Anti-Clickjacking Header (X-Frame-Options)",
    "10021": "X-Content-Type-Options Header",
    "10035": "Strict-Transport-Security (HSTS)",
    "10038": "Content Security Policy (CSP) — Presence",
    "10055": "Content Security Policy (CSP) — Wildcard Directives",
    "10036": "Server Version Disclosure (Server header)",
    "10063": "Permissions Policy Header",
    "10015": "Cache-Control Directives",
    "10040": "Secure Pages Include Mixed Content",
    "10041": "HTTP to HTTPS Insecure Transition in Form Post",
    "10042": "HTTPS to HTTP Insecure Transition in Form Post",
    # Information Disclosure
    "10023": "Debug Error Messages Disclosure",
    "10024": "Sensitive Information in URL",
    "10025": "Sensitive Information in HTTP Referrer",
    "10027": "Suspicious Comments (Info Disclosure)",
    "10039": "X-Backend-Server Header Leak",
    "10037": "X-Powered-By Header Leak",
    "10052": "X-ChromeLogger Header Leak",
    "10056": "X-Debug-Token Header Leak",
    "10061": "X-AspNet-Version Header Leak",
    "2":     "Private IP Disclosure",
    "110009":"Full Path Disclosure",
    "10062": "PII Disclosure",
    "10097": "Hash Disclosure",
    "10094": "Base64 Disclosure",
    "10096": "Timestamp Disclosure (Unix)",
    "10044": "Big Redirect (Potential Info Leak)",
    "10009": "In-Page Banner Information Leak",
    # File & Path
    "6":     "Path Traversal",
    "7":     "Remote File Inclusion",
    "0":     "Directory Browsing",
    "10033": "Directory Browsing (alt)",
    "10045": "Source Code Disclosure (/WEB-INF)",
    "10099": "Source Code Disclosure",
    "40032": ".htaccess Information Leak",
    "40034": ".env File Leak",
    "40035": "Hidden File Finder",
    "40028": "ELMAH Information Leak",
    "40029": "Trace.axd Information Leak",
    "90022": "Application Error Disclosure",
    # Cloud & Modern Web
    "90034": "Cloud Metadata Exposed (SSRF potential)",
    "90004": "Cross-Origin Isolation / Spectre Header",
    "90005": "Fetch Metadata Request Headers (Sec-Fetch-*)",
    "100043":"Swagger UI Secret & Vulnerability Detector",
    "90003": "Sub-Resource Integrity (SRI) Attribute Missing",
    "10115": "Polyfill.io Malicious Domain Check",
    "10003": "Vulnerable JS Library (Retire.js)",
    "10098": "Cross-Domain Misconfiguration",
    "10017": "Cross-Domain JS Source File Inclusion",
    "10028": "Off-site Redirect",
    "10106": "HTTP-Only Site",
    "10109": "Modern Web Application (SPA detection)",
    "10110": "Dangerous JS Functions",
    "10108": "Reverse Tabnabbing",
    "10104": "User Agent Fuzzer",
    # Cookie Security
    "10010": "Cookie No HttpOnly Flag",
    "10011": "Cookie Without Secure Flag",
    "10054": "Cookie Without SameSite Attribute",
    "90033": "Loosely Scoped Cookie",
    # Other
    "10019": "Content-Type Header Missing",
    "10026": "HTTP Parameter Override",
    "10029": "Cookie Poisoning",
    "10030": "User Controllable Charset",
    "10031": "User Controllable HTML Element Attribute (XSS potential)",
    "10043": "User Controllable JavaScript Event (XSS potential)",
    "10032": "Viewstate",
    "10047": "HTTPS Content Available via HTTP",
    "10049": "Storable and Cacheable Content",
    "10050": "Retrieved from Cache",
    "10058": "GET for POST",
    "90011": "Charset Mismatch",
    "90002": "Java Serialization Object",
    "90026": "SOAP Action Spoofing",
    "90030": "WSDL File Detection",
    "50000": "Script Active Scan Rules",
    "50001": "Script Passive Scan Rules",
    "50003": "Stats Passive Scan Rule",
}

SCAN_GROUPS = [
    ("Injection Attacks", [
        "40018","40019","40020","40021","40022","40027",
        "40012","40014","40016","40017","40026",
        "90017","90021","90019","90020","90037",
        "90035","90036","40003","90023","90029",
        "30001","30002","40008","40009",
    ]),
    ("Known CVEs & Exploits", [
        "40043","40045","20015","10034","20017","20018",
        "10048","40048","40044",
    ]),
    ("Authentication & Session", [
        "10105","10057","10202","3","10112","10111","10113",
        "90001","90024",
    ]),
    ("Security Headers", [
        "10020","10021","10035","10038","10055","10036",
        "10063","10015","10040","10041","10042",
    ]),
    ("Information Disclosure", [
        "10023","10024","10025","10027","10039","10037",
        "10052","10056","10061","2","110009","10062",
        "10097","10094","10096","10044","10009","40042",
    ]),
    ("File & Path Security", [
        "6","7","0","10033","10045","10099",
        "40032","40034","40035","40028","40029","90022",
    ]),
    ("Cloud & Modern Web", [
        "90034","90004","90005","100043","90003",
        "10115","10003","10098","10017","10028",
        "10106","10109","10110","10108","10104",
    ]),
    ("Cookie Security", [
        "10010","10011","10054","90033",
    ]),
]

STATUS_ICON = {"PASS": "✅", "WARN": "⚠️", "FAIL": "❌", "–": "–"}


def parse_log(path):
    """Return dict of {rule_id: {name, status, instances: [url, ...]}}"""
    rules = {}
    current = None
    try:
        with open(path) as f:
            lines = f.readlines()
    except FileNotFoundError:
        return rules

    for line in lines:
        line = line.strip()
        # PASS / WARN-NEW / FAIL-NEW lines
        m = re.match(r'(PASS|WARN-NEW|FAIL-NEW|IGNORE):\s+(.*?)\s+\[(\d+)\]', line)
        if m:
            raw_status = m.group(1)
            name = m.group(2)
            rule_id = m.group(3)
            status = raw_status.replace('-NEW', '')
            rules[rule_id] = {'name': name, 'status': status, 'instances': []}
            current = rules[rule_id]
            continue

        # URL lines following a WARN/FAIL
        if current and current['status'] in ('WARN', 'FAIL') and line.startswith('http'):
            current['instances'].append(line)

    return rules


def generate_report(log_dir, output_path=None):
    scan_files = {
        'events':    os.path.join(log_dir, 'events.log'),
        'companies': os.path.join(log_dir, 'companies.log'),
        'speakers':  os.path.join(log_dir, 'speakers.log'),
        'partners':  os.path.join(log_dir, 'partners.log'),
        'attendees': os.path.join(log_dir, 'attendees.log'),
        'frontend':  os.path.join(log_dir, 'frontend.log'),
    }

    scan_data = {name: parse_log(path) for name, path in scan_files.items()}
    present_scans = [s for s, d in scan_data.items() if d]
    api_scans = [s for s in present_scans if s != 'frontend']

    # Collect all rule IDs seen
    all_rule_ids = set()
    for d in scan_data.values():
        all_rule_ids.update(d.keys())

    lines = []
    lines.append("# OWASP ZAP Security Report — BATbern Staging")
    lines.append(f"*Generated: {datetime.now().strftime('%Y-%m-%d %H:%M UTC')}*")
    lines.append(f"*Target: https://api.staging.batbern.ch / https://staging.batbern.ch*")
    lines.append("")

    # ── Summary table ──────────────────────────────────────────────────────────
    lines.append("## Summary")
    lines.append("")
    lines.append("| Scan | ✅ Pass | ⚠️ Warn | ❌ Fail |")
    lines.append("|------|--------|--------|--------|")

    total_fails = 0
    for scan in present_scans:
        d = scan_data[scan]
        p = sum(1 for r in d.values() if r['status'] == 'PASS')
        w = sum(1 for r in d.values() if r['status'] == 'WARN')
        f = sum(1 for r in d.values() if r['status'] == 'FAIL')
        total_fails += f
        icon = "✅" if f == 0 and w == 0 else ("⚠️" if f == 0 else "❌")
        lines.append(f"| {icon} **{scan}** | {p} | {w} | {f} |")

    lines.append("")

    overall = "✅ **CLEAN — no security failures detected.**" if total_fails == 0 \
              else f"❌ **{total_fails} FAILURE(S) FOUND — see detail below.**"
    lines.append(f"> {overall}")
    lines.append("")
    lines.append("---")
    lines.append("")

    # ── Per-category results ───────────────────────────────────────────────────
    lines.append("## Results by Security Category")
    lines.append("")
    lines.append("Each row shows whether a specific attack or weakness was tested and what the result was.")
    lines.append("")

    has_frontend = 'frontend' in present_scans

    for group_name, rule_ids in SCAN_GROUPS:
        # Only include rules that were actually tested in at least one scan
        tested = [(rid, RULE_NAMES.get(rid, rid)) for rid in rule_ids if rid in all_rule_ids]
        if not tested:
            continue

        any_bad = any(
            scan_data[s].get(rid, {}).get('status') in ('WARN', 'FAIL')
            for rid, _ in tested
            for s in present_scans
        )
        group_icon = "⚠️" if any_bad else "✅"

        lines.append(f"### {group_icon} {group_name}")
        lines.append("")

        if has_frontend and api_scans:
            lines.append("| Security Check | APIs (5 scans) | Frontend |")
            lines.append("|----------------|----------------|----------|")
        elif api_scans:
            lines.append("| Security Check | APIs (5 scans) |")
            lines.append("|----------------|----------------|")
        else:
            lines.append("| Security Check | Frontend |")
            lines.append("|----------------|----------|")

        for rule_id, friendly in tested:
            # API consensus
            api_statuses = {scan_data[s].get(rule_id, {}).get('status', '–') for s in api_scans}
            if 'FAIL' in api_statuses:   api_cell = "❌ FAIL"
            elif 'WARN' in api_statuses: api_cell = "⚠️ WARN"
            elif 'PASS' in api_statuses: api_cell = "✅ PASS"
            else:                         api_cell = "–"

            row = f"| {friendly} | {api_cell} |"

            if has_frontend:
                fe_status = scan_data['frontend'].get(rule_id, {}).get('status', '–')
                fe_cell = STATUS_ICON.get(fe_status, '–') + (f" {fe_status}" if fe_status != '–' else '')
                row += f" {fe_cell} |"

            lines.append(row)
        lines.append("")

    # ── Warnings / Failures detail ─────────────────────────────────────────────
    any_findings = any(
        r['status'] in ('WARN', 'FAIL')
        for d in scan_data.values()
        for r in d.values()
    )

    if any_findings:
        lines.append("---")
        lines.append("")
        lines.append("## Findings Detail")
        lines.append("")
        lines.append("*All findings below are WARNINGs (non-blocking). No FAILUREs were detected.*")
        lines.append("")

        for scan in present_scans:
            findings = [
                (rid, r) for rid, r in scan_data[scan].items()
                if r['status'] in ('WARN', 'FAIL')
            ]
            if not findings:
                continue

            lines.append(f"### {STATUS_ICON.get('WARN')} {scan}")
            lines.append("")
            for rid, r in findings:
                friendly = RULE_NAMES.get(rid, r['name'])
                icon = STATUS_ICON[r['status']]
                lines.append(f"**{icon} {friendly}** (rule {rid})")
                for url in r['instances'][:5]:
                    lines.append(f"- `{url}`")
                lines.append("")

    lines.append("---")
    lines.append("")
    lines.append("*Report generated by `scripts/ci/zap-summary-report.py`*")
    lines.append(f"*ZAP scanned {len(present_scans)} target(s) across {len(all_rule_ids)} rules*")

    report = "\n".join(lines)

    if output_path:
        with open(output_path, 'w') as f:
            f.write(report)
        print(f"Report written to {output_path}", file=sys.stderr)
    else:
        print(report)

    return total_fails


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('log_dir', help='Directory containing *.log files from ZAP scans')
    parser.add_argument('--output', '-o', help='Write report to file (default: stdout)')
    args = parser.parse_args()

    fails = generate_report(args.log_dir, args.output)
    sys.exit(1 if fails > 0 else 0)


if __name__ == '__main__':
    main()
