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
 * 1. Extract cognito_user_id from event
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
// The third `callback` parameter is deliberately absent. AWS Lambda removed callback-based
// handlers in Node.js 24, and it decides which style a handler is by its ARITY — declaring a
// third parameter is enough to be rejected, even for an `async` function returning a Promise.
// Bumping the runtime while it was still declared produced, on EVERY sign-in:
//
//   UserLambdaValidationException: PreAuthentication failed with error
//   ERROR: AWS Lambda has removed support for callback-based function handlers starting with
//   Node.js 24.
//
// Each call was immediately followed by `return event` or a `throw`, so they were redundant and
// removing them preserves behaviour exactly. See #883.
export const handler: PreAuthenticationTriggerHandler = async (
  event: PreAuthenticationTriggerEvent,
  context
) => {
  // Don't wait for event loop to be empty before finishing - return immediately after callback
  context.callbackWaitsForEmptyEventLoop = false;

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
       WHERE cognito_user_id = $1`,
      [cognitoId]
    );

    // User not found - allow (JIT provisioning will handle)
    if (result.rows.length === 0) {
      console.log('User not found in database, allowing JIT provisioning', { cognitoId });

      // Publish metrics without awaiting (fire and forget - non-blocking)
      publishMetric('UserNotFoundAllowed', 1).catch((err) =>
        console.error('Metric publish failed', err)
      );
      publishMetric('PreAuthLatency', Date.now() - startTime).catch((err) =>
        console.error('Metric publish failed', err)
      );

      return event;
    }

    const user = result.rows[0];

    // User inactive - block authentication
    if (!user.is_active) {
      console.warn('Blocking inactive user authentication', {
        cognitoId,
        deactivationReason: user.deactivation_reason,
      });

      // Publish metrics without awaiting (fire and forget - non-blocking)
      publishMetric('InactiveUserBlocked', 1).catch((err) =>
        console.error('Metric publish failed', err)
      );
      publishMetric('PreAuthLatency', Date.now() - startTime).catch((err) =>
        console.error('Metric publish failed', err)
      );

      // Throwing is how a PreAuthentication trigger BLOCKS a sign-in, and that is the intent
      // here: an inactive account must not authenticate. This is the one deliberate throw in
      // this handler; every other path returns the event so authentication proceeds.
      const errorMessage = `User account is inactive. Reason: ${user.deactivation_reason || 'Account deactivated'}`;
      throw new Error(errorMessage);
    }

    // User active - allow authentication
    console.log('User active, allowing authentication', { cognitoId });

    // Publish metrics without awaiting (fire and forget - non-blocking)
    publishMetric('ActiveUserAllowed', 1).catch((err) =>
      console.error('Metric publish failed', err)
    );
    publishMetric('PreAuthLatency', Date.now() - startTime).catch((err) =>
      console.error('Metric publish failed', err)
    );

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

    // Publish metrics without awaiting (fire and forget - non-blocking)
    publishMetric('PreAuthFailure', 1).catch((err) => console.error('Metric publish failed', err));
    publishMetric('PreAuthLatency', Date.now() - startTime).catch((err) =>
      console.error('Metric publish failed', err)
    );

    // Allow authentication even on error (fail open — a broken trigger must not lock every
    // user out; see CLAUDE.md on Cognito triggers).
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
