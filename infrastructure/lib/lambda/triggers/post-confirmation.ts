/**
 * PostConfirmation Lambda Trigger
 * Story 1.2.5: User Sync and Reconciliation Implementation
 *
 * This Lambda function is triggered by AWS Cognito after a user confirms their email address.
 * It syncs the user to the PostgreSQL database and assigns an initial role.
 *
 * AC1: PostConfirmation trigger creates database user within 1 second
 * - When a user completes email verification in Cognito
 * - Then a corresponding user record is created in the `users` table
 * - And an initial role is assigned based on Cognito custom attribute `custom:batbern_role`
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
  'custom:batbern_role'?: string;
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
    'custom:batbern_role': attributes['custom:batbern_role'],
  };
}

/**
 * Determine user role from custom attribute or default to ATTENDEE
 */
function determineUserRole(customRole?: string): UserRole {
  if (customRole && VALID_ROLES.includes(customRole as UserRole)) {
    return customRole as UserRole;
  }

  // Default to ATTENDEE if no valid role specified
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
 */
async function createUser(
  cognitoId: string,
  email: string,
  emailVerified: boolean,
  role: UserRole
): Promise<string | null> {
  const queries = [
    // Insert user with ON CONFLICT DO NOTHING for idempotency
    {
      query: `
        INSERT INTO users (cognito_id, email, email_verified, active, created_at, updated_at)
        VALUES ($1, $2, $3, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON CONFLICT (cognito_id) DO NOTHING
        RETURNING id
      `,
      params: [cognitoId, email, emailVerified],
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
          'SELECT id FROM users WHERE cognito_id = $1',
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
      'SELECT id FROM user_roles WHERE user_id = $1 AND role = $2 AND end_date IS NULL',
      [userId, role]
    );

    if (existingRoleResult.rows.length > 0) {
      console.log('Role already assigned (idempotent)', { userId, role });
      return;
    }

    // Insert role with start_date
    await client.query(
      `
        INSERT INTO user_roles (user_id, role, start_date, created_at)
        VALUES ($1, $2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
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
    const { sub: cognitoId, email, email_verified, 'custom:batbern_role': customRole } = attributes;

    // Determine initial role
    const role = determineUserRole(customRole);

    console.log('Processing user confirmation', {
      cognitoId,
      email,
      emailVerified: email_verified === 'true',
      role,
    });

    // Create user and assign role in database
    await createUser(cognitoId, email, email_verified === 'true', role);

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
