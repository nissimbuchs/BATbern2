/**
 * PreTokenGeneration Lambda Trigger
 * Story 1.2.5: User Sync and Reconciliation Implementation
 * ADR-001: Unidirectional Sync (Database → JWT custom claims)
 *
 * This Lambda function is triggered by AWS Cognito before token generation.
 * It reads user roles from PostgreSQL and adds them to JWT as custom claims.
 *
 * AC2: PreTokenGeneration adds roles to JWT custom claims
 * - When a user authenticates and token is generated
 * - Then roles are fetched from the database
 * - And added to JWT as `custom:role` claim (comma-separated)
 * - NO Cognito Groups used (ADR-001: Database is source of truth)
 */

import { PreTokenGenerationTriggerEvent, PreTokenGenerationTriggerHandler } from 'aws-lambda';
import { getDbClient } from './common/database';
import { CloudWatchClient, PutMetricDataCommand } from '@aws-sdk/client-cloudwatch';

// CloudWatch client for metrics
const cloudWatchClient = new CloudWatchClient({ region: process.env.AWS_REGION || 'eu-central-1' });

/**
 * User data from database
 */
interface UserData {
  username: string;
  role: string;
}

/**
 * Publish CloudWatch metric for fetch operation
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
    // Don't throw - metrics failure shouldn't block token generation
  }
}

/**
 * Fetch user data (username and roles) from database by Cognito ID.
 * Falls back to email lookup when the record was pre-created by an organizer
 * (cognito_user_id is NULL until the JIT interceptor links it on the first API call).
 * Returns username and all assigned roles from role_assignments table.
 * Story 1.16.2: Username is the public meaningful identifier
 */
async function fetchUserData(cognitoId: string, email?: string): Promise<UserData[]> {
  const client = await getDbClient();

  try {
    const roleOrderSql = `
      ORDER BY
        CASE ra.role
          WHEN 'ORGANIZER' THEN 1
          WHEN 'SPEAKER' THEN 2
          WHEN 'PARTNER' THEN 3
          WHEN 'ATTENDEE' THEN 4
          ELSE 5
        END
    `;

    // Primary lookup: by cognito_user_id (fast path — indexed column)
    const result = await client.query(
      `SELECT u.username, ra.role
       FROM user_profiles u
       LEFT JOIN role_assignments ra ON ra.user_id = u.id
       WHERE u.cognito_user_id = $1
       ${roleOrderSql}`,
      [cognitoId]
    );

    if (result.rows.length > 0) {
      console.log('Fetched user data from database by cognitoId', {
        cognitoId,
        rowCount: result.rows.length,
        username: result.rows[0]?.username,
      });
      return result.rows;
    }

    // Fallback: email lookup for pre-existing records created by an organizer
    // (cognito_user_id is NULL until the JIT interceptor links it on first API call)
    if (email) {
      const emailResult = await client.query(
        `SELECT u.username, ra.role
         FROM user_profiles u
         LEFT JOIN role_assignments ra ON ra.user_id = u.id
         WHERE u.email = $1
         ${roleOrderSql}`,
        [email]
      );

      console.log('Fetched user data from database by email fallback', {
        cognitoId,
        email,
        rowCount: emailResult.rows.length,
        username: emailResult.rows[0]?.username,
      });

      return emailResult.rows;
    }

    console.log('No user data found in database', { cognitoId, email });
    return [];
  } catch (error) {
    console.error('Failed to fetch user data from database', {
      cognitoId,
      error: error instanceof Error ? error.message : 'Unknown error',
    });
    throw error;
  } finally {
    client.release();
  }
}


/**
 * Main Lambda handler for PreTokenGeneration trigger
 *
 * IMPORTANT: This function MUST NOT throw errors for normal operations.
 * If role fetch fails, fallback to empty groups (graceful degradation).
 */
export const handler: PreTokenGenerationTriggerHandler = async (event) => {
  const startTime = Date.now();

  console.log('PreTokenGeneration trigger invoked', {
    userPoolId: event.userPoolId,
    userName: event.userName,
    triggerSource: event.triggerSource,
  });

  try {
    // Extract Cognito ID and email from user attributes
    const cognitoId = event.request.userAttributes.sub;
    const email = event.request.userAttributes.email;

    if (!cognitoId) {
      console.error('Missing sub attribute in user attributes');
      // Return event unchanged - allow token generation without group override
      return event;
    }

    // Fetch user data (username + roles) from database.
    // Email fallback handles pre-existing records where cognito_user_id is still NULL.
    let userData: UserData[];
    try {
      userData = await fetchUserData(cognitoId, email);
    } catch (error) {
      // Fallback to empty data on database error (graceful degradation)
      console.error('Database fetch failed, falling back to empty data', { error });
      userData = [];

      // Record failure metric
      await publishMetric('UserDataFetchFailure', 1, 'Count');
    }

    // Extract username and roles from fetched data
    // Username should be same for all rows (comes from user_profiles)
    const username = userData[0]?.username || '';
    const roles = userData.map(r => r.role).filter(Boolean);

    // ADR-001: Add username and roles as JWT custom claims (NO Cognito Groups)
    // Database is source of truth for user data
    // Story 1.16.2: username is the public meaningful identifier
    const rolesString = roles.join(',');

    event.response = {
      ...event.response,
      claimsOverrideDetails: {
        ...event.response.claimsOverrideDetails,
        claimsToAddOrOverride: {
          'custom:username': username,
          'custom:role': rolesString,
        },
      },
    };

    // Record success metrics
    const duration = Date.now() - startTime;
    await publishMetric('UserDataFetchLatency', duration, 'Milliseconds');
    await publishMetric('RoleCount', roles.length, 'Count');

    console.log('PreTokenGeneration completed successfully (ADR-001: DB → JWT)', {
      cognitoId,
      username,
      roleCount: roles.length,
      roles: rolesString,
      duration,
    });
  } catch (error) {
    // Log error but DO NOT throw - allow token generation to continue
    console.error('PreTokenGeneration failed (non-blocking)', {
      error: error instanceof Error ? error.message : 'Unknown error',
      stack: error instanceof Error ? error.stack : undefined,
    });

    // Record failure metric
    await publishMetric('TokenEnrichmentFailure', 1, 'Count');

    // Return event unchanged - token will be generated without group override
  }

  // Always return the event (modified or unchanged)
  return event;
};
