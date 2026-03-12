-- Story 10.27: iCal RSVP Tracking for Partner Meetings
-- Creates the partner_meeting_rsvps table for storing calendar RSVP replies.
-- The UNIQUE(meeting_id, attendee_email) constraint enables upsert behaviour:
-- an attendee who changes their RSVP (e.g. TENTATIVE → ACCEPTED) gets their
-- record updated in-place rather than a new record being inserted.

CREATE TABLE partner_meeting_rsvps (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id      UUID NOT NULL REFERENCES partner_meetings(id) ON DELETE CASCADE,
    attendee_email  VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL
                        CHECK (status IN ('ACCEPTED', 'DECLINED', 'TENTATIVE')),
    responded_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (meeting_id, attendee_email)
);

CREATE INDEX idx_partner_meeting_rsvps_meeting ON partner_meeting_rsvps(meeting_id);
