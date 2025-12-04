import * as fs from 'fs-extra';
import * as path from 'path';

// Types
export interface Speaker {
  name: string;
  bio: string;
  company: string;
  portrait: string;
}

export interface Session {
  bat: number;
  pdf: string;
  title: string;
  abstract?: string;
  authoren?: string;
  referenten?: Speaker[];
}

export interface Topic {
  bat: number;
  topic: string;
  datum: string;
  eventType: string;
}

export interface EventsByType {
  abendBat: number;
  halbBat: number;
  ganztagBat: number;
  other: number;
}

export interface UniqueSpeaker {
  name: string;
  company: string;
  hasBio: boolean;
  hasPhoto: boolean;
  eventCount: number;
}

export interface MigrationMetrics {
  // Event counts
  totalEvents: number;
  eventsByType: EventsByType;

  // Session/Presentation counts
  totalSessions: number;
  sessionsWithAbstract: number;
  programBrochures: number;

  // Speaker counts
  totalSpeakerMentions: number;
  uniqueSpeakers: number;
  speakersWithBio: number;
  speakersWithPhoto: number;

  // Company counts
  uniqueCompanies: number;

  // Detailed data
  speakerList: UniqueSpeaker[];
  companyList: string[];
}

// Core Functions
export async function countUniqueEvents(legacyAppPath: string): Promise<number> {
  const topicsPath = path.join(legacyAppPath, 'src/api/topics.json');
  const topics: Topic[] = await fs.readJSON(topicsPath);

  const uniqueBats = new Set(topics.map((t) => t.bat));
  return uniqueBats.size;
}

export async function countEventsByType(legacyAppPath: string): Promise<EventsByType> {
  const topicsPath = path.join(legacyAppPath, 'src/api/topics.json');
  const topics: Topic[] = await fs.readJSON(topicsPath);

  const result: EventsByType = {
    abendBat: 0,
    halbBat: 0,
    ganztagBat: 0,
    other: 0,
  };

  for (const topic of topics) {
    const type = topic.eventType?.toLowerCase() || '';
    if (type.includes('abend')) {
      result.abendBat++;
    } else if (type.includes('halb')) {
      result.halbBat++;
    } else if (type.includes('ganztag') || type.includes('forum')) {
      result.ganztagBat++;
    } else {
      result.other++;
    }
  }

  return result;
}

export async function extractUniqueSpeakers(legacyAppPath: string): Promise<UniqueSpeaker[]> {
  const sessionsPath = path.join(legacyAppPath, 'src/api/sessions.json');
  const sessions: Session[] = await fs.readJSON(sessionsPath);

  const speakerMap = new Map<string, UniqueSpeaker>();

  for (const session of sessions) {
    if (session.referenten && Array.isArray(session.referenten)) {
      for (const speaker of session.referenten) {
        // Extract name (format: "FirstName LastName, Company")
        const nameParts = speaker.name.split(',');
        const fullName = nameParts[0].trim();

        if (!speakerMap.has(fullName)) {
          speakerMap.set(fullName, {
            name: fullName,
            company: speaker.company || '',
            hasBio: !!(speaker.bio && speaker.bio.trim().length > 0),
            hasPhoto: !!(speaker.portrait && speaker.portrait.trim().length > 0),
            eventCount: 1,
          });
        } else {
          const existing = speakerMap.get(fullName)!;
          existing.eventCount++;

          // Update if this instance has more info
          if (!existing.hasBio && speaker.bio && speaker.bio.trim().length > 0) {
            existing.hasBio = true;
          }
          if (!existing.hasPhoto && speaker.portrait && speaker.portrait.trim().length > 0) {
            existing.hasPhoto = true;
          }
        }
      }
    }
  }

  return Array.from(speakerMap.values()).sort((a, b) => a.name.localeCompare(b.name));
}

export async function countUniqueSpeakers(legacyAppPath: string): Promise<number> {
  const speakers = await extractUniqueSpeakers(legacyAppPath);
  return speakers.length;
}

export async function extractUniqueCompanies(legacyAppPath: string): Promise<string[]> {
  const speakers = await extractUniqueSpeakers(legacyAppPath);

  const companies = new Set<string>();
  for (const speaker of speakers) {
    if (speaker.company && speaker.company.trim().length > 0) {
      companies.add(speaker.company.toLowerCase().trim());
    }
  }

  return Array.from(companies).sort();
}

export async function countUniqueCompanies(legacyAppPath: string): Promise<number> {
  const companies = await extractUniqueCompanies(legacyAppPath);
  return companies.length;
}

export async function calculateMigrationMetrics(legacyAppPath: string): Promise<MigrationMetrics> {
  const sessionsPath = path.join(legacyAppPath, 'src/api/sessions.json');
  const sessions: Session[] = await fs.readJSON(sessionsPath);

  const totalSessions = sessions.length;

  // Count sessions with abstracts
  let sessionsWithAbstract = 0;
  let programBrochures = 0;
  let totalSpeakerMentions = 0;

  for (const session of sessions) {
    if (session.abstract && session.abstract.trim().length > 0) {
      sessionsWithAbstract++;
    }

    // Program brochures have authoren field or title contains "Programmheft"
    if (
      session.authoren !== undefined ||
      session.title.toLowerCase().includes('programmheft') ||
      session.title.toLowerCase().includes('programm')
    ) {
      programBrochures++;
    }

    if (session.referenten && Array.isArray(session.referenten)) {
      totalSpeakerMentions += session.referenten.length;
    }
  }

  const totalEvents = await countUniqueEvents(legacyAppPath);
  const eventsByType = await countEventsByType(legacyAppPath);
  const speakerList = await extractUniqueSpeakers(legacyAppPath);
  const companyList = await extractUniqueCompanies(legacyAppPath);

  const speakersWithBio = speakerList.filter((s) => s.hasBio).length;
  const speakersWithPhoto = speakerList.filter((s) => s.hasPhoto).length;

  return {
    totalEvents,
    eventsByType,
    totalSessions,
    sessionsWithAbstract,
    programBrochures,
    totalSpeakerMentions,
    uniqueSpeakers: speakerList.length,
    speakersWithBio,
    speakersWithPhoto,
    uniqueCompanies: companyList.length,
    speakerList,
    companyList,
  };
}

// Markdown Report Generator
export function generateMarkdownReport(metrics: MigrationMetrics): string {
  let md = '# Volume Metrics Report\n\n';
  md += `Generated: ${new Date().toISOString()}\n\n`;

  md += '## Summary Statistics\n\n';
  md += '| Category | Metric | Count |\n';
  md += '|----------|--------|-------|\n';
  md += `| Events | Total Unique Events | ${metrics.totalEvents} |\n`;
  md += `| | Abend-BAT (Evening) | ${metrics.eventsByType.abendBat} |\n`;
  md += `| | Halb-BAT (Half-day) | ${metrics.eventsByType.halbBat} |\n`;
  md += `| | Ganztag-BAT (Full-day) | ${metrics.eventsByType.ganztagBat} |\n`;
  if (metrics.eventsByType.other > 0) {
    md += `| | Other | ${metrics.eventsByType.other} |\n`;
  }
  md += `| Sessions | Total Sessions/Presentations | ${metrics.totalSessions} |\n`;
  md += `| | Sessions with Abstract | ${metrics.sessionsWithAbstract} |\n`;
  md += `| | Program Brochures | ${metrics.programBrochures} |\n`;
  md += `| Speakers | Total Speaker Mentions | ${metrics.totalSpeakerMentions} |\n`;
  md += `| | Unique Speakers | ${metrics.uniqueSpeakers} |\n`;
  md += `| | Speakers with Bio | ${metrics.speakersWithBio} |\n`;
  md += `| | Speakers with Photo | ${metrics.speakersWithPhoto} |\n`;
  md += `| Companies | Unique Companies | ${metrics.uniqueCompanies} |\n\n`;

  md += '## Data Completeness\n\n';
  const abstractPercentage = Math.round((metrics.sessionsWithAbstract / metrics.totalSessions) * 100);
  const bioPercentage = Math.round((metrics.speakersWithBio / metrics.uniqueSpeakers) * 100);
  const photoPercentage = Math.round((metrics.speakersWithPhoto / metrics.uniqueSpeakers) * 100);

  md += `- Sessions with Abstracts: ${abstractPercentage}%\n`;
  md += `- Speakers with Biographies: ${bioPercentage}%\n`;
  md += `- Speakers with Photos: ${photoPercentage}%\n\n`;

  md += '## Top Speakers (by appearances)\n\n';
  const topSpeakers = [...metrics.speakerList].sort((a, b) => b.eventCount - a.eventCount).slice(0, 20);

  md += '| Speaker | Company | Appearances | Has Bio | Has Photo |\n';
  md += '|---------|---------|-------------|---------|----------|\n';
  for (const speaker of topSpeakers) {
    md += `| ${speaker.name} | ${speaker.company} | ${speaker.eventCount} | ${speaker.hasBio ? '✓' : '✗'} | ${speaker.hasPhoto ? '✓' : '✗'} |\n`;
  }

  md += '\n## All Companies\n\n';
  md += '<details>\n<summary>View all companies</summary>\n\n';
  for (const company of metrics.companyList) {
    md += `- ${company}\n`;
  }
  md += '\n</details>\n\n';

  return md;
}

// Main execution
async function main() {
  const legacyAppPath = path.resolve(__dirname, '../../BATspa-old');
  const outputDir = path.resolve(__dirname, '../../../docs/migration');

  console.log('Calculating Volume Metrics...');
  console.log(`Source: ${legacyAppPath}`);

  const metrics = await calculateMigrationMetrics(legacyAppPath);

  // Save JSON report
  const jsonReportPath = path.join(__dirname, '../reports/volume-metrics.json');
  await fs.ensureDir(path.dirname(jsonReportPath));
  await fs.writeJSON(jsonReportPath, metrics, { spaces: 2 });
  console.log(`JSON report saved: ${jsonReportPath}`);

  // Generate and save Markdown report
  const markdownReport = generateMarkdownReport(metrics);
  const mdReportPath = path.join(outputDir, 'volume-metrics.md');
  await fs.ensureDir(outputDir);
  await fs.writeFile(mdReportPath, markdownReport);
  console.log(`Markdown report saved: ${mdReportPath}`);

  console.log('\nKey Metrics:');
  console.log(`- Total Events: ${metrics.totalEvents}`);
  console.log(`- Total Sessions: ${metrics.totalSessions}`);
  console.log(`- Unique Speakers: ${metrics.uniqueSpeakers}`);
  console.log(`- Unique Companies: ${metrics.uniqueCompanies}`);
}

if (require.main === module) {
  main().catch(console.error);
}
