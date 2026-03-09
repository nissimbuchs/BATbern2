# Fix Summary — User guide — speaker portal
**Fixed:** 2026-03-09

## Changes made

_None. The audit found zero mismatches and zero undocumented behaviours. All doc files are internally consistent and no test assertions contradicted existing claims._

## Skipped — needs manual decision (UNTESTED)

All 22 items below are test gaps — the documentation is correct, but no automated test verifies the claimed behaviour. These require new tests to be written in `services/speaker-coordination-service/src/test/java`, not doc changes.

- U1: "RESPOND token is single-use" — no test exists; security risk (token replay attack)
- U2: "VIEW token is reusable" — no test exists; may silently break after first click if accidentally single-use
- U3: "Token validity period is 30 days" — no test exists; expired tokens could be accepted indefinitely
- U4: "Only SHA-256 hash stored in DB, not raw token" — no test exists; DB breach risk if raw token persisted
- U5: "Rate limiting: 5 requests/minute per IP" — no test exists; rate-limit threshold unverified
- U6: "Accept transitions INVITED → ACCEPTED" — no test exists; core state machine transition
- U7: "Decline transitions INVITED → DECLINED" — no test exists; core state machine transition
- U8: "Decline reason is required" — no test exists; empty decline reason accepted silently
- U9: "`change_reason = 'SPEAKER_PORTAL_RESPONSE'` recorded in status history" — no test exists; audit trail unverified
- U10: "Confirmation email sent on acceptance" — no test exists; email handler may be missing
- U11: "Organiser notified in-app on accept and decline" — no test exists; async notification silently broken
- U12: "Title max 200 chars (required), abstract max 1000 chars (required)" — no test exists; oversized content enters DB
- U13: "Abstract warning shown when under 200 characters" — no test exists; UX-level check, low risk
- U14: "File types restricted to PPTX/PDF/KEY; max 50 MB" — no test exists; arbitrary files could reach S3
- U15: "Files uploaded directly to S3 via presigned URL, never proxied" — no test exists; architecture regression risk
- U16: "Draft auto-saved every 30 seconds; restored on re-visit" — no test exists; data loss risk on browser close
- U17: "Content submission transitions ACCEPTED → CONTENT_SUBMITTED" — no test exists; organiser Phase C depends on this
- U18: "Each resubmission increments version number; all versions stored" — no test exists; organiser review history unverified
- U19: "Reminder tiers: Tier 1 = 14 days, Tier 2 = 7 days, Tier 3 = 3 days before deadline" — no test exists; wrong thresholds send reminders at wrong times
- U20: "Reminders skipped if speaker already responded or submitted" — no test exists; accepted/submitted speakers receive spam
- U21: "After Tier 3, in-app notification created for organiser" — no test exists; escalation path silently broken
- U22: "Dashboard upcoming/past event filter logic" — no test exists; wrong filter shows cancelled speakers or hides valid past events
