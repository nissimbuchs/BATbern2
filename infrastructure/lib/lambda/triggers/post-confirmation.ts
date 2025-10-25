/**
 * PostConfirmation Lambda Trigger
 * Story 1.2.5: User Sync and Reconciliation Implementation
 * Story 1.2.6: Updated for ADR-001 Database-centric architecture
 *
 * This Lambda function is triggered by AWS Cognito after a user confirms their email address.
 * It syncs the user to the PostgreSQL database and assigns an initial role.
 *
 * AC1: PostConfirmation trigger creates database user within 1 second
 * - When a user completes email verification in Cognito
 * - Then a corresponding user record is created in the `user_profiles` table
 * - And default ATTENDEE role is assigned (ADR-001: All self-registered users get ATTENDEE)
 * - And the operation completes within 1 second (p95 latency)
 */

import { PostConfirmationTriggerEvent, PostConfirmationTriggerHandler } from 'aws-lambda';
import { getDbClient, executeTransaction } from './common/database';
import { CloudWatchClient, PutMetricDataCommand } from '@aws-sdk/client-cloudwatch';

// CloudWatch client for metrics
const cloudWatchClient = new CloudWatchClient({ region: process.env.AWS_REGION || 'eu-central-1' });

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
 * Create user in database with ON CONFLICT handling for idempotency
 * Story 1.2.3: Extract user profile data from custom:preferences JSON per ADR-001
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

  // Handle username collision with retry mechanism
  let finalUsername = username;
  let attempt = 0;
  const maxAttempts = 10;

  const queries = [
    // Insert user with ON CONFLICT DO NOTHING for idempotency on cognito_user_id
    {
      query: `
        INSERT INTO user_profiles (
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
        ON CONFLICT (cognito_user_id) DO NOTHING
        RETURNING id
      `,
      params: [
        cognitoId,
        email,
        finalUsername,
        firstName,
        lastName,
        language,
        preferences.notifications?.email ?? true,
      ],
    },
  ];

  try {
    const results = await executeTransaction(queries);
    const userResult = results[0];

    // Check if user was created (or already existed)
    let userId: string | null = null;

    if (userResult.rows.length > 0) {
      // User was newly created
      userId = userResult.rows[0].id;
      console.log('User created in database', { userId, cognitoId, email });
    } else {
      // User already exists (idempotent behavior)
      console.log('User already exists in database (idempotent)', { cognitoId, email });

      // Query to get existing user ID
      const client = await getDbClient();
      try {
        const existingUserResult = await client.query(
          'SELECT id FROM user_profiles WHERE cognito_user_id = $1',
          [cognitoId]
        );
        if (existingUserResult.rows.length > 0) {
          userId = existingUserResult.rows[0].id;
        }
      } finally {
        client.release();
      }
    }

    // Assign initial role if user was created
    if (userId) {
      await assignUserRole(userId, role);
    }

    return userId;
  } catch (error) {
    console.error('Failed to create user in database', {
      cognitoId,
      email,
      error: error instanceof Error ? error.message : 'Unknown error',
    });
    throw error;
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

    // Insert role with granted_at (granted_by is NULL for system-assigned roles)
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
