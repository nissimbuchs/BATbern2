import fs from 'fs-extra';
import path from 'path';
import { globSync } from 'glob';

/**
 * Parser for OWASP ZAP report files.
 *
 * Supports two formats:
 *   1. JSON (report_json.json) — ZAP alerts with risk codes (findings only)
 *   2. Console log (*.log)     — PASS/WARN-NEW/FAIL-NEW per rule (full coverage)
 *
 * riskcode values: 0 = Informational, 1 = Low, 2 = Medium, 3 = High
 */

// ─── Rule ID → friendly name ────────────────────────────────────────────────
const RULE_NAMES = {
  '40018': 'SQL Injection',
  '40019': 'SQL Injection (MySQL, Time Based)',
  '40020': 'SQL Injection (Hypersonic, Time Based)',
  '40021': 'SQL Injection (Oracle, Time Based)',
  '40022': 'SQL Injection (PostgreSQL, Time Based)',
  '40027': 'SQL Injection (MsSQL, Time Based)',
  '40012': 'Cross-Site Scripting — Reflected (XSS)',
  '40014': 'Cross-Site Scripting — Persistent (XSS)',
  '40016': 'Cross-Site Scripting — Persistent, Prime (XSS)',
  '40017': 'Cross-Site Scripting — Persistent, Spider (XSS)',
  '40026': 'Cross-Site Scripting — DOM Based (XSS)',
  '90017': 'XSLT Injection',
  '90021': 'XPath Injection',
  '90019': 'Server Side Code Injection',
  '90020': 'Remote OS Command Injection',
  '90037': 'Remote OS Command Injection (Time Based)',
  '90035': 'Server Side Template Injection',
  '90036': 'Server Side Template Injection (Blind)',
  '40003': 'CRLF Injection',
  '90023': 'XML External Entity (XXE)',
  '90029': 'SOAP XML Injection',
  '30001': 'Buffer Overflow',
  '30002': 'Format String Error',
  '40008': 'Parameter Tampering',
  '40009': 'Server Side Include',
  '40043': 'Log4Shell (CVE-2021-44228)',
  '40045': 'Spring4Shell (CVE-2022-22965)',
  '20015': 'Heartbleed OpenSSL (CVE-2014-0160)',
  '10034': 'Heartbleed OpenSSL — Indicative',
  '20017': 'PHP CGI Remote Code Execution (CVE-2012-1823)',
  '20018': 'Remote Code Execution (CVE-2012-1823)',
  '10048': 'Remote Code Execution — ShellShock',
  '40048': 'Remote Code Execution (React2Shell)',
  '40044': 'Exponential Entity Expansion (Billion Laughs)',
  '40042': 'Spring Actuator Information Leak',
  '10105': 'Weak Authentication Method',
  '10057': 'Username Hash Found',
  '10202': 'Absence of Anti-CSRF Tokens',
  '3':     'Session ID in URL Rewrite',
  '10112': 'Session Management Response Identified',
  '10111': 'Authentication Request Identified',
  '10113': 'Verification Request Identified',
  '90001': 'Insecure JSF ViewState',
  '90024': 'Generic Padding Oracle',
  '10020': 'Anti-Clickjacking Header (X-Frame-Options)',
  '10021': 'X-Content-Type-Options Header',
  '10035': 'Strict-Transport-Security (HSTS)',
  '10038': 'Content Security Policy (CSP) — Presence',
  '10055': 'Content Security Policy (CSP) — Wildcard Directives',
  '10036': 'Server Version Disclosure (Server header)',
  '10063': 'Permissions Policy Header',
  '10015': 'Cache-Control Directives',
  '10040': 'Secure Pages Include Mixed Content',
  '10041': 'HTTP to HTTPS Insecure Transition in Form Post',
  '10042': 'HTTPS to HTTP Insecure Transition in Form Post',
  '10023': 'Debug Error Messages Disclosure',
  '10024': 'Sensitive Information in URL',
  '10025': 'Sensitive Information in HTTP Referrer',
  '10027': 'Suspicious Comments (Info Disclosure)',
  '10039': 'X-Backend-Server Header Leak',
  '10037': 'X-Powered-By Header Leak',
  '10052': 'X-ChromeLogger Header Leak',
  '10056': 'X-Debug-Token Header Leak',
  '10061': 'X-AspNet-Version Header Leak',
  '2':     'Private IP Disclosure',
  '110009':'Full Path Disclosure',
  '10062': 'PII Disclosure',
  '10097': 'Hash Disclosure',
  '10094': 'Base64 Disclosure',
  '10096': 'Timestamp Disclosure (Unix)',
  '10044': 'Big Redirect (Potential Info Leak)',
  '10009': 'In-Page Banner Information Leak',
  '6':     'Path Traversal',
  '7':     'Remote File Inclusion',
  '0':     'Directory Browsing',
  '10033': 'Directory Browsing (alt)',
  '10045': 'Source Code Disclosure (/WEB-INF)',
  '10099': 'Source Code Disclosure',
  '40032': '.htaccess Information Leak',
  '40034': '.env File Leak',
  '40035': 'Hidden File Finder',
  '40028': 'ELMAH Information Leak',
  '40029': 'Trace.axd Information Leak',
  '90022': 'Application Error Disclosure',
  '90034': 'Cloud Metadata Exposed (SSRF potential)',
  '90004': 'Cross-Origin Isolation / Spectre Header',
  '90005': 'Fetch Metadata Request Headers (Sec-Fetch-*)',
  '100043':'Swagger UI Secret & Vulnerability Detector',
  '90003': 'Sub-Resource Integrity (SRI) Attribute Missing',
  '10115': 'Polyfill.io Malicious Domain Check',
  '10003': 'Vulnerable JS Library (Retire.js)',
  '10098': 'Cross-Domain Misconfiguration',
  '10017': 'Cross-Domain JS Source File Inclusion',
  '10028': 'Off-site Redirect',
  '10106': 'HTTP-Only Site',
  '10109': 'Modern Web Application (SPA detection)',
  '10110': 'Dangerous JS Functions',
  '10108': 'Reverse Tabnabbing',
  '10104': 'User Agent Fuzzer',
  '10010': 'Cookie No HttpOnly Flag',
  '10011': 'Cookie Without Secure Flag',
  '10054': 'Cookie Without SameSite Attribute',
  '90033': 'Loosely Scoped Cookie',
};

// ─── Category groupings (same as zap-summary-report.py) ────────────────────
export const SCAN_GROUPS = [
  { name: 'Injection Attacks', rules: [
    '40018','40019','40020','40021','40022','40027',
    '40012','40014','40016','40017','40026',
    '90017','90021','90019','90020','90037',
    '90035','90036','40003','90023','90029',
    '30001','30002','40008','40009',
  ]},
  { name: 'Known CVEs & Exploits', rules: [
    '40043','40045','20015','10034','20017','20018',
    '10048','40048','40044',
  ]},
  { name: 'Authentication & Session', rules: [
    '10105','10057','10202','3','10112','10111','10113',
    '90001','90024',
  ]},
  { name: 'Security Headers', rules: [
    '10020','10021','10035','10038','10055','10036',
    '10063','10015','10040','10041','10042',
  ]},
  { name: 'Information Disclosure', rules: [
    '10023','10024','10025','10027','10039','10037',
    '10052','10056','10061','2','110009','10062',
    '10097','10094','10096','10044','10009','40042',
  ]},
  { name: 'File & Path Security', rules: [
    '6','7','0','10033','10045','10099',
    '40032','40034','40035','40028','40029','90022',
  ]},
  { name: 'Cloud & Modern Web', rules: [
    '90034','90004','90005','100043','90003',
    '10115','10003','10098','10017','10028',
    '10106','10109','10110','10108','10104',
  ]},
  { name: 'Cookie Security', rules: [
    '10010','10011','10054','90033',
  ]},
];

export class ZapParser {
  static RISK_NAMES  = { '0': 'info', '1': 'low', '2': 'medium', '3': 'high' };
  static RISK_LABELS = { '0': 'Informational', '1': 'Low', '2': 'Medium', '3': 'High' };

  // ── JSON report parsing ────────────────────────────────────────────────────

  static async parseFile(filePath, scanName) {
    try {
      const content = await fs.readFile(filePath, 'utf8');
      const report = JSON.parse(content);
      const name = scanName || path.basename(filePath, '.json').replace(/^zap-/, '');
      return this.extractFindings(report, name);
    } catch (error) {
      console.error(`Failed to parse ZAP report: ${filePath}`, error.message);
      return null;
    }
  }

  static extractFindings(report, scanName) {
    const sites = report.site || [];
    const allAlerts = [];

    for (const site of sites) {
      for (const alert of (site.alerts || [])) {
        const riskCode = String(alert.riskcode);
        allAlerts.push({
          pluginId: alert.pluginid,
          name: alert.alert || alert.name || 'Unknown',
          riskCode,
          risk: this.RISK_NAMES[riskCode] || 'info',
          riskLabel: this.RISK_LABELS[riskCode] || 'Informational',
          confidence: alert.confidence,
          description: alert.desc,
          solution: alert.solution,
          reference: alert.reference,
          instances: (alert.instances || []).slice(0, 5).map(i => ({ uri: i.uri, method: i.method || 'GET' })),
          count: parseInt(alert.count || '0', 10)
        });
      }
    }

    allAlerts.sort((a, b) => parseInt(b.riskCode) - parseInt(a.riskCode));

    return {
      scanName,
      target: sites[0]?.['@name'] || 'unknown',
      generatedDate: report['@generated'] || null,
      alerts: allAlerts,
      summary: {
        high:   allAlerts.filter(a => a.riskCode === '3').length,
        medium: allAlerts.filter(a => a.riskCode === '2').length,
        low:    allAlerts.filter(a => a.riskCode === '1').length,
        info:   allAlerts.filter(a => a.riskCode === '0').length,
        total:  allAlerts.length
      }
    };
  }

  static async findAndParseReports(baseDir, pattern = 'security-reports/zap-*.json') {
    const reports = [];
    const files = globSync(pattern, { cwd: baseDir, absolute: true, ignore: '**/node_modules/**' });

    for (const file of files) {
      const scanName = path.basename(file, '.json').replace(/^zap-/, '');
      const findings = await this.parseFile(file, scanName);
      if (findings) reports.push({ reportPath: path.relative(baseDir, file), scanName, findings });
    }

    reports.sort((a, b) => a.scanName.localeCompare(b.scanName));
    return reports;
  }

  static calculateOverallSummary(reports) {
    const overall = { high: 0, medium: 0, low: 0, info: 0, total: 0, scansRun: reports.length };
    for (const r of reports) {
      overall.high   += r.findings.summary.high;
      overall.medium += r.findings.summary.medium;
      overall.low    += r.findings.summary.low;
      overall.info   += r.findings.summary.info;
      overall.total  += r.findings.summary.total;
    }
    overall.clean  = overall.high === 0 && overall.medium === 0;
    overall.status = overall.high > 0 ? 'fail' : overall.medium > 0 ? 'warn' : 'pass';
    return overall;
  }

  static groupByAlertType(reports) {
    const alertMap = new Map();
    for (const report of reports) {
      for (const alert of report.findings.alerts) {
        if (!alertMap.has(alert.pluginId)) {
          alertMap.set(alert.pluginId, {
            pluginId: alert.pluginId, name: alert.name,
            riskCode: alert.riskCode, risk: alert.risk, riskLabel: alert.riskLabel,
            scans: [], totalInstances: 0,
            solution: alert.solution, reference: alert.reference
          });
        }
        const e = alertMap.get(alert.pluginId);
        e.scans.push(report.scanName);
        e.totalInstances += alert.count || alert.instances.length;
      }
    }
    return Array.from(alertMap.values()).sort((a, b) => parseInt(b.riskCode) - parseInt(a.riskCode));
  }

  // ── Log file parsing (PASS/WARN/FAIL per rule) ────────────────────────────

  /**
   * Parse a ZAP console log file.
   * Returns { scanName, rules: { [ruleId]: { name, status, instances[] } } }
   */
  static parseLogFile(content, scanName) {
    const rules = {};
    let current = null;
    const LINE_RE = /^(PASS|WARN-NEW|FAIL-NEW|IGNORE):\s+(.*?)\s+\[(\d+)\]/;

    for (const rawLine of content.split('\n')) {
      const line = rawLine.trim();
      const m = LINE_RE.exec(line);
      if (m) {
        const rawStatus = m[1];
        const name      = m[2];
        const ruleId    = m[3];
        const status    = rawStatus.replace('-NEW', '');
        rules[ruleId]   = { name, status, instances: [] };
        current         = rules[ruleId];
        continue;
      }
      if (current && current.status !== 'PASS' && line.startsWith('http')) {
        current.instances.push(line);
      }
    }

    return { scanName, rules };
  }

  static async findAndParseLogFiles(baseDir, pattern = 'security-reports/zap-logs/*.log') {
    const scans = [];
    const files = globSync(pattern, { cwd: baseDir, absolute: true, ignore: '**/node_modules/**' });

    for (const file of files) {
      try {
        const content  = await fs.readFile(file, 'utf8');
        const scanName = path.basename(file, '.log').replace(/^zap-/, '');
        scans.push(this.parseLogFile(content, scanName));
      } catch (err) {
        console.warn(`Could not read ZAP log: ${file}`, err.message);
      }
    }

    scans.sort((a, b) => a.scanName.localeCompare(b.scanName));
    return scans;
  }

  /**
   * Build the per-category findings matrix from parsed JSON reports (no log files needed).
   * Rules that fired appear with their risk level; untested/passing rules are omitted.
   * Uses the same SCAN_GROUPS and structure as buildCategoryMatrix so the template works for both.
   */
  static buildCategoryMatrixFromJson(reports) {
    if (!reports || reports.length === 0) return null;

    const scanNames = reports.map(r => r.scanName);

    // Map: pluginId → { scanName → riskCode }
    const ruleHits = new Map();
    for (const report of reports) {
      for (const alert of report.findings.alerts) {
        if (!ruleHits.has(alert.pluginId)) ruleHits.set(alert.pluginId, {});
        const existing = ruleHits.get(alert.pluginId);
        // Keep worst risk per scan
        if (!existing[report.scanName] || parseInt(alert.riskCode) > parseInt(existing[report.scanName])) {
          existing[report.scanName] = alert.riskCode;
        }
      }
    }

    const categories = [];
    let totalWarn = 0, totalFail = 0;

    for (const group of SCAN_GROUPS) {
      const triggered = group.rules.filter(id => ruleHits.has(id));
      if (triggered.length === 0) continue;

      const rules = triggered.map(ruleId => {
        const cells = scanNames.map(scan => {
          const rc = ruleHits.get(ruleId)?.[scan];
          const status = rc === '3' ? 'FAIL' : rc === '2' ? 'WARN' : rc ? 'INFO' : '–';
          return { scanName: scan, status, instances: [] };
        });

        const worst = cells.some(c => c.status === 'FAIL') ? 'FAIL'
          : cells.some(c => c.status === 'WARN') ? 'WARN'
          : cells.some(c => c.status === 'INFO') ? 'INFO' : '–';

        if (worst === 'FAIL') totalFail++;
        else if (worst === 'WARN') totalWarn++;

        return { ruleId, friendlyName: RULE_NAMES[ruleId] || ruleId, cells, worst };
      });

      const catStatus = rules.some(r => r.worst === 'FAIL') ? 'FAIL'
        : rules.some(r => r.worst === 'WARN') ? 'WARN' : 'INFO';

      categories.push({
        name: group.name,
        status: catStatus,
        icon: catStatus === 'FAIL' ? '❌' : catStatus === 'WARN' ? '⚠️' : 'ℹ️',
        rules,
        scanNames
      });
    }

    return {
      scans: scanNames,
      categories,
      totals: { pass: 0, warn: totalWarn, fail: totalFail, rulesPerScan: ruleHits.size },
      fromJson: true   // flag so template can show appropriate label
    };
  }

  /**
   * Build the per-category PASS/WARN/FAIL matrix from parsed log scans.
   * Returns an array of category objects ready for the Handlebars template.
   *
   * Each category: { name, icon, rules: [ { ruleId, friendlyName, cells: [ { scanName, status } ] } ] }
   */
  static buildCategoryMatrix(scans) {
    if (!scans || scans.length === 0) return null;

    const scanNames    = scans.map(s => s.scanName);
    const allRuleIds   = new Set(scans.flatMap(s => Object.keys(s.rules)));
    const scanMap      = Object.fromEntries(scans.map(s => [s.scanName, s.rules]));

    const categories = [];

    for (const group of SCAN_GROUPS) {
      // Only include rules actually tested in at least one scan
      const tested = group.rules.filter(id => allRuleIds.has(id));
      if (tested.length === 0) continue;

      const rules = tested.map(ruleId => {
        const cells = scanNames.map(scan => {
          const r = scanMap[scan]?.[ruleId];
          return { scanName: scan, status: r?.status ?? '–', instances: r?.instances ?? [] };
        });

        // Worst status across all scans
        const statuses = cells.map(c => c.status);
        const worst = statuses.includes('FAIL') ? 'FAIL'
          : statuses.includes('WARN') ? 'WARN'
          : statuses.every(s => s === 'PASS') ? 'PASS' : '–';

        return {
          ruleId,
          friendlyName: RULE_NAMES[ruleId] || ruleId,
          cells,
          worst
        };
      });

      // Category status = worst rule status
      const catStatus = rules.some(r => r.worst === 'FAIL') ? 'FAIL'
        : rules.some(r => r.worst === 'WARN') ? 'WARN' : 'PASS';

      categories.push({
        name: group.name,
        status: catStatus,
        icon: catStatus === 'FAIL' ? '❌' : catStatus === 'WARN' ? '⚠️' : '✅',
        rules,
        scanNames
      });
    }

    // Summary counts from all rules across all scans
    let totalPass = 0, totalWarn = 0, totalFail = 0;
    for (const scan of scans) {
      for (const r of Object.values(scan.rules)) {
        if (r.status === 'PASS') totalPass++;
        else if (r.status === 'WARN') totalWarn++;
        else if (r.status === 'FAIL') totalFail++;
      }
    }

    return {
      scans: scanNames,
      categories,
      totals: { pass: totalPass, warn: totalWarn, fail: totalFail,
                rulesPerScan: scans[0] ? Object.keys(scans[0].rules).length : 0 }
    };
  }
}

export default ZapParser;
