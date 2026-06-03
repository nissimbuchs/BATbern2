/**
 * PreSignUp Lambda Trigger
 * Story 12.6 (SSO Phase 2): Account-Linking PreSignUp Trigger
 *
 * Two responsibilities, branched on event.triggerSource:
 *
 *  1. NATIVE sign-up (PreSignUp_SignUp / PreSignUp_AdminCreateUser):
 *     Reproduces the legacy inline trigger VERBATIM — an optional company-UUID
 *     format check, then return. NO DB call, NO auto-verify (native confirmation
 *     still flows through CustomEmailSender). This path is regression-critical:
 *     it runs for every password signup in the one real (production) pool.
 *
 *  2. FEDERATED sign-in (PreSignUp_ExternalProvider — e.g. Google, Story 12.5):
 *     On first Google sign-in for an email that already belongs to a native user,
 *     merge the Google identity into that native user via AdminLinkProviderForUser
 *     (matched by email, DESTINATION sub preserved) so user_profiles.cognito_user_id
 *     stays stable → roles/profile/company/history intact, no AliasExistsException,
 *     no orphaned roles (ADR-010 D3). A brand-new Google user (no native match) is
 *     auto-confirmed and provisioned lazily later by canonical JIT (Story 12.3) —
 *     this trigger creates NO DB row. The federated path NEVER throws (a throw 503s
 *     the sign-in).
 *
 * Federated trigger set note (ADR-010, plan §3): for external IdPs Cognito fires
 * PreSignUp + PreTokenGeneration + PostAuthentication, but NOT PostConfirmation or
 * PreAuthentication. So DB-row creation (PostConfirmation) and the is_active gate
 * (PreAuthentication) are bypassed for federated users and handled elsewhere
 * (canonical JIT / the API-gateway is_active gate) — deliberately NOT here.
 */

import { PreSignUpTriggerEvent, PreSignUpTriggerHandler } from 'aws-lambda';
import { CloudWatchClient, PutMetricDataCommand } from '@aws-sdk/client-cloudwatch';
import {
  CognitoIdentityProviderClient,
  AdminLinkProviderForUserCommand,
} from '@aws-sdk/client-cognito-identity-provider';
import { getDbClient } from './common/database';

const cloudwatch = new CloudWatchClient({ region: process.env.AWS_REGION || 'eu-central-1' });
const cognitoClient = new CognitoIdentityProviderClient({
  region: process.env.AWS_REGION || 'eu-central-1',
});

// Company-UUID validation regex — copied VERBATIM from the legacy inline preSignUp
// trigger (cognito-stack.ts, Story 1.2.6). Same pattern, same case-insensitive flag.
const COMPANY_ID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export const handler: PreSignUpTriggerHandler = async (event: PreSignUpTriggerEvent, context) => {
  // Return immediately after the handler resolves; don't wait for the (warm) DB pool's
  // idle socket to drain (mirrors the other VPC/DB triggers).
  context.callbackWaitsForEmptyEventLoop = false;

  console.log('PreSignUp trigger invoked', {
    triggerSource: event.triggerSource,
    userPoolId: event.userPoolId,
  });

  if (event.triggerSource === 'PreSignUp_ExternalProvider') {
    return handleFederated(event);
  }

  // Native path (PreSignUp_SignUp, PreSignUp_AdminCreateUser, and any unrecognised
  // source as a safe default — never auto-confirm an unknown source).
  return handleNative(event);
};

/**
 * Native sign-up — verbatim port of the legacy inline trigger. NO DB / SDK call.
 */
function handleNative(event: PreSignUpTriggerEvent): PreSignUpTriggerEvent {
  // Validate company ID if provided
  const companyId = event.request.userAttributes['custom:companyId'];
  if (companyId && !companyId.match(COMPANY_ID_PATTERN)) {
    throw new Error('Invalid company ID format. Must be a valid UUID.');
  }

  // Role validation removed - Story 1.2.6: ADR-001 database-centric architecture
  // Roles are managed in PostgreSQL and synced to JWT via PreTokenGeneration Lambda
  // Self-registered users receive ATTENDEE role (assigned by PostConfirmation trigger)

  // Auto-verification disabled to test email verification flow
  // Users must verify their email via CustomEmailSender Lambda

  return event;
}

/**
 * Federated sign-in (PreSignUp_ExternalProvider). Links to an existing native user
 * by email when one exists; otherwise lets the brand-new federated user through for
 * lazy JIT provisioning. NEVER throws.
 */
async function handleFederated(event: PreSignUpTriggerEvent): Promise<PreSignUpTriggerEvent> {
  const email = event.request.userAttributes.email;

  // Missing-email guard: email-keyed linking is impossible without an email (Apple
  // private-relay is explicitly out of scope — plan §5 Phase 6 / ADR-010 D2). Do NOT
  // auto-confirm here; fall through unchanged.
  if (!email) {
    console.warn('Federated sign-in without an email — cannot link by email, skipping', {
      userName: event.userName,
    });
    publishMetric('FederatedNoEmail', 1).catch((err) => console.error('Metric publish failed', err));
    return event;
  }

  // Both the link path and the brand-new path complete without a confirmation
  // round-trip (email already proven by the IdP).
  event.response.autoConfirmUser = true;
  event.response.autoVerifyEmail = true;

  let client;
  try {
    client = await getDbClient();

    // Source of truth = the DB row. Match case-INSENSITIVELY (LOWER(email) = LOWER($1)):
    // registration normalises email to lowercase (UserService.normalizeEmail), Google sends
    // a lowercased email, and the rest of the codebase treats email case-insensitively
    // (user_additional_emails LOWER(email) indexes, TestFixtureCleanupRepository LOWER(email)).
    // An IdP email whose casing differs from a non-normalised legacy/admin row must still link
    // — a case-sensitive '=' would silently route it to the brand-new path and orphan the sub.
    const result = await client.query(
      `SELECT cognito_user_id, username
       FROM user_profiles
       WHERE LOWER(email) = LOWER($1)`,
      [email]
    );

    const existing = result.rows[0];
    if (existing && existing.cognito_user_id) {
      // Parse "Google_<sub>" → provider name + the Google subject. Split on the FIRST
      // underscore (Google subjects are numeric, but be robust to any provider prefix).
      const sep = event.userName.indexOf('_');
      const providerName = sep > 0 ? event.userName.substring(0, sep) : 'Google';
      const providerUserId = sep > 0 ? event.userName.substring(sep + 1) : event.userName;

      await cognitoClient.send(
        new AdminLinkProviderForUserCommand({
          UserPoolId: event.userPoolId,
          // Destination = the existing native Cognito user (its username IS the sub for
          // an email-alias pool), so the sub is preserved on link.
          DestinationUser: {
            ProviderName: 'Cognito',
            ProviderAttributeValue: existing.cognito_user_id,
          },
          // Source = the incoming external (Google) identity.
          SourceUser: {
            ProviderName: providerName,
            ProviderAttributeName: 'Cognito_Subject',
            ProviderAttributeValue: providerUserId,
          },
        })
      );

      console.log('Linked federated identity to existing native user', {
        email,
        destinationSub: existing.cognito_user_id,
        username: existing.username,
        providerName,
      });
      publishMetric('FederatedUserLinked', 1).catch((err) =>
        console.error('Metric publish failed', err)
      );
    } else if (existing) {
      // A user_profiles row exists for this email but has NO cognito_user_id — i.e. an
      // anonymous event registrant (ADR-005, V11__Make_cognito_id_nullable_for_anonymous_users).
      // We deliberately do NOT link here: AdminLinkProviderForUser needs an existing Cognito
      // DESTINATION user, and a brand-new federated identity has none yet; the new user's `sub`
      // is also unknown inside PreSignUp (it is assigned post-confirmation), so the trigger
      // cannot stamp cognito_user_id onto the row either. Canonical JIT
      // (JITUserProvisioningInterceptor.findByEmail → setCognitoUserId, Story 12.3) ADOPTS this
      // row on the federated user's first authenticated API call, when the sub IS in the JWT —
      // so the anonymous registration's history is preserved without a duplicate. Distinct
      // metric so this (rarer) adopt-pending case is observable separately from a true new user.
      console.log('Federated email matches an anonymous (no-sub) row; JIT will adopt on first call', {
        email,
        username: existing.username,
      });
      publishMetric('FederatedAnonymousPendingJit', 1).catch((err) =>
        console.error('Metric publish failed', err)
      );
    } else {
      // No row at all → brand-new federated user. Do NOT link; canonical JIT
      // (Story 12.3) creates the user_profiles row lazily on the first API call.
      console.log('New federated user (no native account to link); JIT will provision', {
        email,
      });
      publishMetric('FederatedNewUser', 1).catch((err) =>
        console.error('Metric publish failed', err)
      );
    }
  } catch (error) {
    // Never throw on the federated path — a thrown error 503s the sign-in. Log, emit a
    // failure metric, and return the (already auto-confirmed) event.
    console.error('PreSignUp federated linking error (returning event, not throwing)', {
      email,
      error: (error as Error).message,
    });
    publishMetric('PreSignUpFailure', 1).catch((err) => console.error('Metric publish failed', err));
  } finally {
    if (client) {
      client.release();
    }
  }

  return event;
}

/**
 * Publish a CloudWatch metric (fire-and-forget; swallow errors).
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
            Unit: 'Count',
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
