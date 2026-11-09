-- Owner decision 2026-11-09 (Epic 17.0 deferred work): topic usage engagement was never measured
-- (rows written by the service had none; the V18 backfill stored an estimate derived from the
-- event's workflow state), so the API, the heat maps and these columns no longer carry it.
-- attendee_count was written by nothing; the usage history computes attendance from the event's
-- registrations. Nothing reads either column (Java, SQL, frontend, Bruno and e2e checked).
ALTER TABLE topic_usage_history DROP COLUMN IF EXISTS engagement_score;
ALTER TABLE topic_usage_history DROP COLUMN IF EXISTS attendee_count;
