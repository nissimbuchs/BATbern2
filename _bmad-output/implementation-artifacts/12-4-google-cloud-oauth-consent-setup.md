# Story 12.4: Google Cloud OAuth + Consent Setup (SSO Phase 0)

Status: done

> **This is a JOINT, NO-CODE, NO-DEPLOY runbook** (Epic 12 / SSO "Phase 0", `docs/plans/sso-oidc-federation.md`). Nissim performs the Google Cloud Console clicks; Amelia provides exact values and verifies. Nothing here is wired into CDK — that is Story **12-5** (which reads the secret this runbook stores). **START EARLY:** domain verification + consent-screen publishing have external review latency that can stall 12-5.

## Goal
Stand up a Google Cloud project + OAuth 2.0 Web client whose **authorized redirect URI points at our Cognito hosted-UI**, with the consent screen **published to Production**, and store the client **id + secret in AWS Secrets Manager** (never inline in CDK).

## Exact values (confirmed from infra 2026-05-31)
| Thing | Value |
|---|---|
| Cognito hosted-UI domain | `batbern-staging-auth.auth.eu-central-1.amazoncognito.com` |
| **Authorized redirect URI** (must be byte-exact) | `https://batbern-staging-auth.auth.eu-central-1.amazoncognito.com/oauth2/idpresponse` |
| Authorized JavaScript origins | *(none needed — Cognito is the only OAuth client; redirect-only flow)* |
| AWS region / account | `eu-central-1` / `188701360969` (production; profile `batbern-staging`) |
| App homepage | `https://www.batbern.ch` |
| Privacy policy | `https://batbern.ch/privacy` |
| App support / contact | `https://batbern.ch/support` (+ a support email) |
| Authorized domain (consent screen) | `batbern.ch` |
| OAuth scopes | `openid`, `email`, `profile` (non-sensitive → **no Google security assessment**) |
| Secrets Manager secret name | `batbern/staging/sso/google-oauth` |
| Secret JSON shape | `{"clientId":"…apps.googleusercontent.com","clientSecret":"…"}` |

## Checklist

- [ ] **Step 0 — Verify the Cognito hosted-UI domain is live** (so the redirect URI is valid). Run locally:
  `! curl -s -o /dev/null -w "%{http_code}\n" https://batbern-staging-auth.auth.eu-central-1.amazoncognito.com/login` — expect a 200/400-class HTTP response (not DNS failure).
- [ ] **Step 1 — Create Google Cloud project.** console.cloud.google.com → project picker → **New Project** → name e.g. `batbern-sso` → Create. Note the **Project ID**.
- [ ] **Step 2 — OAuth consent screen.** APIs & Services → **OAuth consent screen** → User type **External** → Create. App name `BATbern`; user support email; App logo optional; **App home page** `https://www.batbern.ch`; **App privacy policy** `https://batbern.ch/privacy`; ToS optional. **Authorized domains** → add `batbern.ch`. Developer contact email. Save & Continue.
- [ ] **Step 3 — Scopes.** Add scopes `openid`, `email`, `profile` (the three non-sensitive ones). Save & Continue. (No "sensitive/restricted scope" → no verification review.)
- [ ] **Step 4 — Verify `batbern.ch` domain ownership** if Google flags the authorized domain as unverified: Search Console (search.google.com/search-console) → add `batbern.ch` property → verify (DNS TXT record in the `batbern.ch` Route53 zone, hosted-zone `Z08825557YYLWVHISLPY`, account 188701360969). Amelia can give the exact `aws route53 change-resource-record-sets` command once Google shows the TXT value.
- [ ] **Step 5 — Publish consent screen to Production.** OAuth consent screen → **Publishing status: Testing → Publish app → Confirm**. (Avoids the 100-user testing cap + the "unverified app" warning for public attendees. With only non-sensitive scopes this is a light brand check, not a full review.)
- [ ] **Step 6 — Create OAuth 2.0 Web client.** APIs & Services → **Credentials** → Create credentials → **OAuth client ID** → Application type **Web application** → name `batbern-cognito-web` → **Authorized redirect URIs** → add the exact URI above → Create. Copy the **Client ID** + **Client secret**.
- [ ] **Step 7 — Store in Secrets Manager** (Amelia provides the exact command in step 7 below; do NOT paste the secret into chat/commit).
- [ ] **Step 8 — Hand off to Story 12-5.** Record: Project ID, Client ID (the secret stays only in Secrets Manager), secret ARN. 12-5's CDK reads `batbern/staging/sso/google-oauth`.

## Notes
- The redirect URI must match **exactly** (scheme, host, `/oauth2/idpresponse`, no trailing slash) or Google returns `redirect_uri_mismatch` at 12-5 test time.
- Per CLAUDE.md: never commit the client secret; it lives only in Secrets Manager. This runbook + the Secrets Manager entry are the only records.
- Rollback (whole phase): delete the Google OAuth client + project; delete the secret. Nothing else is wired.

## Dev Agent Record
### Completion Notes List
- 2026-05-31: Step 0 verified — Cognito hosted-UI `/login` returns HTTP 302 (domain live, redirect URI host valid).
- 2026-05-31: Step 1 done — Google Cloud project created, **Project ID = `batbern-sso`**.
- 2026-05-31: Steps 2,3,5 done — consent screen **In production**; scopes `openid`/`email`/`profile` set; authorized domain `batbern.ch` accepted (no Search Console verification required → Step 4 N/A).
- 2026-05-31: Steps 6,7 done — OAuth Web client `batbern-cognito-web` created with redirect URI `https://batbern-staging-auth.auth.eu-central-1.amazoncognito.com/oauth2/idpresponse`; secret stored at **ARN `arn:aws:secretsmanager:eu-central-1:188701360969:secret:batbern/staging/sso/google-oauth-TpSKCG`** (name `batbern/staging/sso/google-oauth`, JSON `{clientId, clientSecret}`).
- 🔴 2026-05-31: **SECURITY — client secret was exposed in the dev-session transcript** (pasted command output). **Rotation REQUIRED** before considering Phase 0 closed: regenerate the Google client secret, `put-secret-value` the new value into the same secret, delete the exposed secret in Google. Tracked below.
- ✅ 2026-05-31: **Rotated + closed** — new secret value written via `put-secret-value` (VersionId `766e06a5-7ff2-41fd-8255-fd3b5510b184`, AWSCURRENT); value not echoed. Same ARN/name preserved. **Old exposed `GOCSPX-…` secret deleted in Google — confirmed.** Security loop closed; Phase 0 DONE.
- HANDOFF to Story 12-5: secret name `batbern/staging/sso/google-oauth` (ARN above); CDK `UserPoolIdentityProviderGoogle` reads `clientId`/`clientSecret` from it. The secret-id stays stable across rotation, so 12-5 is unaffected by the rotation.
### File List
- `_bmad-output/implementation-artifacts/12-4-google-cloud-oauth-consent-setup.md` (this runbook)
- AWS Secrets Manager: `batbern/staging/sso/google-oauth` (eu-central-1, acct 188701360969)
- Google Cloud project `batbern-sso` — OAuth client `batbern-cognito-web`
