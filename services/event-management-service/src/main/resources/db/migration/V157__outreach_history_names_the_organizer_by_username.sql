-- V157 (#1287, owner option 1, 2026-11-15): until Story 4.10 SpeakerOutreachController wrote the
-- organizer's e-mail address into speaker_outreach_history.organizer_username. Each such value
-- moves to the username of the one account that owns the address: its primary address
-- (user_profiles.email) or a verified additional address (user_additional_emails.verified_at set,
-- the rule SSO linking uses), compared case-insensitively. An address that matches no account or
-- several accounts stays unchanged. Both counts are reported as a NOTICE, which Flyway logs.
-- user_profiles and user_additional_emails belong to company-user-management; all services share
-- one database (V116, V151). Without them (the EMS test stubs) the migration does nothing.
DO $$
DECLARE
    mapped INTEGER;
    unchanged INTEGER;
BEGIN
    IF to_regclass('user_additional_emails') IS NULL
       OR NOT EXISTS (SELECT 1 FROM information_schema.columns
                      WHERE table_schema = current_schema()
                        AND table_name = 'user_profiles' AND column_name = 'email') THEN
        RAISE NOTICE 'V157: user address tables missing, outreach history left as it is';
        RETURN;
    END IF;

    WITH owners AS (
        SELECT lower(email) AS address, id AS user_id FROM user_profiles WHERE email IS NOT NULL
        UNION
        SELECT lower(email), user_id FROM user_additional_emails WHERE verified_at IS NOT NULL
    ),
    unique_owner AS (
        SELECT o.address, min(u.username) AS username
        FROM owners o JOIN user_profiles u ON u.id = o.user_id
        GROUP BY o.address
        HAVING count(DISTINCT o.user_id) = 1
    )
    UPDATE speaker_outreach_history h
    SET organizer_username = uo.username, updated_at = NOW()
    FROM unique_owner uo
    WHERE h.organizer_username LIKE '%@%'
      AND lower(h.organizer_username) = uo.address;
    GET DIAGNOSTICS mapped = ROW_COUNT;

    SELECT count(*) INTO unchanged FROM speaker_outreach_history WHERE organizer_username LIKE '%@%';

    RAISE NOTICE 'V157: % outreach rows now name the organizer by username, % rows with an address left unchanged',
        mapped, unchanged;
END $$;
