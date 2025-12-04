import * as fs from 'fs-extra';
import * as path from 'path';
import { glob } from 'glob';

interface Speaker {
  name: string;
  bio: string;
  company: string;
  portrait: string;
}

interface Session {
  bat: number;
  pdf: string;
  title: string;
  abstract?: string;
  authoren?: string;
  referenten?: Speaker[];
}

interface Topic {
  bat: number;
  topic: string;
  datum: string;
  eventType: string;
}

interface ValidationCheck {
  name: string;
  passed: boolean;
  details: string;
}

interface EventValidation {
  batNumber: number;
  topic: string;
  date: string;
  eventType: string;
  sessionCount: number;
  speakerCount: number;
  checks: ValidationCheck[];
  overallStatus: 'PASS' | 'PARTIAL' | 'FAIL';
}

interface SampleValidationReport {
  generatedAt: string;
  totalEventsValidated: number;
  passCount: number;
  partialCount: number;
  failCount: number;
  validations: EventValidation[];
  summary: string;
}

async function selectRandomEvents(legacyAppPath: string, count: number = 10): Promise<number[]> {
  const topicsPath = path.join(legacyAppPath, 'src/api/topics.json');
  const topics: Topic[] = await fs.readJSON(topicsPath);

  // Shuffle and select
  const batNumbers = topics.map((t) => t.bat);
  const shuffled = batNumbers.sort(() => Math.random() - 0.5);
  return shuffled.slice(0, count);
}

async function validateEvent(
  batNumber: number,
  topics: Topic[],
  sessions: Session[],
  existingPdfs: Set<string>,
  existingImages: Set<string>
): Promise<EventValidation> {
  const topic = topics.find((t) => t.bat === batNumber);
  const eventSessions = sessions.filter((s) => s.bat === batNumber);

  const checks: ValidationCheck[] = [];

  // 1. Event Data (topics.json)
  if (topic) {
    checks.push({
      name: 'BAT number exists',
      passed: true,
      details: `BAT ${batNumber} found in topics.json`,
    });

    // Check topic/theme
    const hasValidTopic = Boolean(topic.topic && topic.topic.trim().length > 0);
    checks.push({
      name: 'Topic/theme is accurate',
      passed: hasValidTopic,
      details: hasValidTopic ? `Topic: ${topic.topic.trim()}` : 'Missing or empty topic',
    });

    // Check date is parseable
    const dateRegex = /\d{1,2}\.\s+\w+\s+\d{2,4}/;
    const hasValidDate = dateRegex.test(topic.datum);
    checks.push({
      name: 'Date is parseable',
      passed: hasValidDate,
      details: hasValidDate ? `Date: ${topic.datum}` : `Unparseable date: ${topic.datum}`,
    });

    // Check event type
    const validTypes = ['abend-bat', 'halb-bat', 'ganztag-bat', 'forum'];
    const hasValidType = validTypes.some((t) => topic.eventType.toLowerCase().includes(t.split('-')[0]));
    checks.push({
      name: 'Event type is valid',
      passed: Boolean(hasValidType),
      details: hasValidType ? `Type: ${topic.eventType}` : `Unknown type: ${topic.eventType}`,
    });
  } else {
    checks.push({
      name: 'BAT number exists',
      passed: false,
      details: `BAT ${batNumber} NOT found in topics.json`,
    });
  }

  // 2. Session Data (sessions.json)
  if (eventSessions.length > 0) {
    checks.push({
      name: 'Has sessions/presentations',
      passed: true,
      details: `Found ${eventSessions.length} sessions`,
    });

    // Check PDF files exist
    let pdfsMissing = 0;
    for (const session of eventSessions) {
      if (!existingPdfs.has(session.pdf.toLowerCase())) {
        pdfsMissing++;
      }
    }
    checks.push({
      name: 'PDF files exist on disk',
      passed: pdfsMissing === 0,
      details: pdfsMissing === 0 ? 'All PDFs found' : `${pdfsMissing}/${eventSessions.length} PDFs missing`,
    });

    // Check titles are meaningful
    const emptyTitles = eventSessions.filter((s) => !s.title || s.title.trim().length < 3).length;
    checks.push({
      name: 'Session titles are meaningful',
      passed: emptyTitles === 0,
      details: emptyTitles === 0 ? 'All titles valid' : `${emptyTitles} sessions with empty/short titles`,
    });

    // Check abstract quality (for non-program sessions)
    const presentations = eventSessions.filter(
      (s) => !s.authoren && !s.title.toLowerCase().includes('programmheft')
    );
    const withAbstract = presentations.filter((s) => s.abstract && s.abstract.length > 50).length;
    checks.push({
      name: 'Abstracts have sufficient content',
      passed: presentations.length === 0 || withAbstract > 0,
      details:
        presentations.length === 0
          ? 'No presentations to check'
          : `${withAbstract}/${presentations.length} presentations have abstracts`,
    });

    // Check speaker count is reasonable
    let totalSpeakers = 0;
    for (const session of eventSessions) {
      if (session.referenten) {
        totalSpeakers += session.referenten.length;
      }
    }
    const reasonableSpeakerCount = totalSpeakers > 0 && totalSpeakers <= eventSessions.length * 3;
    checks.push({
      name: 'Speaker count is reasonable',
      passed: reasonableSpeakerCount,
      details: `${totalSpeakers} speaker mentions across ${eventSessions.length} sessions`,
    });
  } else {
    checks.push({
      name: 'Has sessions/presentations',
      passed: false,
      details: 'No sessions found for this event',
    });
  }

  // 3. Speaker Data
  let speakerChecks = 0;
  let speakersParseable = 0;
  let speakersWithMeaningfulBio = 0;
  let speakersWithValidPortrait = 0;

  for (const session of eventSessions) {
    if (session.referenten) {
      for (const speaker of session.referenten) {
        speakerChecks++;

        // Name parseable (FirstName LastName, Company)
        if (speaker.name && speaker.name.includes(',')) {
          speakersParseable++;
        }

        // Bio is meaningful
        if (speaker.bio && speaker.bio.length > 50) {
          speakersWithMeaningfulBio++;
        }

        // Portrait exists
        if (speaker.portrait && existingImages.has(speaker.portrait.toLowerCase())) {
          speakersWithValidPortrait++;
        }
      }
    }
  }

  if (speakerChecks > 0) {
    checks.push({
      name: 'Speaker names parseable',
      passed: speakersParseable === speakerChecks,
      details: `${speakersParseable}/${speakerChecks} names follow "Name, Company" format`,
    });

    checks.push({
      name: 'Speaker bios meaningful',
      passed: speakersWithMeaningfulBio > 0,
      details: `${speakersWithMeaningfulBio}/${speakerChecks} speakers have detailed bios`,
    });

    checks.push({
      name: 'Speaker portraits exist',
      passed: speakersWithValidPortrait > 0,
      details: `${speakersWithValidPortrait}/${speakerChecks} portraits found on disk`,
    });
  }

  // Calculate overall status
  const passedChecks = checks.filter((c) => c.passed).length;
  const totalChecks = checks.length;
  let overallStatus: 'PASS' | 'PARTIAL' | 'FAIL';

  if (passedChecks === totalChecks) {
    overallStatus = 'PASS';
  } else if (passedChecks >= totalChecks * 0.7) {
    overallStatus = 'PARTIAL';
  } else {
    overallStatus = 'FAIL';
  }

  return {
    batNumber,
    topic: topic?.topic.trim() || 'Unknown',
    date: topic?.datum || 'Unknown',
    eventType: topic?.eventType || 'Unknown',
    sessionCount: eventSessions.length,
    speakerCount: speakerChecks,
    checks,
    overallStatus,
  };
}

export async function generateSampleValidationReport(legacyAppPath: string): Promise<SampleValidationReport> {
  const topicsPath = path.join(legacyAppPath, 'src/api/topics.json');
  const sessionsPath = path.join(legacyAppPath, 'src/api/sessions.json');
  const topics: Topic[] = await fs.readJSON(topicsPath);
  const sessions: Session[] = await fs.readJSON(sessionsPath);

  // Build set of existing files
  const existingPdfs = new Set<string>();
  const existingImages = new Set<string>();

  const archivPath = path.join(legacyAppPath, 'src/archiv');
  const docsPath = path.join(legacyAppPath, 'docs');

  if (await fs.pathExists(archivPath)) {
    const pdfs = await glob('**/*.pdf', { cwd: archivPath });
    pdfs.forEach((p) => existingPdfs.add(path.basename(p).toLowerCase()));

    const images = await glob('**/*.{jpg,jpeg,png}', { cwd: archivPath });
    images.forEach((i) => existingImages.add(path.basename(i).toLowerCase()));
  }

  if (await fs.pathExists(docsPath)) {
    const pdfs = await glob('**/*.pdf', { cwd: docsPath });
    pdfs.forEach((p) => existingPdfs.add(path.basename(p).toLowerCase()));
  }

  // Select 10 random events
  const selectedBats = await selectRandomEvents(legacyAppPath, 10);

  // Validate each event
  const validations: EventValidation[] = [];
  for (const bat of selectedBats) {
    const validation = await validateEvent(bat, topics, sessions, existingPdfs, existingImages);
    validations.push(validation);
  }

  // Sort by BAT number
  validations.sort((a, b) => a.batNumber - b.batNumber);

  const passCount = validations.filter((v) => v.overallStatus === 'PASS').length;
  const partialCount = validations.filter((v) => v.overallStatus === 'PARTIAL').length;
  const failCount = validations.filter((v) => v.overallStatus === 'FAIL').length;

  const summary =
    passCount >= 8
      ? 'Excellent data quality - migration can proceed with high confidence.'
      : passCount >= 5
        ? 'Good data quality with some issues to address during migration.'
        : 'Significant data quality issues found - recommend remediation before migration.';

  return {
    generatedAt: new Date().toISOString(),
    totalEventsValidated: validations.length,
    passCount,
    partialCount,
    failCount,
    validations,
    summary,
  };
}

function generateMarkdownReport(report: SampleValidationReport): string {
  let md = '# Sample Data Validation Report\n\n';
  md += `Generated: ${report.generatedAt}\n\n`;

  md += '## Summary\n\n';
  md += `- **Events Validated**: ${report.totalEventsValidated}\n`;
  md += `- **PASS**: ${report.passCount}\n`;
  md += `- **PARTIAL**: ${report.partialCount}\n`;
  md += `- **FAIL**: ${report.failCount}\n\n`;
  md += `**Assessment**: ${report.summary}\n\n`;

  md += '## Validation Results\n\n';

  for (const validation of report.validations) {
    const statusEmoji = validation.overallStatus === 'PASS' ? '✅' : validation.overallStatus === 'PARTIAL' ? '⚠️' : '❌';

    md += `### BAT ${validation.batNumber}: ${validation.topic} ${statusEmoji}\n\n`;
    md += `- **Date**: ${validation.date}\n`;
    md += `- **Type**: ${validation.eventType}\n`;
    md += `- **Sessions**: ${validation.sessionCount}\n`;
    md += `- **Speakers**: ${validation.speakerCount}\n\n`;

    md += '**Validation Checks:**\n\n';
    md += '| Check | Status | Details |\n';
    md += '|-------|--------|----------|\n';

    for (const check of validation.checks) {
      const checkStatus = check.passed ? '✅' : '❌';
      md += `| ${check.name} | ${checkStatus} | ${check.details} |\n`;
    }

    md += '\n---\n\n';
  }

  return md;
}

// Main execution
async function main() {
  const legacyAppPath = path.resolve(__dirname, '../../BATspa-old');
  const outputDir = path.resolve(__dirname, '../../../docs/migration');

  console.log('Running Sample Data Validation...');
  console.log(`Source: ${legacyAppPath}`);
  console.log('Selecting 10 random events for validation...\n');

  const report = await generateSampleValidationReport(legacyAppPath);

  // Save JSON report
  const jsonReportPath = path.join(__dirname, '../reports/sample-validation.json');
  await fs.ensureDir(path.dirname(jsonReportPath));
  await fs.writeJSON(jsonReportPath, report, { spaces: 2 });
  console.log(`JSON report saved: ${jsonReportPath}`);

  // Generate and save Markdown report
  const markdownReport = generateMarkdownReport(report);
  const mdReportPath = path.join(outputDir, 'sample-validation-report.md');
  await fs.ensureDir(outputDir);
  await fs.writeFile(mdReportPath, markdownReport);
  console.log(`Markdown report saved: ${mdReportPath}`);

  console.log('\nValidation Summary:');
  console.log(`- Total Events: ${report.totalEventsValidated}`);
  console.log(`- PASS: ${report.passCount}`);
  console.log(`- PARTIAL: ${report.partialCount}`);
  console.log(`- FAIL: ${report.failCount}`);
  console.log(`\n${report.summary}`);
}

if (require.main === module) {
  main().catch(console.error);
}
