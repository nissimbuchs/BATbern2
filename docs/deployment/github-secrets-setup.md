# GitHub Secrets Setup

This document describes the GitHub repository secrets required for CI/CD workflows.

## Required Secrets

### STAGING_TEST_USER_EMAIL

**Purpose**: Email/username for the bootstrap test user in staging environment.

**Required for**:
- `deploy-staging.yml` workflow
- Bruno API contract tests (Cognito authentication)

**Value**: `nissim@buchs.be`

**How to set**:
1. Navigate to: Repository → Settings → Secrets and variables → Actions
2. Click "New repository secret"
3. Name: `STAGING_TEST_USER_EMAIL`
4. Value: `nissim@buchs.be`
5. Click "Add secret"

### STAGING_TEST_USER_PASSWORD

**Purpose**: Password for the bootstrap test user in staging environment.

**Required for**:
- `deploy-staging.yml` workflow
- Bruno API contract tests (Cognito authentication)

**Value**: The password for the `nissim@buchs.be` user in staging Cognito user pool.

**How to set**:
1. Navigate to: Repository → Settings → Secrets and variables → Actions
2. Click "New repository secret"
3. Name: `STAGING_TEST_USER_PASSWORD`
4. Value: Paste the password (sensitive - never commit to version control)
5. Click "Add secret"

**How it works**:
- The workflow authenticates directly with AWS Cognito on each run
- Fresh tokens are generated automatically (no manual token management)
- Tokens are scoped to the workflow run only (never stored)
- No token expiration issues - always gets a fresh token

## Optional Secrets (Future)

### PRODUCTION_TEST_USER_EMAIL

**Purpose**: Email/username for the bootstrap test user in production environment.

**Value**: TBD when production environment is ready

### PRODUCTION_TEST_USER_PASSWORD

**Purpose**: Password for the bootstrap test user in production environment.

**Value**: TBD when production environment is ready

## Security Best Practices

1. **Never commit credentials to version control**
2. **Use dedicated test accounts** for CI/CD (bootstrap user `nissim@buchs.be`)
3. **Rotate passwords regularly** (recommended: quarterly)
4. **Limit user permissions** to minimum required roles
5. **Monitor authentication activity** in AWS Cognito logs
6. **Use GitHub secrets** for all sensitive values (never hardcode)

## Troubleshooting

### Bruno tests failing with 401 Unauthorized

**Symptom**:
```
events-api/01-list-events (401 Unauthorized)
AssertionError: expected 401 to equal 200
```

**Possible causes**:
1. Secrets not configured
2. Wrong credentials
3. User account disabled in Cognito

**Solution**:
1. Verify secrets are set in GitHub: Repository → Settings → Secrets and variables → Actions
2. Ensure `STAGING_TEST_USER_EMAIL` = `nissim@buchs.be`
3. Ensure `STAGING_TEST_USER_PASSWORD` is correct
4. Check user status in AWS Cognito console
5. Re-run the workflow

### Cognito authentication failed

**Symptom**:
```
ERROR: Cognito authentication failed
```

**Possible causes**:
1. Wrong password
2. User not confirmed
3. User disabled
4. Wrong Cognito configuration

**Solution**:
1. Verify password is correct
2. Check Cognito user pool console: Users → nissim@buchs.be → Status
3. Ensure user is "Confirmed" and "Enabled"
4. Verify Cognito User Pool ID and Client ID in workflow match staging environment

### Secrets not set error

**Symptom**:
```
ERROR: STAGING_TEST_USER_EMAIL and STAGING_TEST_USER_PASSWORD secrets must be set
```

**Cause**: GitHub secrets not configured

**Solution**:
1. Go to: Repository → Settings → Secrets and variables → Actions
2. Add both secrets as described above
3. Verify secret names are exact (case-sensitive):
   - `STAGING_TEST_USER_EMAIL`
   - `STAGING_TEST_USER_PASSWORD`
4. Re-run the workflow

## Advantages of This Approach

✅ **No token expiration issues**: Fresh token generated on each workflow run

✅ **No manual maintenance**: No need to update GitHub secrets regularly

✅ **Simplified setup**: Only two secrets needed (email + password)

✅ **Better security**: Tokens are ephemeral and scoped to workflow run

✅ **Automatic**: Workflow handles authentication transparently

## Related Documentation

- [Local Authentication Setup](../authentication/LOCAL_AUTH_SETUP.md) - Local development token setup
- [Authentication Script](../../scripts/auth/get-token.sh) - Token retrieval script
- [Bruno Tests](../../bruno-tests/) - API contract tests
- [GitHub Actions Secrets Documentation](https://docs.github.com/en/actions/security-guides/encrypted-secrets)
