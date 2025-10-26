# Bootstrap User Sync Script

This script syncs a Cognito-only user to the PostgreSQL database. It was specifically created to fix the bootstrap user `nissim@buchs.be` issue where the user exists in Cognito but not in the database.

## Problem

After deploying Epic 1 to staging, the bootstrap user exists in AWS Cognito but has no corresponding entry in the `user_profiles` table or `role_assignments` table in the PostgreSQL database.

## Solution

This script:
1. Fetches user details from AWS Cognito by email
2. Connects to the staging database via SSH tunnel
3. Creates the user_profiles entry
4. Assigns the specified role (e.g., ORGANIZER) to the user

## Prerequisites

1. **DB Tunnel Running**: The database SSH tunnel must be active
   ```bash
   ./scripts/staging/start-db-tunnel.sh
   ```
   Keep this running in a separate terminal window.

2. **AWS Credentials**: Configure AWS credentials for the staging environment
   ```bash
   aws configure --profile batbern-staging
   ```

3. **Dependencies**: Required npm packages (already installed in infrastructure/)
   - pg
   - @aws-sdk/client-cognito-identity-provider
   - @aws-sdk/client-secrets-manager
   - @types/pg

4. **ts-node**: Install if not already available
   ```bash
   npm install -g ts-node typescript
   ```

## Usage

### Using the Shell Wrapper (Recommended)

```bash
./scripts/staging/sync-bootstrap-user.sh <email> <role>
```

Example:
```bash
./scripts/staging/sync-bootstrap-user.sh nissim@buchs.be ORGANIZER
```

### Direct TypeScript Execution

```bash
cd infrastructure
npx ts-node ../scripts/staging/sync-bootstrap-user.ts <email> <role>
```

### Valid Roles

- `ORGANIZER` - Event organizer with full permissions
- `SPEAKER` - Conference speaker
- `PARTNER` - Event partner/sponsor
- `ATTENDEE` - Event attendee

## Step-by-Step Process

1. **Start the DB Tunnel** (in a separate terminal)
   ```bash
   cd /Users/nissim/dev/bat/BATbern-develop
   ./scripts/staging/start-db-tunnel.sh
   ```
   Keep this terminal open!

2. **Run the Sync Script** (in your main terminal)
   ```bash
   cd /Users/nissim/dev/bat/BATbern-develop
   ./scripts/staging/sync-bootstrap-user.sh nissim@buchs.be ORGANIZER
   ```

3. **Verify the Result**
   The script will output detailed logs showing:
   - Cognito user lookup
   - Database connection
   - User creation
   - Role assignment

## What the Script Does

1. **Fetches Cognito User Pool ID** from CloudFormation stack `BATbern-staging-Cognito`

2. **Queries Cognito** for the user with the specified email
   - Retrieves: Cognito User ID (sub), email, first name, last name
   - Checks email verification status

3. **Fetches DB Credentials** from AWS Secrets Manager
   - Uses CloudFormation stack `BATbern-staging-Database` to get secret ARN
   - Falls back to manual input if Secrets Manager access fails

4. **Connects to Database** via the SSH tunnel
   - Host: localhost
   - Port: 5433 (SSH tunnel port)
   - Database: batbern

5. **Creates User Record** in `user_profiles` table
   - Generates username from first/last name (e.g., `nissim.buchs`)
   - Sets default preferences (language: de, email notifications: enabled)
   - Handles duplicate entries gracefully (idempotent)

6. **Assigns Role** in `role_assignments` table
   - Links user_id to the specified role
   - Sets granted_at timestamp
   - Sets granted_by to NULL (system-assigned)

## Troubleshooting

### DB Tunnel Not Running

**Error**: Connection refused on port 5433

**Solution**: Start the DB tunnel first:
```bash
./scripts/staging/start-db-tunnel.sh
```

### User Not Found in Cognito

**Error**: User with email X not found in Cognito

**Solution**:
- Verify the email address is correct
- Check if the user exists in the Cognito User Pool:
  ```bash
  AWS_PROFILE=batbern-staging aws cognito-idp list-users \
    --user-pool-id <pool-id> \
    --filter "email = \"nissim@buchs.be\""
  ```

### Database Credentials Issue

**Error**: Failed to get database credentials from Secrets Manager

**Solution**: The script will prompt for manual credentials. Use:
- Username: `batbern_app`
- Password: (get from AWS Secrets Manager or team lead)

### User Already Exists

**Warning**: User already exists in database!

This is expected if the user was previously synced. The script will still assign the role if it's not already assigned.

## Files Created

1. `scripts/staging/sync-bootstrap-user.ts` - Main TypeScript script
2. `scripts/staging/sync-bootstrap-user.sh` - Bash wrapper for easier execution
3. `scripts/staging/README-BOOTSTRAP-USER-SYNC.md` - This documentation

## Security Notes

- The script uses AWS credentials from the `batbern-staging` profile
- Database credentials are fetched from AWS Secrets Manager
- Connection to the database is through a secure SSH tunnel (no direct internet access)
- No credentials are logged or stored by the script

## Related Documentation

- Story: `docs/stories/1.2.5-user-sync-reconciliation-implementation.md`
- Database Schema:
  - `services/company-user-management-service/src/main/resources/db/migration/V4__Create_user_profiles_table.sql`
  - `services/company-user-management-service/src/main/resources/db/migration/V5__Create_role_assignments_table.sql`
- Lambda Trigger: `infrastructure/lib/lambda/triggers/post-confirmation.ts`
