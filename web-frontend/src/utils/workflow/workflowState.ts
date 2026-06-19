/**
 * Workflow State Utilities
 *
 * Centralized utilities for managing and displaying EventWorkflowState.
 * Single source of truth for workflow state logic across the application.
 *
 * Story 5.1a - Workflow State Machine Foundation
 * Refactoring: Eliminates code duplication across EventCard, WorkflowProgressBar,
 * and WorkflowProgressBarWithQuery components.
 */

import type { TFunction } from 'i18next';

/**
 * Complete 8-step workflow state order for BATbern events.
 * States progress linearly from CREATED through ARCHIVED.
 * Updated for Story 5.7 - consolidated from 16 states to 9; then to 8 in V82
 * (AGENDA_FINALIZED removed — AGENDA_PUBLISHED transitions directly to EVENT_LIVE).
 */
export const WORKFLOW_STATE_ORDER = [
  'CREATED',
  'TOPIC_SELECTION',
  'SPEAKER_IDENTIFICATION',
  'SLOT_ASSIGNMENT',
  'AGENDA_PUBLISHED',
  'EVENT_LIVE',
  'EVENT_COMPLETED',
  'ARCHIVED',
] as const;

/**
 * Type representing valid workflow state values
 */
export type WorkflowStateType = (typeof WORKFLOW_STATE_ORDER)[number];

/**
 * Early stage workflow states where topic selection is relevant
 */
const EARLY_STAGE_STATES: readonly string[] = ['CREATED', 'TOPIC_SELECTION'] as const;

/**
 * Late stage workflow states where event is essentially complete
 */
const LATE_STAGE_STATES: readonly string[] = ['EVENT_LIVE', 'EVENT_COMPLETED', 'ARCHIVED'] as const;

/**
 * Calculate workflow completion percentage from current state.
 *
 * @param workflowState - The current workflow state (e.g., 'SPEAKER_IDENTIFICATION')
 * @returns Progress percentage (0-100), or 0 if state is invalid
 *
 * @example
 * getWorkflowProgress('CREATED') // Returns 13 (step 1/8)
 * getWorkflowProgress('ARCHIVED') // Returns 100 (step 8/8)
 * getWorkflowProgress('INVALID') // Returns 0
 */
export function getWorkflowProgress(workflowState: string): number {
  const currentIndex = WORKFLOW_STATE_ORDER.indexOf(workflowState as WorkflowStateType);
  if (currentIndex === -1) return 0;
  return Math.round(((currentIndex + 1) / WORKFLOW_STATE_ORDER.length) * 100);
}

/**
 * Get the appropriate color for the workflow progress bar based on completion percentage.
 *
 * Color coding follows Material-UI conventions:
 * - warning (yellow): < 30% - early stages, needs attention
 * - primary (blue): 30-70% - progressing normally
 * - success (green): > 70% - nearly complete or complete
 *
 * @param progress - Progress percentage (0-100)
 * @returns Material-UI color variant
 *
 * @example
 * getProgressColor(20) // Returns 'warning'
 * getProgressColor(50) // Returns 'primary'
 * getProgressColor(85) // Returns 'success'
 */
export function getProgressColor(progress: number): 'warning' | 'primary' | 'success' {
  if (progress < 30) return 'warning';
  if (progress < 70) return 'primary';
  return 'success';
}

/**
 * Generate the i18n translation key for a workflow state.
 *
 * Converts workflow state to lowercase_snake_case format expected by i18n.
 *
 * @param state - The workflow state (e.g., 'SPEAKER_OUTREACH')
 * @returns i18n key path (e.g., 'workflow.states.speaker_outreach')
 *
 * @example
 * getWorkflowStateI18nKey('CREATED') // Returns 'workflow.states.created'
 * getWorkflowStateI18nKey('SPEAKER_OUTREACH') // Returns 'workflow.states.speaker_outreach'
 */
export function getWorkflowStateI18nKey(state: string): string {
  return `workflow.states.${state.toLowerCase()}`;
}

/**
 * Get the translated human-readable label for a workflow state.
 *
 * Uses the i18n translation function to return localized state name.
 * Falls back to the raw state value if translation is missing.
 *
 * @param state - The workflow state
 * @param t - i18n translation function
 * @returns Translated state label (e.g., "Speaker Outreach", "Themenauswahl")
 *
 * @example
 * getWorkflowStateLabel('SPEAKER_OUTREACH', t) // Returns "Speaker Outreach" (EN) or "Referenten-Kontaktaufnahme" (DE)
 * getWorkflowStateLabel('CREATED', t) // Returns "Created" (EN) or "Erstellt" (DE)
 */
export function getWorkflowStateLabel(state: string, t: TFunction): string {
  return t(getWorkflowStateI18nKey(state), state);
}

/**
 * Get the step number for a workflow state (1-8).
 *
 * @param state - The workflow state
 * @returns Step number (1-indexed), or 0 if state is invalid
 *
 * @example
 * getWorkflowStepNumber('CREATED') // Returns 1
 * getWorkflowStepNumber('ARCHIVED') // Returns 8
 * getWorkflowStepNumber('INVALID') // Returns 0
 */
export function getWorkflowStepNumber(state: string): number {
  const index = WORKFLOW_STATE_ORDER.indexOf(state as WorkflowStateType);
  return index === -1 ? 0 : index + 1;
}

/**
 * Check if the workflow state is in an early stage (CREATED or TOPIC_SELECTION).
 *
 * Early stages are when topic selection functionality is relevant and should be displayed.
 *
 * @param state - The workflow state to check
 * @returns true if state is CREATED or TOPIC_SELECTION
 *
 * @example
 * isEarlyStage('CREATED') // Returns true
 * isEarlyStage('TOPIC_SELECTION') // Returns true
 * isEarlyStage('SPEAKER_IDENTIFICATION') // Returns false
 */
export function isEarlyStage(state: string): boolean {
  return EARLY_STAGE_STATES.includes(state);
}

/**
 * Check if the workflow state is in a late stage (EVENT_LIVE, EVENT_COMPLETED, or ARCHIVED).
 *
 * Late stages indicate the event workflow is essentially complete.
 *
 * @param state - The workflow state to check
 * @returns true if state is in late stage
 *
 * @example
 * isLateStage('ARCHIVED') // Returns true
 * isLateStage('EVENT_COMPLETED') // Returns true
 * isLateStage('CREATED') // Returns false
 */
export function isLateStage(state: string): boolean {
  return LATE_STAGE_STATES.includes(state);
}

/**
 * Validate if a string is a valid workflow state.
 *
 * @param state - The state string to validate
 * @returns true if state is one of the 8 valid workflow states
 *
 * @example
 * isValidWorkflowState('CREATED') // Returns true
 * isValidWorkflowState('SPEAKER_IDENTIFICATION') // Returns true
 * isValidWorkflowState('INVALID_STATE') // Returns false
 */
export function isValidWorkflowState(state: string): boolean {
  return WORKFLOW_STATE_ORDER.includes(state as WorkflowStateType);
}

/* ------------------------------------------------------------------------- *
 * Epic 14 — workflow-state → tab-relevance map (lifecycle-aware event page)
 * ------------------------------------------------------------------------- */

/**
 * The 7 tabs of the redesigned organizer event-detail page.
 * Two clusters: the "work" cluster (cockpit … wrapup) and the "config"
 * cluster — a single `details` tab whose Info / Tasks / Settings sub-tabs hold
 * what used to be the separate Details + Settings tabs (Story 14.F.2 merge).
 * These ids are the stable `?tab=` keys and the `data-testid="event-tab-{id}"`
 * suffixes consumed by EventPage.
 */
export type EventTabId =
  | 'cockpit'
  | 'speakers' // "Speakers & Agenda"
  | 'registrations'
  | 'communications'
  | 'publishing'
  | 'wrapup'
  | 'details'; // config cluster — Info / Tasks / Settings sub-tabs

/**
 * Per-tab relevance for a given workflow state.
 * - `active`  — fully interactive, normal emphasis
 * - `dimmed`  — visually de-emphasized (not yet the focus) but still usable
 * - `locked`  — dimmed + 🔒 + non-interactive (not yet applicable)
 */
export type TabRelevance = 'active' | 'dimmed' | 'locked';

/**
 * A single row of the relevance table for one workflow state.
 * `cockpitEmphasis` is an i18n key suffix describing the "what now" the
 * Cockpit should foreground (consumed by Phase B; declared here so the map is
 * the single source of truth).
 */
export interface WorkflowRelevance {
  cockpitEmphasis: string;
  focusTabs: EventTabId[];
  dimmedTabs: EventTabId[];
  lockedTabs: EventTabId[];
}

/**
 * Tabs that are ALWAYS active in every workflow state — orientation +
 * set-and-forget config. Checked first in {@link getTabRelevance} so a map
 * authoring mistake can never dim or lock them (FR5).
 */
export const ALWAYS_ACTIVE_TABS: readonly EventTabId[] = ['cockpit', 'details'] as const;

/**
 * The single declarative `workflowState → relevance` table (FR4, AR2).
 *
 * Exactly **8 rows** — one per shipped workflow state. There is **no
 * `AGENDA_FINALIZED`** (removed in V82, AR1): the "finalize" emphasis the spec
 * parked under that state (final newsletter / catering offer / print agenda)
 * is attributed here to `AGENDA_PUBLISHED` and surfaced by the Cockpit as
 * due-date-driven tasks, not a separate state.
 *
 * Invariants (enforced by getTabRelevance + unit tests):
 * - `cockpit`, `details` never appear in any dimmed/locked list.
 * - `wrapup` is locked for CREATED…AGENDA_PUBLISHED and active from EVENT_LIVE.
 */
export const WORKFLOW_RELEVANCE: Record<WorkflowStateType, WorkflowRelevance> = {
  CREATED: {
    cockpitEmphasis: 'created',
    focusTabs: ['details'],
    dimmedTabs: ['speakers', 'registrations', 'communications', 'publishing'],
    lockedTabs: ['wrapup'],
  },
  TOPIC_SELECTION: {
    cockpitEmphasis: 'topic',
    focusTabs: ['details', 'speakers'],
    dimmedTabs: ['registrations', 'communications', 'publishing'],
    lockedTabs: ['wrapup'],
  },
  SPEAKER_IDENTIFICATION: {
    cockpitEmphasis: 'speakers',
    focusTabs: ['speakers', 'publishing'],
    dimmedTabs: ['registrations', 'communications'],
    lockedTabs: ['wrapup'],
  },
  SLOT_ASSIGNMENT: {
    cockpitEmphasis: 'slots',
    focusTabs: ['speakers', 'publishing'],
    dimmedTabs: ['communications'],
    lockedTabs: ['wrapup'],
  },
  AGENDA_PUBLISHED: {
    // 8-state reconciliation: final newsletter / catering offer / print agenda
    cockpitEmphasis: 'finalize',
    focusTabs: ['communications', 'registrations'],
    dimmedTabs: [],
    lockedTabs: ['wrapup'],
  },
  EVENT_LIVE: {
    cockpitEmphasis: 'live',
    focusTabs: ['cockpit', 'wrapup'],
    dimmedTabs: ['publishing'],
    lockedTabs: [],
  },
  EVENT_COMPLETED: {
    cockpitEmphasis: 'wrapup',
    focusTabs: ['wrapup', 'communications'],
    dimmedTabs: ['speakers', 'publishing'],
    lockedTabs: [],
  },
  ARCHIVED: {
    cockpitEmphasis: 'archived',
    focusTabs: ['wrapup'],
    dimmedTabs: ['speakers', 'registrations', 'communications', 'publishing'],
    lockedTabs: [],
  },
};

/**
 * Resolve a single tab's relevance for a workflow state (FR4/FR5).
 *
 * Resolution order:
 * 1. `cockpit`/`details`/`settings` are always `active` (hard rule).
 * 2. An unknown/invalid state defaults to `active` — never throw, never hide a
 *    tab (a brownfield/legacy state must not blank the page).
 * 3. Otherwise the per-state map decides: locked > dimmed > active.
 *
 * @example
 * getTabRelevance('CREATED', 'wrapup')      // 'locked'
 * getTabRelevance('EVENT_LIVE', 'wrapup')   // 'active'
 * getTabRelevance('CREATED', 'cockpit')     // 'active'
 * getTabRelevance('BOGUS', 'wrapup')        // 'active' (safe default)
 */
export function getTabRelevance(state: string, tabId: EventTabId): TabRelevance {
  if (ALWAYS_ACTIVE_TABS.includes(tabId)) return 'active';

  const row = WORKFLOW_RELEVANCE[state as WorkflowStateType];
  if (!row) return 'active';

  if (row.lockedTabs.includes(tabId)) return 'locked';
  if (row.dimmedTabs.includes(tabId)) return 'dimmed';
  return 'active';
}
