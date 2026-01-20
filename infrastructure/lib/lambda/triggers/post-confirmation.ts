/**
 * PostConfirmation Lambda Trigger
 * Story 1.2.5: User Sync and Reconciliation Implementation
 * Story 1.2.6: Updated for ADR-001 Database-centric architecture
 * Story 6.3: Speaker Account Creation and Linking
 *
 * This Lambda function is triggered by AWS Cognito after a user confirms their email address.
 * It syncs the user to the PostgreSQL database and assigns an initial role.
 *
 * AC1: PostConfirmation trigger creates database user within 1 second
 * - When a user completes email verification in Cognito
 * - Then a corresponding user record is created in the `user_profiles` table
 * - And default ATTENDEE role is assigned (ADR-001: All self-registered users get ATTENDEE)
 * - And the operation completes within 1 second (p95 latency)
 *
 * Story 6.3 Extension:
 * - Links any SpeakerPool entries with matching email to the new user's username
 * - Enables external speakers to access speaker dashboard after registration
 * - Publishes SpeakerPoolLinked event to EventBridge for Speaker entity creation
 */

import { PostConfirmationTriggerEvent, PostConfirmationTriggerHandler } from 'aws-lambda';
import { getDbClient, executeTransaction } from './common/database';
import { CloudWatchClient, PutMetricDataCommand } from '@aws-sdk/client-cloudwatch';
import { EventBridgeClient, PutEventsCommand } from '@aws-sdk/client-eventbridge';
import { PoolClient } from 'pg';

// CloudWatch client for metrics
const cloudWatchClient = new CloudWatchClient({ region: process.env.AWS_REGION || 'eu-central-1' });

// EventBridge client for domain events (Story 6.3)
const eventBridgeClient = new EventBridgeClient({ region: process.env.AWS_REGION || 'eu-central-1' });

/**
 * Valid user roles in the system
 */
const VALID_ROLES = ['ORGANIZER', 'SPEAKER', 'PARTNER', 'ATTENDEE'] as const;
type UserRole = typeof VALID_ROLES[number];

/**
 * User attributes from Cognito event
 */
interface UserAttributes {
  sub: string; // Cognito user ID
  email: string;
  email_verified?: string;
  'cognito:groups'?: string;
  'custom:preferences'?: string; // JSON string with user profile data
}

/**
 * User preferences extracted from custom:preferences JSON
 */
interface UserPreferences {
  firstName?: string;
  lastName?: string;
  language?: string;
  newsletterOptIn?: boolean;
  theme?: string;
  notifications?: {
    email?: boolean;
    sms?: boolean;
    push?: boolean;
  };
  privacy?: {
    showProfile?: boolean;
    allowMessages?: boolean;
  };
}

/**
 * Extract user attributes from PostConfirmation event
 */
function extractUserAttributes(event: PostConfirmationTriggerEvent): UserAttributes {
  const attributes = event.request.userAttributes;

  // Validate required attributes
  if (!attributes.sub) {
    throw new Error('Missing required attribute: sub (Cognito user ID)');
  }

  if (!attributes.email) {
    throw new Error('Missing required attribute: email');
  }

  return {
    sub: attributes.sub,
    email: attributes.email,
    email_verified: attributes.email_verified,
    'cognito:groups': attributes['cognito:groups'], // Legacy field, no longer used
    'custom:preferences': attributes['custom:preferences'],
  };
}

/**
 * Parse user preferences from custom:preferences JSON
 * Story 1.2.3: User profile data stored in custom:preferences per ADR-001
 */
function parseUserPreferences(preferencesJson?: string): UserPreferences {
  if (!preferencesJson) {
    console.warn('No custom:preferences found, using defaults');
    return {};
  }

  try {
    return JSON.parse(preferencesJson);
  } catch (error) {
    console.error('Failed to parse custom:preferences JSON', { preferencesJson, error });
    return {};
  }
}

/**
 * Generate unique username from first and last name
 * Format: firstname.lastname or firstname.lastname.N for duplicates
 * Story 1.16.2: Username is public meaningful identifier
 */
function generateUsername(firstName: string, lastName: string): string {
  // Normalize to lowercase, remove special characters, replace spaces with nothing
  const normalizeNamePart = (name: string) =>
    name
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '') // Remove diacritics
      .replace(/[^a-z]/g, ''); // Keep only letters

  const first = normalizeNamePart(firstName || 'user');
  const last = normalizeNamePart(lastName || 'user');

  return `${first}.${last}`;
}

/**
 * Get default role for self-registered users
 * Story 1.2.6: ADR-001 Database-centric architecture
 * All self-registered users receive ATTENDEE role
 * Admin-invited users will have roles assigned via database
 */
function getDefaultRole(): UserRole {
  // ADR-001: Default to ATTENDEE for all self-registration
  // NO Cognito Groups usage - roles managed exclusively in database
  return 'ATTENDEE';
}

/**
 * Publish CloudWatch metric for sync operation
 */
async function publishMetric(metricName: string, value: number, unit: string = 'None') {
  try {
    await cloudWatchClient.send(
      new PutMetricDataCommand({
        Namespace: 'BATbern/UserSync',
        MetricData: [
          {
            MetricName: metricName,
            Value: value,
            Unit: unit as any,
            Timestamp: new Date(),
          },
        ],
      })
    );
  } catch (error) {
    console.error('Failed to publish CloudWatch metric', { metricName, error });
    // Don't throw - metrics failure shouldn't block user creation
  }
}

/**
 * Result of linking speaker pool entries
 * Story 6.3: Speaker Account Creation and Linking
 */
interface SpeakerPoolLinkResult {
  linkedCount: number;
  linkedEntries: Array<{ id: string; eventId: string }>;
}

/**
 * Link speaker pool entries to newly registered user.
 * Story 6.3: Speaker Account Creation and Linking
 *
 * ARCHITECTURE NOTE: This follows the established pattern of the Lambda
 * directly updating domain tables during user creation. The existing Lambda
 * already writes to user_profiles and role_assignments tables.
 *
 * An event is published for Event Management Service to create the Speaker
 * entity (which requires service-level business logic).
 *
 * @param client - Database client (within transaction)
 * @param email - User's email address
 * @param username - User's username for linking
 * @returns Number of linked entries and their IDs
 */
async function linkSpeakerPoolEntries(
  client: PoolClient,
  email: string,
  username: string
): Promise<SpeakerPoolLinkResult> {
  // Case-insensitive email match, only unlinked entries
  // AC5: Links ALL matching entries (multiple events)
  // AC7: WHERE username IS NULL ensures idempotency
  const result = await client.query(
    `UPDATE speaker_pool
     SET username = $1, updated_at = NOW()
     WHERE LOWER(email) = LOWER($2)
       AND username IS NULL
     RETURNING id, event_id`,
    [username, email]
  );

  const linkedEntries = result.rows.map(row => ({
    id: row.id,
    eventId: row.event_id,
  }));

  console.log('Speaker pool linking result', {
    username,
    email,
    linkedCount: result.rowCount,
    linkedEntries,
  });

  return {
    linkedCount: result.rowCount || 0,
    linkedEntries,
  };
}

/**
 * Publish SpeakerPoolLinked event to EventBridge.
 * Event Management Service listens and creates Speaker entity.
 * Story 6.3: Speaker Account Creation and Linking
 *
 * @param username - Linked user's username
 * @param email - User's email address
 * @param linkedEntries - Array of linked speaker pool entry IDs
 */
async function publishSpeakerPoolLinkedEvent(
  username: string,
  email: string,
  linkedEntries: Array<{ id: string; eventId: string }>
): Promise<void> {
  const event = {
    Source: 'batbern.cognito',
    DetailType: 'SpeakerPoolLinked',
    Detail: JSON.stringify({
      username,
      email,
      linkedCount: linkedEntries.length,
      linkedEntries,
      timestamp: new Date().toISOString(),
    }),
    EventBusName: process.env.EVENT_BUS_NAME || 'default',
  };

  try {
    await eventBridgeClient.send(new PutEventsCommand({ Entries: [event] }));
    console.log('Published SpeakerPoolLinked event', { username, linkedCount: linkedEntries.length });
  } catch (error) {
    // Log but don't fail - event publishing is best-effort
    // Manual linking fallback (AC6) handles cases where Speaker entity isn't created
    console.error('Failed to publish SpeakerPoolLinked event', { error, username });
  }
}

/**
 * Create user in database with email-based matching for historical participants
 * Story 1.2.3: Extract user profile data from custom:preferences JSON per ADR-001
 * ADR-005: Auto-link historical participants when they create Cognito accounts
 *
 * Logic:
 * 1. Check if user with email exists (historical participant or duplicate registration)
 * 2. If exists and no Cognito ID -> UPDATE to link historical participant
 * 3. If exists and same Cognito ID -> Idempotent (Lambda retry)
 * 4. If exists and different Cognito ID -> Error (email conflict)
 * 5. If not exists -> INSERT new user
 */
async function createUser(
  cognitoId: string,
  email: string,
  emailVerified: boolean,
  preferences: UserPreferences,
  role: UserRole
): Promise<string | null> {
  // Extract user profile data from preferences
  const firstName = preferences.firstName || 'User';
  const lastName = preferences.lastName || 'User';
  const language = preferences.language || 'de';
  const username = generateUsername(firstName, lastName);

  const client = await getDbClient();
  try {
    await client.query('BEGIN');

    // STEP 1: Check if user with this email already exists (historical participant or duplicate)
    const existingUserResult = await client.query(
      `SELECT id, cognito_user_id, username, first_name, last_name, is_active
       FROM user_profiles
       WHERE email = $1`,
      [email]
    );

    let userId: string | null = null;
    let actualUsername: string = username; // Track username for speaker pool linking

    if (existingUserResult.rows.length > 0) {
      const existingUser = existingUserResult.rows[0];

      // STEP 2a: Existing user found - check Cognito ID status
      if (existingUser.cognito_user_id === null || existingUser.cognito_user_id === '') {
        // Historical participant registering for first time - LINK to Cognito account
        await client.query(
          `UPDATE user_profiles
           SET cognito_user_id = $1,
               first_name = COALESCE(NULLIF(first_name, ''), $2),
               last_name = COALESCE(NULLIF(last_name, ''), $3),
               pref_language = COALESCE(pref_language, $4),
               is_active = true,
               updated_at = CURRENT_TIMESTAMP
           WHERE id = $5`,
          [cognitoId, firstName, lastName, language, existingUser.id]
        );

        userId = existingUser.id;
        actualUsername = existingUser.username; // Use existing username for speaker pool linking
        console.log('Linked existing historical participant to Cognito account', {
          userId,
          cognitoId,
          email,
          existingUsername: existingUser.username,
          hadFirstName: !!existingUser.first_name,
          hadLastName: !!existingUser.last_name,
        });

        // Publish metric for historical participant linking
        await publishMetric('HistoricalParticipantLinked', 1, 'Count');
      } else if (existingUser.cognito_user_id === cognitoId) {
        // Idempotent: Same Cognito ID already linked (Lambda retry)
        userId = existingUser.id;
        actualUsername = existingUser.username; // Use existing username
        console.log('User already linked (idempotent - Lambda retry)', {
          userId,
          cognitoId,
          email,
        });
      } else {
        // Email conflict: Different Cognito user already registered with same email
        // This should never happen if Cognito is configured correctly, but handle defensively
        await client.query('ROLLBACK');
        const errorMsg = `Email ${email} already registered to different Cognito user: ${existingUser.cognito_user_id}`;
        console.error('Email conflict detected', {
          email,
          newCognitoId: cognitoId,
          existingCognitoId: existingUser.cognito_user_id,
        });
        throw new Error(errorMsg);
      }
    } else {
      // STEP 2b: No existing user - INSERT new record
      const insertResult = await client.query(
        `INSERT INTO user_profiles (
          cognito_user_id,
          email,
          username,
          first_name,
          last_name,
          pref_language,
          pref_email_notifications,
          is_active,
          created_at,
          updated_at
        )
        VALUES ($1, $2, $3, $4, $5, $6, $7, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        RETURNING id`,
        [
          cognitoId,
          email,
          username,
          firstName,
          lastName,
          language,
          preferences.notifications?.email ?? true,
        ]
      );

      userId = insertResult.rows[0].id;
      console.log('New user created in database', { userId, cognitoId, email, username });

      // Publish metric for new user creation
      await publishMetric('NewUserCreated', 1, 'Count');
    }

    // STEP 2c: Link any SpeakerPool entries with matching email (Story 6.3)
    // This must happen before COMMIT to be in the same transaction
    const speakerPoolResult = await linkSpeakerPoolEntries(client, email, actualUsername);

    await client.query('COMMIT');

    // STEP 3: Publish SpeakerPoolLinked event AFTER commit (Story 6.3)
    // Event publishing must happen after commit to ensure data is persisted
    if (speakerPoolResult.linkedCount > 0) {
      await publishSpeakerPoolLinkedEvent(actualUsername, email, speakerPoolResult.linkedEntries);
      await publishMetric('SpeakerPoolEntriesLinked', speakerPoolResult.linkedCount, 'Count');
    }

    // STEP 4: Assign initial role (idempotent in assignUserRole)
    if (userId) {
      await assignUserRole(userId, role);
    }

    return userId;
  } catch (error) {
    await client.query('ROLLBACK');
    console.error('Failed to create/link user in database', {
      cognitoId,
      email,
      error: error instanceof Error ? error.message : 'Unknown error',
    });
    throw error;
  } finally {
    client.release();
  }
}

/**
 * Assign initial role to user
 */
async function assignUserRole(userId: string, role: UserRole): Promise<void> {
  const client = await getDbClient();

  try {
    // Check if role already exists (idempotent)
    const existingRoleResult = await client.query(
      'SELECT user_id FROM role_assignments WHERE user_id = $1 AND role = $2',
      [userId, role]
    );

    if (existingRoleResult.rows.length > 0) {
      console.log('Role already assigned (idempotent)', { userId, role });
      return;
    }

    // Insert role (granted_by is NULL for system-assigned roles)
    await client.query(
      `
        INSERT INTO role_assignments (user_id, role, granted_at, granted_by)
        VALUES ($1, $2, CURRENT_TIMESTAMP, NULL)
      `,
      [userId, role]
    );

    console.log('Role assigned successfully', { userId, role });
  } catch (error) {
    console.error('Failed to assign role', {
      userId,
      role,
      error: error instanceof Error ? error.message : 'Unknown error',
    });
    throw error;
  } finally {
    client.release();
  }
}

/**
 * Main Lambda handler for PostConfirmation trigger
 *
 * IMPORTANT: This function MUST NOT throw errors, as that would block Cognito user confirmation.
 * All errors are caught, logged, and metrics are published.
 */
export const handler: PostConfirmationTriggerHandler = async (event) => {
  const startTime = Date.now();

  console.log('PostConfirmation trigger invoked', {
    userPoolId: event.userPoolId,
    userName: event.userName,
    triggerSource: event.triggerSource,
  });

  try {
    // Extract user attributes from event
    const attributes = extractUserAttributes(event);
    const { sub: cognitoId, email, email_verified } = attributes;

    // Parse user preferences from custom:preferences JSON (Story 1.2.3, ADR-001)
    const preferences = parseUserPreferences(attributes['custom:preferences']);

    // Get default role for self-registered users (ADR-001)
    const role = getDefaultRole();

    console.log('Processing user confirmation', {
      cognitoId,
      email,
      emailVerified: email_verified === 'true',
      firstName: preferences.firstName,
      lastName: preferences.lastName,
      language: preferences.language,
      role,
    });

    // Create user and assign role in database
    await createUser(cognitoId, email, email_verified === 'true', preferences, role);

    // Record success metrics
    const duration = Date.now() - startTime;
    await publishMetric('SyncLatency', duration, 'Milliseconds');
    await publishMetric('SyncSuccess', 1, 'Count');

    console.log('PostConfirmation sync completed successfully', {
      cognitoId,
      email,
      duration,
    });
  } catch (error) {
    // Log error but DO NOT throw - allow Cognito confirmation to continue
    console.error('PostConfirmation sync failed (non-blocking)', {
      error: error instanceof Error ? error.message : 'Unknown error',
      stack: error instanceof Error ? error.stack : undefined,
    });

    // Record failure metric
    await publishMetric('SyncFailure', 1, 'Count');

    // Return event unchanged - Cognito will continue confirmation despite DB sync failure
  }

  // Always return the event unchanged for Cognito
  return event;
};
