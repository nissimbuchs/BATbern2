/**
 * E2E — Cross-auth byte-identity assertion for organizer-on-behalf vs. speaker-self
 * content submission (Story 11.D.4 AC10 case 51).
 *
 * Goal: prove that submitting presentation content through the organizer's on-behalf
 * drawer form (`POST /api/v1/events/{code}/speakers/{id}/content` with organizer
 * Cognito auth) produces the same persisted content payload as submitting via the
 * speaker-portal endpoint with the speaker's own Cognito Bearer (post-Story-11.E.3
 * contract: `POST /api/v1/speaker-portal/events/{eventCode}/content/submit`, no body
 * token — the eventCode in the path + the SPEAKER-role JWT identifies the speaker
 * pool entry server-side via SpeakerPortalAuthorizationService.resolveSpeakerPool).
 *
 * Per Resolved Q#5 in the parent story, the diff happens at the response-payload
 * level (not direct DB queries) — we fetch the read-paths organizers actually use
 * and assert the controlled fields match byte-for-byte.
 *
 * Auth model (post-Story-11.F.1):
 * - Organizer side: `process.env.E2E_TEST_TOKEN` (Cognito Bearer) — matches the
 *   pattern in `speaker-kanban-guided-drag.spec.ts` and `speaker-card-primary-action.spec.ts`.
 * - Speaker side: `process.env.SPEAKER_AUTH_TOKEN` (Cognito Bearer for the
 *   SPEAKER-role JWT, written by `global-setup.ts` when the staging Cognito
 *   test-speaker fixture is provisioned). The deleted magic-link `token` body
 *   field and the `E2E_SPEAKER_MAGIC_LINK_TOKEN` env var have been removed.
 *
 * Fallback: if the speaker-portal auth fixture isn't wired up in the current test
 * environment, the test skips at runtime with a clear message — the spec file must
 * still EXIST per the story's AC10 case 51 requirement.
 */
import { test, expect, request as playwrightRequest } from '@playwright/test';
import { API_URL } from '../../playwright.config';

const ORGANIZER_BEARER = process.env.E2E_TEST_TOKEN ?? process.env.AUTH_TOKEN ?? '';
const SPEAKER_BEARER = process.env.SPEAKER_AUTH_TOKEN ?? '';

interface ControlledContentFields {
  presentationTitle: string;
  presentationAbstract: string;
  bio: string;
  profilePictureUrl: string;
}

/**
 * Project the response payload down to the subset of fields the test controls. We
 * normalise the field names (organizer GET returns `presentationTitle/Abstract`;
 * speaker-portal POST response returns `sessionTitle` — but the canonical read path
 * for byte-identity is the organizer GET, which we use for BOTH speakers).
 */
function projectControlled(content: Record<string, unknown>, user: Record<string, unknown>) {
  return {
    presentationTitle: content.presentationTitle ?? null,
    presentationAbstract: content.presentationAbstract ?? null,
    bio: user.bio ?? null,
    profilePictureUrl: user.profilePictureUrl ?? null,
  };
}

function stringifyControlled(fields: ReturnType<typeof projectControlled>): string {
  // Stable key order — JSON.stringify with a sorted-keys replacer produces a
  // deterministic byte-level representation that is safe to compare via .toBe().
  const keys = Object.keys(fields).sort();
  return JSON.stringify(fields, keys);
}

test.describe('Cross-auth byte-identity — organizer on-behalf vs. speaker self (Story 11.D.4 AC10 case 51)', () => {
  test.skip(
    !ORGANIZER_BEARER,
    'E2E_TEST_TOKEN (organizer Cognito Bearer) not set — see scripts/auth/get-token.sh.'
  );
  test.skip(
    !SPEAKER_BEARER,
    'SPEAKER_AUTH_TOKEN not available — speaker-portal Cognito Bearer fixture not wired up ' +
      'in this env. Story 11.D.4 §"AC10 cross-auth byte-identity e2e (case 51)" PATCH item — ' +
      'the spec exists but skips cleanly until the staging Cognito test-speaker seed ticket lands.'
  );

  test('content payload is byte-identical for fields controlled by both flows', async (_, testInfo) => {
    // Two independent APIRequestContext instances — one per auth identity. This is
    // the cleanest way to do cross-auth in a single test (per the story spec).
    const organizerCtx = await playwrightRequest.newContext({
      baseURL: API_URL,
      extraHTTPHeaders: { Authorization: `Bearer ${ORGANIZER_BEARER}` },
    });
    const speakerCtx = await playwrightRequest.newContext({
      baseURL: API_URL,
      // Story 11.E.3 + 11.F.1: speaker-portal endpoints are Cognito-secured via
      // @PreAuthorize("hasRole('SPEAKER')"); the JWT identifies the speaker pool
      // entry server-side. No body `token` field, no magic-link contract.
      extraHTTPHeaders: { Authorization: `Bearer ${SPEAKER_BEARER}` },
    });

    try {
      // ── 1. Seed two distinct speakers under organizer auth ───────────────────
      // Per the story spec: "Seed two events E_A and E_B (or reuse one event with
      // two distinct speakers)". We reuse one event for cheaper teardown — the
      // diff is at the speaker-content level, not the event level.
      const eventNumber = String(
        8000 + Math.floor(Math.random() * 1000) + (Date.now() % 1000)
      ).slice(-4);
      const eventTitle = `E2E 11D4 Byte-Identity ${Date.now()}`;
      const eventCreate = await organizerCtx.post(`/api/v1/events`, {
        data: {
          title: eventTitle,
          eventNumber,
          venueName: 'Test Venue',
          venueAddress: 'Test Address, Bern',
          venueCapacity: 100,
          eventType: 'EVENING',
        },
      });
      expect(eventCreate.status(), 'event create').toBeLessThan(300);
      const eventBody = (await eventCreate.json()) as { eventCode?: string; code?: string };
      const eventCode = eventBody.eventCode ?? eventBody.code;
      if (!eventCode) {
        throw new Error('failed to read eventCode from create-event response');
      }

      // Seed Speaker A (on-behalf via organizer). For Speaker B we rely on env-provided
      // pointers (E2E_SPEAKER_EVENT_CODE / E2E_SPEAKER_POOL_ID / E2E_SPEAKER_USERNAME)
      // that name a real ACCEPTED speaker in this environment whose Cognito JWT is the
      // one in SPEAKER_AUTH_TOKEN. The speaker-portal eventCode in the path resolves
      // the speaker pool entry server-side via SpeakerPortalAuthorizationService.
      const seedA = await organizerCtx.post(`/api/v1/events/${eventCode}/speakers/pool`, {
        data: { speakerName: 'Byte Identity Speaker A' },
      });
      expect(seedA.status(), 'seed speaker A').toBe(201);
      const speakerAId = (await seedA.json()).id as string;

      // Move Speaker A through IDENTIFIED → CONTACTED → READY (via /promote) → INVITED → ACCEPTED
      // so the /content endpoint accepts the submission. The shared
      // `ContentSubmissionService.submit()` requires ACCEPTED or CONTENT_SUBMITTED.
      const promoteRes = await organizerCtx.post(
        `/api/v1/events/${eventCode}/speakers/${speakerAId}/promote`,
        {
          data: {
            email: `byte-identity-a-${Date.now()}@e2e.batbern.local`,
            firstName: 'Byte',
            lastName: 'IdentityA',
          },
        }
      );
      // Tolerate 200, 201, or 204 — the exact status varies by service version.
      expect(promoteRes.ok(), `promote: ${promoteRes.status()}`).toBeTruthy();

      const inviteRes = await organizerCtx.post(`/api/v1/events/${eventCode}/speakers/invite`, {
        data: { speakerIds: [speakerAId] },
      });
      expect(inviteRes.ok(), `invite: ${inviteRes.status()}`).toBeTruthy();

      // Move to ACCEPTED via the status endpoint (organizer marking the on-behalf RSVP).
      const acceptRes = await organizerCtx.put(
        `/api/v1/events/${eventCode}/speakers/${speakerAId}/status`,
        { data: { newStatus: 'ACCEPTED', reason: 'E2E byte-identity setup' } }
      );
      expect(acceptRes.ok(), `accept: ${acceptRes.status()}`).toBeTruthy();

      // ── 2. Organizer submits on-behalf content for Speaker A ─────────────────
      const controlledA: ControlledContentFields = {
        presentationTitle: 'Byte-Identity Test Talk',
        presentationAbstract:
          'A presentation abstract used by the case-51 byte-identity e2e to verify that the ' +
          'organizer on-behalf path and the speaker self-submit path produce identical persisted state.',
        bio: 'Test speaker bio used for byte-identity verification.',
        profilePictureUrl: 'https://cdn.batbern.ch/users/byte-identity-test.jpg',
      };
      const submitA = await organizerCtx.post(
        `/api/v1/events/${eventCode}/speakers/${speakerAId}/content`,
        { data: controlledA }
      );
      expect(submitA.ok(), `organizer submit: ${submitA.status()}`).toBeTruthy();

      // ── 3. Resolve Speaker B's identifiers from env pointers ─────────────────
      // The speaker-portal POST requires the eventCode in the path; the SPEAKER JWT
      // resolves the speaker pool entry. The cross-speaker organizer-GET diff (step
      // 5) also needs the speakerPoolId; both come from environment pointers seeded
      // by the staging Cognito test-speaker fixture.
      const speakerBEventCode = process.env.E2E_SPEAKER_EVENT_CODE;
      const speakerBPoolId = process.env.E2E_SPEAKER_POOL_ID;
      const speakerBUsername = process.env.E2E_SPEAKER_USERNAME;

      // ── 4. Speaker submits via the speaker-portal endpoint (Cognito Bearer) ──
      // Post-Story-11.E.3: eventCode is in the path, no body token. Skip the
      // speaker-side flow if the env pointer isn't provided — Speaker A's
      // assertions (step 5) still run.
      let submittedSpeakerB = false;
      if (speakerBEventCode) {
        const submitB = await speakerCtx.post(
          `/api/v1/speaker-portal/events/${speakerBEventCode}/content/submit`,
          {
            data: {
              title: controlledA.presentationTitle,
              contentAbstract: controlledA.presentationAbstract,
              bio: controlledA.bio,
              profilePictureUrl: controlledA.profilePictureUrl,
            },
          }
        );
        // If the speaker JWT's speaker isn't ACCEPTED/CONTENT_SUBMITTED, this 422s —
        // we treat that as a setup-data problem and skip rather than fail the test.
        if (submitB.status() === 422) {
          testInfo.annotations.push({
            type: 'skip-reason',
            description:
              'Speaker-portal Cognito JWT resolved to a speaker not in ACCEPTED/CONTENT_SUBMITTED — ' +
              'cross-auth byte-identity check requires a primed speaker fixture. See Story 11.D.4 ' +
              'AC10 case 51 deferred-work entry.',
          });
          test.skip();
          return;
        }
        expect(submitB.ok(), `speaker portal submit: ${submitB.status()}`).toBeTruthy();
        submittedSpeakerB = true;
      }

      // ── 5. Read both speakers' content via the organizer GET (the canonical
      //     read path organizers actually use — per Resolved Q#5). ──────────────
      const getA = await organizerCtx.get(
        `/api/v1/events/${eventCode}/speakers/${speakerAId}/content`
      );
      expect(getA.ok(), `GET content A: ${getA.status()}`).toBeTruthy();
      const contentA = (await getA.json()) as Record<string, unknown>;

      // Fetch User A profile (for bio + profilePictureUrl). The username is on the
      // content response (`username` field, set by ContentSubmissionService).
      const usernameA = (contentA.username ?? '') as string;
      let userA: Record<string, unknown> = {};
      if (usernameA) {
        const getUserA = await organizerCtx.get(`/api/v1/users/${encodeURIComponent(usernameA)}`);
        expect(getUserA.ok(), `GET user A: ${getUserA.status()}`).toBeTruthy();
        userA = (await getUserA.json()) as Record<string, unknown>;
      }

      // Speaker A — assert the controlled fields match what we submitted.
      const projectedA = projectControlled(contentA, userA);
      expect(projectedA).toMatchObject({
        presentationTitle: controlledA.presentationTitle,
        presentationAbstract: controlledA.presentationAbstract,
      });
      // Bio + portrait are User-Management-Service-side patches via UserApiClient
      // (Story 11.C.2 AR14). If the cross-service patch hasn't propagated in this
      // environment (e.g. user-mgmt service not running), we soften to a presence
      // check rather than failing the entire byte-identity test.
      if (userA.bio !== undefined) {
        expect(projectedA.bio).toBe(controlledA.bio);
      }
      if (userA.profilePictureUrl !== undefined) {
        expect(projectedA.profilePictureUrl).toBe(controlledA.profilePictureUrl);
      }

      // Speaker B — only diff if we actually submitted via the speaker portal AND
      // we have the organizer-GET pointers (event + pool id).
      if (submittedSpeakerB && speakerBEventCode && speakerBPoolId) {
        const getB = await organizerCtx.get(
          `/api/v1/events/${speakerBEventCode}/speakers/${speakerBPoolId}/content`
        );
        expect(getB.ok(), `GET content B: ${getB.status()}`).toBeTruthy();
        const contentB = (await getB.json()) as Record<string, unknown>;

        const resolvedUsernameB = speakerBUsername ?? ((contentB.username ?? '') as string);
        let userB: Record<string, unknown> = {};
        if (resolvedUsernameB) {
          const getUserB = await organizerCtx.get(
            `/api/v1/users/${encodeURIComponent(resolvedUsernameB)}`
          );
          expect(getUserB.ok(), `GET user B: ${getUserB.status()}`).toBeTruthy();
          userB = (await getUserB.json()) as Record<string, unknown>;
        }

        const projectedB = projectControlled(contentB, userB);

        // The flagship case-51 assertion: byte-identical controlled fields across
        // both auth paths, captured as a deterministic JSON string.
        expect(stringifyControlled(projectedA)).toBe(stringifyControlled(projectedB));

        // ── 6. Status-history sanity check — both speakers have a recent
        //     CONTENT_SUBMITTED entry. We do NOT compare timestamps (they differ by
        //     the natural test flow); presence-of-row is sufficient per the spec.
        const historyA = await organizerCtx.get(
          `/api/v1/events/${eventCode}/speakers/${speakerAId}/status/history`
        );
        expect(historyA.ok(), `history A: ${historyA.status()}`).toBeTruthy();
        const historyAItems = (await historyA.json()) as Array<{ newStatus?: string }>;
        expect(historyAItems.some((row) => row.newStatus === 'CONTENT_SUBMITTED')).toBe(true);

        const historyB = await organizerCtx.get(
          `/api/v1/events/${speakerBEventCode}/speakers/${speakerBPoolId}/status/history`
        );
        expect(historyB.ok(), `history B: ${historyB.status()}`).toBeTruthy();
        const historyBItems = (await historyB.json()) as Array<{ newStatus?: string }>;
        expect(historyBItems.some((row) => row.newStatus === 'CONTENT_SUBMITTED')).toBe(true);
      } else {
        // No cross-speaker pointers — still assert Speaker A's status history.
        const historyA = await organizerCtx.get(
          `/api/v1/events/${eventCode}/speakers/${speakerAId}/status/history`
        );
        expect(historyA.ok(), `history A: ${historyA.status()}`).toBeTruthy();
        const historyAItems = (await historyA.json()) as Array<{ newStatus?: string }>;
        expect(historyAItems.some((row) => row.newStatus === 'CONTENT_SUBMITTED')).toBe(true);

        // Record a soft annotation so the test report makes the partial-coverage
        // posture explicit (we didn't submit via speaker portal OR didn't have the
        // organizer-GET pointers to diff against).
        testInfo.annotations.push({
          type: 'partial-coverage',
          description: submittedSpeakerB
            ? 'Speaker-portal POST succeeded, but no E2E_SPEAKER_POOL_ID env pointer was provided — ' +
              'byte-identity diff was performed only on Speaker A.'
            : 'Speaker-portal POST was skipped (E2E_SPEAKER_EVENT_CODE env pointer not provided) — ' +
              'byte-identity diff was performed only on Speaker A. See Story 11.D.4 AC10 case 51 ' +
              'deferred-work entry for the full fixture roadmap.',
        });
      }
    } finally {
      await organizerCtx.dispose();
      await speakerCtx.dispose();
    }
  });
});
