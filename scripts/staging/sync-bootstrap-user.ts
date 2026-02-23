#!/usr/bin/env ts-node
/**
 * Bootstrap User Sync Script for Staging
 *
 * This script syncs a Cognito-only user to the PostgreSQL database.
 * Specifically created to fix the bootstrap user nissim@buchs.be issue
 * where the user exists in Cognito but not in the database.
 *
 * Prerequisites:
 * 1. DB tunnel must be running (run: ./scripts/staging/start-db-tunnel.sh)
 * 2. AWS credentials configured with batbern-staging profile
 * 3. ts-node installed (npm install -g ts-node typescript)
 * 4. Required npm packages: pg, @aws-sdk/client-cognito-identity-provider, @aws-sdk/client-secrets-manager
 *
 * Usage:
 *   ts-node scripts/staging/sync-bootstrap-user.ts <email> <role>
 *
 * Example:
 *   ts-node scripts/staging/sync-bootstrap-user.ts nissim@buchs.be ORGANIZER
 */

import { CognitoIdentityProviderClient, ListUsersCommand, UserType } from '@aws-sdk/client-cognito-identity-provider';
import { SecretsManagerClient, GetSecretValueCommand } from '@aws-sdk/client-secrets-manager';
import { Client } from 'pg';

const AWS_PROFILE = 'batbern-staging';
const AWS_REGION = 'eu-central-1';
const DB_PORT = 5433; // SSM tunnel port
const DB_HOST = 'localhost';
const DB_NAME = 'batbern';

// Valid roles
const VALID_ROLES = ['ORGANIZER', 'SPEAKER', 'PARTNER', 'ATTENDEE'] as const;
type UserRole = typeof VALID_ROLES[number];

/**
 * Get Cognito User Pool ID from CloudFormation stack
 */
async function getCognitoUserPoolId(): Promise<string> {
  const { execSync } = require('child_process');

  try {
    const output = execSync(
      `AWS_PROFILE=${AWS_PROFILE} aws cloudformation describe-stacks --stack-name BATbern-staging-Cognito --query 'Stacks[0].Outputs[?OutputKey==\`UserPoolId\`].OutputValue' --output text --region ${AWS_REGION}`,
      { encoding: 'utf-8' }
    );

    return output.trim();
  } catch (error) {
    console.error('❌ Failed to get Cognito User Pool ID from CloudFormation');
    throw error;
  }
}

/**
 * Get database credentials from Secrets Manager
 */
async function getDbCredentials(): Promise<{ username: string; password: string }> {
  const { execSync } = require('child_process');

  try {
    // Get secret ARN from CloudFormation
    const secretArn = execSync(
      `AWS_PROFILE=${AWS_PROFILE} aws cloudformation describe-stacks --stack-name BATbern-staging-Database --query 'Stacks[0].Outputs[?OutputKey==\`DatabaseSecretArn\`].OutputValue' --output text --region ${AWS_REGION}`,
      { encoding: 'utf-8' }
    ).trim();

    // AWS SDK will automatically use AWS_PROFILE environment variable
    const secretsManager = new SecretsManagerClient({
      region: AWS_REGION
    });

    const response = await secretsManager.send(
      new GetSecretValueCommand({ SecretId: secretArn })
    );

    if (!response.SecretString) {
      throw new Error('Database secret not found or empty');
    }

    return JSON.parse(response.SecretString);
  } catch (error) {
    console.error('❌ Failed to get database credentials from Secrets Manager');
    console.error('   Falling back to manual credentials...');

    // Fallback: prompt for credentials
    const readline = require('readline').createInterface({
      input: process.stdin,
      output: process.stdout
    });

    return new Promise((resolve) => {
      readline.question('Enter DB username (default: batbern_app): ', (username: string) => {
        readline.question('Enter DB password: ', (password: string) => {
          readline.close();
          resolve({
            username: username || 'batbern_app',
            password: password
          });
        });
      });
    });
  }
}

/**
 * Get Cognito user details by email
 */
async function getCognitoUser(userPoolId: string, email: string): Promise<{
  cognitoUserId: string;
  email: string;
  emailVerified: boolean;
  firstName: string;
  lastName: string;
}> {
  // AWS SDK will automatically use AWS_PROFILE environment variable
  const cognito = new CognitoIdentityProviderClient({
    region: AWS_REGION
  });

  try {
    console.log(`🔍 Searching for user with email: ${email}`);

    // List users with email filter
    const listResponse = await cognito.send(
      new ListUsersCommand({
        UserPoolId: userPoolId,
        Filter: `email = "${email}"`
      })
    );

    if (!listResponse.Users || listResponse.Users.length === 0) {
      throw new Error(`User with email ${email} not found in Cognito`);
    }

    const user = listResponse.Users[0];

    // Extract attributes
    const getAttribute = (name: string): string => {
      const attr = user.Attributes?.find((a: { Name?: string; Value?: string }) => a.Name === name);
      return attr?.Value || '';
    };

    const cognitoUserId = getAttribute('sub');
    const emailVerified = getAttribute('email_verified') === 'true';

    // Derive name from email local part (e.g. "batbern.organizer@..." → "Batbern" / "Organizer")
    const capitalize = (s: string) => s.charAt(0).toUpperCase() + s.slice(1).toLowerCase();
    const emailLocal = email.split('@')[0] ?? '';
    const emailParts = emailLocal.split('.');
    const emailFirstName = capitalize(emailParts[0] ?? 'User');
    const emailLastName = capitalize(emailParts[1] ?? 'User');

    // Parse custom:preferences for first/last name if available
    const preferencesJson = getAttribute('custom:preferences');
    let firstName = emailFirstName;
    let lastName = emailLastName;

    if (preferencesJson) {
      try {
        const prefs = JSON.parse(preferencesJson);
        firstName = prefs.firstName || firstName;
        lastName = prefs.lastName || lastName;
      } catch (e) {
        console.warn('⚠️  Could not parse custom:preferences, using email-derived name');
      }
    }

    // Standard name attributes take priority over email-derived name
    const givenName = getAttribute('given_name');
    const familyName = getAttribute('family_name');

    if (givenName) firstName = givenName;
    if (familyName) lastName = familyName;

    console.log('✅ Found Cognito user:');
    console.log(`   Cognito ID: ${cognitoUserId}`);
    console.log(`   Email: ${email}`);
    console.log(`   Email Verified: ${emailVerified}`);
    console.log(`   Name: ${firstName} ${lastName}`);

    return {
      cognitoUserId,
      email,
      emailVerified,
      firstName,
      lastName
    };
  } catch (error) {
    console.error('❌ Failed to get user from Cognito');
    throw error;
  }
}

/**
 * Generate username from first and last name
 */
function generateUsername(firstName: string, lastName: string): string {
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
 * Create user in database
 */
async function createUserInDb(
  client: Client,
  userData: {
    cognitoUserId: string;
    email: string;
    emailVerified: boolean;
    firstName: string;
    lastName: string;
  },
  role: UserRole
): Promise<string> {
  const username = generateUsername(userData.firstName, userData.lastName);

  console.log(`\n📝 Creating user in database...`);
  console.log(`   Username: ${username}`);
  console.log(`   Role: ${role}`);

  try {
    await client.query('BEGIN');

    // Check if user already exists
    const existingUser = await client.query(
      'SELECT id, username FROM user_profiles WHERE cognito_user_id = $1 OR email = $2',
      [userData.cognitoUserId, userData.email]
    );

    let userId: string;

    if (existingUser.rows.length > 0) {
      console.log('⚠️  User already exists in database!');
      console.log(`   Existing username: ${existingUser.rows[0].username}`);
      userId = existingUser.rows[0].id;
    } else {
      // Insert user
      const insertResult = await client.query(
        `
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
        RETURNING id
        `,
        [
          userData.cognitoUserId,
          userData.email,
          username,
          userData.firstName,
          userData.lastName,
          'de', // Default language
          true  // Email notifications enabled
        ]
      );

      userId = insertResult.rows[0].id;
      console.log(`✅ User created with ID: ${userId}`);
    }

    // Check if role already exists
    const existingRole = await client.query(
      'SELECT role FROM role_assignments WHERE user_id = $1 AND role = $2',
      [userId, role]
    );

    if (existingRole.rows.length > 0) {
      console.log(`⚠️  Role ${role} already assigned to user`);
    } else {
      // Assign role
      await client.query(
        `
        INSERT INTO role_assignments (user_id, role, granted_at, granted_by)
        VALUES ($1, $2, CURRENT_TIMESTAMP, NULL)
        `,
        [userId, role]
      );
      console.log(`✅ Role ${role} assigned successfully`);
    }

    await client.query('COMMIT');

    console.log('\n🎉 User sync completed successfully!');
    console.log(`   User ID: ${userId}`);
    console.log(`   Username: ${username}`);
    console.log(`   Email: ${userData.email}`);
    console.log(`   Role: ${role}`);

    return userId;
  } catch (error) {
    await client.query('ROLLBACK');
    console.error('❌ Database transaction failed');
    throw error;
  }
}

/**
 * Main function
 */
async function main() {
  console.log('🚀 Bootstrap User Sync Script');
  console.log('================================\n');

  // Parse arguments
  const args = process.argv.slice(2);

  if (args.length < 2) {
    console.error('❌ Usage: ts-node sync-bootstrap-user.ts <email> <role>');
    console.error('   Example: ts-node sync-bootstrap-user.ts nissim@buchs.be ORGANIZER');
    console.error(`   Valid roles: ${VALID_ROLES.join(', ')}`);
    process.exit(1);
  }

  const email = args[0];
  const role = args[1].toUpperCase() as UserRole;

  if (!VALID_ROLES.includes(role)) {
    console.error(`❌ Invalid role: ${role}`);
    console.error(`   Valid roles: ${VALID_ROLES.join(', ')}`);
    process.exit(1);
  }

  console.log(`Email: ${email}`);
  console.log(`Role: ${role}`);
  console.log(`AWS Profile: ${AWS_PROFILE}`);
  console.log(`AWS Region: ${AWS_REGION}\n`);

  let dbClient: Client | null = null;

  try {
    // Step 1: Get Cognito User Pool ID
    console.log('📡 Fetching Cognito User Pool ID...');
    const userPoolId = await getCognitoUserPoolId();
    console.log(`✅ User Pool ID: ${userPoolId}\n`);

    // Step 2: Get Cognito user details
    const userData = await getCognitoUser(userPoolId, email);

    // Step 3: Get database credentials
    console.log('\n🔐 Fetching database credentials...');
    const dbCreds = await getDbCredentials();
    console.log(`✅ DB Username: ${dbCreds.username}\n`);

    // Step 4: Connect to database
    console.log('🔌 Connecting to database...');
    console.log(`   Host: ${DB_HOST}`);
    console.log(`   Port: ${DB_PORT}`);
    console.log(`   Database: ${DB_NAME}`);
    console.log('   (Make sure the DB tunnel is running!)\n');

    dbClient = new Client({
      host: DB_HOST,
      port: DB_PORT,
      database: DB_NAME,
      user: dbCreds.username,
      password: dbCreds.password,
      ssl: { rejectUnauthorized: false } // RDS requires SSL even through tunnel
    });

    await dbClient.connect();
    console.log('✅ Connected to database\n');

    // Step 5: Create user in database
    await createUserInDb(dbClient, userData, role);

  } catch (error) {
    console.error('\n❌ Error:', error instanceof Error ? error.message : error);
    process.exit(1);
  } finally {
    if (dbClient) {
      await dbClient.end();
      console.log('\n🔌 Database connection closed');
    }
  }
}

// Run the script
main();
