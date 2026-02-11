/**
 * Timing Configuration for Screencast Narration English
 *
 * This file defines the timing delays for each narration segment to keep
 * the Playwright screencast synchronized with the voiceover audio.
 *
 * Each segment specifies:
 * - marker: The narration identifier (e.g., "NARRATION_01")
 * - delayAfter: Milliseconds to wait after this narration starts (includes narration duration + UI buffer)
 * - description: Brief description of the narration content
 *
 * Generated: 2026-01-23
 */

export interface TimingSegment {
  marker: string;
  delayAfter: number; // milliseconds
  description: string;
}

export const timingConfig: TimingSegment[] = [
  {
    marker: 'NARRATION_01',
    delayAfter: 25000, // 32 seconds - Welcome message while showing homepage
    description: 'Welcome to BATbern Event Management Platform',
  },
  {
    marker: 'NARRATION_02',
    delayAfter: 32000, // 27000
    description: 'Dashboard and Authentication',
  },
  {
    marker: 'NARRATION_03',
    delayAfter: 14000,
    description: 'Create New Event',
  },
  {
    marker: 'NARRATION_04',
    delayAfter: 17000,
    description: 'Event Number',
  },
  {
    marker: 'NARRATION_05',
    delayAfter: 16000,
    description: 'Event Title',
  },
  {
    marker: 'NARRATION_06',
    delayAfter: 13000,
    description: 'Event Description',
  },
  {
    marker: 'NARRATION_07',
    delayAfter: 29000,
    description: 'Event Type',
  },
  {
    marker: 'NARRATION_08',
    delayAfter: 15000,
    description: 'Date and Registration Deadline',
  },
  {
    marker: 'NARRATION_09',
    delayAfter: 14000,
    description: 'Venue',
  },
  {
    marker: 'NARRATION_10',
    delayAfter: 14000,
    description: 'Event Created Successfully',
  },
  {
    marker: 'NARRATION_11',
    delayAfter: 16000,
    description: 'Navigate to Event Details',
  },
  {
    marker: 'NARRATION_12',
    delayAfter: 33000,
    description: 'Assign Tasks',
  },
  {
    marker: 'NARRATION_13',
    delayAfter: 9000,
    description: 'Save Tasks',
  },
  {
    marker: 'NARRATION_14',
    delayAfter: 28000,
    description: 'Review Task List',
  },
  {
    marker: 'NARRATION_15',
    delayAfter: 10000,
    description: 'Begin Topic Selection',
  },
  {
    marker: 'NARRATION_16',
    delayAfter: 13000,
    description: 'Open Heat Map',
  },
  {
    marker: 'NARRATION_17',
    delayAfter: 23000,
    description: 'Explain Heat Map',
  },
  {
    marker: 'NARRATION_18',
    delayAfter: 20000,
    description: 'Select and Confirm Topic',
  },
  {
    marker: 'NARRATION_19',
    delayAfter: 23000,
    description: 'Add Speaker Candidates',
  },
  {
    marker: 'NARRATION_20',
    delayAfter: 14000,
    description: 'Proceed to Outreach',
  },
  {
    marker: 'NARRATION_21',
    delayAfter: 19000,
    description: 'Kanban Board Explanation',
  },
  {
    marker: 'NARRATION_22',
    delayAfter: 19000,
    description: 'Contact Speakers',
  },
  {
    marker: 'NARRATION_23',
    delayAfter: 16000,
    description: 'Move Speakers to READY',
  },
  {
    marker: 'NARRATION_24',
    delayAfter: 16000,
    description: 'Move Speakers to ACCEPTED',
  },
  {
    marker: 'NARRATION_25',
    delayAfter: 11000,
    description: 'Publish Topic',
  },
  {
    marker: 'NARRATION_26',
    delayAfter: 22000,
    description: 'Submit Presentation Content',
  },
  {
    marker: 'NARRATION_27',
    delayAfter: 22000,
    description: 'Publish Speakers',
  },
  {
    marker: 'NARRATION_28',
    delayAfter: 19000,
    description: 'Approve Presentations',
  },
  {
    marker: 'NARRATION_29',
    delayAfter: 10000,
    description: 'Switch to Sessions View',
  },
  {
    marker: 'NARRATION_30',
    delayAfter: 21000,
    description: 'Auto-assign Speakers',
  },
  {
    marker: 'NARRATION_31',
    delayAfter: 4000,
    description: 'Return to Event',
  },
  {
    marker: 'NARRATION_32',
    delayAfter: 23000,
    description: 'Publish Agenda',
  },
  {
    marker: 'NARRATION_33',
    delayAfter: 10000,
    description: 'Archive Event',
  },
  {
    marker: 'NARRATION_34',
    delayAfter: 8000,
    description: 'Change Status to ARCHIVED',
  },
  {
    marker: 'NARRATION_35',
    delayAfter: 16000,
    description: 'Override Workflow Validation',
  },
  {
    marker: 'NARRATION_36',
    delayAfter: 21000,
    description: 'Workflow Complete - Thank You',
  },
];

// Summary:
// - Total segments: 36
// - Total delay time: ~242 seconds (~4 minutes)
// - Note: This is just pause time between narrations, not total video duration
// - Actual video will be much longer due to UI interactions
