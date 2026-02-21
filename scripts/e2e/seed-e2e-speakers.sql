-- E2E Test Seed Data for Speaker Onboarding Tests
--
-- Run with: docker exec batbern-dev-postgres psql -U postgres -d batbern_development -f /path/to/seed-e2e-speakers.sql
-- Or via make: make e2e-seed-speakers
--
-- Creates speakers in specific workflow states needed for E2E testing:
-- 1. INVITED speaker - for full onboarding flow test
-- 2. ACCEPTED speaker with session - for profile/content tests
-- 3. ACCEPTED speaker without session - for no-session test
--

DO $$
DECLARE
    test_event_id UUID;
    test_session_id UUID;
BEGIN
    -- Get or create the test event
    SELECT id INTO test_event_id FROM events WHERE event_code = 'BATbern998' LIMIT 1;

    IF test_event_id IS NULL THEN
        -- Create the test event if it doesn't exist
        INSERT INTO events (
            id, event_number, title, description, event_date,
            registration_deadline, venue_name, venue_address, venue_capacity,
            event_code, event_type, workflow_state, version, organizer_username
        ) VALUES (
            'e2e00000-0000-0000-0000-000000000001',
            998,
            'E2E Test Event - Speaker Onboarding',
            'Test event for E2E speaker onboarding tests',
            '2026-06-15 09:00:00+02',
            '2026-06-01 23:59:59+02',
            'Test Venue',
            'Test Address, Bern',
            100,
            'BATbern998',
            'full_day',
            'speaker_identification',
            1,
            'nissim.buchs'
        )
        RETURNING id INTO test_event_id;

        RAISE NOTICE '✅ Created test event: BATbern998';
    END IF;

    -- Create a test session for content submission tests
    SELECT id INTO test_session_id FROM sessions WHERE event_id = test_event_id LIMIT 1;

    IF test_session_id IS NULL THEN
        INSERT INTO sessions (
            id, event_id, title, description, session_type,
            capacity, language, session_slug, event_code
        ) VALUES (
            'e2e00000-0000-0000-0000-000000000010',
            test_event_id,
            'E2E Test Session - Cloud Architecture',
            'Test session for E2E content submission tests',
            'presentation',
            50,
            'en',
            'e2e-test-session-cloud-architecture',
            'BATbern998'
        )
        RETURNING id INTO test_session_id;

        RAISE NOTICE '✅ Created test session';
    END IF;

    -- 1. Create/Update INVITED speaker for onboarding flow test
    -- Note: username must match format ^[a-z]+\.[a-z]+(\.[0-9]+)?$ (firstname.lastname or firstname.lastname.number)
    INSERT INTO speaker_pool (
        id, event_id, speaker_name, username, email, company, expertise,
        status, notes, created_at
    ) VALUES (
        'e2e00000-0000-0000-0000-000000000101',
        test_event_id,
        'Test Invited',
        'test.invited',
        'test.invited@example.com',
        'E2E Test Company',
        'E2E Testing, Test Automation',
        'invited',
        'E2E test speaker - INVITED status for onboarding flow',
        NOW()
    )
    ON CONFLICT (id) DO UPDATE SET
        status = 'invited',
        session_id = NULL,
        accepted_at = NULL;

    RAISE NOTICE '✅ Created/Updated INVITED speaker: test.invited';

    -- 2. Create/Update ACCEPTED speaker with session for profile/content tests
    INSERT INTO speaker_pool (
        id, event_id, speaker_name, username, email, company, expertise,
        status, session_id, accepted_at, notes, created_at
    ) VALUES (
        'e2e00000-0000-0000-0000-000000000102',
        test_event_id,
        'Test Withsession',
        'test.withsession',
        'test.withsession@example.com',
        'E2E Session Company',
        'Cloud Architecture, Microservices',
        'accepted',
        test_session_id,
        NOW() - INTERVAL '1 day',
        'E2E test speaker - ACCEPTED status with session assigned',
        NOW() - INTERVAL '2 days'
    )
    ON CONFLICT (id) DO UPDATE SET
        status = 'accepted',
        session_id = test_session_id,
        accepted_at = NOW() - INTERVAL '1 day';

    RAISE NOTICE '✅ Created/Updated ACCEPTED speaker with session: test.withsession';

    -- 3. Create/Update ACCEPTED speaker without session for no-session test
    INSERT INTO speaker_pool (
        id, event_id, speaker_name, username, email, company, expertise,
        status, session_id, accepted_at, notes, created_at
    ) VALUES (
        'e2e00000-0000-0000-0000-000000000103',
        test_event_id,
        'Test Nosession',
        'test.nosession',
        'test.nosession@example.com',
        'E2E NoSession Company',
        'General Testing',
        'accepted',
        NULL,
        NOW() - INTERVAL '1 day',
        'E2E test speaker - ACCEPTED status without session (for no-session test)',
        NOW() - INTERVAL '2 days'
    )
    ON CONFLICT (id) DO UPDATE SET
        status = 'accepted',
        session_id = NULL,
        accepted_at = NOW() - INTERVAL '1 day';

    RAISE NOTICE '✅ Created/Updated ACCEPTED speaker without session: test.nosession';

    -- Also create corresponding records in the speakers table (needed for profile API)
    INSERT INTO speakers (
        id, username, first_name, last_name, email, workflow_state,
        expertise_areas, speaking_topics, bio
    ) VALUES (
        'e2e00000-0000-0000-0000-000000000201',
        'test.invited',
        'Test',
        'Invited',
        'test.invited@example.com',
        'accepted',
        ARRAY['E2E Testing', 'Test Automation'],
        ARRAY['Test Strategies'],
        'E2E test speaker for onboarding flow'
    )
    ON CONFLICT (username) DO UPDATE SET
        first_name = 'Test',
        last_name = 'Invited',
        workflow_state = 'accepted';

    INSERT INTO speakers (
        id, username, first_name, last_name, email, workflow_state,
        expertise_areas, speaking_topics, bio
    ) VALUES (
        'e2e00000-0000-0000-0000-000000000202',
        'test.withsession',
        'Test',
        'Withsession',
        'test.withsession@example.com',
        'accepted',
        ARRAY['Cloud Architecture', 'Microservices'],
        ARRAY['Kubernetes', 'Event-Driven Design'],
        'E2E test speaker with session assigned'
    )
    ON CONFLICT (username) DO UPDATE SET
        first_name = 'Test',
        last_name = 'Withsession',
        workflow_state = 'accepted';

    INSERT INTO speakers (
        id, username, first_name, last_name, email, workflow_state,
        expertise_areas, speaking_topics, bio
    ) VALUES (
        'e2e00000-0000-0000-0000-000000000203',
        'test.nosession',
        'Test',
        'Nosession',
        'test.nosession@example.com',
        'accepted',
        ARRAY['General Testing'],
        ARRAY['Test Patterns'],
        'E2E test speaker without session'
    )
    ON CONFLICT (username) DO UPDATE SET
        first_name = 'Test',
        last_name = 'Nosession',
        workflow_state = 'accepted';

    RAISE NOTICE '✅ Created/Updated speaker profile records';

    -- Create corresponding user_profiles records (needed for speaker portal profile API)
    INSERT INTO user_profiles (
        id, username, email, first_name, last_name, bio
    ) VALUES (
        'e2e00000-0000-0000-0000-000000000301',
        'test.invited',
        'test.invited@example.com',
        'Test',
        'Invited',
        'E2E test speaker for onboarding flow'
    )
    ON CONFLICT (username) DO UPDATE SET
        first_name = 'Test',
        last_name = 'Invited',
        bio = 'E2E test speaker for onboarding flow';

    INSERT INTO user_profiles (
        id, username, email, first_name, last_name, bio
    ) VALUES (
        'e2e00000-0000-0000-0000-000000000302',
        'test.withsession',
        'test.withsession@example.com',
        'Test',
        'Withsession',
        'E2E test speaker with session assigned'
    )
    ON CONFLICT (username) DO UPDATE SET
        first_name = 'Test',
        last_name = 'Withsession',
        bio = 'E2E test speaker with session assigned';

    INSERT INTO user_profiles (
        id, username, email, first_name, last_name, bio
    ) VALUES (
        'e2e00000-0000-0000-0000-000000000303',
        'test.nosession',
        'test.nosession@example.com',
        'Test',
        'Nosession',
        'E2E test speaker without session'
    )
    ON CONFLICT (username) DO UPDATE SET
        first_name = 'Test',
        last_name = 'Nosession',
        bio = 'E2E test speaker without session';

    RAISE NOTICE '✅ Created/Updated user_profiles records';

    -- Clean up any old tokens for these speakers (to ensure fresh tokens)
    DELETE FROM speaker_invitation_tokens
    WHERE speaker_pool_id IN (
        'e2e00000-0000-0000-0000-000000000101',
        'e2e00000-0000-0000-0000-000000000102',
        'e2e00000-0000-0000-0000-000000000103'
    );

    RAISE NOTICE '✅ Cleaned up old tokens for E2E speakers';

    -- Clean up old history records for these speakers
    DELETE FROM speaker_outreach_history
    WHERE speaker_pool_id IN (
        'e2e00000-0000-0000-0000-000000000101',
        'e2e00000-0000-0000-0000-000000000102',
        'e2e00000-0000-0000-0000-000000000103'
    );

    DELETE FROM speaker_status_history
    WHERE speaker_pool_id IN (
        'e2e00000-0000-0000-0000-000000000101',
        'e2e00000-0000-0000-0000-000000000102',
        'e2e00000-0000-0000-0000-000000000103'
    );

    -- Create outreach history for INVITED speaker (test.invited)
    INSERT INTO speaker_outreach_history (
        id, speaker_pool_id, contact_date, contact_method, notes, organizer_username
    ) VALUES (
        'e2e00000-0000-0000-0000-000000000401',
        'e2e00000-0000-0000-0000-000000000101',
        NOW() - INTERVAL '3 days',
        'email',
        'Automated invitation email sent via speaker portal',
        'system'
    );

    -- Create outreach history for ACCEPTED speakers
    INSERT INTO speaker_outreach_history (
        id, speaker_pool_id, contact_date, contact_method, notes, organizer_username
    ) VALUES (
        'e2e00000-0000-0000-0000-000000000402',
        'e2e00000-0000-0000-0000-000000000102',
        NOW() - INTERVAL '5 days',
        'email',
        'Automated invitation email sent via speaker portal',
        'system'
    );

    INSERT INTO speaker_outreach_history (
        id, speaker_pool_id, contact_date, contact_method, notes, organizer_username
    ) VALUES (
        'e2e00000-0000-0000-0000-000000000403',
        'e2e00000-0000-0000-0000-000000000103',
        NOW() - INTERVAL '5 days',
        'email',
        'Automated invitation email sent via speaker portal',
        'system'
    );

    RAISE NOTICE '✅ Created outreach history records';

    -- Create status history for INVITED speaker: IDENTIFIED → INVITED
    INSERT INTO speaker_status_history (
        id, speaker_pool_id, event_id, previous_status, new_status,
        changed_by_username, change_reason, changed_at
    ) VALUES (
        'e2e00000-0000-0000-0000-000000000501',
        'e2e00000-0000-0000-0000-000000000101',
        test_event_id,
        'identified',
        'invited',
        'system',
        'Invitation email sent',
        NOW() - INTERVAL '3 days'
    );

    -- Create status history for ACCEPTED speaker with session: IDENTIFIED → INVITED → ACCEPTED
    INSERT INTO speaker_status_history (
        id, speaker_pool_id, event_id, previous_status, new_status,
        changed_by_username, change_reason, changed_at
    ) VALUES (
        'e2e00000-0000-0000-0000-000000000502',
        'e2e00000-0000-0000-0000-000000000102',
        test_event_id,
        'identified',
        'invited',
        'system',
        'Invitation email sent',
        NOW() - INTERVAL '5 days'
    );

    INSERT INTO speaker_status_history (
        id, speaker_pool_id, event_id, session_id, previous_status, new_status,
        changed_by_username, change_reason, changed_at
    ) VALUES (
        'e2e00000-0000-0000-0000-000000000503',
        'e2e00000-0000-0000-0000-000000000102',
        test_event_id,
        test_session_id,
        'invited',
        'accepted',
        'test.withsession',
        'Accepted invitation via speaker portal',
        NOW() - INTERVAL '1 day'
    );

    -- Create status history for ACCEPTED speaker without session: IDENTIFIED → INVITED → ACCEPTED
    INSERT INTO speaker_status_history (
        id, speaker_pool_id, event_id, previous_status, new_status,
        changed_by_username, change_reason, changed_at
    ) VALUES (
        'e2e00000-0000-0000-0000-000000000504',
        'e2e00000-0000-0000-0000-000000000103',
        test_event_id,
        'identified',
        'invited',
        'system',
        'Invitation email sent',
        NOW() - INTERVAL '5 days'
    );

    INSERT INTO speaker_status_history (
        id, speaker_pool_id, event_id, previous_status, new_status,
        changed_by_username, change_reason, changed_at
    ) VALUES (
        'e2e00000-0000-0000-0000-000000000505',
        'e2e00000-0000-0000-0000-000000000103',
        test_event_id,
        'invited',
        'accepted',
        'test.nosession',
        'Accepted invitation via speaker portal',
        NOW() - INTERVAL '1 day'
    );

    RAISE NOTICE '✅ Created status history records';
    RAISE NOTICE '';
    RAISE NOTICE '🎉 E2E speaker test data seeded successfully!';
    RAISE NOTICE 'Run ./scripts/e2e/generate-speaker-tokens.sh to generate tokens';

END $$;

-- Display the created speakers
SELECT
    speaker_name,
    username,
    status::text as status,
    CASE WHEN session_id IS NOT NULL THEN 'Yes' ELSE 'No' END as has_session
FROM speaker_pool
WHERE id IN (
    'e2e00000-0000-0000-0000-000000000101',
    'e2e00000-0000-0000-0000-000000000102',
    'e2e00000-0000-0000-0000-000000000103'
)
ORDER BY speaker_name;

-- Display outreach history
SELECT
    sp.speaker_name,
    oh.contact_method,
    oh.contact_date::date as contacted_on,
    oh.organizer_username
FROM speaker_outreach_history oh
JOIN speaker_pool sp ON sp.id = oh.speaker_pool_id
WHERE oh.speaker_pool_id IN (
    'e2e00000-0000-0000-0000-000000000101',
    'e2e00000-0000-0000-0000-000000000102',
    'e2e00000-0000-0000-0000-000000000103'
)
ORDER BY sp.speaker_name, oh.contact_date;

-- Display status history
SELECT
    sp.speaker_name,
    sh.previous_status::text as from_status,
    sh.new_status::text as to_status,
    sh.change_reason,
    sh.changed_at::date as changed_on
FROM speaker_status_history sh
JOIN speaker_pool sp ON sp.id = sh.speaker_pool_id
WHERE sh.speaker_pool_id IN (
    'e2e00000-0000-0000-0000-000000000101',
    'e2e00000-0000-0000-0000-000000000102',
    'e2e00000-0000-0000-0000-000000000103'
)
ORDER BY sp.speaker_name, sh.changed_at;
