import * as fs from 'fs-extra';
import * as path from 'path';
import { glob } from 'glob';

// Types
export type Severity = 'critical' | 'warning' | 'info';

export interface DataQualityIssue {
  type: string;
  severity: Severity;
  field: string;
  description: string;
  affectedRecords: number;
  examples: string[];
  remediation: string;
}

export interface CategorizedIssues {
  critical: DataQualityIssue[];
  warning: DataQualityIssue[];
  info: DataQualityIssue[];
}

export interface QualitySummary {
  totalIssues: number;
  criticalCount: number;
  warningCount: number;
  infoCount: number;
}

export interface QualityReport {
  generatedAt: string;
  summary: QualitySummary;
  allIssues: DataQualityIssue[];
  categorized: CategorizedIssues;
}

interface Session {
  bat: number;
  pdf: string;
  title: string;
  abstract?: string;
  authoren?: string;
  referenten?: Array<{
    name: string;
    bio: string;
    company: string;
    portrait: string;
  }>;
}

interface Topic {
  bat: number;
  topic: string;
  datum: string;
  eventType: string;
}

// Quality Check Functions
export async function checkMissingRequiredFields(legacyAppPath: string): Promise<DataQualityIssue[]> {
  const issues: DataQualityIssue[] = [];
  const sessionsPath = path.join(legacyAppPath, 'src/api/sessions.json');
  const sessions: Session[] = await fs.readJSON(sessionsPath);

  // Check for sessions without abstracts (excluding program brochures)
  const noAbstract = sessions.filter(
    (s) =>
      !s.abstract &&
      !s.authoren &&
      !s.title.toLowerCase().includes('programmheft')
  );

  if (noAbstract.length > 0) {
    issues.push({
      type: 'missing_field',
      severity: 'warning',
      field: 'abstract',
      description: `Sessions without abstracts (excluding program brochures)`,
      affectedRecords: noAbstract.length,
      examples: noAbstract.slice(0, 3).map((s) => `BAT ${s.bat}: ${s.title}`),
      remediation: 'Review sessions and add abstracts where appropriate. Consider if these are intentionally abstract-free.',
    });
  }

  // Check for sessions without speakers (should have referenten array)
  const noSpeakers = sessions.filter(
    (s) =>
      (!s.referenten || s.referenten.length === 0) &&
      !s.authoren &&
      !s.title.toLowerCase().includes('programmheft')
  );

  if (noSpeakers.length > 0) {
    issues.push({
      type: 'missing_field',
      severity: 'warning',
      field: 'referenten',
      description: 'Sessions without speaker information',
      affectedRecords: noSpeakers.length,
      examples: noSpeakers.slice(0, 3).map((s) => `BAT ${s.bat}: ${s.title}`),
      remediation: 'Add speaker information or mark as panel/workshop session.',
    });
  }

  // Check for speakers without bios
  let speakersWithoutBio = 0;
  const noBioExamples: string[] = [];

  for (const session of sessions) {
    if (session.referenten) {
      for (const speaker of session.referenten) {
        if (!speaker.bio || speaker.bio.trim().length === 0) {
          speakersWithoutBio++;
          if (noBioExamples.length < 3) {
            noBioExamples.push(speaker.name);
          }
        }
      }
    }
  }

  if (speakersWithoutBio > 0) {
    issues.push({
      type: 'missing_field',
      severity: 'info',
      field: 'bio',
      description: 'Speaker entries with missing or empty biography',
      affectedRecords: speakersWithoutBio,
      examples: noBioExamples,
      remediation: 'Add biographies for speakers where available.',
    });
  }

  // Check for speakers without portraits
  let speakersWithoutPortrait = 0;
  const noPortraitExamples: string[] = [];

  for (const session of sessions) {
    if (session.referenten) {
      for (const speaker of session.referenten) {
        if (!speaker.portrait || speaker.portrait.trim().length === 0) {
          speakersWithoutPortrait++;
          if (noPortraitExamples.length < 3) {
            noPortraitExamples.push(speaker.name);
          }
        }
      }
    }
  }

  if (speakersWithoutPortrait > 0) {
    issues.push({
      type: 'missing_field',
      severity: 'info',
      field: 'portrait',
      description: 'Speaker entries with missing portrait filename',
      affectedRecords: speakersWithoutPortrait,
      examples: noPortraitExamples,
      remediation: 'Add portrait images for speakers or use placeholder.',
    });
  }

  return issues;
}

export async function checkDuplicateSessions(legacyAppPath: string): Promise<DataQualityIssue[]> {
  const issues: DataQualityIssue[] = [];
  const sessionsPath = path.join(legacyAppPath, 'src/api/sessions.json');
  const sessions: Session[] = await fs.readJSON(sessionsPath);

  // Check for duplicate titles within same BAT
  const titleMap = new Map<string, Session[]>();

  for (const session of sessions) {
    const key = `${session.bat}-${session.title.toLowerCase().trim()}`;
    if (!titleMap.has(key)) {
      titleMap.set(key, []);
    }
    titleMap.get(key)!.push(session);
  }

  const duplicates = Array.from(titleMap.entries()).filter(([, sessions]) => sessions.length > 1);

  if (duplicates.length > 0) {
    issues.push({
      type: 'duplicate',
      severity: 'warning',
      field: 'title',
      description: 'Duplicate session titles within same event',
      affectedRecords: duplicates.reduce((sum, [, s]) => sum + s.length, 0),
      examples: duplicates.slice(0, 3).map(([key]) => key),
      remediation: 'Review and deduplicate or differentiate session titles.',
    });
  }

  // Check for duplicate PDF filenames
  const pdfMap = new Map<string, Session[]>();

  for (const session of sessions) {
    if (!pdfMap.has(session.pdf)) {
      pdfMap.set(session.pdf, []);
    }
    pdfMap.get(session.pdf)!.push(session);
  }

  const duplicatePdfs = Array.from(pdfMap.entries()).filter(([, sessions]) => sessions.length > 1);

  if (duplicatePdfs.length > 0) {
    issues.push({
      type: 'duplicate',
      severity: 'info',
      field: 'pdf',
      description: 'Same PDF referenced by multiple sessions (may be intentional)',
      affectedRecords: duplicatePdfs.reduce((sum, [, s]) => sum + s.length, 0),
      examples: duplicatePdfs.slice(0, 3).map(([pdf]) => pdf),
      remediation: 'Verify if PDF sharing is intentional or needs correction.',
    });
  }

  return issues;
}

export async function checkReferentialIntegrity(legacyAppPath: string): Promise<DataQualityIssue[]> {
  const issues: DataQualityIssue[] = [];
  const sessionsPath = path.join(legacyAppPath, 'src/api/sessions.json');
  const topicsPath = path.join(legacyAppPath, 'src/api/topics.json');
  const sessions: Session[] = await fs.readJSON(sessionsPath);
  const topics: Topic[] = await fs.readJSON(topicsPath);

  // Check for sessions referencing non-existent events
  const validBats = new Set(topics.map((t) => t.bat));
  const invalidBatSessions = sessions.filter((s) => !validBats.has(s.bat));

  if (invalidBatSessions.length > 0) {
    issues.push({
      type: 'referential_integrity',
      severity: 'critical',
      field: 'bat',
      description: 'Sessions reference events not in topics.json',
      affectedRecords: invalidBatSessions.length,
      examples: invalidBatSessions.slice(0, 3).map((s) => `BAT ${s.bat}: ${s.title}`),
      remediation: 'Add missing events to topics.json or correct BAT numbers.',
    });
  }

  // Check for missing PDF files
  const archivPath = path.join(legacyAppPath, 'src/archiv');
  const docsPath = path.join(legacyAppPath, 'docs');

  const existingPdfs = new Set<string>();

  // Scan archiv folder
  if (await fs.pathExists(archivPath)) {
    const archivPdfs = await glob('**/*.pdf', { cwd: archivPath });
    archivPdfs.forEach((pdf) => existingPdfs.add(path.basename(pdf).toLowerCase()));
  }

  // Scan docs folder
  if (await fs.pathExists(docsPath)) {
    const docsPdfs = await glob('**/*.pdf', { cwd: docsPath });
    docsPdfs.forEach((pdf) => existingPdfs.add(path.basename(pdf).toLowerCase()));
  }

  const missingPdfs = sessions.filter((s) => !existingPdfs.has(s.pdf.toLowerCase()));

  if (missingPdfs.length > 0) {
    issues.push({
      type: 'referential_integrity',
      severity: 'warning',
      field: 'pdf',
      description: 'PDF files referenced but not found on disk',
      affectedRecords: missingPdfs.length,
      examples: missingPdfs.slice(0, 5).map((s) => s.pdf),
      remediation: 'Locate missing PDF files or update references.',
    });
  }

  // Check for missing portrait images
  const existingImages = new Set<string>();

  if (await fs.pathExists(archivPath)) {
    const archivImages = await glob('**/*.{jpg,jpeg,png}', { cwd: archivPath });
    archivImages.forEach((img) => existingImages.add(path.basename(img).toLowerCase()));
  }

  let missingPortraits = 0;
  const missingPortraitExamples: string[] = [];

  for (const session of sessions) {
    if (session.referenten) {
      for (const speaker of session.referenten) {
        if (speaker.portrait && !existingImages.has(speaker.portrait.toLowerCase())) {
          missingPortraits++;
          if (missingPortraitExamples.length < 5) {
            missingPortraitExamples.push(`${speaker.name}: ${speaker.portrait}`);
          }
        }
      }
    }
  }

  if (missingPortraits > 0) {
    issues.push({
      type: 'referential_integrity',
      severity: 'warning',
      field: 'portrait',
      description: 'Portrait images referenced but not found on disk',
      affectedRecords: missingPortraits,
      examples: missingPortraitExamples,
      remediation: 'Locate missing portrait images or update references.',
    });
  }

  // Check for events without sessions
  const batsWithSessions = new Set(sessions.map((s) => s.bat));
  const eventsWithoutSessions = topics.filter((t) => !batsWithSessions.has(t.bat));

  if (eventsWithoutSessions.length > 0) {
    issues.push({
      type: 'referential_integrity',
      severity: 'info',
      field: 'bat',
      description: 'Events with no sessions/presentations',
      affectedRecords: eventsWithoutSessions.length,
      examples: eventsWithoutSessions.slice(0, 3).map((t) => `BAT ${t.bat}: ${t.topic}`),
      remediation: 'Add sessions for these events or confirm they are future events.',
    });
  }

  return issues;
}

export async function checkDataConsistency(legacyAppPath: string): Promise<DataQualityIssue[]> {
  const issues: DataQualityIssue[] = [];
  const topicsPath = path.join(legacyAppPath, 'src/api/topics.json');
  const sessionsPath = path.join(legacyAppPath, 'src/api/sessions.json');
  const topics: Topic[] = await fs.readJSON(topicsPath);
  const sessions: Session[] = await fs.readJSON(sessionsPath);

  // Check date format consistency
  const dateFormats = new Set<string>();
  for (const topic of topics) {
    // Extract format pattern
    if (topic.datum.match(/^\d{1,2}\.\s+\w+\s+\d{2},/)) {
      dateFormats.add('DD. Month YY, HH:MMh');
    } else if (topic.datum.match(/^\d{1,2}\.\s+\w+\s+\d{4}/)) {
      dateFormats.add('DD. Month YYYY');
    } else {
      dateFormats.add('other');
    }
  }

  if (dateFormats.size > 1) {
    issues.push({
      type: 'inconsistency',
      severity: 'info',
      field: 'datum',
      description: 'Multiple date formats detected in topics',
      affectedRecords: topics.length,
      examples: Array.from(dateFormats),
      remediation: 'Standardize date format during migration.',
    });
  }

  // Check company name consistency
  const companyVariations = new Map<string, Set<string>>();

  for (const session of sessions) {
    if (session.referenten) {
      for (const speaker of session.referenten) {
        if (speaker.company) {
          const company = speaker.company.toLowerCase().trim();
          const normalized = company.replace(/[^a-z0-9]/g, '');

          if (!companyVariations.has(normalized)) {
            companyVariations.set(normalized, new Set());
          }
          companyVariations.get(normalized)!.add(speaker.company);
        }
      }
    }
  }

  const inconsistentCompanies = Array.from(companyVariations.entries())
    .filter(([, variations]) => variations.size > 1)
    .map(([, variations]) => Array.from(variations));

  if (inconsistentCompanies.length > 0) {
    issues.push({
      type: 'inconsistency',
      severity: 'warning',
      field: 'company',
      description: 'Company name variations detected',
      affectedRecords: inconsistentCompanies.length,
      examples: inconsistentCompanies.slice(0, 3).map((v) => v.join(' / ')),
      remediation: 'Normalize company names during migration.',
    });
  }

  // Check event type consistency
  const eventTypes = new Set(topics.map((t) => t.eventType));
  if (eventTypes.size > 3) {
    issues.push({
      type: 'inconsistency',
      severity: 'info',
      field: 'eventType',
      description: 'Multiple event type variations detected',
      affectedRecords: topics.length,
      examples: Array.from(eventTypes),
      remediation: 'Standardize event types during migration.',
    });
  }

  return issues;
}

export function categorizeIssuesBySeverity(issues: DataQualityIssue[]): CategorizedIssues {
  return {
    critical: issues.filter((i) => i.severity === 'critical'),
    warning: issues.filter((i) => i.severity === 'warning'),
    info: issues.filter((i) => i.severity === 'info'),
  };
}

export async function generateQualityReport(legacyAppPath: string): Promise<QualityReport> {
  const missingFieldIssues = await checkMissingRequiredFields(legacyAppPath);
  const duplicateIssues = await checkDuplicateSessions(legacyAppPath);
  const integrityIssues = await checkReferentialIntegrity(legacyAppPath);
  const consistencyIssues = await checkDataConsistency(legacyAppPath);

  const allIssues = [...missingFieldIssues, ...duplicateIssues, ...integrityIssues, ...consistencyIssues];

  const categorized = categorizeIssuesBySeverity(allIssues);

  return {
    generatedAt: new Date().toISOString(),
    summary: {
      totalIssues: allIssues.length,
      criticalCount: categorized.critical.length,
      warningCount: categorized.warning.length,
      infoCount: categorized.info.length,
    },
    allIssues,
    categorized,
  };
}

// Markdown Report Generator
export function generateMarkdownReport(report: QualityReport): string {
  let md = '# Data Quality Report\n\n';
  md += `Generated: ${report.generatedAt}\n\n`;

  md += '## Summary\n\n';
  md += `- **Total Issues Found**: ${report.summary.totalIssues}\n`;
  md += `- **Critical**: ${report.summary.criticalCount}\n`;
  md += `- **Warning**: ${report.summary.warningCount}\n`;
  md += `- **Info**: ${report.summary.infoCount}\n\n`;

  if (report.categorized.critical.length > 0) {
    md += '## Critical Issues\n\n';
    md += 'These issues must be resolved before migration.\n\n';
    for (const issue of report.categorized.critical) {
      md += `### ${issue.description}\n\n`;
      md += `- **Type**: ${issue.type}\n`;
      md += `- **Field**: ${issue.field}\n`;
      md += `- **Affected Records**: ${issue.affectedRecords}\n`;
      md += `- **Examples**:\n`;
      issue.examples.forEach((ex) => (md += `  - ${ex}\n`));
      md += `- **Remediation**: ${issue.remediation}\n\n`;
    }
  }

  if (report.categorized.warning.length > 0) {
    md += '## Warnings\n\n';
    md += 'These issues should be addressed to ensure data quality.\n\n';
    for (const issue of report.categorized.warning) {
      md += `### ${issue.description}\n\n`;
      md += `- **Type**: ${issue.type}\n`;
      md += `- **Field**: ${issue.field}\n`;
      md += `- **Affected Records**: ${issue.affectedRecords}\n`;
      md += `- **Examples**:\n`;
      issue.examples.forEach((ex) => (md += `  - ${ex}\n`));
      md += `- **Remediation**: ${issue.remediation}\n\n`;
    }
  }

  if (report.categorized.info.length > 0) {
    md += '## Information\n\n';
    md += 'These are observations that may be useful during migration.\n\n';
    for (const issue of report.categorized.info) {
      md += `### ${issue.description}\n\n`;
      md += `- **Type**: ${issue.type}\n`;
      md += `- **Field**: ${issue.field}\n`;
      md += `- **Affected Records**: ${issue.affectedRecords}\n`;
      md += `- **Examples**:\n`;
      issue.examples.forEach((ex) => (md += `  - ${ex}\n`));
      md += `- **Remediation**: ${issue.remediation}\n\n`;
    }
  }

  return md;
}

// Main execution
async function main() {
  const legacyAppPath = path.resolve(__dirname, '../../BATspa-old');
  const outputDir = path.resolve(__dirname, '../../../docs/migration');

  console.log('Running Data Quality Checks...');
  console.log(`Source: ${legacyAppPath}`);

  const report = await generateQualityReport(legacyAppPath);

  // Save JSON report
  const jsonReportPath = path.join(__dirname, '../reports/data-quality-report.json');
  await fs.ensureDir(path.dirname(jsonReportPath));
  await fs.writeJSON(jsonReportPath, report, { spaces: 2 });
  console.log(`JSON report saved: ${jsonReportPath}`);

  // Generate and save Markdown report
  const markdownReport = generateMarkdownReport(report);
  const mdReportPath = path.join(outputDir, 'data-quality-report.md');
  await fs.ensureDir(outputDir);
  await fs.writeFile(mdReportPath, markdownReport);
  console.log(`Markdown report saved: ${mdReportPath}`);

  console.log('\nQuality Summary:');
  console.log(`- Total Issues: ${report.summary.totalIssues}`);
  console.log(`- Critical: ${report.summary.criticalCount}`);
  console.log(`- Warnings: ${report.summary.warningCount}`);
  console.log(`- Info: ${report.summary.infoCount}`);
}

if (require.main === module) {
  main().catch(console.error);
}
