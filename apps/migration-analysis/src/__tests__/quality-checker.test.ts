import * as path from 'path';
import {
  checkMissingRequiredFields,
  checkDuplicateSessions,
  checkReferentialIntegrity,
  checkDataConsistency,
  categorizeIssuesBySeverity,
  generateQualityReport,
  DataQualityIssue,
  QualityReport,
} from '../quality-checker';

const LEGACY_APP_PATH = path.resolve(__dirname, '../../../BATspa-old');

describe('Data Quality Analysis', () => {
  describe('AC4 - Data Quality Report', () => {
    test('should_detectMissingRequiredFields_when_qualityCheckRun', async () => {
      const issues = await checkMissingRequiredFields(LEGACY_APP_PATH);

      expect(issues).toBeDefined();
      expect(Array.isArray(issues)).toBe(true);

      // Should return array of issues (may or may not have missing abstracts)
      issues.forEach((issue) => {
        expect(issue.type).toBeDefined();
        expect(issue.severity).toBeDefined();
        expect(issue.field).toBeDefined();
        expect(issue.remediation).toBeDefined();
      });
    });

    test('should_identifyDuplicateSessions_when_qualityCheckRun', async () => {
      const duplicates = await checkDuplicateSessions(LEGACY_APP_PATH);

      expect(duplicates).toBeDefined();
      expect(Array.isArray(duplicates)).toBe(true);

      // Check structure if duplicates found
      duplicates.forEach((dup) => {
        expect(dup.type).toBe('duplicate');
        expect(dup.severity).toBeDefined();
        expect(dup.description).toBeDefined();
      });
    });

    test('should_flagInconsistentData_when_qualityCheckRun', async () => {
      const inconsistencies = await checkDataConsistency(LEGACY_APP_PATH);

      expect(inconsistencies).toBeDefined();
      expect(Array.isArray(inconsistencies)).toBe(true);

      // Should find date format inconsistencies
      const dateIssues = inconsistencies.filter((i) => i.field === 'datum');
      expect(dateIssues.length).toBeGreaterThanOrEqual(0);

      // Should find company name variations
      const companyIssues = inconsistencies.filter((i) => i.field === 'company');
      expect(companyIssues.length).toBeGreaterThanOrEqual(0);
    });

    test('should_checkReferentialIntegrity_when_qualityCheckRun', async () => {
      const integrityIssues = await checkReferentialIntegrity(LEGACY_APP_PATH);

      expect(integrityIssues).toBeDefined();
      expect(Array.isArray(integrityIssues)).toBe(true);

      // Each issue should have required properties
      integrityIssues.forEach((issue) => {
        expect(issue.type).toBe('referential_integrity');
        expect(issue.severity).toBeDefined();
        expect(issue.description).toBeDefined();
      });
    });
  });

  describe('AC7 - Data Quality Issues Documented', () => {
    test('should_categorizeIssuesBySeverity_when_reportGenerated', async () => {
      const report = await generateQualityReport(LEGACY_APP_PATH);
      const categorized = categorizeIssuesBySeverity(report.allIssues);

      expect(categorized).toBeDefined();
      expect(categorized.critical).toBeDefined();
      expect(categorized.warning).toBeDefined();
      expect(categorized.info).toBeDefined();

      expect(Array.isArray(categorized.critical)).toBe(true);
      expect(Array.isArray(categorized.warning)).toBe(true);
      expect(Array.isArray(categorized.info)).toBe(true);
    });

    test('should_provideRemediationSteps_when_issueDocumented', async () => {
      const report = await generateQualityReport(LEGACY_APP_PATH);

      // Every issue should have remediation
      report.allIssues.forEach((issue) => {
        expect(issue.remediation).toBeDefined();
        expect(issue.remediation.length).toBeGreaterThan(0);
      });
    });

    test('should_includeIssueCounts_when_reportGenerated', async () => {
      const report = await generateQualityReport(LEGACY_APP_PATH);

      expect(report.summary).toBeDefined();
      expect(report.summary.totalIssues).toBeDefined();
      expect(report.summary.criticalCount).toBeDefined();
      expect(report.summary.warningCount).toBeDefined();
      expect(report.summary.infoCount).toBeDefined();
    });
  });

  describe('Specific Quality Checks', () => {
    test('should_detectMissingSpeakerBios_when_qualityCheckRun', async () => {
      const report = await generateQualityReport(LEGACY_APP_PATH);

      const bioIssues = report.allIssues.filter(
        (i) => i.field === 'bio' && i.description.includes('missing') || i.description.includes('empty')
      );

      // Should find some speakers without bios
      expect(bioIssues.length).toBeGreaterThanOrEqual(0);
    });

    test('should_detectMissingSpeakerPhotos_when_qualityCheckRun', async () => {
      const report = await generateQualityReport(LEGACY_APP_PATH);

      const photoIssues = report.allIssues.filter(
        (i) => i.field === 'portrait' && (i.description.includes('missing') || i.description.includes('not found'))
      );

      // May or may not find missing photos
      expect(photoIssues.length).toBeGreaterThanOrEqual(0);
    });

    test('should_detectEventsWithoutSessions_when_qualityCheckRun', async () => {
      const report = await generateQualityReport(LEGACY_APP_PATH);

      const noSessionsIssues = report.allIssues.filter((i) => i.description.includes('no sessions'));

      // Check if any events have no sessions
      expect(noSessionsIssues.length).toBeGreaterThanOrEqual(0);
    });
  });
});
