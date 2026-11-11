-- V30__mail_center_request_feed_cursor.sql
-- Epic 17 entry 7 (ADR-016 decision 4): event-management pulls merge and logo requests from
-- GET /internal/mail-center-requests instead of CUMS pushing them. The feed is ordered by a change
-- cursor (changed_at, id). changed_at is the time the row was created or last changed its status
-- or decision; the trigger sets it, so no code path can forget it. V23 and V25 stay untouched.

ALTER TABLE profile_merge_requests ADD COLUMN changed_at TIMESTAMPTZ;
ALTER TABLE company_logo_requests ADD COLUMN changed_at TIMESTAMPTZ;

UPDATE profile_merge_requests SET changed_at = coalesce(decided_at, created_at);
UPDATE company_logo_requests SET changed_at = coalesce(decided_at, created_at);

ALTER TABLE profile_merge_requests ALTER COLUMN changed_at SET NOT NULL;
ALTER TABLE company_logo_requests ALTER COLUMN changed_at SET NOT NULL;

-- clock_timestamp(), not now(): now() is the start of the transaction, which would put a row
-- further behind the moment it becomes visible than necessary. The feed's settle window covers
-- the remaining gap between this time and the commit.
CREATE FUNCTION mail_center_request_changed() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'INSERT'
       OR NEW.status IS DISTINCT FROM OLD.status
       OR NEW.decided_by IS DISTINCT FROM OLD.decided_by
       OR NEW.decided_at IS DISTINCT FROM OLD.decided_at THEN
        NEW.changed_at := clock_timestamp();
    END IF;
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_profile_merge_requests_changed
    BEFORE INSERT OR UPDATE ON profile_merge_requests
    FOR EACH ROW EXECUTE FUNCTION mail_center_request_changed();

CREATE TRIGGER trg_company_logo_requests_changed
    BEFORE INSERT OR UPDATE ON company_logo_requests
    FOR EACH ROW EXECUTE FUNCTION mail_center_request_changed();

CREATE INDEX idx_profile_merge_requests_changed ON profile_merge_requests (changed_at, id);
CREATE INDEX idx_company_logo_requests_changed ON company_logo_requests (changed_at, id);
