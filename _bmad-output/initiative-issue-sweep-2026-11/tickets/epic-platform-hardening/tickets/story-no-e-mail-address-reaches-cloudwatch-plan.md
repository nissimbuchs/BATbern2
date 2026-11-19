---
title: 'No e-mail address reaches CloudWatch from any service'
type: 'bugfix'
ticket: '3'
created: '2026-11-19'
status: 'approved'
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
