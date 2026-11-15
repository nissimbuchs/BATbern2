# Plan: Spring Actuator Honeypot

**Status:** Deferred — implementation not started
**Reason for deferral:** Risk of forgetting the trap exists in production; revisit when monitoring/alerting story is active.

---

## Why This is Convincing

The real `/actuator/health` endpoint already exists and is public (required for ECS health checks). Any attacker or automated scanner targeting a Spring Boot application WILL probe the sibling paths: `/actuator/env`, `/actuator/heapdump`, `/actuator/configprops`. The existence of the real health endpoint is itself the bait — it signals "Spring Boot app".

---

## What It Does

Three fake actuator endpoints serve realistic-looking but entirely synthetic responses. When any of them is accessed:

1. A structured alert email is sent to the organizer (via AWS SES)
2. A CloudWatch audit event is logged at CRITICAL severity
3. The response contains a secondary trap — a fake HATEOAS link that, if followed, fires a second alert

**No real data is ever exposed.** All response content is fabricated.

---

## Endpoint Design

### Primary traps

| Path | Response | Notes |
|------|----------|-------|
| `GET /actuator/env` | 200 + fake Spring env JSON | Includes masked fake credentials + secondary link |
| `GET /actuator/heapdump` | 200 + fake HPROF binary header | MIME: `application/octet-stream` |
| `GET /actuator/configprops` | 200 + fake config properties JSON | Includes fake datasource config |

### Fake `/actuator/env` response (primary trap)

```json
{
  "activeProfiles": ["prod"],
  "propertySources": [
    {
      "name": "systemEnvironment",
      "properties": {
        "SPRING_DATASOURCE_URL":      {"value": "jdbc:postgresql://rds-batbern-prod.eu-central-1.rds.amazonaws.com:5432/batbern"},
        "SPRING_DATASOURCE_PASSWORD": {"value": "******"},
        "JWT_SIGNING_SECRET":         {"value": "******"},
        "COGNITO_CLIENT_SECRET":      {"value": "******"},
        "AWS_REGION":                 {"value": "eu-central-1"},
        "_export": {"href": "/actuator/export?token=${COMPAT_ACTUATOR_EXPORT_TOKEN}"}
      }
    }
  ]
}
```

The `_export.href` field is natural-looking (Spring HATEOAS-style link). If the attacker follows it:

### Secondary trap

`GET /actuator/export?token=<token>` → fires second alert, returns fake CSV log dump.

---

## Files to Create

**New package:** `api-gateway/src/main/java/ch/batbern/gateway/compat/`

| File | Purpose |
|------|---------|
| `LegacyActuatorCompatibilityController.java` | Serves all trap endpoints + secondary trap |
| `CompatibilityAlertService.java` | Captures request details, sends alert via `ObjectProvider<EmailService>` |
| `CompatibilityProperties.java` | `@ConfigurationProperties("compat.actuator")` — enabled, alertEmail, exportToken |

**Test files:**

| File | What it verifies |
|------|-----------------|
| `LegacyActuatorCompatibilityControllerTest.java` | Response structure, alert fired, no real credentials in response |
| `CompatibilityAlertServiceTest.java` | Email content, IP extraction (X-Forwarded-For), disabled/blank-email guards |

## Files to Modify

| File | Change |
|------|--------|
| `api-gateway/src/main/java/ch/batbern/gateway/config/SecurityConfig.java` | Add trap paths to `permitAll()` (this IS the fake vulnerability) |
| `api-gateway/src/main/resources/application.yml` | Add `compat.actuator.*` properties (all disabled by default) |
| `api-gateway/src/main/java/ch/batbern/gateway/ApiGatewayApplication.java` | Add `ch.batbern.shared.service` to `scanBasePackages` so `EmailService` is available |

---

## OPSEC: Hiding from GitHub Readers

All sensitive configuration is **injected at deploy-time via AWS Parameter Store** — never committed:

```yaml
# application.yml — committed to repo (all disabled by default)
compat:
  actuator:
    enabled: ${COMPAT_ACTUATOR_ENABLED:false}
    alert-email: ${COMPAT_ACTUATOR_ALERT_EMAIL:}
    export-token: ${COMPAT_ACTUATOR_EXPORT_TOKEN:}
```

In production, set these in ECS task environment variables (sourced from Parameter Store). An attacker reading the repo sees that a mechanism exists — but not which paths are active or what the token is.

**Class naming is deliberately innocuous:**
- Class: `LegacyActuatorCompatibilityController` (not `HoneypotController`)
- Package: `ch.batbern.gateway.compat` (not `honeypot`)
- Alert service: `CompatibilityAlertService`
- Log tag: `COMPAT_PROBE` (not `HONEYPOT_TRIGGERED`)

---

## Known Implementation Challenge

`EmailService` lives in `ch.batbern.shared.service` — outside the api-gateway's default component scan (`ch.batbern.gateway`). Solution:
- Add `scanBasePackages = {"ch.batbern.gateway", "ch.batbern.shared.service"}` to `ApiGatewayApplication`
- Use `ObjectProvider<EmailService>` in `CompatibilityAlertService` for graceful fallback (logs alert if email service unavailable in context)

---

## Alert Email Content

**Subject:** `[BATbern Security] Compatibility probe detected: /actuator/env`

**Body includes:**
- Timestamp (UTC)
- Endpoint probed
- Client IP (X-Forwarded-For chain → RemoteAddr fallback)
- User-Agent (sanitized, max 512 chars)
- Whether `Authorization` header was present
- Whether secondary trap token was used (second trigger)
- Reassurance footer: "No real data was exposed."

---

## Verification Steps (when implementing)

```bash
# Hit primary trap (no auth required — the fake vulnerability)
curl -v http://localhost:8000/actuator/env
# → 200, fake JSON, check alert fired in log

# Hit secondary trap (follow the embedded link)
curl -v "http://localhost:8000/actuator/export?token=<configured-token>"
# → 200, fake CSV, check second alert in log

# Confirm real actuator still works
curl http://localhost:8000/actuator/health
# → {"status":"UP"}

# Check alert email captured in local dev
grep -i "COMPAT_PROBE" /tmp/batbern-1-api-gateway.log
```
