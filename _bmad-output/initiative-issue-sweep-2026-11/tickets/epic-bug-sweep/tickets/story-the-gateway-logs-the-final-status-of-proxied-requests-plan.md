---
title: 'The gateway logs the final status of proxied requests'
type: 'bugfix'
ticket: '10'
created: '2026-11-18'
status: 'in-progress'
baseline_revision: 'bf3819ad34ca60e6d88798e06328647675ba9188'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: ''
review_source: ''
lenses_ran: []
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** (#1313) `ClientErrorMetricsFilter` records `GATEWAY_API_REQUEST status=… clientError=…` right after `chain.doFilter` returns. `ProxyController.proxyRequest` returns a `CompletableFuture`, so for every proxied request that is the start of async processing and the status is still 200; the async dispatch is skipped by `OncePerRequestFilter`. Production, 7 days: 56,808 `status=200`, 17 correct 404s (gateway-local only), while 11,712 downstream 4xx were logged elsewhere. The 4xx-ratio alarm (`infrastructure/lib/constructs/alb-alarms.ts`) is blind to downstream errors.

**Approach:** Owner chose option 1: when `request.isAsyncStarted()`, register an `AsyncListener` and record in `onComplete` (and `onError`/`onTimeout` with the final status); synchronous requests record as now. Check what the alarm will do with the real numbers before it ships.

## Boundaries & Constraints

**Always:** Exactly one `GATEWAY_API_REQUEST` line per request (sync or async), with the final status, method, path pattern and `clientError` as today; the metric filter pattern in `alb-alarms.ts` still matches (CDK test). Integration test proxies to a stub answering 404 and finds `status=404 clientError=true` (red before the fix), plus a 200 and a 5xx case, plus a gateway-local 404 (sync) recorded once.

**Never:** Make `ProxyController` synchronous. Change other filters.

</frozen-after-approval>

## Code Map

- `api-gateway/src/main/java/ch/batbern/gateway/observability/ClientErrorMetricsFilter.java` -- `record()` after `chain.doFilter`.
- `api-gateway/.../ProxyController` (`proxyRequest` returns `CompletableFuture<ResponseEntity<byte[]>>`).
- Existing gateway ITs (look for a test that boots the gateway with a stub downstream: MockRestServiceServer/WireMock/MockWebServer) and any existing `ClientErrorMetricsFilter` test.
- `infrastructure/lib/constructs/alb-alarms.ts:~176-300`: `ApiRequests` / `ApiClientErrors` metric filters, alarm `batbern-${env}-api-4xx-ratio` (`IF(requests >= apiClientErrorMinRequests, errors/requests*100, 0)`, threshold `apiClientErrorPercent`, 3 of 3 periods); thresholds in `lib/config/staging-config.ts`; CDK test for it.
- Alarm impact: with the fix, downstream 4xx (incl. 401/403/404 of normal use) start counting. Run a read-only CloudWatch Logs Insights query on `/aws/ecs/BATbern-staging/api-gateway` (`AWS_PROFILE=batbern-staging`, last 7 days) for the "Downstream service … returned error: 4xx" lines per 5-minute period vs `GATEWAY_API_REQUEST` lines, broken down by status and by whether the request was authenticated as the alarm defines it, and compute how often the ratio would have exceeded the threshold for 3 periods. If it would fire on normal traffic, adjust what counts (e.g. exclude statuses that are not errors for this alarm, as the alarm's own comment intends) or the threshold, with the numbers in the plan's Implementation Notes and the alarm doc updated (`docs/architecture/08-operations-security.md` alarm table).

## Tasks & Acceptance

**Execution:**
- [ ] RED: gateway IT, stub 404 → `status=404 clientError=true`, one line per request
- [ ] filter fix
- [ ] alarm impact query and, if needed, alarm adjustment with CDK test
- [ ] doc 08 alarm row and 06 observability if it describes the line

**Acceptance Criteria:**
- Given the fix, when last week's production traffic is replayed through the alarm rule (the query above), then the alarm would not fire on normal traffic, or the plan records why it should.

## Design Notes

Architecture note: api-gateway observability filter, infrastructure alarm (possibly). No new dependency or endpoint. Deviations: none.

## Verification

**Commands:**
- `./gradlew --max-workers=4 :api-gateway:test` + checkstyle, tee'd -- green.
- `cd infrastructure && npx jest test/unit` (or the alarm tests) -- green.

**Rack rules:** one heavy build at a time, Gradle `--max-workers=4`, output via `set -o pipefail; ... | tee ~/.cache/batbern-test-logs/<name>.log`. AWS: read-only queries only, never change anything in the account. Do not commit or push. A bug found on the way is fixed too and reported (initiative C9).
