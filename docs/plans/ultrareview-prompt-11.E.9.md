# /ultrareview prompt — Story 11.E.9 (drop speaker_pool.username/email)

Paste the block below into `/ultrareview` from the repo root to launch the
multi-agent review of the column-drop PR before merging to `develop` (which
auto-deploys to staging = production).

Companion artefacts:
- `docs/plans/v103-staging-preflight-cleanup.sql` — manual data cleanup to run
  via the bastion tunnel BEFORE merging.
- `docs/plans/v104-rollback-emergency.sql` — emergency restore migration; cherry-pick
  as `V104__*.sql` onto a hotfix branch if rollback is needed.

---

```text
Branch feature/speaker-workflow-refactor, focus on the last two commits:
2719ab47 fix(11.E.9): drop speaker_pool.username/email columns
7facf3bd docs(11.E.9): align with speaker_pool.username/email column drop

This is the highest-risk PR of the Epic 11 refactor. Context for reviewers:

1. WHY THIS IS RISKY
   - Flyway V103 does ALTER TABLE DROP COLUMN on speaker_pool.username and
     speaker_pool.email — irreversible without restoring from RDS snapshot or
     applying the V104 rollback drafted at docs/plans/v104-rollback-emergency.sql.
   - We deploy directly to production (single AWS account, no real staging env).
   - Staging is at Flyway V92 — V93 through V103 (TEN migrations) will run
     sequentially on first EMS boot post-merge. Pre-flight checks against the
     prod DB are in docs/plans/v103-staging-preflight-cleanup.sql.
   - The refactor touches 30+ files across the EMS service layer, repository,
     controllers, and ~25 test files (44 files / +673 −285).
   - All EMS tests pass locally (1565/1565), but the local DB shape is not
     identical to staging.

2. PLEASE SCRUTINIZE
   a) PrimarySpeakerResolver — every speaker-identity read funnels through it.
      Look for null-handling holes, especially the resolveEmail().orElse(null)
      fallback chain in MagicLinkService, QualityReviewService,
      SpeakerInvitationService.sendInvitation, SpeakerWorkflowService.runInvitedHook.
      What if CUMS is briefly unreachable mid-transaction? What if the
      PRIMARY_SPEAKER session_users row was just deleted?

   b) SecurityContextHelper.getCurrentUsername — the new Pattern 3b username
      twin (DB fallback when JWT custom:username is empty). Mirrors
      JwtRolesConverter exactly, but lives in event-management-service rather
      than shared-kernel. If other services (CUMS, partner-coordination) ever
      read custom:username for speaker identity, they'll need the same twin.
      Is the scope acceptable, or should we promote to shared-kernel now?

   c) SpeakerInvitationService.inviteSpeaker — the email-based idempotency
      check is GONE. Multiple speaker_pool rows for the same email/User on
      the same event are now allowed at IDENTIFIED. Is the brainstorm-UX
      trade-off documented in the PR worth it? Any case where this leaks
      (e.g. via the kanban view) and confuses an organizer?

   d) SpeakerInvitationService.sendInvitation — request.email() override
      semantics changed. Previously the override was persisted on
      speaker_pool.email; now it's a one-shot pass-through to TransitionPayload.
      Frontend or organizer workflow that depended on "the override sticks"
      will silently break.

   e) SpeakerWorkflowService.runReadyHook — identity-rebind guard rewritten to
      check session_users instead of pool.username. The CHANGED test case in
      SpeakerWorkflowServiceTest:should_throwValidationException_when_provisionReturnsDifferentUsername
      seeds a session_users row to trigger the guard. Is the new guard
      strictly equivalent to the old one for ALL the cases the old one caught?

   f) SpeakerPoolResponse.fromEntity no longer reads username/email from the
      entity. All 5 callsites now apply primarySpeakerResolver.applyOverlay()
      after fromEntity(). PROMOTE_TO_READY in particular hits this — and it
      calls UserApiClient.getUserByUsername inside the same @Transactional
      block as the workflow transition. Acceptable transaction-boundary risk,
      or should the overlay move to AFTER_COMMIT?

   g) Repository: findByEventIdAndUsername rewritten as a JPQL JOIN through
      session_users (PRIMARY_SPEAKER). The previous derived-name query had
      O(log n) index-backed lookup; the new query is a 3-table join. Performance
      on speaker_pool tables with thousands of rows?

   h) V103 pre-flight: throws RAISE EXCEPTION if any pool row has
      username IS NOT NULL AND session_id IS NULL. On staging there are 3
      such rows (BATbern58 brainstorm leftovers). V96 backfill SHOULD handle
      them (it provisions a session + session_users for exactly this case).
      But V96 + V103 run in the same Flyway invocation on first boot — is
      there any ordering guarantee that V96 commits BEFORE V103 reads, given
      they're in the same JDBC connection?

   i) Test fixtures: 24 test files had SpeakerPool.builder().username(...).email(...)
      chains stripped via a Python script with a SpeakerPool-builder-scoped regex.
      Verify the script didn't miss any builder edges (especially multi-line
      chains with comments interleaved, or builders nested inside lambdas).

3. NICE-TO-HAVE
   - Review the doc-drift updates in 7facf3bd for technical accuracy. The
     06b-user-lifecycle-sync.md added a "Pattern 3b twin" section that
     reuses the same fallback story for username — does the prose make
     sense as a standalone section, or should it be folded into the existing
     Pattern 3b section?

4. EXPLICITLY OUT OF SCOPE
   - The 3 BATbern58 archived orphan rows. These are pre-existing data
     drift, handled by the manual pre-flight cleanup script.
   - Sue Ajdini / Marcus Schwemmle / Oliver Chatelain — pre-Epic-11 brainstorm
     rows, same cleanup script.
   - V93–V102 migration content (already in main, this PR only adds V103).
```
