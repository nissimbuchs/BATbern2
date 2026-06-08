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
    eventNumber: 10000, // reserved test range (>=10000); specs add Math.random()*1000 → [10000,10999]. Swept by ems/events_by_number.
    title: 'Demo BATbern Event',
    eventType: 'EVENING', // Abend = Evening event (3-4 slots, 45 min each)
    date: '2042-02-04', // Format: YYYY-MM-DD
    registrationDeadline: '2042-02-01',
    venue: {
      name: 'Zentrum Paul Klee',
      address: 'Monument im Fruchtland, 3000 Bern',
      capacity: 250,
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
    {
      // 5th candidate — contacted but deliberately NOT promoted; stays CONTACTED in the pool
      // to show a realistic "still open" card at the end (see declinedSpeaker / NARRATION_24D).
      firstName: 'Vanessa Deubel',
      lastName: '',
      company: 'BKW',
      expertise: 'SAP',
      assignedUserId: null,
      assignedUserName: 'Vanessa Deubel',
      contactMethod: 'E-Mail',
      contactNotes: 'Angefragt — überlegt es sich noch.',
    },
  ],

  /**
   * Speaker Outreach Tracking
   * One "Kontakt erfassen" (log outreach) per candidate via the card primary action (ADR-009).
   * cardName: visible-name fragment that uniquely identifies the kanban card (screencast).
   * displayName: legacy full card label (still used by complete-event-workflow.spec.ts).
   */
  speakerOutreach: [
    {
      cardName: 'Nissim',
      displayName: 'N Nissim ELCA AI',
      contactMethod: 'phone' as const,
      notes: 'Hab mit ihm gesprochen. er überlegt es sich,',
      speakerIndex: 0,
    },
    {
      cardName: 'Balti',
      displayName: 'B Balti Galenica AI',
      contactMethod: 'in_person' as const,
      notes: 'Ja, ich machs',
      speakerIndex: 1,
    },
    {
      cardName: 'Andreas',
      displayName: 'A Andreas Mobiliar AI',
      contactMethod: 'email' as const,
      notes: 'Hab mal ein eMail gesendet.\nSeine antwort war:\nIpsum larum lirum',
      speakerIndex: 2,
    },
    {
      cardName: 'Daniel',
      displayName: 'D Daniel BKW AI',
      contactMethod: 'in_person' as const,
      notes: 'Leider keine Zeit dieses Mal.',
      speakerIndex: 3,
    },
    {
      cardName: 'Vanessa',
      displayName: 'V Vanessa Deubel BKW SAP',
      contactMethod: 'email' as const,
      notes: 'Angefragt — überlegt es sich noch.',
      speakerIndex: 4,
    },
  ],

  /**
   * Speakers promoted CONTACTED → READY (creates a SPEAKER user account; provisions
   * Cognito out-of-band — Pattern N).
   *
   * Real names are used so the tutorial video reads authentically (the kanban card shows
   * the linked user's name once promoted). The created CUMS rows do NOT match the
   * `bruno.test%` global sweep, so the spec's afterAll deletes them explicitly via
   * `DELETE /api/v1/users/{username}` (organizer-authorized) — see the spec's afterAll.
   * The Cognito accounts still leak (CUMS delete is DB-only) — documented manual sweep.
   * Each `marker` is the per-speaker accept narration (NARRATION_24/24B/24C), so the
   * voice talks through every accept instead of one segment covering all three.
   *
   * Daniel and Vanessa are NOT promoted — Daniel declines from CONTACTED, Vanessa stays
   * CONTACTED — so neither creates an orphan placeholder session that blocks agenda publishing.
   */
  promotedSpeakers: [
    {
      cardName: 'Nissim',
      marker: 'NARRATION_24',
      user: { firstName: 'Nissim', lastName: 'Buchs' },
    },
    {
      cardName: 'Balti',
      marker: 'NARRATION_24B',
      user: { firstName: 'Baltisar', lastName: 'Oswald' },
    },
    {
      cardName: 'Andreas',
      marker: 'NARRATION_24C',
      user: { firstName: 'Andreas', lastName: 'Grütter' },
    },
  ],

  /** READY → ACCEPTED via drawer "Accept on behalf" (reason required, no invitation email). */
  acceptOnBehalfReason: 'Mündlich zugesagt (Screencast-Demo)',

  /** Daniel declines from CONTACTED via the drawer "Decline with reason" action (NARRATION_24D). */
  declinedSpeaker: {
    cardName: 'Daniel',
    reason: 'Hat dieses Mal leider keine Zeit (Screencast-Demo)',
  },

  /**
   * Presentation Details — submitted via the card primary action "Inhalt erfassen".
   * The speaker is auto-resolved from the promoted user (no picker), so the screencast only
   * needs cardName + content. speakerIndex/speakerSearchTerm/actualSpeakerName are legacy
   * fields still used by complete-event-workflow.spec.ts.
   */
  presentations: [
    {
      cardName: 'Nissim',
      title: 'Presentation of Nissim',
      abstract: 'Description of Presentation',
      speakerIndex: 0,
      speakerSearchTerm: null,
      actualSpeakerName: 'Nissim Buchs',
    },
    {
      cardName: 'Balti',
      title: 'Präsi von Balti',
      abstract: 'Seine Beschreibung',
      speakerIndex: 1,
      speakerSearchTerm: null,
      actualSpeakerName: 'Baltisar Oswald',
    },
    {
      cardName: 'Andreas',
      title: 'Talk von Andreas',
      abstract: 'Seine Beschreibung',
      speakerIndex: 2,
      speakerSearchTerm: 'and',
      actualSpeakerName: 'Andreas Grütter',
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
