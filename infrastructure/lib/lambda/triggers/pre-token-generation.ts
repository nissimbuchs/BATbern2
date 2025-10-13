/**
 * PreTokenGeneration Lambda Trigger
 * Story 1.2.5: User Sync and Reconciliation Implementation
 *
 * This Lambda function is triggered by AWS Cognito before token generation.
 * It enriches JWT tokens with user roles fetched from the PostgreSQL database.
 *
 * AC1: PreTokenGeneration enriches JWT with roles from database
 * - When a user authenticates and token is generated
 * - Then roles are fetched from the database
 * - And JWT claims are enriched with user roles and event-specific roles
 */

import { PreTokenGenerationTriggerEvent, PreTokenGenerationTriggerHandler } from 'aws-lambda';
import { getDbClient } from './common/database';
import { CloudWatchClient, PutMetricDataCommand } from '@aws-sdk/client-cloudwatch';

// CloudWatch client for metrics
const cloudWatchClient = new CloudWatchClient({ region: process.env.AWS_REGION || 'eu-central-1' });

/**
 * User role from database with optional event ID
 */
interface UserRole {
  role: string;
  event_id: string | null;
}

/**
 * Event-specific role for JWT claims
 */
interface EventRole {
  eventId: string;
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
 * Returns both global roles and event-specific roles
 */
async function fetchUserRoles(cognitoId: string): Promise<UserRole[]> {
  const client = await getDbClient();

  try {
    // Query for active roles joined with users table by cognito_id
    const result = await client.query(
      `
        SELECT ur.role, ur.event_id
        FROM user_roles ur
        JOIN users u ON ur.user_id = u.id
        WHERE u.cognito_id = $1
          AND ur.end_date IS NULL
        ORDER BY ur.role, ur.event_id
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
 * Separate roles into global roles and event-specific roles
 */
function categorizeRoles(roles: UserRole[]): {
  globalRoles: string[];
  eventRoles: EventRole[];
} {
  const globalRoles: string[] = [];
  const eventRoles: EventRole[] = [];

  // Filter out null/invalid roles
  const validRoles = roles.filter((r) => r.role != null && r.role.trim() !== '');

  for (const role of validRoles) {
    if (role.event_id) {
      // Event-specific role
      eventRoles.push({
        eventId: role.event_id,
        role: role.role,
      });
    } else {
      // Global role
      if (!globalRoles.includes(role.role)) {
        // Deduplicate global roles
        globalRoles.push(role.role);
      }
    }
  }

  return { globalRoles, eventRoles };
}

/**
 * Build claims override object with roles
 */
function buildClaimsOverride(globalRoles: string[], eventRoles: EventRole[]): Record<string, string> {
  const claims: Record<string, string> = {};

  // Add global roles as JSON array string
  claims['custom:batbern_roles'] = JSON.stringify(globalRoles);

  // Add event-specific roles if any
  if (eventRoles.length > 0) {
    claims['custom:batbern_event_roles'] = JSON.stringify(eventRoles);
  }

  // Add sync timestamp for cache invalidation
  claims['custom:roles_synced_at'] = new Date().toISOString();

  return claims;
}

/**
 * Main Lambda handler for PreTokenGeneration trigger
 *
 * IMPORTANT: This function MUST NOT throw errors for normal operations.
 * If role fetch fails, fallback to empty roles (graceful degradation).
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
      // Return event unchanged - allow token generation without role enrichment
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

    // Separate global and event-specific roles
    const { globalRoles, eventRoles } = categorizeRoles(userRoles);

    // Build claims override
    const claims = buildClaimsOverride(globalRoles, eventRoles);

    // Add claims to response
    event.response = {
      ...event.response,
      claimsOverrideDetails: {
        ...event.response.claimsOverrideDetails,
        claimsToAddOrOverride: claims,
      },
    };

    // Record success metrics
    const duration = Date.now() - startTime;
    await publishMetric('RoleFetchLatency', duration, 'Milliseconds');
    await publishMetric('RoleCount', globalRoles.length + eventRoles.length, 'Count');

    console.log('PreTokenGeneration completed successfully', {
      cognitoId,
      globalRolesCount: globalRoles.length,
      eventRolesCount: eventRoles.length,
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

    // Return event unchanged - token will be generated without role enrichment
  }

  // Always return the event (modified or unchanged)
  return event;
};
