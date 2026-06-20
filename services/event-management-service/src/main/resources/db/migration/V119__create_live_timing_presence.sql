-- Story 15.1: Replace WebSockets with REST polling for live agenda control.
--
-- Persistent organizer-presence for the live-timing snapshot, replacing the per-task
-- in-memory ConcurrentHashMap in WatchPresenceService (which is lost on Fargate task
-- restart/scale and differs between tasks — the root cause of the desync this story fixes).
--
-- Presence is "active" when last_seen_at is within the TTL (~30 s, enforced in the query).
-- The authenticated organizer's GET /live-timing poll upserts its own row; anonymous
-- presenter polls do NOT (they must not flip the organizer-present flag). One row per
-- (event, organizer).
--
-- eventCode/username are meaningful cross-aggregate identifiers (ADR-003) — no FK to events.

CREATE TABLE live_timing_presence (
    event_code   VARCHAR(50)  NOT NULL,
    username     VARCHAR(100) NOT NULL,
    last_seen_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (event_code, username)
);

-- Lookup pattern: "is any organizer active for this event within the TTL".
CREATE INDEX idx_live_timing_presence_event_seen
    ON live_timing_presence (event_code, last_seen_at);
