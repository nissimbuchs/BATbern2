---
title: 'Event bus gone, domain events in process (17.2 part B)'
type: 'refactor'
ticket: '2b'
created: '2026-11-07'
status: 'done'
baseline_revision: '9786d3c9394116e7572dbdaa26189e5f5dd332f3'
route: 'full'
route_source: 'auto'
risk: 'high'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/ADR-014-backend-service-consolidation.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Every service publishes domain events to EventBridge, but nothing consumes them: the `batbern-staging` bus has no rules, and EMS publishes to `batbern-events`, a bus that does not exist. The publisher, its client, Resilience4j wiring, a bus stack, two cross-stack imports, IAM grants and an alarm that can never fire are maintained for nothing (#1220, bus part).

**Approach:** Keep `DomainEventPublisher` and its callers; replace the only implementation with one that hands the event to Spring's `ApplicationEventPublisher` (what the in-process listeners already rely on) and delete everything EventBridge: SDK dependency, config, publisher, local/test mocks, the bus stack, the CUMS/PCS bus props, env vars and grants, the EMS grant, the `PublishingFailures` alarm. An integration test per in-process listener proves each still sees its event through the real publisher. The orphaned `BATbern-staging-EventBus` stack is deleted after the deploy (owner-authorized).

## Boundaries & Constraints

**Always:** listeners receive exactly the events they received before, synchronously as before (`OrganizerNotificationListener` stays `@Async`); a failure inside the local publish keeps today's behaviour (logged, caller not failed) and is logged at ERROR; no class depends on `software.amazon.awssdk.services.eventbridge` (the SDK leaves every `build.gradle`, so the compiler is the guard) and no stack declares `AWS::Events::EventBus` (CDK test); the deploy is clean: CUMS and PCS drop their imports while the bus stack is no longer in the app.

**Never:** no change to the EMS auto-publish scheduler's `events:PutRule`/`PutTargets` on the default bus (`event-management-stack.ts:310-327`); no new messaging infrastructure; no change to which events are published or their payloads; no removal of the `DomainEventPublisher` interface (17.12 decides).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Event created | organizer creates an event via `POST /events` | `AutoEnrollmentListener` effect visible (enrolment rows) | none |
| Workflow transition | `EventWorkflowStateMachine` transitions an event | `EventWorkflowTransitionListener` effect (tasks) and `SessionQnaWindowListener` effect (Q&A window) | none |
| Notified event | an event `OrganizerNotificationListener` handles (e.g. speaker workflow state change) | its effect after the async hand-off (awaited in the test) | none |
| Null event | `publish(null)` | `IllegalArgumentException`, as today's `validateEvent` | thrown |
| Listener throws | a synchronous listener throws | caller completes; ERROR log names the event type | logged |

</frozen-after-approval>

## Code Map

- `shared-kernel/src/main/java/ch/batbern/shared/events/DomainEventPublisher.java` -- reduce to `void publish(DomainEvent<?> event)`; the other methods and the `PutEventsRequestEntry` leak have no caller (check with grep, tests included).
- `shared-kernel/.../events/EventBridgeEventPublisher.java` -- delete; new `SpringDomainEventPublisher` (`@Component`) keeps `validateEvent` and the local publish (`:80-90`).
- `shared-kernel/.../config/EventBridgeConfig.java` -- delete; its `awsCredentialsProvider` bean is used only by CUMS, which defines its own (`AwsConfig.java:34`); confirm EMS/PCS do not inject it. `eventBridgeObjectMapper` (`:80`): delete if unused.
- `shared-kernel/build.gradle:54-56`, EMS `build.gradle:47`, CUMS `build.gradle:22,26`, PCS `build.gradle:36` -- drop `awssdk:eventbridge`.
- `shared-kernel/src/main/resources/application-shared.yml:5-20` -- drop the unused bus key and the `eventBridgePublisher` resilience4j instances. `DateTimeUtils.formatForEventBridge` -- delete if unused outside tests.
- Wiring: EMS/CUMS/PCS `LocalAwsConfig` (mock EventBridge client + mapper), CUMS `AwsConfig:19` comment, CUMS/PCS `application.yml` `EVENT_BUS_NAME` keys.
- Tests: shared-kernel `TestEventBridgeConfig`, `EventBridgePublisherTest` (replace with a `SpringDomainEventPublisher` unit test), `EventCreatedEventTest:56`, `src/test/resources/application-test.yml:7`; EMS `TestAwsConfig:44,58`, `SpeakerWorkflowServiceTest`; CUMS `TestAwsConfig`, `LocalAwsConfigTest`, `EventPublishingIntegrationTest`, `JITUserProvisioningInterceptorTest`; PCS `TestAwsConfig`.
- Listeners (EMS): `listener/AutoEnrollmentListener.java:31` (published `EventController:1035,1050`; only a Mockito test today); `listener/EventWorkflowTransitionListener.java:44` (`service/EventTaskServiceIntegrationTest.java:100-121` uses the real publisher with a mocked client: switch it); `listener/SessionQnaWindowListener.java:32`; `notification/OrganizerNotificationListener.java:47` (`@Async`, all `DomainEvent`s). `mailcenter/AutomaticTriggerAudienceGuardTest.java:43-71` lists listener methods: keep in sync if any change.
- CDK: `lib/stacks/event-bus-stack.ts` delete; `bin/batbern-infrastructure.ts:10,162-167,371,383,422,435`; `partner-coordination-stack.ts:23,51-53,132-141`; `company-management-stack.ts:28,79-81,182-190`; `constructs/domain-service-construct.ts:180-190` (EMS grant on `batbern-events`); `constructs/ecs-service-alarms.ts:230-249` + `eventBridgePublishingFailures` thresholds in the service stacks; `lambda/github-issues-integration/index.ts:574,591`; `package.json:33` (layer1 list); `deploy-staging.yml:632` (echo).
- Env/config: `docker-compose.yml:112-113,166-167,252-253` (left after part A), `.env.example:45-46`, `config/templates/backend.env.template:51-53`, `scripts/dev/start-all-native.sh:~286-288`.
- Docs: runtime claims about EventBridge / the bus in `CLAUDE.md`, `docs/architecture/{01,02,06,source-tree}.md`, ADR-014 operational-state section (add the bus).

## Tasks & Acceptance

**Execution:**
- [x] Shared kernel: new publisher + unit test (matrix rows Null event, Listener throws); delete EventBridge classes, config, deps, yml keys.
- [x] Services: wiring, application.yml, LocalAwsConfig/TestAwsConfig, affected tests compile and pass.
- [x] EMS: one integration test per listener (matrix rows 1-3) through the real publisher, asserting the effect, not the call; no `@MockitoBean DomainEventPublisher` in them.
- [x] CDK: as mapped; new unit test: synthesizing the staging app declares no `AWS::Events::EventBus` and no `events:PutEvents` grant; existing tests updated.
- [x] Env/config, scripts, docs as mapped.

**Acceptance Criteria:**
- Given the repo, when grepping `eventbridge|EventBridge|PutEvents|EVENT_BUS` outside history docs, then only the EMS auto-publish scheduler (default bus) and history remain, each listed in Implementation Notes.
- Given `cdk synth --context environment=staging`, then no EventBus stack exists, and CUMS/PCS templates lose only the bus import, env var and grant; the EMS template loses only the `batbern-events` grant and the alarm.

## Design Notes

Architecture note: modules: shared-kernel events/config, EMS/CUMS/PCS AWS config, infrastructure (CDK, alarms, lambda) · new dependencies: none · endpoints: none · reused: `DomainEventPublisher`, Spring `ApplicationEventPublisher` · deviations: none.

Today's `publish` swallows a local-listener exception (`:80-89`). Keeping that avoids changing caller behaviour in this refactor; logging moves to ERROR so it is not silent. Whether listener failures should fail the caller is a 17.12 question.

## Verification

Rack rules for every run, including any implementation subagent: one heavy job at a time; Gradle `--max-workers=4`, and `./gradlew --stop` before Node work; Vitest `--maxWorkers=4`; logs to `~/.cache/batbern-test-logs/` (never `/tmp`); before each run check `grep anon /sys/fs/cgroup/dev.slice/memory.stat` and stop above 12 GB. Run the service suites one after another, never in parallel.

**Commands:**
- `set -o pipefail; ./gradlew --max-workers=4 :shared-kernel:build publishToMavenLocal 2>&1 | tee ~/.cache/batbern-test-logs/2b-sk.log` -- expected: BUILD SUCCESSFUL.
- `set -o pipefail; ./gradlew --max-workers=4 :services:event-management-service:test 2>&1 | tee ~/.cache/batbern-test-logs/2b-ems.log` -- expected: BUILD SUCCESSFUL (then the same for CUMS, then PCS, then `:api-gateway:test`).
- `./gradlew --stop; cd infrastructure && npx jest --maxWorkers=4 2>&1 | tee ~/.cache/batbern-test-logs/2b-cdk.log` -- expected: all pass.
- `cd infrastructure && npx cdk synth --context environment=staging -q && npx cdk list --context environment=staging` -- expected: no EventBus stack.
- `cd web-frontend && npm run check:test-ratchet` -- expected: exit 0 (`--update` only for falling counts, with the reason).
- `make dev-native-up-instance BASE_PORT=9000`; create a test event via the gateway as organizer; check the EMS log shows the listeners ran and no EventBridge error; `make dev-native-down-instance BASE_PORT=9000`.

## Implementation Notes

Plan approved by the coordinator under the owner's standing go for autonomous overnight work (2026-11-07: "work as autonomously as possible"); no open questions.

**Done 2026-11-07 (implementation):**

- Shared kernel: `DomainEventPublisher` reduced to `publish`; `SpringDomainEventPublisher` (validate,
  `ApplicationEventPublisher.publishEvent`, listener exception logged at ERROR with the event type and
  swallowed). Deleted `EventBridgeEventPublisher`, `EventBridgeConfig`, `TestEventBridgeConfig`,
  `TestResilience4jConfig`, `EventBridgePublisherTest`, `DateTimeUtils.formatForEventBridge`, the
  `aws.eventbridge` / `resilience4j` yml keys, and the `eventbridge`, Resilience4j, Guava and
  `testcontainers-localstack` dependencies (all used only by the bus code; checked by grep).
- **Deviation, found on the way:** `eventBridgeObjectMapper` was not unused. It was the only Jackson
  `ObjectMapper` bean, so Spring Boot's Jackson 2 auto-config backed off and it has been the HTTP
  mapper of EMS, CUMS and PCS (prod API JSON is indented: checked `GET api.batbern.ch/api/v1/events`;
  `spring.jackson2.*` in CUMS has no effect). Deleting it would have changed every API payload. It
  moved unchanged to `shared-kernel/.../config/SharedObjectMapperConfig` (all profiles,
  `@ConditionalOnMissingBean`), pinned by `SharedObjectMapperConfigTest`. Whether to drop the
  indentation / honour `spring.jackson2.*` is an owner decision, not part of this story.
- EMS `DomainEventListenersIntegrationTest`: AutoEnrollment (POST /events → confirmed registrations),
  EventWorkflowTransition (pending → todo), SessionQnaWindow (window opened), OrganizerNotification
  (async notification awaited). Not `@Transactional`, cleans up. Mutation check: with
  `publishEvent` disabled all four fail. It stubs the context-wide `TestUserApiClientConfig` mock for
  the stakeholder lists (an own-code S2S mock, commented; no `@MockitoBean`, so the ratchet does not
  count it) because that `@Primary` mock is component-scanned into every EMS IT context; 17.0b's
  harness moves it to the wire.
- CUMS `EventPublishingIntegrationTest` now asserts the events with `@RecordApplicationEvents`.
- CDK test `test/unit/no-event-bus.test.ts` synthesizes `bin/batbern-infrastructure.ts` for staging
  (website asset replaced inline, because `web-frontend/dist` exists only after a frontend build).

**Grep `eventbridge|EventBridge|PutEvents|EVENT_BUS` outside history docs, what remains (AC 1):**

- EMS auto-publish scheduler: `infrastructure/lib/stacks/event-management-stack.ts:309` (default-bus
  rule grants), `web-frontend/src/services/publishingService/publishingService.ts:74` and
  `web-frontend/src/types/event.types.ts:529` (rule ARN).
- Code and tests that name the removal on purpose: `shared-kernel/.../config/SharedObjectMapperConfig.java:14`
  (Javadoc: where the mapper came from, the former `eventBridgeObjectMapper`),
  `shared-kernel/.../unit/config/SharedObjectMapperConfigTest.java:15` (Javadoc: same history),
  `infrastructure/test/unit/no-event-bus.test.ts` (the guard itself asserts absence of
  `AWS::Events::EventBus` / `events:PutEvents`), `infrastructure/CLAUDE.md:189` (note that there is
  no EventBus stack any more).
- ADR-016 (context section: the bus had 0 rules, now stated as removed by 17.2 part B, past tense).
- Docs that record the removal: ADR-014 (decision + operational state), ADR-016, `01-system-overview`,
  `03-data-architecture`, `06-backend-architecture`, `06b-user-lifecycle-sync`, `runbooks.md:92`,
  `shared-kernel/CHANGELOG.md`.
- History: `docs/deployment/platform-stability-deployment-guide.md` and
  `docs/assessment/system-quality-assessment.md` (both now carry a superseded note),
  `web-frontend/e2e/.../company-creation.spec.ts:26` (comment on deleted tests), and the
  `docs/plans`, `docs/prd`, `docs/stories`, `docs/qa`, `docs/presentation`, `docs/trainings`,
  `docs/wireframes`, `_bmad-output` archives.


## Plan Change Log

## Review Triage Log

Pass 1 (quick lens), 2026-11-07: high 0, medium 1, low 4, false 1. Patched 3, deferred 1, rejected 1.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | Listener IT tearDown asserts before cleanup and stub reset; late async rows | medium | patch | Stubs reset first, cleanup in `finally`, quiet-wait without assertion; 4/4 pass. |
| 2 | IT stubs `UserApiClient` (own Java client) | false | rejected | coding-standards "Test fidelity rules": until 17.0b a test that cannot avoid an own-code mock says why in a comment; the class Javadoc does. |
| 3 | Remaining-grep list incomplete; ADR-016:20 present tense | low | patch | List completed; ADR-016 now past tense. |
| 4 | `infrastructure/CLAUDE.md` stack numbering gap 8 → 10 | low | defer | Agent-context file; deferred-work.md. |
| 5 | Speaker-workflow event not exercised through the real publisher | low | rejected | Same `publish` path and the listener takes `DomainEvent<?>`; the matrix row says "e.g."; the tested transition event proves the hand-off. |

## Production verification

2026-11-10, owner go: orphaned stacks `BATbern-staging-SpeakerCoordination`, `-AttendeeExperience` and `-EventBus` deleted (no stack imported their exports; the bus had no rules or archives), and the two retained stub ECR repositories deleted. Checked live afterwards: `aws ecs list-services` lists the gateway, EMS, CUMS and PCS only; `aws events list-event-buses` lists `default` only; no leftover log group, alarm or Cloud Map service. The CDK guard `infrastructure/test/unit/no-event-bus.test.ts` now also fails on a stub stack or repository.
