/**
 * PreTokenGeneration Lambda Trigger
 * Story 1.2.5: User Sync and Reconciliation Implementation
 *
 * This Lambda function is triggered by AWS Cognito before token generation.
 * It ensures Cognito Groups are synced with user roles from the PostgreSQL database.
 *
 * AC1: PreTokenGeneration syncs Cognito Groups with database roles
 * - When a user authenticates and token is generated
 * - Then roles are fetched from the database
 * - And Cognito Groups are set via groupsToOverride to match database roles
 */

import { PreTokenGenerationTriggerEvent, PreTokenGenerationTriggerHandler } from 'aws-lambda';
import { getDbClient } from './common/database';
import { CloudWatchClient, PutMetricDataCommand } from '@aws-sdk/client-cloudwatch';

// CloudWatch client for metrics
const cloudWatchClient = new CloudWatchClient({ region: process.env.AWS_REGION || 'eu-central-1' });

/**
 * User role from database
 */
interface UserRole {
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
 * Fetch user roles from database by Cognito ID
 * Returns global roles only (event-specific roles are not part of Cognito Groups)
 */
async function fetchUserRoles(cognitoId: string): Promise<UserRole[]> {
  const client = await getDbClient();

  try {
    // Query for active global roles only (event_id IS NULL)
    const result = await client.query(
      `
        SELECT DISTINCT ur.role
        FROM user_roles ur
        JOIN users u ON ur.user_id = u.id
        WHERE u.cognito_id = $1
          AND ur.end_date IS NULL
          AND ur.event_id IS NULL
        ORDER BY ur.role
      `,
      [cognitoId]
    );

    console.log('Fetched user roles from database', {
      cognitoId,
      roleCount: result.rows.length,
    });

    return result.rows;
  } catch (error) {
    console.error('Failed to fetch user roles from database', {
      cognitoId,
      error: error instanceof Error ? error.message : 'Unknown error',
    });
    throw error;
  } finally {
    client.release();
  }
}

/**
 * Convert database roles to Cognito Group names
 * Maps uppercase role names (ORGANIZER, SPEAKER, PARTNER, ATTENDEE) to lowercase group names
 */
function rolesToCognitoGroups(roles: UserRole[]): string[] {
  // Filter out null/invalid roles
  const validRoles = roles.filter((r) => r.role != null && r.role.trim() !== '');

  // Convert to lowercase for Cognito Group names
  return validRoles.map((r) => r.role.toLowerCase());
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
    // Extract Cognito ID from user attributes
    const cognitoId = event.request.userAttributes.sub;

    if (!cognitoId) {
      console.error('Missing sub attribute in user attributes');
      // Return event unchanged - allow token generation without group override
      return event;
    }

    // Fetch user roles from database
    let userRoles: UserRole[];
    try {
      userRoles = await fetchUserRoles(cognitoId);
    } catch (error) {
      // Fallback to empty roles on database error (graceful degradation)
      console.error('Database fetch failed, falling back to empty roles', { error });
      userRoles = [];

      // Record failure metric
      await publishMetric('RoleFetchFailure', 1, 'Count');
    }

    // Convert roles to Cognito Group names
    const cognitoGroups = rolesToCognitoGroups(userRoles);

    // Override Cognito Groups with database roles
    event.response = {
      ...event.response,
      claimsOverrideDetails: {
        ...event.response.claimsOverrideDetails,
        groupsToOverride: cognitoGroups,
      },
    };

    // Record success metrics
    const duration = Date.now() - startTime;
    await publishMetric('RoleFetchLatency', duration, 'Milliseconds');
    await publishMetric('RoleCount', cognitoGroups.length, 'Count');

    console.log('PreTokenGeneration completed successfully', {
      cognitoId,
      groupCount: cognitoGroups.length,
      groups: cognitoGroups,
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
