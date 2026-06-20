-- Story 15.7 (Epic 15 — post-Event-#2 hardening), Part B: per-(window, recipient) digest state.
--
-- The Q&A notification flush (QnaNotificationService) emails a session's speaker / co-speakers /
-- moderator when new top-level questions arrive, at a cadence the recipient chose
-- (qnaNotificationFrequency: live | daily | off — Part A). This table remembers, per recipient
-- per Q&A window:
--   last_notified_at  — throttle anchor (live = >=15 min apart, daily = >=24h apart)
--   notified_through  — high-water mark: created_at of the newest post already covered by a
--                       sent digest, so the next email counts only genuinely NEW questions.
--
-- Additive, prod-safe (staging IS production). window_id is a same-service UUID FK (ADR-003 OK);
-- recipient_username is a cross-service meaningful id (ADR-003 — NO FK, NO UUID).

-- All timestamps are TIMESTAMPTZ to match session_qna_post.created_at (V112) and the JPA Instant
-- mapping. A without-tz column would shift the water mark by the server's tz offset and break the
-- `created_at > notified_through` comparison.
CREATE TABLE session_qna_notification (
    window_id          UUID         NOT NULL,
    recipient_username VARCHAR(100) NOT NULL,
    last_notified_at   TIMESTAMPTZ,
    notified_through   TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_session_qna_notification PRIMARY KEY (window_id, recipient_username),
    CONSTRAINT fk_session_qna_notification_window
        FOREIGN KEY (window_id) REFERENCES session_qna_window (id) ON DELETE CASCADE
);

CREATE INDEX idx_session_qna_notification_window ON session_qna_notification (window_id);

COMMENT ON TABLE session_qna_notification IS
    'Story 15.7 — per-(Q&A window, recipient) digest throttle + high-water mark for new-question notifications.';
