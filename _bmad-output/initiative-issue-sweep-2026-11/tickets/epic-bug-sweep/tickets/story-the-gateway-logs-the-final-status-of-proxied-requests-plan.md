---
title: 'The gateway logs the final status of proxied requests'
type: 'bugfix'
ticket: '10'
created: '2026-11-18'
status: 'done'
baseline_revision: '7fd75d42ebce45b13b7850eabe5fc4795b30807a'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
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
- [x] RED: gateway IT, stub 404 → `status=404 clientError=true`, one line per request
- [x] filter fix
- [x] alarm impact query and, if needed, alarm adjustment with CDK test
- [x] doc 08 alarm row and 06 observability if it describes the line

**Acceptance Criteria:**
- Given the fix, when last week's production traffic is replayed through the alarm rule (the query above), then the alarm would not fire on normal traffic, or the plan records why it should.

## Design Notes

Architecture note: api-gateway observability filter, infrastructure alarm (possibly). No new dependency or endpoint. Deviations: none.

## Verification

**Commands:**
- `./gradlew --max-workers=4 :api-gateway:test` + checkstyle, tee'd -- green.
- `cd infrastructure && npx jest test/unit` (or the alarm tests) -- green.

**Rack rules:** one heavy build at a time, Gradle `--max-workers=4`, output via `set -o pipefail; ... | tee ~/.cache/batbern-test-logs/<name>.log`. AWS: read-only queries only, never change anything in the account. Do not commit or push. A bug found on the way is fixed too and reported (initiative C9).

## Implementation Notes

- **RED, verified.** `ClientErrorMetricsFilterIntegrationTest` boots the gateway on a real Tomcat
  (RANDOM_PORT; MockMvc does not run the container's async completion) and proxies to a JDK
  `HttpServer` stub. Before the fix: stub 404 and stub 500 both logged `status=200
  clientError=false`; 200 and the gateway-local 404 were already right. After: all four green,
  one line each. New unit test `should_recordFinalStatus_when_asyncRequestCompletes` was red against
  the old filter; `should_recordOnce_when_asyncRequestTimesOutOrFailsBeforeCompleting` guards
  against a double line and passes either way.
- **Deviation from the approach text, deliberate.** The line is written only in `onComplete`, not
  also in `onError`/`onTimeout`: the servlet spec calls `onComplete` after both, and only there is
  the status final (at `onTimeout` Spring's 503 has not been set yet). Recording in all three would
  break "exactly one line per request". `onStartAsync` re-registers the listener for a nested
  async cycle.
- **Alarm replay** (read-only Logs Insights, `/aws/ecs/BATbern-staging/api-gateway`, 2026-11-12
  00:10 to 2026-11-18 21:30 UTC, JSON rendering only, run 2026-11-18):
  - denominator `GATEWAY_API_REQUEST` lines: 36,181 (200: 36,159; 404: 22). Unchanged by the fix.
  - `Downstream service … returned error`: 400 3,881 · 401 186 · 403 142 · 404 4,179 · 405 20 ·
    409 375 · 415 46 · 422 40 · 429 821 · 500 3.
  - The downstream line runs on the async thread without MDC, so it cannot be split by
    authentication. Used an upper bound instead: gateway-local 4xx + all downstream 4xx of the
    window, capped at the window's authenticated requests. Completeness checked: `Received response
    from` (the non-exception path) logged only 200/201/202/204, so no proxied 4xx escapes the count.
  - 169 windows with >= 10 requests. Highest ratio 39.2% (2026-11-12 23:00, 29 of 74), highest value
    held for 3 consecutive windows 28.6%, threshold 50%: **0 breaching windows, 0 alarm episodes**.
    Week total under the bound 2.8%.
  - So no threshold or status-exclusion change; `alb-alarms.ts` untouched, `platform-alarms.test.ts`
    green (18/18).
- Docs: 08 alarm row + new paragraph "It sees the status the client got" with the replay; 06 error
  section notes how the access line gets the passed-through status.
- Side observation, not investigated: 821 downstream 429s in the week (source not checked).

## Review Triage Log

Quick review, 5 findings, all patched.
- medium, patched: listener registration was outside the swallow; guarded (warn + record synchronously), unit test with a throwing `addListener`.
- medium, patched: the IT never exercised the active-user gate production runs; gate on, stub answers `/internal/users/{id}` as an active user, test asserts no fail-open warning and that the gate was called.
- low, patched: log lines read under the appender's lock.
- low, patched: nested `startAsync` test (red with the re-registration removed).
- low, patched: timeout/error test uses 408 and fails on the old filter.
Alarm replay (implementer, read-only Logs Insights, 2026-11-12 to 11-18): 36,181 requests, 9,690 downstream 4xx; worst 3-window level 28.6% vs threshold 50%, so 0 alarms. Thresholds unchanged.
Verification (orchestrator): `:api-gateway:test` 348 tests, 0 failures; checkstyle clean.
