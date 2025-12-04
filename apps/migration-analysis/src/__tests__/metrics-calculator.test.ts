import * as path from 'path';
import {
  countUniqueEvents,
  countEventsByType,
  countUniqueSpeakers,
  extractUniqueSpeakers,
  countUniqueCompanies,
  extractUniqueCompanies,
  calculateMigrationMetrics,
} from '../metrics-calculator';

const LEGACY_APP_PATH = path.resolve(__dirname, '../../../BATspa-old');

describe('Volume Metrics Calculation', () => {
  describe('AC5 - Volume Metrics', () => {
    test('should_countUniqueEvents_when_metricsCalculated', async () => {
      const count = await countUniqueEvents(LEGACY_APP_PATH);

      expect(count).toBeDefined();
      expect(count).toBeGreaterThanOrEqual(60);
    });

    test('should_countEventsByType_when_metricsCalculated', async () => {
      const eventsByType = await countEventsByType(LEGACY_APP_PATH);

      expect(eventsByType).toBeDefined();
      expect(eventsByType.abendBat).toBeDefined();
      expect(eventsByType.halbBat).toBeDefined();
      expect(eventsByType.ganztagBat).toBeDefined();

      const total = eventsByType.abendBat + eventsByType.halbBat + eventsByType.ganztagBat;
      expect(total).toBeGreaterThan(0);
    });

    test('should_countUniqueSpeakers_when_metricsCalculated', async () => {
      const count = await countUniqueSpeakers(LEGACY_APP_PATH);

      expect(count).toBeDefined();
      expect(count).toBeGreaterThan(100);
    });

    test('should_extractUniqueSpeakers_when_dataAnalyzed', async () => {
      const speakers = await extractUniqueSpeakers(LEGACY_APP_PATH);

      expect(speakers).toBeDefined();
      expect(speakers.length).toBeGreaterThan(100);

      // Each speaker should have these properties
      speakers.forEach((speaker) => {
        expect(speaker.name).toBeDefined();
        expect(speaker.company).toBeDefined();
      });

      // Check for known speaker
      const hasNissim = speakers.some((s) => s.name.includes('Nissim'));
      expect(hasNissim).toBe(true);
    });

    test('should_countUniqueCompanies_when_metricsCalculated', async () => {
      const count = await countUniqueCompanies(LEGACY_APP_PATH);

      expect(count).toBeDefined();
      expect(count).toBeGreaterThan(50);
    });

    test('should_extractUniqueCompanies_when_dataAnalyzed', async () => {
      const companies = await extractUniqueCompanies(LEGACY_APP_PATH);

      expect(companies).toBeDefined();
      expect(companies.length).toBeGreaterThan(50);

      // Companies should be lowercase identifiers
      companies.forEach((company) => {
        expect(typeof company).toBe('string');
        expect(company.length).toBeGreaterThan(0);
      });
    });

    test('should_countSpeakersWithBio_when_metricsCalculated', async () => {
      const metrics = await calculateMigrationMetrics(LEGACY_APP_PATH);

      expect(metrics.speakersWithBio).toBeDefined();
      expect(metrics.speakersWithBio).toBeGreaterThan(0);
      expect(metrics.speakersWithBio).toBeLessThanOrEqual(metrics.totalSpeakerMentions);
    });

    test('should_countSpeakersWithPhoto_when_metricsCalculated', async () => {
      const metrics = await calculateMigrationMetrics(LEGACY_APP_PATH);

      expect(metrics.speakersWithPhoto).toBeDefined();
      expect(metrics.speakersWithPhoto).toBeGreaterThan(0);
      expect(metrics.speakersWithPhoto).toBeLessThanOrEqual(metrics.totalSpeakerMentions);
    });

    test('should_countSessionsWithAbstract_when_metricsCalculated', async () => {
      const metrics = await calculateMigrationMetrics(LEGACY_APP_PATH);

      expect(metrics.sessionsWithAbstract).toBeDefined();
      expect(metrics.sessionsWithAbstract).toBeGreaterThan(0);
      expect(metrics.sessionsWithAbstract).toBeLessThan(metrics.totalSessions);
    });

    test('should_countProgramBrochures_when_metricsCalculated', async () => {
      const metrics = await calculateMigrationMetrics(LEGACY_APP_PATH);

      expect(metrics.programBrochures).toBeDefined();
      expect(metrics.programBrochures).toBeGreaterThan(0);
    });
  });

  describe('Report Generation', () => {
    test('should_generateCompleteMetrics_when_calculated', async () => {
      const metrics = await calculateMigrationMetrics(LEGACY_APP_PATH);

      expect(metrics.totalEvents).toBeGreaterThanOrEqual(60);
      expect(metrics.eventsByType).toBeDefined();
      expect(metrics.totalSessions).toBeGreaterThan(300);
      expect(metrics.sessionsWithAbstract).toBeGreaterThan(0);
      expect(metrics.programBrochures).toBeGreaterThan(0);
      expect(metrics.totalSpeakerMentions).toBeGreaterThan(0);
      expect(metrics.uniqueSpeakers).toBeGreaterThan(100);
      expect(metrics.speakersWithBio).toBeGreaterThan(0);
      expect(metrics.speakersWithPhoto).toBeGreaterThan(0);
      expect(metrics.uniqueCompanies).toBeGreaterThan(50);
    });
  });
});
