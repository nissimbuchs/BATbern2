# Local Authentication Token Setup

This guide explains how to set up local authentication tokens for testing without storing sensitive credentials in git.

## Overview

Authentication tokens are stored in `~/.batbern/` directory (outside the git repository) to prevent accidental commits of sensitive credentials.

## Directory Structure

```
~/.batbern/
├── README.md
├── staging.json      # Staging environment tokens
└── production.json   # Production environment tokens (when needed)
```

## Getting a Token

### Staging Environment

Run the authentication script with your staging credentials:

```bash
./scripts/auth/get-token.sh staging your-email@example.com your-password
```

**Example**:
```bash
./scripts/auth/get-token.sh staging nissim@buchs.be mypassword
```

### Production Environment

(Not yet implemented)

```bash
./scripts/auth/get-token.sh production your-email@example.com your-password
```

## Token Storage

Tokens are stored in JSON format in `~/.batbern/<environment>.json`:

```json
{
  "environment": "staging",
  "userId": "c334a852-10c1-70d2-f403-136e0a60acf7",
  "email": "your-email@example.com",
  "idToken": "eyJraWQiOiJ...",
  "accessToken": "eyJraWQiOiJ...",
  "refreshToken": "eyJraWQiOiJ...",
  "expiresIn": 3600,
  "retrievedAt": "2025-10-18T11:16:00Z"
}
```

**File permissions**: `600` (read/write for owner only)

## Token Expiration

- **Cognito tokens expire after 1 hour by default**
- When token expires, re-run the `get-token.sh` script
- The script will display when the token was retrieved and its expiration time

## How It Works

1. **Bruno environments** use environment variables:
   ```
   authToken: {{process.env.BATBERN_AUTH_TOKEN}}
   ```

2. **Test script** (`scripts/ci/run-bruno-tests.sh`) automatically:
   - Checks `~/.batbern/staging.json` for a token
   - Loads the `idToken` value
   - Sets `BATBERN_AUTH_TOKEN` environment variable
   - Bruno tests use this token

3. **Manual override** is still possible:
   ```bash
   ./scripts/ci/run-bruno-tests.sh staging "manual-token-here"
   ```

## Usage Examples

### Running Bruno Tests

The test script will automatically use the local token:

```bash
./scripts/ci/run-bruno-tests.sh staging
```

Output will show:
```
Loading auth token from local config: ~/.batbern/staging.json
✓ Token loaded successfully
Retrieved at: 2025-10-18T11:16:00Z
Expires in: ~60 minutes from retrieval
```

### Checking Token Status

View your current token:

```bash
cat ~/.batbern/staging.json | jq
```

Check expiration time:

```bash
cat ~/.batbern/staging.json | jq -r '.retrievedAt, .expiresIn'
```

### Refreshing Expired Token

When tests fail with 401 Unauthorized:

```bash
./scripts/auth/get-token.sh staging your-email@example.com your-password
```

## Security Best Practices

✅ **DO:**
- Store tokens in `~/.batbern/` (outside git repo)
- Use file permissions `600` on token files
- Refresh tokens regularly
- Use different credentials for staging/production

❌ **DON'T:**
- Commit `~/.batbern/` directory to git
- Share token files
- Store tokens in the project directory
- Use production credentials for testing

## CI/CD Integration

In CI/CD environments (GitHub Actions), tokens can be:
1. Generated on-demand using stored credentials (AWS Secrets Manager)
2. Passed as environment variables
3. Refreshed automatically before test execution

Example GitHub Actions:
```yaml
- name: Get staging token
  run: |
    ./scripts/auth/get-token.sh staging \
      ${{ secrets.TEST_USER_EMAIL }} \
      ${{ secrets.TEST_USER_PASSWORD }}

- name: Run Bruno tests
  run: ./scripts/ci/run-bruno-tests.sh staging
```

## Troubleshooting

### Token not loading

**Issue**: `WARNING: No auth token found`

**Solution**: Run the get-token script:
```bash
./scripts/auth/get-token.sh staging your-email your-password
```

### Token expired

**Issue**: Tests fail with `401 Unauthorized`

**Solution**: Token has expired. Refresh it:
```bash
./scripts/auth/get-token.sh staging your-email your-password
```

### Permission denied

**Issue**: Cannot write to `~/.batbern/`

**Solution**: Check directory permissions:
```bash
mkdir -p ~/.batbern
chmod 700 ~/.batbern
```

## Related Files

- `scripts/auth/get-token.sh` - Token retrieval script
- `scripts/ci/run-bruno-tests.sh` - Test runner with auto-loading
- `bruno-tests/environments/staging.bru` - Bruno environment config
- `~/.batbern/staging.json` - Local token storage

## Future Enhancements

- [ ] Token auto-refresh using refresh tokens
- [ ] Expiration warnings before running tests
- [ ] Production environment support
- [ ] Multiple test user support
