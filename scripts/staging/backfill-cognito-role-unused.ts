#!/usr/bin/env ts-node
/**
 * Backfill custom:role='UNUSED' on all existing Cognito pool users (Story 12.1 AC6).
 *
 * Why: ADR-001 "minimal target footprint" — the stored `custom:role` Cognito attribute
 * is a fossil. The authorization claim Spring Security consumes is projected fresh from
 * the database by the PreTokenGeneration Lambda; the stored attribute no longer flows
 * into tokens (Story 12.1 dropped 'role' from the client readAttributes). This script
 * stamps every existing user's stored `custom:role` to the sentinel value 'UNUSED' so a
 * console inspector immediately sees the attribute is deliberately unused. New users get
 * 'UNUSED' going forward at both creation chokepoints (post-confirmation.ts for
 * self-registered; CUMS adminCreateUserSilently for admin-provisioned).
 *
 * SAFETY — staging IS production (single real pool, account 188701360969):
 *   - Defaults to --dry-run: lists affected users + prints the count, mutates NOTHING.
 *   - A live run requires BOTH the --execute flag AND a typed confirmation at the prompt.
 *   - 'UNUSED' fits the custom:role schema maxLen:20.
 *   - This NEVER touches the DB-projected custom:role authorization claim.
 *
 * Prerequisites:
 *   1. AWS credentials configured with the batbern-staging profile
 *   2. ts-node installed (npm install -g ts-node typescript)
 *   3. @aws-sdk/client-cognito-identity-provider available
 *
 * Usage:
 *   # Dry run (default — safe, read-only):
 *   AWS_PROFILE=batbern-staging ts-node scripts/staging/backfill-cognito-role-unused.ts
 *
 *   # Live run (mutates the production pool — requires typed confirmation):
 *   AWS_PROFILE=batbern-staging ts-node scripts/staging/backfill-cognito-role-unused.ts --execute
 */

import {
  CognitoIdentityProviderClient,
  ListUsersCommand,
  ListUsersCommandOutput,
  AdminUpdateUserAttributesCommand,
  AttributeType,
  UserType,
} from '@aws-sdk/client-cognito-identity-provider';
import * as readline from 'readline';

const AWS_PROFILE = process.env.AWS_PROFILE || 'batbern-staging';
const AWS_REGION = process.env.AWS_REGION || 'eu-central-1';
const SENTINEL_VALUE = 'UNUSED';
const CONFIRM_PHRASE = 'backfill unused';

const execute = process.argv.includes('--execute');
const dryRun = !execute;

const sleep = (ms: number): Promise<void> => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Stamp custom:role='UNUSED' on one user, retrying on Cognito throttling. A large pool
 * can exceed the AdminUpdateUserAttributes request-rate quota; without backoff the
 * throttled users would be tallied as permanent failures and skipped. Re-tries up to
 * `maxRetries` times with exponential backoff (capped at 2s); non-throttle errors and
 * exhausted retries propagate to the caller, which tallies them as failed.
 */
async function updateWithRetry(
  client: CognitoIdentityProviderClient,
  userPoolId: string,
  username: string,
  maxRetries = 5
): Promise<void> {
  for (let attempt = 0; ; attempt++) {
    try {
      await client.send(
        new AdminUpdateUserAttributesCommand({
          UserPoolId: userPoolId,
          Username: username,
          UserAttributes: [{ Name: 'custom:role', Value: SENTINEL_VALUE }],
        })
      );
      return;
    } catch (error) {
      const name = (error as { name?: string }).name ?? '';
      const throttled = name === 'TooManyRequestsException' || name === 'ThrottlingException';
      if (!throttled || attempt >= maxRetries) {
        throw error;
      }
      await sleep(Math.min(2000, 100 * 2 ** attempt));
    }
  }
}

/**
 * Resolve the Cognito User Pool ID from the CloudFormation stack output.
 */
function getUserPoolId(): string {
  const { execSync } = require('child_process');
  const output = execSync(
    `AWS_PROFILE=${AWS_PROFILE} aws cloudformation describe-stacks ` +
      `--stack-name BATbern-staging-Cognito ` +
      `--query 'Stacks[0].Outputs[?OutputKey==\`UserPoolId\`].OutputValue' ` +
      `--output text --region ${AWS_REGION}`,
    { encoding: 'utf-8' }
  );
  const poolId = output.trim();
  if (!poolId) {
    throw new Error('Could not resolve UserPoolId from BATbern-staging-Cognito stack outputs');
  }
  return poolId;
}

/**
 * List ALL users in the pool, following pagination tokens.
 */
async function listAllUsers(
  client: CognitoIdentityProviderClient,
  userPoolId: string
): Promise<UserType[]> {
  const users: UserType[] = [];
  let paginationToken: string | undefined = undefined;

  do {
    const resp: ListUsersCommandOutput = await client.send(
      new ListUsersCommand({
        UserPoolId: userPoolId,
        Limit: 60, // Cognito max page size
        PaginationToken: paginationToken,
      })
    );
    if (resp.Users) {
      users.push(...resp.Users);
    }
    paginationToken = resp.PaginationToken;
  } while (paginationToken);

  return users;
}

function getAttribute(user: UserType, name: string): string | undefined {
  return user.Attributes?.find((a: AttributeType) => a.Name === name)?.Value;
}

/**
 * Prompt for a typed confirmation before mutating the production pool.
 */
async function confirmLiveRun(affectedCount: number): Promise<boolean> {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  return new Promise((resolve) => {
    rl.question(
      `\n⚠️  LIVE RUN against the PRODUCTION pool — ${affectedCount} user(s) will get ` +
        `custom:role='${SENTINEL_VALUE}'.\n    Type "${CONFIRM_PHRASE}" to proceed: `,
      (answer) => {
        rl.close();
        resolve(answer.trim() === CONFIRM_PHRASE);
      }
    );
  });
}

async function main() {
  console.log(`\n🔧 Cognito custom:role='${SENTINEL_VALUE}' backfill (Story 12.1 AC6)`);
  console.log(`   Profile: ${AWS_PROFILE} | Region: ${AWS_REGION}`);
  console.log(`   Mode:    ${dryRun ? 'DRY-RUN (read-only)' : 'EXECUTE (will mutate)'}\n`);

  const userPoolId = getUserPoolId();
  console.log(`   User Pool: ${userPoolId}`);

  const client = new CognitoIdentityProviderClient({ region: AWS_REGION });

  const users = await listAllUsers(client, userPoolId);
  const alreadySet = users.filter((u) => getAttribute(u, 'custom:role') === SENTINEL_VALUE);
  const toUpdate = users.filter((u) => getAttribute(u, 'custom:role') !== SENTINEL_VALUE);

  console.log(`\n📊 Pool scan complete:`);
  console.log(`   Total users:                 ${users.length}`);
  console.log(`   Already '${SENTINEL_VALUE}':              ${alreadySet.length}`);
  console.log(`   To update:                   ${toUpdate.length}`);

  // Print a small sample for operator sanity-check.
  const sample = toUpdate.slice(0, 5);
  if (sample.length > 0) {
    console.log(`\n   Sample of users to update (username — current custom:role):`);
    for (const u of sample) {
      console.log(`     - ${u.Username} — ${getAttribute(u, 'custom:role') ?? '(unset)'}`);
    }
    if (toUpdate.length > sample.length) {
      console.log(`     … and ${toUpdate.length - sample.length} more`);
    }
  }

  if (dryRun) {
    console.log(
      `\n✅ DRY-RUN complete — nothing mutated. ` +
        `Re-run with --execute (and the typed confirmation) to apply.\n`
    );
    return;
  }

  if (toUpdate.length === 0) {
    console.log(`\n✅ Nothing to do — every user already carries custom:role='${SENTINEL_VALUE}'.\n`);
    return;
  }

  const confirmed = await confirmLiveRun(toUpdate.length);
  if (!confirmed) {
    console.log(`\n🚫 Confirmation not matched — aborting. No changes made.\n`);
    process.exitCode = 1;
    return;
  }

  let updated = 0;
  let failed = 0;
  for (const u of toUpdate) {
    if (!u.Username) {
      continue;
    }
    try {
      await updateWithRetry(client, userPoolId, u.Username);
      updated++;
      if (updated % 25 === 0) {
        console.log(`   … ${updated}/${toUpdate.length} updated`);
      }
    } catch (error) {
      failed++;
      console.error(
        `   ❌ Failed for ${u.Username}: ${error instanceof Error ? error.message : String(error)}`
      );
    }
  }

  console.log(`\n✅ Backfill complete — updated ${updated}, failed ${failed}.\n`);
  if (failed > 0) {
    process.exitCode = 1;
  }
}

main().catch((error) => {
  console.error('\n❌ Backfill aborted with an error:', error);
  process.exit(1);
});
