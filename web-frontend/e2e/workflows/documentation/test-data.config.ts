/**
 * Centralized Test Data Configuration
 *
 * This file contains all test data for the E2E workflow documentation test.
 * Modify values here to adapt the test without touching test logic.
 *
 * Usage:
 * ```typescript
 * import { testConfig } from './test-data.config';
 * await page.fill('[name="eventTitle"]', testConfig.event.title);
 * ```
 */

export const testConfig = {
  /**
   * Event Configuration
   * Based on actual recording from playwright-recording.ts
   */
  event: {
    eventNumber: 942, // Will be auto-incremented in test to avoid conflicts
    title: 'Demo BATbern Event',
    eventType: 'EVENING', // Abend = Evening event (3-4 slots, 45 min each)
    date: '2042-02-04', // Format: YYYY-MM-DD
    registrationDeadline: '2024-01-02',
    venue: {
      name: 'Zentrum Paul Klee',
      address: 'Monument im Fruchtland, 3000 Bern',
    },
    description: 'Demo Event for the Userguide',
    venueImagePath: 'ChatGPT Image 4. Jan. 2026, 21_01_32.png', // Will need to be adjusted to test fixture
  },

  /**
   * Topics to select during topic selection phase
   * Heat map is used for selection
   */
  topics: {
    useHeatmap: true,
    // Cell coordinates from heat map (row 20, column 9 in recording)
    heatmapSelection: {
      row: 20,
      column: 9,
    },
  },

  /**
   * Speaker Candidates
   * Will be used during speaker brainstorming phase
   * Based on actual recording
   */
  speakerCandidates: [
    {
      firstName: 'Nissim',
      lastName: '', // Last name not provided in recording
      company: 'ELCA',
      expertise: 'AI',
      assignedUserId: null, // Will map to Nissim Buchsfrom seed data
      assignedUserName: 'Nissim Buchs',
      contactMethod: 'Persönlich',
      contactNotes: 'OK. Er machts',
      outreachNotes: 'Hab mit ihm gesprochen. er überlegt es sich,',
    },
    {
      firstName: 'Balti',
      lastName: '',
      company: 'Galenica',
      expertise: 'AI',
      assignedUserId: null, // Will map to Baltisar Oswald from seed data
      assignedUserName: 'Baltisar Oswald',
      contactMethod: 'Persönlich',
      contactNotes: 'Ja, ich machs',
    },
    {
      firstName: 'Andreas',
      lastName: '',
      company: 'Mobiliar',
      expertise: 'AI',
      assignedUserId: null, // Will map to Andreas Grütter from seed data
      assignedUserName: 'Andreas Grütter',
      contactMethod: 'E-Mail',
      contactNotes: 'Hab mal ein eMail gesendet.\nSeine antwort war:\nIpsum larum lirum',
    },
    {
      firstName: 'Daniel',
      lastName: '',
      company: 'BKW',
      expertise: 'AI',
      assignedUserId: null, // Will map to Daniel Kühni from seed data
      assignedUserName: 'Daniel Kühni',
      contactMethod: 'Persönlich',
      contactNotes: 'OK.',
    },
  ],

  /**
   * Speaker Outreach Tracking
   * Records contact interactions with speakers
   * Based on recording lines 90-123
   * Note: Nissim is contacted twice (first via Telefon, then Persönlich)
   */
  speakerOutreach: [
    {
      displayName: 'N Nissim ELCA AI',
      contactMethod: 'phone' as const,
      notes: 'Hab mit ihm gesprochen. er überlegt es sich,',
      speakerIndex: 0,
    },
    {
      displayName: 'B Balti Galenica AI',
      contactMethod: 'in_person' as const,
      notes: 'Ja, ich machs',
      speakerIndex: 1,
    },
    {
      displayName: 'A Andreas Mobiliar AI',
      contactMethod: 'email' as const,
      notes: 'Hab mal ein eMail gesendet.\nSeine antwort war:\nIpsum larum lirum',
      speakerIndex: 2,
    },
    {
      displayName: 'D Daniel BKW AI',
      contactMethod: 'in_person' as const,
      notes: 'OK.',
      speakerIndex: 3,
    },
    {
      displayName: 'N Nissim ELCA AI',
      contactMethod: 'in_person' as const,
      notes: 'OK. Er machts',
      speakerIndex: 0, // Same speaker as first interaction
    },
  ],

  /**
   * Presentation Details
   * Used when submitting speaker content
   * Based on actual recording (only 3 speakers got content submitted)
   *
   * IMPORTANT: speakerIndex maps to testConfig.speakerCandidates array:
   * - Index 0 = Nissim (ELCA, AI)
   * - Index 1 = Balti (Galenica, AI)
   * - Index 2 = Andreas (Mobiliar, AI)
   * - Index 3 = Daniel (BKW, AI)
   */
  presentations: [
    {
      title: 'Presentation of Nissim',
      abstract: 'Description of Presentation',
      speakerIndex: 0, // Nissim (first speaker in brainstorm) → Nissim Buchs (actual speaker)
      speakerSearchTerm: null, // No search needed, auto-mapped to Nissim Buchs
      actualSpeakerName: 'Nissim Buchs',
    },
    {
      title: 'Präsi von Balti',
      abstract: 'Seine Beschreibung',
      speakerIndex: 1, // Balti (second speaker in brainstorm) → Baltisar Oswald (auto-assigned)
      speakerSearchTerm: null, // No search needed, auto-mapped
      actualSpeakerName: 'Baltisar Oswald',
    },
    {
      title: 'Talk von Andreas',
      abstract: 'Seine Beschreibung',
      speakerIndex: 2, // Andreas (brainstormed) → Andreas Spichiger (actual speaker)
      speakerSearchTerm: 'and', // Search term from recording line 216
      actualSpeakerName: 'Andreas Grütter', // From recording line 217
    },
  ],

  /**
   * Screenshot Configuration
   */
  screenshots: {
    viewport: {
      width: 1920,
      height: 1080,
    },
    fullPage: true,
    delay: 500, // Wait 500ms after page load for animations to complete
    namingPattern: '{sequence:02d}-{name}.png', // e.g., "01-event-dashboard.png"
  },

  /**
   * Test Execution Settings
   */
  execution: {
    slowMo: 100, // Slow down actions for screenshot clarity (ms)
    timeout: 30000, // Default timeout for element waits (ms)
    screenshotDelay: 300, // Additional delay before screenshot capture (ms)
  },
};

/**
 * Helper function to generate unique event title with timestamp
 * Prevents conflicts when running tests multiple times
 */
export function generateUniqueEventTitle(): string {
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  return `${testConfig.event.title} [${timestamp}]`;
}

/**
 * Helper function to get speaker candidate by index
 */
export function getSpeakerCandidate(index: number) {
  if (index < 0 || index >= testConfig.speakerCandidates.length) {
    throw new Error(
      `Invalid speaker candidate index: ${index}. Valid range: 0-${testConfig.speakerCandidates.length - 1}`
    );
  }
  return testConfig.speakerCandidates[index];
}

/**
 * Helper function to get presentation by index
 */
export function getPresentation(index: number) {
  if (index < 0 || index >= testConfig.presentations.length) {
    throw new Error(
      `Invalid presentation index: ${index}. Valid range: 0-${testConfig.presentations.length - 1}`
    );
  }
  return testConfig.presentations[index];
}
