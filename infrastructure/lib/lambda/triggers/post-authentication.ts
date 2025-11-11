/**
 * PostAuthentication Lambda Trigger
 * Story 2.2a: Backend - Anonymous Event Registration (ADR-005)
 *
 * This Lambda function is triggered by AWS Cognito after a user successfully authenticates.
 * It links anonymous user profiles to their new Cognito accounts by matching email addresses.
 *
 * ADR-005: Anonymous Event Registration
 * - When a user creates a Cognito account with an email that matches an existing anonymous user
 * - Then the anonymous user profile is automatically linked to the Cognito account
 * - And the user can access all their previous event registrations
 *
 * Flow:
 * 1. User registers for an event anonymously (cognito_id = NULL)
 * 2. User later creates a Cognito account with the same email
 * 3. This trigger fires on first successful authentication
 * 4. Anonymous user profile is linked by setting cognito_id = sub
 */

import { PostAuthenticationTriggerEvent, PostAuthenticationTriggerHandler } from 'aws-lambda';
import { getDbClient, executeTransaction } from './common/database';
import { CloudWatchClient, PutMetricDataCommand } from '@aws-sdk/client-cloudwatch';

// CloudWatch client for metrics
const cloudWatchClient = new CloudWatchClient({ region: process.env.AWS_REGION || 'eu-central-1' });

/**
 * User attributes from Cognito event
 */
interface UserAttributes {
  sub: string; // Cognito user ID
  email: string;
  email_verified?: string;
}

/**
 * Extract user attributes from PostAuthentication event
 */
function extractUserAttributes(event: PostAuthenticationTriggerEvent): UserAttributes {
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
  };
}

/**
 * Link anonymous user profile to Cognito account
 * ADR-005: Updates cognito_user_id for anonymous users (where cognito_user_id IS NULL)
 */
async function linkAnonymousUserProfile(
  cognitoUserId: string,
  email: string
): Promise<{ linked: boolean; username?: string }> {
  const client = await getDbClient();

  try {
    const startTime = Date.now();

    // Find anonymous user by email (cognito_user_id IS NULL)
    const findResult = await client.query(
      `SELECT id, username, cognito_user_id
       FROM user_profiles
       WHERE email = $1 AND cognito_user_id IS NULL
       LIMIT 1`,
      [email]
    );

    if (findResult.rows.length === 0) {
      console.info('No anonymous user found for email', { email });
      return { linked: false };
    }

    const anonymousUser = findResult.rows[0];
    const username = anonymousUser.username;

    console.info('Found anonymous user profile to link', {
      username,
      email,
      cognitoUserId,
    });

    // Update cognito_user_id to link the account
    const updateResult = await client.query(
      `UPDATE user_profiles
       SET cognito_user_id = $1,
           updated_at = NOW()
       WHERE id = $2 AND cognito_user_id IS NULL
       RETURNING username`,
      [cognitoUserId, anonymousUser.id]
    );

    if (updateResult.rows.length === 0) {
      console.warn('Failed to link anonymous user (race condition or already linked)', {
        username,
        email,
      });
      return { linked: false };
    }

    const duration = Date.now() - startTime;

    console.info('Successfully linked anonymous user profile to Cognito account', {
      username,
      email,
      cognitoUserId,
      durationMs: duration,
    });

    // Publish CloudWatch metric for account linking
    await publishAccountLinkingMetric(duration);

    return { linked: true, username };
  } finally {
    client.release();
  }
}

/**
 * Publish CloudWatch metric for account linking
 */
async function publishAccountLinkingMetric(durationMs: number): Promise<void> {
  try {
    await cloudWatchClient.send(
      new PutMetricDataCommand({
        Namespace: 'BATbern/UserManagement',
        MetricData: [
          {
            MetricName: 'AnonymousAccountLinkingDuration',
            Value: durationMs,
            Unit: 'Milliseconds',
            Timestamp: new Date(),
          },
          {
            MetricName: 'AnonymousAccountLinkingCount',
            Value: 1,
            Unit: 'Count',
            Timestamp: new Date(),
          },
        ],
      })
    );
  } catch (error) {
    console.error('Failed to publish CloudWatch metric', { error });
    // Don't fail the trigger if metric publishing fails
  }
}

/**
 * Main Lambda handler
 * Triggers after successful Cognito authentication
 */
export const handler: PostAuthenticationTriggerHandler = async (event) => {
  const startTime = Date.now();

  console.info('PostAuthentication trigger invoked', {
    userPoolId: event.userPoolId,
    triggerSource: event.triggerSource,
  });

  try {
    // Extract user attributes
    const userAttributes = extractUserAttributes(event);

    console.info('Extracted user attributes', {
      sub: userAttributes.sub,
      email: userAttributes.email,
      emailVerified: userAttributes.email_verified,
    });

    // Attempt to link anonymous user profile
    const linkResult = await linkAnonymousUserProfile(userAttributes.sub, userAttributes.email);

    if (linkResult.linked) {
      console.info('Account linking successful', {
        username: linkResult.username,
        email: userAttributes.email,
        cognitoUserId: userAttributes.sub,
      });
    } else {
      console.debug('No anonymous user to link (normal for new Cognito accounts)', {
        email: userAttributes.email,
      });
    }

    const duration = Date.now() - startTime;
    console.info('PostAuthentication trigger completed', {
      durationMs: duration,
      linked: linkResult.linked,
    });

    // Return the event unchanged (required by Cognito)
    return event;
  } catch (error) {
    console.error('PostAuthentication trigger failed', {
      error: error instanceof Error ? error.message : String(error),
      stack: error instanceof Error ? error.stack : undefined,
    });

    // IMPORTANT: Don't throw errors from PostAuthentication trigger
    // Errors would block user login, which is unacceptable for account linking feature
    // Log the error and allow authentication to proceed
    console.warn('Allowing authentication to proceed despite account linking failure');
    return event;
  }
};
