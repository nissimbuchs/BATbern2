# Epic 9: Speaker Authentication & Account Integration

**Status:** 📋 **PLANNED** (Ready for Implementation)

**Epic Goal**: Unify authentication architecture so speakers who are also attendees can access both portals with a single JWT-based session, eliminating dual login patterns and preventing duplicate accounts.

**Deliverable**: JWT-based speaker portal authentication with automatic account creation/role extension, seamless multi-portal access, and zero duplicate accounts for multi-role users.

**Architecture Context**:
- **Core Services**: Company User Management Service + API Gateway (JWT authentication)
- **Integration**: Refactors Epic 6 token-based auth to JWT-based auth
- **Storage**: AWS Cognito for user accounts and JWT token management
- **Migration**: One-time migration of Epic 6 staging users to new JWT system
- **Frontend**: Unified navigation for multi-role users (speaker + attendee)

**Duration**: Estimated 6-8 weeks (Stories 9.1-9.5)

**Dependencies**:
- Epic 6 Stories 6.0-6.3 (speaker portal foundation)
- AWS Cognito configuration for JWT tokens
- Backend: company-user-management-service modifications
- Frontend: speaker portal auth flow updates

---

## Why Epic 9 is Needed

### Current State (Epic 6 Token-Based Auth)

**Problems:**
- Speaker portal uses magic link tokens (anonymous access, no user account)
- Attendee portal uses Cognito JWT authentication (user accounts)
- Speakers who are also attendees → two separate auth methods → confusing UX
- No way to track "former speakers who attend events" metric
- Duplicate identity management (speaker tokens vs attendee accounts)

**Epic 6 Implementation:**
- `MagicLinkService` generates 30-day reusable tokens
- Tokens stored in `speaker_tokens` table
- GET endpoints use `?token=xxx` query param
- POST endpoints use token in request body
- No user account creation
- No role-based access control

### Desired State (Epic 9 JWT-Based Auth)

**Solutions:**
- ONE authentication system (JWT-based via Cognito)
- Speaker clicks magic link → auto-login with JWT
- If speaker is also attendee → same session, sees both portals
- ONE user account with multiple roles (ATTENDEE, SPEAKER)
- Seamless portal switching with role-based navigation

**Epic 9 Implementation:**
- JWT tokens embedded in magic links
- Auto-create user accounts on invitation acceptance
- Add SPEAKER role to existing attendee accounts (email match)
- Single session accessing both speaker and attendee portals
- Dual authentication: magic link (JWT) + email/password

---

## User Value Proposition

### For Speakers Who Are Also Attendees

**Before Epic 9:**
1. Click speaker magic link → access speaker portal (token-based)
2. Want to register for event → must create separate attendee account
3. Two logins, two sessions, no unified experience
4. Confusion: "Why do I need two accounts?"

**After Epic 9:**
1. Click speaker magic link → JWT auto-login
2. System recognizes existing attendee account → adds SPEAKER role
3. Navigation shows both "Speaker Portal" and "Attendee Portal"
4. Single session → seamless switching between portals
5. Can register for events, view speaker dashboard, all in one place

### For Organizers

**Before Epic 9:**
- No visibility into speaker-attendee overlap
- Manual tracking of "former speakers who attend" metric
- Duplicate records for same person (speaker token + attendee account)

**After Epic 9:**
- Automatic tracking of multi-role users
- Single source of truth for each person
- Metrics: "% of speakers with attendee accounts"
- Better understanding of community engagement

---

## Epic 9 Stories

### Story 9.1: JWT-Based Magic Link Authentication for Speaker Portal

**User Story:**
As a **speaker**, I want to click a magic link that logs me in automatically with a JWT token, so that I can access the speaker portal without creating a separate password.

**Acceptance Criteria:**
1. Magic link emails contain JWT tokens (30-day expiry, reusable)
2. Clicking magic link extracts JWT from URL, stores in HTTP-only cookie
3. Frontend redirects to speaker dashboard after successful JWT validation
4. JWT tokens support same 30-day reusability as Epic 6 tokens
5. Invalid/expired JWT tokens show clear error message with contact info
6. JWT tokens include user_id, email, roles (SPEAKER), expiration timestamp

**Technical Implementation:**
- `MagicLinkService.generateJwtToken(speakerPoolId)` - creates JWT with embedded claims
- API Gateway validates JWT on all `/api/v1/speaker-portal/**` endpoints
- Frontend stores JWT in secure HTTP-only cookie (not localStorage)
- Backend verifies JWT signature, expiration, and SPEAKER role claim

**Testing:**
- Integration tests: JWT generation, validation, expiration handling
- E2E tests: Magic link click-through flow, token refresh
- Security tests: Invalid signature, expired token, tampered claims

---

### Story 9.2: Automatic Account Creation & Role Extension on Invitation Acceptance

**User Story:**
As a **system**, I want to automatically create or update user accounts when speakers accept invitations, so that speakers have unified access without duplicate accounts.

**Acceptance Criteria:**
1. When speaker accepts invitation:
   - Email doesn't exist in Cognito → create new user with SPEAKER role + temp password
   - Email exists (attendee account) → add SPEAKER role to existing account (no duplicate)
2. Cognito user attributes include: email, name, company_id, roles (SPEAKER, ATTENDEE)
3. Temporary password sent via email (for non-magic-link login)
4. Account creation/update logged in audit trail
5. Zero duplicate accounts created (email uniqueness enforced)
6. Existing attendee sessions remain valid after SPEAKER role added

**Technical Implementation:**
- `SpeakerInvitationService.processAcceptance()` - checks Cognito for existing user
- Cognito Admin SDK: `adminCreateUser()` or `adminUpdateUserAttributes()`
- Role management: Custom attribute `custom:roles` stores comma-separated roles
- Email service sends welcome email with credentials (magic link + temp password)

**Testing:**
- Integration tests: New user creation, existing user role extension, duplicate prevention
- E2E tests: Full invitation acceptance flow (new user + existing user scenarios)
- Security tests: Role addition audit, unauthorized role assignment prevention

---

### Story 9.3: Dual Authentication Support (Magic Link + Email/Password)

**User Story:**
As a **speaker**, I want to access the speaker portal via magic link OR email/password, so that I have flexibility in how I authenticate.

**Acceptance Criteria:**
1. Invitation email contains:
   - Magic link (primary: reusable JWT for 30 days)
   - Email + temporary password (secondary: traditional login)
2. Login page supports both authentication methods:
   - "Use Magic Link" button (sends new magic link email)
   - Email + password form (traditional Cognito login)
3. Both methods result in same JWT token (same claims, same session)
4. Password reset flow available for speakers who forget password
5. Magic link login doesn't invalidate password-based sessions (and vice versa)

**Technical Implementation:**
- Frontend `/speaker-portal/login` page with dual auth options
- Magic link path: Email → JWT URL → auto-login
- Password path: Email + password → Cognito authentication → JWT issued
- Shared JWT generation logic ensures consistent session state

**Testing:**
- Integration tests: Both auth paths produce equivalent JWTs
- E2E tests: Magic link flow, password flow, password reset flow
- UX tests: Clear instructions for both methods, error messaging

---

### Story 9.4: Migration Script for Epic 6 Staging Users

**User Story:**
As a **system administrator**, I want to migrate existing Epic 6 token-based speakers to JWT-based authentication, so that we can deploy Epic 9 without data loss.

**Acceptance Criteria:**
1. Migration script identifies all speakers with active tokens in `speaker_tokens` table
2. For each speaker:
   - Create Cognito user account (if not exists)
   - Add SPEAKER role
   - Send new invitation email with JWT magic link + credentials
3. Old token-based magic links marked as deprecated (still work for 7-day grace period)
4. Migration runs successfully on staging environment
5. Rollback script available in case of migration failure
6. Migration report shows: users created, users updated, errors (if any)

**Technical Implementation:**
- `scripts/migration/epic9-jwt-migration.sh` - Bash script orchestrating migration
- Java batch job: `Epic9MigrationService` - queries speaker_tokens, creates Cognito users
- Email batch: Send new magic links to all migrated speakers
- Grace period: Old tokens still validated for 7 days post-migration

**Testing:**
- Integration tests: Migration logic (create user, update user, send email)
- Dry-run tests: Migration script with `--dry-run` flag (no actual changes)
- Rollback tests: Reverting migration if issues detected

---

### Story 9.5: Frontend Unified Navigation for Multi-Role Users

**User Story:**
As a **speaker who is also an attendee**, I want to see navigation options for both speaker and attendee portals, so that I can easily switch between my roles.

**Acceptance Criteria:**
1. JWT token includes roles claim (e.g., `roles: ['SPEAKER', 'ATTENDEE']`)
2. Frontend navigation bar shows role-based links:
   - If SPEAKER role → "Speaker Portal" link visible
   - If ATTENDEE role → "Attendee Portal" link visible
   - If ORGANIZER role → "Organizer Dashboard" link visible
3. Clicking portal link switches context without re-authentication
4. Current portal highlighted in navigation (visual indication)
5. Mobile-responsive navigation supports multi-role users
6. User profile dropdown shows all assigned roles

**Technical Implementation:**
- `AuthContext` extracts roles from JWT token
- React components: `<RoleBasedNav roles={user.roles} />`
- CSS styling: Active portal highlighted with blue underline
- Role-based routing guards prevent unauthorized access

**Testing:**
- Unit tests: Role extraction from JWT, conditional rendering
- E2E tests: Multi-role user navigation, single-role user navigation
- Accessibility tests: Keyboard navigation, screen reader support

---

## Success Criteria

**Epic 9 Success Criteria (from PRD):**
- ✅ Zero duplicate accounts for speakers who are also attendees
- ✅ Single JWT session enables seamless portal switching
- ✅ Magic link tokens remain 30-day reusable
- ✅ Account creation automated on first magic link use
- ✅ Migration complete without data loss

**Metrics to Track:**
1. **% of invited speakers with existing attendee accounts** - Validates ROI of Epic 9
2. **Duplicate account prevention rate** - Should be 100% (zero duplicates created)
3. **Magic link success rate** - % of magic link clicks resulting in successful login
4. **Multi-portal usage rate** - % of multi-role users who access both portals
5. **Migration success rate** - % of Epic 6 users successfully migrated to Epic 9

**Definition of Done (Epic Level):**
- [ ] All 5 stories (9.1-9.5) implemented and tested
- [ ] Migration script tested on staging with 100% success rate
- [ ] Zero duplicate accounts created post-Epic 9 deployment
- [ ] Frontend navigation supports multi-role users seamlessly
- [ ] All Epic 6 magic link URLs replaced with JWT-based URLs
- [ ] Rollback plan documented and tested
- [ ] Epic 9 deployed to staging and validated by organizers

---

## Technical Architecture

### Authentication Flow Diagram

**Epic 6 (Token-Based):**
```
Organizer → Sends Invitation
            ↓
SpeakerPool → MagicLinkService.generateToken(speakerPoolId, VIEW)
            ↓
Email → Speaker clicks link (?token=abc123)
            ↓
Frontend → Validates token via API call
            ↓
Backend → Checks speaker_tokens table
            ↓
Access Granted (anonymous, no user account)
```

**Epic 9 (JWT-Based):**
```
Organizer → Sends Invitation
            ↓
SpeakerPool → Check Cognito for existing user (email match)
            ↓
            ├─→ User exists → Add SPEAKER role
            └─→ User not exists → Create Cognito user + SPEAKER role
            ↓
MagicLinkService.generateJwtToken(userId, roles)
            ↓
Email → Speaker clicks link (embedded JWT in URL)
            ↓
Frontend → Extracts JWT, stores in HTTP-only cookie
            ↓
API Gateway → Validates JWT signature + expiration + roles
            ↓
Access Granted (authenticated, user account, role-based)
```

### Database Changes

**New Tables:**
- None (uses existing Cognito for user accounts)

**Modified Tables:**
- `speaker_tokens` table → deprecated post-migration (kept for historical reference)

**Cognito User Attributes:**
- `email` (unique identifier)
- `name` (speaker full name)
- `custom:company_id` (optional company association)
- `custom:roles` (comma-separated: SPEAKER, ATTENDEE, ORGANIZER, PARTNER)

### API Changes

**New Endpoints:**
- `POST /api/v1/auth/speaker-magic-login` - Validates JWT from magic link, returns session
- `POST /api/v1/auth/speaker-password-login` - Email + password login for speakers
- `POST /api/v1/auth/password-reset` - Initiates password reset for speakers

**Modified Endpoints:**
- All `/api/v1/speaker-portal/**` endpoints now validate JWT (not token query param)
- `POST /api/v1/speaker-portal/invitations/{id}/accept` - Creates/updates Cognito user

**Deprecated Endpoints:**
- Token-based authentication kept for 7-day grace period post-migration

### Security Considerations

**JWT Security:**
- Tokens signed with RS256 (asymmetric encryption)
- HTTP-only cookies prevent XSS attacks
- Short-lived access tokens (30 days) with refresh capability
- Secure flag ensures HTTPS-only transmission

**Migration Security:**
- Old tokens invalidated after grace period
- New passwords generated with high entropy (20+ characters)
- Audit trail logs all account creations/updates
- Rollback plan in case of security issues

---

## Implementation Sequence

**Recommended Order:**
1. **Story 9.1** - JWT magic link authentication (foundation)
2. **Story 9.2** - Account creation/role extension (core logic)
3. **Story 9.3** - Dual authentication support (UX enhancement)
4. **Story 9.5** - Frontend unified navigation (UX completion)
5. **Story 9.4** - Migration script (deployment enabler)

**Why This Order:**
- Foundation first (JWT auth must work before account creation)
- Core logic next (account creation enables multi-role scenarios)
- UX enhancements after core logic validated
- Migration last (requires all Epic 9 features operational)

---

## Testing Strategy

### Integration Tests
- JWT generation and validation
- Cognito user creation/update
- Role management (add/remove roles)
- Email delivery (magic link + credentials)
- Token expiration handling

### E2E Tests (Bruno API)
- Magic link flow (valid JWT, invalid JWT, expired JWT)
- Account creation flow (new user, existing user)
- Dual authentication flow (magic link vs password)
- Multi-role user flow (speaker + attendee portal access)

### E2E Tests (Playwright UI)
- Full invitation acceptance flow
- Portal switching for multi-role users
- Password reset flow
- Migration validation (old token vs new JWT)

### Security Tests
- JWT signature validation
- Role claim tampering prevention
- Cookie security attributes (HTTP-only, Secure, SameSite)
- Password complexity enforcement

---

## Risks & Mitigations

### Risk 1: Migration Breaks Existing Speaker Access

**Impact:** High (speakers locked out during migration)

**Mitigation:**
- 7-day grace period where both old tokens and new JWTs work
- Email all speakers before migration with new magic links
- Rollback script tested and ready
- Migration performed during low-traffic window

### Risk 2: Duplicate Accounts Still Created

**Impact:** Medium (defeats purpose of Epic 9)

**Mitigation:**
- Email uniqueness enforced at Cognito level
- Integration tests validate duplicate prevention
- Code review focuses on account creation logic
- Post-deployment audit to catch any duplicates

### Risk 3: JWT Token Security Vulnerabilities

**Impact:** High (unauthorized access to speaker portal)

**Mitigation:**
- RS256 asymmetric encryption (not HS256 symmetric)
- HTTP-only cookies (not localStorage)
- Short token expiration with refresh capability
- Security audit before production deployment

### Risk 4: Frontend Navigation Confusing for Multi-Role Users

**Impact:** Low (UX issue, not functional)

**Mitigation:**
- User testing with multi-role personas
- Clear visual indicators of current portal
- Tooltips explaining role-based navigation
- Feedback mechanism for UX improvements

---

## Open Questions

1. **Password Policy:** What password complexity requirements for temporary passwords?
   - **Recommendation:** 12+ characters, mix of upper/lower/numbers/symbols (Cognito default)

2. **Token Refresh:** Should JWT tokens be refreshable beyond 30 days?
   - **Recommendation:** No, keep 30-day expiration consistent with Epic 6. Speakers can click magic link again.

3. **Role Removal:** If speaker declines future invitations, should SPEAKER role be removed?
   - **Recommendation:** Keep role (historical record). Add `active_speaker` flag instead.

4. **Multi-Company Speakers:** Speaker works at Company A, later moves to Company B. How to handle?
   - **Recommendation:** User account email remains constant, company_id updates on new invitation.

---

## Definition of Done (Epic 9)

**Code Complete:**
- [ ] All 5 stories implemented with passing tests
- [ ] Integration tests: 95%+ coverage on account creation/JWT logic
- [ ] E2E tests: Full user journeys covered (Bruno + Playwright)
- [ ] Code review completed by 2+ developers
- [ ] Security review completed (JWT implementation, Cognito config)

**Documentation Complete:**
- [ ] API documentation updated (new endpoints, deprecated endpoints)
- [ ] Migration runbook created (step-by-step instructions)
- [ ] Rollback procedure documented and tested
- [ ] User-facing documentation (speaker portal login guide)

**Deployment Ready:**
- [ ] Migration script tested on staging with real data
- [ ] Zero duplicate accounts detected in staging
- [ ] Multi-role user navigation validated by QA
- [ ] Performance benchmarks met (<200ms JWT validation)
- [ ] Security scan passed (no critical vulnerabilities)

**Production Criteria:**
- [ ] Epic 9 running on staging for 2+ weeks without issues
- [ ] Organizer sign-off after staging validation
- [ ] Migration plan approved by stakeholders
- [ ] Monitoring and alerts configured for production

---

**END OF EPIC 9**
