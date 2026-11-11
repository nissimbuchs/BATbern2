---
title: 'Topic canvas loads partner topics itself'
type: 'refactor'
ticket: '8'
created: '2026-11-11'
status: 'in-progress'
baseline_revision: '4c44601a4d0c2db5ccd57bb95b6a61310422f140'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: ''
review_source: ''
lenses_ran: []
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/ADR-016-service-directions-and-service-to-service-calls.md'
  - '{project-root}/docs/architecture/coding-standards.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** EMS calls PCS downstream (`PartnerApiClientImpl`: public `GET /partners/topics` and `GET /partners?include=company&size=200`) only to put partner topics on the topic canvas (`GET /events/{eventCode}/topic-session-data`). That is the EMS→PCS edge ADR-016 forbids, 2 s2s guard lines, 2 EMS ArchUnit store lines and 2 `ignoringMissingAuthorization` bindings.

**Approach:** The frontend loads partner topics (`partnerTopicsApi.getTopics`) and logos (`GET /partners?include=company&size=200`) from PCS itself, and the clusters from one new EMS operation `POST /topics/classifications` (batch of titles, organizer). `topic-session-data` loses `partnerTopics`; `PartnerApiClient(Impl)` and their tests are deleted. The canvas shows the same partner topics, clusters and logos as before.

## Boundaries & Constraints

**Always:** Owner blanket go 2026-11-10. Commit locally on `feature/17-overnight-2026-11-10`, never open a PR. Verify on local dev only. One heavy job at a time, Gradle `--max-workers=4`, Vitest `--maxWorkers=4`, logs under `~/.cache/batbern-test-logs`, long commands wrapped in `timeout`; restart dev services only with `timeout 600 make dev-native-restart-service SERVICE=<name>`. Spec first. C4: spec, generated types, frontend caller, Bruno and Playwright in the same commit series. C3: EMS module scripts before (below) and after. Frontend calls go through `src/services/`. Test fidelity: MSW harness `api.on(...)` and `ContractServer`, no own-code mocks in touched tests.

**Never:** Change `POST /events/{eventCode}/topic-similarity` (stays public, convention changes are epic 4). Touch `PartnerMeetingRsvpClient*` or `partner-service.base-url` (8b). Change PCS endpoints or roles. Change what the canvas shows (grouping, the "Unknown" group for topics without company, logos, clusters, the 20-company cap).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Canvas open | ORGANIZER, PCS has topics of N companies | Same `partnerTopics` shape as before: grouped by `suggestedByCompany` (null → "Unknown"), logo by company name or null, cluster per title, `voteCount`, `createdAt` | No error expected |
| No partner topics | `GET /partners/topics` → `[]` | No classify call; `partnerTopics: []` | No error expected |
| PCS topics fails | 5xx/network | `partnerTopics: []`, rest of canvas loads | as before (EMS returned `[]`) |
| Logos fail | `GET /partners` fails | Topics shown, `logoUrl: null` | as before |
| Classify fails | EMS 5xx | `partnerTopics: []` | as before (whole partner chain degrades) |
| Classify op | `{"topics":["AI in Operations","Zero Trust"]}` | 200, `classifications` in request order: `{topic, cluster, similarityScore, relatedPastEventNumbers}` | empty list, blank title, over `maxItems` → 400; non-ORGANIZER 403 |

**Decisions (2026-11-11, decided by agent under owner's blanket go):**
- Partner topics without a company keep appearing as the group "Unknown" without a logo, as before (the ticket says "as before"). Whether community topics belong on the canvas is a product question for later, not this story.

</frozen-after-approval>

## Code Map

- EMS `service/TopicSessionDataService.java` -- `fetchPartnerTopics` (L72-96) maps groups + `clusterService.matchCluster`; drop it and the `PartnerApiClient` field.
- EMS `client/PartnerApiClient.java`, `client/impl/PartnerApiClientImpl.java` -- grouping (null company → "Unknown") and logo map; delete. Test `client/impl/PartnerApiClientImplContractTest.java` delete.
- EMS `service/TopicSimilarityService.java` (`getSimilarity`: cluster, 0.85/0.50 score, related events) -- reuse per title; `controller/TopicSimilarityController.java` -- nearest op, single topic, `eventCode` contextual only.
- EMS tests: `controller/TopicSessionDataControllerIntegrationTest.java` (binds partners-api + `ignoringMissingAuthorization`, `@MockitoBean TrendingTopicsService`, class `@Transactional`); `config/CumsContractHarnessTest.java` L71-85 (binds partners-api only to prove `onlyFor`); `src/test/resources/archunit_store/98b035c9-...` (lines 1, 3 = `PartnerApiClient(Impl)`).
- PCS `contract/S2sProviderContractIntegrationTest.java` -- `topics_matchSpec`, `partnerListWithCompany_matchesSpec` and the public-examples check exist only for EMS; delete, keep the RSVP blocks (8b).
- `docs/api/topics-api.openapi.yml` -- L581-650 `topic-session-data` + schemas `PartnerTopicGroup`, `TopicEntry` L725-764.
- Frontend: `src/services/blobTopicService.ts` (`getSessionData`), `src/services/api/partnerTopicsApi.ts` (`getTopics`, reuse), `src/services/api/partnerApi.ts` (add logo list), `src/components/BlobTopicSelector/useTopicSessionData.ts`, `types.ts` (hand-written `TopicSessionData`, kept), `BlobTopicSelector.tsx` L418 (consumer, unchanged); `src/services/blobTopicService.test.ts` (mocks `apiClient`); `src/test/msw/api.ts` (`partners-api/listTopics` qualified key).
- Playwright `e2e/organizer/blob-topic-selector.spec.ts` (`@gate`, BATbern57, no partner assertion). Bruno: none calls `topic-session-data`; `bruno-tests/event-topics-api/` is the home for the new op.
- Gates: `web-frontend/scripts/s2s-client-urls-{baseline,owners}.txt` (2 lines, owner S2); `test-ratchet-baseline.txt` L13, L147, L591; `scripts/architecture/ems-module-deps.py` (`RULES`/`OVR`: `TopicSessionData*` lands in `sessions` by name).
- Docs: `module-map.md` L48, L63; ADR-016 L60, L72, L80-81; `coding-standards.md` L474-477; `01-system-overview.md` L352.

## Tasks & Acceptance

**Execution:**
- [ ] `docs/api/topics-api.openapi.yml` -- add `POST /topics/classifications` (`classifyTopics`, tag "Topic Classifications", ORGANIZER, 200, 400/401/403, example): request `topics: string[]` (`minItems: 1`, `maxItems` set and stated from a local-dev count of partner topics, items `minLength: 1`); response `classifications[]` in request order. Remove `partnerTopics`, `PartnerTopicGroup`, `TopicEntry`; fix the description. `npm run generate:api-types`.
- [ ] EMS `TopicClassificationController` (new) + service method -- per title `TopicSimilarityService.getSimilarity`, `@PreAuthorize("hasRole('ORGANIZER')")`. Integration test: organizer 200 with clusters in order, speaker 403, empty / blank 400, anonymous 401.
- [ ] EMS `TopicSessionDataService` -- no `PartnerApiClient`, no `partnerTopics`; delete `PartnerApiClient`, `PartnerApiClientImpl`, `PartnerApiClientImplContractTest`.
- [ ] `TopicSessionDataControllerIntegrationTest` -- no PCS binding; touch rule: no class `@Transactional`, no `TrendingTopicsService` mock (no API key in tests gives `FALLBACK_TOPICS`, or `OpenAiTestServer`); assert `partnerTopics` absent.
- [ ] `CumsContractHarnessTest` -- rebind the `onlyFor` case to an internal spec that outlives 8b (`ems-internal` `/internal/events/{eventCode}/schedule`), no `ignoringMissingAuthorization`.
- [ ] PCS `S2sProviderContractIntegrationTest` -- delete the EMS-only public blocks, javadoc names only the RSVP contract.
- [ ] Frontend `partnerApi.ts` -- `listPartnerLogos()`: `GET /partners?include=company&size=200` → `Record<companyName, logoUrl>`.
- [ ] Frontend `blobTopicService.ts` -- `getSessionData` composes EMS session data ‖ `getTopics()` ‖ logos, then `classifyTopics` (unique titles), groups as the deleted Java did; degradation per matrix. `classifyTopics` lives here.
- [ ] Frontend tests -- `useTopicSessionData.test.tsx` (new, MSW `api.on`): grouping, "Unknown", logo/null, clusters, each failure row; `blobTopicService.test.ts` onto the MSW harness (ratchet line goes).
- [ ] Playwright `blob-topic-selector.spec.ts` -- assert a partner ghost blob with logo renders on BATbern57 (testid on ghost-partner node if missing).
- [ ] `bruno-tests/event-topics-api/35-classify-topics.bru` -- organizer 200 + order; comments in `docs { }`.
- [ ] Gates -- `check:s2s-client-urls -- --update` (0 lines), remove owner S2; `check:test-ratchet -- --update`; EMS store refreshes on the suite run; `ems-module-deps.py` `OVR`: `TopicSessionDataService`, `TopicSessionDataController` → `topics_ai` (canvas classes, misread by "Session").
- [ ] Docs -- module-map Gates (guard 0, EMS `*Partner*Client*` 2 owner 8b), ADR-016 L60/72/80, coding-standards (no `ignoringMissingAuthorization` binding left), system overview, doc-drift mappings.

**Acceptance Criteria:**
- Given local dev, when an organizer opens `/organizer/events/BATbern57/topic-blob`, then partner topics appear with logos and clusters as before, the browser calls PCS `/partners/topics`, `/partners?include=company…` and EMS `/topics/classifications`, and the EMS log shows no `/api/v1/partners`.
- Given the EMS suite, then store `98b035c9` holds 2 lines (only `PartnerMeetingRsvpClient*`); given the guard, 0 baseline lines.
- Given `rg ignoringMissingAuthorization services/`, then no match.
- Given the module scripts after, then before/after are recorded in Implementation Notes.
- Given `npm run lint:api` and `make check-frontend-api-paths` (17.3), then both pass, baseline still 97.

## Implementation Notes

## Plan Change Log

## Review Triage Log

## Design Notes

**Architecture note.** Modules: EMS topics (controller, `TopicSessionDataService`), EMS L0 client package (2 classes deleted), web-frontend services. Edge removed: EMS→PCS (canvas); new edges: browser→PCS (existing public reads, PARTNER/ORGANIZER and permitAll) and browser→EMS. New endpoint `POST /topics/classifications`; nearest `POST /events/{eventCode}/topic-similarity` cannot be extended: one topic per body and response, and changing a staying public op is epic 4. POST, not GET: up to hundreds of titles exceed the 8 KB header cap; 200, nothing is stored; `classifications` is a noun under `/topics`, no Spectral rule fires. Reused: `TopicSimilarityService`, MSW harness. Deviation: none.

**Epic 17 rule (C15).** Measured at `4c44601a`: s2s guard 2 → 0; EMS ArchUnit store 6 lines (`98b035c9` 4) → 4 (2); Spectral 97 → 97; ratchet `be-mock-in-process` 33 → 32, `be-transactional-it` 117 → 116, `fe-mock-services` 167 → 166; `ignoringMissingAuthorization` 2 → 0. Module scripts before: deps sizes platform 35, sessions 77, topics_ai 26; target ALLOWED 52, VIOLATING EDGES 28, refs 96, of which `sessions->topics_ai` 5 (`TopicSessionDataService`). Expected after: platform 33; with the `OVR` fix 27 edges, 91 refs (record both the raw and the `OVR` run).

## Verification

**Commands:**
- `set -o pipefail; timeout 3000 ./gradlew <task> --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/8-<name>.log`, one at a time: `:services:event-management-service:test`, then `:services:partner-coordination-service:test` -- expected: BUILD SUCCESSFUL, store `98b035c9` 2 lines
- `cd web-frontend && npm run lint:api && npm run check:api-types && npm run type-check && npm run lint && npm run check:s2s-client-urls && npm run check:test-ratchet` -- expected: exit 0, 97 entries, 0 guard lines
- `make check-frontend-api-paths` -- expected: exit 0
- `cd web-frontend && timeout 1800 npx vitest run --maxWorkers=4 2>&1 | tee ~/.cache/batbern-test-logs/8-vitest.log` -- expected: all pass (MSW examples floors still hold)
- From `services/event-management-service/src/main/java/ch/batbern/events`: `python3 -I "$ROOT"/scripts/architecture/ems-module-deps.py . <dir>/emsdeps.json` and `python3 -I "$ROOT"/scripts/architecture/ems-module-target.py <dir>` (ROOT = repo root) -- expected: numbers above
- `npx playwright test e2e/organizer/blob-topic-selector.spec.ts` against local dev -- expected: pass

**Manual checks:**
- Restart `event-management`; as organizer open the BATbern57 canvas: partner ghost blobs with logos (anonymized data: e.g. aity, sbb), clusters as before; DevTools shows the three calls; `/tmp/batbern-1-event-management.log` has no PCS call.
