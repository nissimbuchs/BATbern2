import { PreAuthenticationTriggerHandler, PreAuthenticationTriggerEvent } from 'aws-lambda';
import { CloudWatchClient, PutMetricDataCommand } from '@aws-sdk/client-cloudwatch';
import { getDbClient } from './common/database';

const cloudwatch = new CloudWatchClient({ region: process.env.AWS_REGION || 'eu-central-1' });

/**
 * PreAuthentication Lambda Trigger
 *
 * Purpose: Check if user is active in database before allowing authentication
 *
 * Flow:
 * 1. Extract cognito_id from event
 * 2. Query user_profiles table for user status
 * 3. If user not found, allow (JIT provisioning will handle on first API request)
 * 4. If user found but inactive, block authentication
 * 5. If user active, allow authentication
 * 6. Publish CloudWatch metrics
 *
 * Error Handling:
 * - Database errors: Allow authentication (graceful degradation)
 * - User not found: Allow authentication (JIT provisioning path)
 * - User inactive: Throw error to block authentication
 */
export const handler: PreAuthenticationTriggerHandler = async (
  event: PreAuthenticationTriggerEvent,
  _context,
  callback
) => {
  const startTime = Date.now();
  const cognitoId = event.userName;

  console.log('PreAuthentication trigger invoked', {
    cognitoId,
    triggerSource: event.triggerSource,
    userPoolId: event.userPoolId,
  });

  let client;
  try {
    // Get database client
    client = await getDbClient();

    // Query user status
    const result = await client.query(
      `SELECT is_active, deactivation_reason
       FROM user_profiles
       WHERE cognito_id = $1`,
      [cognitoId]
    );

    // User not found - allow (JIT provisioning will handle)
    if (result.rows.length === 0) {
      console.log('User not found in database, allowing JIT provisioning', { cognitoId });

      await publishMetric('UserNotFoundAllowed', 1);
      await publishMetric('PreAuthLatency', Date.now() - startTime);

      callback(null, event);
      return event;
    }

    const user = result.rows[0];

    // User inactive - block authentication
    if (!user.is_active) {
      console.warn('Blocking inactive user authentication', {
        cognitoId,
        deactivationReason: user.deactivation_reason,
      });

      await publishMetric('InactiveUserBlocked', 1);
      await publishMetric('PreAuthLatency', Date.now() - startTime);

      // Call callback with error AND throw to block authentication
      const errorMessage = `User account is inactive. Reason: ${user.deactivation_reason || 'Account deactivated'}`;
      callback(errorMessage, event);
      throw new Error(errorMessage);
    }

    // User active - allow authentication
    console.log('User active, allowing authentication', { cognitoId });

    await publishMetric('ActiveUserAllowed', 1);
    await publishMetric('PreAuthLatency', Date.now() - startTime);

    callback(null, event);
    return event;
  } catch (error) {
    const err = error as Error;

    // If error is from inactive user check, re-throw (callback already called)
    if (err.message.includes('inactive')) {
      throw error;
    }

    // Database errors - allow with warning (graceful degradation)
    console.error('PreAuthentication error, allowing authentication (graceful degradation)', {
      cognitoId,
      error: err.message,
    });

    await publishMetric('PreAuthFailure', 1);
    await publishMetric('PreAuthLatency', Date.now() - startTime);

    // Allow authentication even on error
    callback(null, event);
    return event;
  } finally {
    if (client) {
      client.release();
    }
  }
};

/**
 * Publish CloudWatch metric
 */
async function publishMetric(metricName: string, value: number): Promise<void> {
  try {
    await cloudwatch.send(
      new PutMetricDataCommand({
        Namespace: 'BATbern/UserSync',
        MetricData: [
          {
            MetricName: metricName,
            Value: value,
            Unit: metricName.includes('Latency') ? 'Milliseconds' : 'Count',
            Timestamp: new Date(),
          },
        ],
      })
    );
  } catch (error) {
    console.error('Failed to publish CloudWatch metric', {
      metricName,
      error: (error as Error).message,
    });
  }
}
