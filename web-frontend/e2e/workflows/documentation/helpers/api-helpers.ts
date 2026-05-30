/**
 * API Helper Functions
 *
 * IMPORTANT: These helpers are for READ-ONLY operations and cleanup ONLY.
 * All workflow actions (event creation, speaker assignment, etc.) must go
 * through the frontend UI to ensure accurate documentation screenshots.
 *
 * Allowed operations:
 * - Fetching seed data (companies, speakers, topics, users)
 * - Verifying backend state after UI actions (for test assertions)
 * - Cleanup (deleting test data after test completion)
 *
 * NOT allowed:
 * - Creating events via API (use frontend UI)
 * - Assigning speakers via API (use frontend UI)
 * - Advancing workflow states via API (use frontend UI)
 */

import { cleanupByCode, sweepAllPrefixes } from '../../../helpers/test-fixtures-cleanup';

const API_BASE_URL = process.env.E2E_API_URL || 'http://localhost:8000';

export interface SeedData {
  companies: Company[];
  users: User[];
  topics: Topic[];
  speakers: Speaker[];
}

export interface Company {
  id: string;
  name: string;
  uid?: string;
}

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
}

export interface Topic {
  id: string;
  name: string;
  category: string;
}

export interface Speaker {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  company?: Company;
}

export interface EventState {
  eventCode: string;
  workflowState: string;
  title: string;
  eventType: string;
  date: string;
}

/**
 * Makes an authenticated API request
 */
async function apiRequest(
  endpoint: string,
  authToken: string,
  options: RequestInit = {}
): Promise<Response> {
  const url = `${API_BASE_URL}${endpoint}`;

  return fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${authToken}`,
      ...options.headers,
    },
  });
}

/**
 * Fetches existing seed data from the backend
 * READ-ONLY operation
 *
 * @param authToken - JWT authentication token
 * @returns Seed data (companies, users, topics, speakers)
 */
export async function getSeedData(authToken: string): Promise<SeedData> {
  console.log('📥 Fetching seed data...');

  // Helper to safely fetch and parse JSON
  const safeFetch = async (endpoint: string, label: string): Promise<unknown[]> => {
    try {
      const response = await apiRequest(endpoint, authToken);

      if (!response.ok) {
        console.warn(`⚠️  ${label} fetch failed: ${response.status} ${response.statusText}`);
        return [];
      }

      const data = await response.json();
      return data.data || [];
    } catch (error) {
      console.warn(
        `⚠️  ${label} fetch error:`,
        error instanceof Error ? error.message : String(error)
      );
      return [];
    }
  };

  // Fetch companies
  const companies = await safeFetch('/api/v1/companies?limit=20', 'Companies');

  // Fetch users
  const users = await safeFetch('/api/v1/users?limit=50', 'Users');

  // Fetch topics
  const topics = await safeFetch('/api/v1/topics?limit=100', 'Topics');

  // Fetch speakers (users with speaker role or speaker profiles)
  const speakers = await safeFetch('/api/v1/users?filter={"role":"SPEAKER"}&limit=30', 'Speakers');

  const seedData: SeedData = {
    companies,
    users,
    topics,
    speakers,
  };

  console.log(`✅ Seed data fetched:
    - Companies: ${seedData.companies.length}
    - Users: ${seedData.users.length}
    - Topics: ${seedData.topics.length}
    - Speakers: ${seedData.speakers.length}
  `);

  return seedData;
}

/**
 * Verifies the current workflow state of an event
 * READ-ONLY operation for test assertions
 *
 * @param authToken - JWT authentication token
 * @param eventCode - Event code to verify
 * @param expectedState - Expected workflow state
 * @returns True if state matches, false otherwise
 */
export async function verifyEventState(
  authToken: string,
  eventCode: string,
  expectedState: string
): Promise<boolean> {
  console.log(`🔍 Verifying event ${eventCode} state (expected: ${expectedState})...`);

  const response = await apiRequest(`/api/v1/events/${eventCode}`, authToken);

  if (!response.ok) {
    console.error(`❌ Failed to fetch event: ${response.status} ${response.statusText}`);
    return false;
  }

  const eventData = await response.json();
  const actualState = eventData.workflowState || eventData.state;

  const matches = actualState === expectedState;

  if (matches) {
    console.log(`✅ Event state verified: ${actualState}`);
  } else {
    console.warn(`⚠️  Event state mismatch: expected ${expectedState}, got ${actualState}`);
  }

  return matches;
}

/**
 * Gets the current state of an event
 * READ-ONLY operation
 */
export async function getEventState(
  authToken: string,
  eventCode: string
): Promise<EventState | null> {
  const response = await apiRequest(`/api/v1/events/${eventCode}`, authToken);

  if (!response.ok) {
    console.error(`❌ Failed to fetch event: ${response.status}`);
    return null;
  }

  const eventData = await response.json();

  return {
    eventCode: eventData.eventCode || eventData.code,
    workflowState: eventData.workflowState || eventData.state,
    title: eventData.title,
    eventType: eventData.eventType || eventData.type,
    date: eventData.date,
  };
}

/**
 * Cleans up a test event by code.
 *
 * SUPERSEDED (plan §A4): delegates to the canonical `cleanupByCode` helper so this suite
 * and Bruno share one explicit-delete contract (accepts 204/404/409). The previous bespoke
 * DELETE here is gone.
 *
 * @param authToken - JWT authentication token (must carry ROLE_ORGANIZER)
 * @param eventCode - Event code to delete
 */
export async function cleanupTestEvent(authToken: string, eventCode: string): Promise<void> {
  await cleanupByCode(authToken, eventCode);
}

/**
 * Cleans up orphaned test data after failed runs.
 *
 * SUPERSEDED (plan §A4): the old implementation matched events with `"E2E"` in the TITLE
 * and deleted them — the exact pattern that produced the `"E2E Test Company"` prod residue
 * the Bruno audit found (2026-05-24). It now delegates to `sweepAllPrefixes`, which removes
 * ONLY rows carrying the canonical Bruno test prefixes across cums/ems/pcs. UI-created
 * events with server-generated `BATbern{N}` codes are not reachable by the sweep and must
 * be torn down per-spec via `cleanupTestEvent(code)`.
 *
 * @param authToken - JWT authentication token (must carry ROLE_ORGANIZER)
 */
export async function cleanupOrphanedTestEvents(authToken: string): Promise<void> {
  await sweepAllPrefixes(authToken);
}

/**
 * Finds a company by name in seed data
 * Helper for UI form filling
 */
export function findCompanyByName(seedData: SeedData, companyName: string): Company | undefined {
  return seedData.companies.find((c) => c.name.toLowerCase().includes(companyName.toLowerCase()));
}

/**
 * Finds a topic by name in seed data
 * Helper for UI selection
 */
export function findTopicByName(seedData: SeedData, topicName: string): Topic | undefined {
  return seedData.topics.find((t) => t.name.toLowerCase().includes(topicName.toLowerCase()));
}

/**
 * Finds a user by email in seed data
 * Helper for UI form filling
 */
export function findUserByEmail(seedData: SeedData, email: string): User | undefined {
  return seedData.users.find((u) => u.email.toLowerCase() === email.toLowerCase());
}
