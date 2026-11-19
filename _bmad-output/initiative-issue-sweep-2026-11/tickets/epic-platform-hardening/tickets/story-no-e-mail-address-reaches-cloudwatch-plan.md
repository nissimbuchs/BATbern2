---
title: 'No e-mail address reaches CloudWatch from any service'
type: 'bugfix'
ticket: '3'
created: '2026-11-19'
status: 'built'
route: 'full'
risk: 'high'
review: 'quick'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** (#1202, R12) Addresses reach CloudWatch through text: exception messages, stack traces, framework lines. Masking exists only per call site (`LoggingUtils.maskEmail`, `maskEmailsIn`).

**Approach:** Mask once in the encoder of every service, with one masking rule in shared-kernel (`LoggingUtils.maskEmailsIn`, idempotent on `a***@x.ch`).
- JSON services (api-gateway, CUMS: `logback-spring.xml` with `LogstashEncoder`): a `net.logstash.logback.mask.ValueMasker` in shared-kernel registered through `MaskingJsonGeneratorDecorator`, so every string value (message, arguments, `stack_trace`, MDC) is masked. Field names untouched.
- Plain-text services (EMS, PCS: Spring Boot default console pattern, no logback-spring.xml): a `logback-spring.xml` that includes Spring Boot's `defaults.xml` and `console-appender.xml` and overrides the message and throwable conversion words with masking converters from shared-kernel, so the rendered line keeps today's shape (CloudWatch queries and the ecs-service-alarms metric filters match literal markers inside the message).
- PCS already depends on logstash-logback-encoder; keep EMS and PCS plain text (no format change beyond masking).

## Boundaries & Constraints

**Always:** A test per service (gateway, EMS, CUMS, PCS) that loads that service's real `logback-spring.xml` (logback `JoranConfigurator` on the classpath resource, or a Spring Boot test with an `OutputCaptureExtension`), logs an address as message text, as an argument and inside an exception message with a stack trace, and asserts the captured output contains the masked form and not the clear address. A test that renders the `GATEWAY_API_REQUEST` line through the gateway's real config and matches it against the CDK metric filter terms (`GATEWAY_API_REQUEST`, `@timestamp`, and the clientError pattern) and renders the `DOMAIN_EVENT_LISTENER_FAILURE_MARKER` and `SCHEDULED_JOB_FAILURE_MARKER` lines through EMS's config and finds the literal marker. Shared converters and masker unit-tested in shared-kernel (idempotent, `%40`, multiple addresses, null). Doc: `docs/architecture/` section describing log masking (find it via `.github/doc-drift-mappings.yml` and grep `maskEmail`) updated.

**Never:** Change log levels, the JSON field names, the plain-text pattern beyond masking, or a metric filter. Mock logback.

</frozen-after-approval>

## Code Map

- `shared-kernel/src/main/java/ch/batbern/shared/utils/LoggingUtils.java` (`maskEmailsIn`, `ADDRESS_IN_TEXT`).
- `api-gateway/src/main/resources/logback-spring.xml`, `services/company-user-management-service/src/main/resources/logback-spring.xml` (LogstashEncoder).
- EMS and PCS: no logback config; Spring Boot 4 defaults (`org/springframework/boot/logging/logback/defaults.xml`, `console-appender.xml`; check the property names CONSOLE_LOG_PATTERN and LOG_EXCEPTION_CONVERSION_WORD in the SB 4.0.9 jar).
- `api-gateway/src/test/resources/logback-test.xml` exists: tests must load the production file explicitly, not this one.
- Metric filters: `infrastructure/lib/constructs/alb-alarms.ts:236-260`, `ecs-service-alarms.ts:205-320` (markers `DOMAIN_EVENT_LISTENER_FAILURE_MARKER`, `SCHEDULED_JOB_FAILURE_MARKER`, OOM `exit code 137` comes from ECS, not the app).

## Verification

- `./gradlew --max-workers=4 :shared-kernel:test :shared-kernel:publishToMavenLocal` then each service's `test checkstyleMain checkstyleTest`, one at a time, tee'd to `~/.cache/batbern-test-logs/`.
- After deploy (owner session): Logs Insights over the first hour for address shapes in all four groups finds none; each metric filter still counts.

## Implementation Notes

Built 2026-11-19, not committed, not deployed.

- [x] One rule, in shared-kernel: `LoggingUtils.maskEmailsIn`. New package `ch.batbern.shared.logging`:
  `EmailValueMasker` (logstash `ValueMasker`), `EmailMaskingMessageConverter` (`%m`),
  `EmailMaskingThrowableConverter` (Spring Boot's `%wEx` subclass), and the include
  `ch/batbern/shared/logging/email-masking-conversion-rules.xml` (rules for `m`, `msg`, `message`, `wEx`).
- [x] Gateway, CUMS: `MaskingJsonGeneratorDecorator` + `EmailValueMasker` in the `LogstashEncoder`.
  The element is `<decorator>`, not `<jsonGeneratorDecorator>` (logstash 9.0 has `addDecorator`; the
  hinted name is ignored with a WARN, which the test harness turns into a failure).
- [x] EMS, PCS: new `logback-spring.xml` = `defaults.xml` + the masking include + `console-appender.xml`
  + root INFO on CONSOLE, i.e. Spring Boot's programmatic default plus masking. No level, pattern or
  field change; no `<logger>` element.
- [x] Tests per service, `LogEncoderMaskingTest` (package `security`), on the real `logback-spring.xml`
  via the new test fixture `LogbackConfigUnderTest` (fresh `LoggerContext`, `JoranConfigurator`, console
  appenders redirected to a buffer; any WARN while loading or ERROR while appending fails; the
  `<springProperty>` values are passed as context properties; the gateway test asserts the source is
  `.../main/logback-spring.xml`, not `logback-test.xml`). RED first with clear addresses in the output
  (EMS/PCS RED ran against a `logback-spring.xml` without the include), then GREEN.
- [x] Metric filter terms: gateway `GATEWAY_API_REQUEST`, `@timestamp`, `clientError=true`; EMS
  `DOMAIN_EVENT_LISTENER_FAILED` and `SCHEDULED_JOB_FAILED`; PCS `DOMAIN_EVENT_LISTENER_FAILED`.
  The lines are rendered with the production call shapes and constants, not by the production classes
  (their static loggers belong to the global context).
- [x] shared-kernel unit tests: `EmailMaskingConvertersTest`, `EmailValueMaskerTest`, two new
  `LoggingUtilsTest` cases (IDN, idempotent incl. `%40`).
- [x] Doc: `docs/architecture/08-operations-security.md` "E-mail addresses in logs are masked";
  `.github/doc-drift-mappings.yml` maps `shared-kernel/.../shared/logging/` to it.

**Deviations**
- logstash-logback-encoder is `implementation` in shared-kernel, not `compileOnly`: EMS's
  `ScheduledZoneGuardTest` loads every `ch.batbern.shared` class and failed with
  `NoClassDefFoundError: net/logstash/logback/mask/ValueMasker`. Every shared-kernel class now loads in
  every service; EMS gets the encoder jar at runtime (Jackson 3 was already there).
- Bug found and fixed test-first: `maskEmailsIn` matched ASCII only, so IDN addresses
  (`x@müller.ch`, which CUMS stores) stayed in clear text. The pattern now uses `\p{L}\p{N}`.

**Review round 1 (2026-11-19), all test-first**
- HIGH, PCS stack traces unmasked in production: PCS's base `application.yml` sets
  `logging.pattern.console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"`. Without a throwable word logback's
  `EnsureExceptionHandling` appends a plain `ThrowableProxyConverter`, which does not mask. The first
  PCS test loaded the file without Spring's properties and so tested a pattern PCS never runs.
  Deviation: PCS's pattern is now `%d{yyyy-MM-dd HH:mm:ss} - %msg%n%wEx` (message line unchanged;
  the stack trace is rendered by the masking `%wEx`, which adds Spring Boot's blank line before it and
  the jar names per frame). `LogbackConfigUnderTest.springLoggingProperties(Path)` reads
  `logging.pattern.*` and `logging.exception-conversion-word` from the service's real
  `application.properties` and `application.yml` (base + `staging` document) and all four service
  tests pass them. The harness now fails when a pattern layout's converter chain holds a message or
  throwable converter that is not the masking one. Red: PCS printed the clear exception address.
  Checked the other services: gateway and CUMS set `logging.pattern.console` too, but render JSON
  (the pattern is unused); EMS sets none.
- MEDIUM, quadratic regex: `ADDRESS_IN_TEXT` starts only where no address character precedes
  (lookbehind), bounds the local part ({0,63} after the first character), each label (63) and the
  label count (at most 10 after the first). Red: a 16 KB token + '@' timed out at 200 ms; the first
  fix attempt then threw `StackOverflowError` on `a@b.b.b…` (8 K labels, unbounded group
  repetition), hence the label count bound. Test covers a 16 KB run, a `%20x`-encoded run, a 16 KB
  label and 8 K labels within 200 ms. `%22anna@x.ch` stays masked.
- LOW, NFD umlauts: `\p{M}` in the local and domain classes; test with U+0308.
- LOW, harness tolerated any status mentioning `springProfile`: now only the exact
  `Ignoring unknown property [springProperty|springProfile] in [...LoggerContext]` status; test
  with an appender class named `...springProfile` that does not exist.
- Not mine, seen while running: `:shared-kernel:checkstyleTestFixtures` fails on
  `LogAddressGuard.java:68` (169 characters), the coordinator's separate change.
- Round 1 ran targeted tests only (`e2-3-r2-*.log`); the full-suite counts below are from before it.

**Verification (logs in `~/.cache/batbern-test-logs/e2-3-*.log`)**
- shared-kernel test + checkstyle: 701 tests, 0 failed. Gateway: 351 (26 skipped), 0 failed.
  CUMS: 991 (16 skipped), 0 failed. EMS: 3059 (6 skipped), 0 failed. PCS: 293, 0 failed. All rc=0.
- In the full suites' captured output every address in a CUMS JSON `message` (404) and in an EMS
  log line (1808) was masked, none clear.
- Open (owner session, after deploy): Logs Insights over the first hour in all four groups; each
  metric filter still counts.
