-- Development seed data for local testing
-- Run with: docker exec batbern-dev-postgres psql -U postgres -d batbern_development -f /path/to/seed-test-data.sql
-- Or: make dev-seed-data
--
-- This creates a sample event with speakers for local UI development.
-- Only runs inserts if no events exist (safe to run multiple times).

DO $$
BEGIN
    -- Check if events table is empty
    IF NOT EXISTS (SELECT 1 FROM events LIMIT 1) THEN

        -- Insert test event
        INSERT INTO events (
            id, event_number, title, description, event_date,
            registration_deadline, venue_name, venue_address, venue_capacity,
            event_code, event_type, workflow_state, version, organizer_username
        ) VALUES (
            'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d',
            999,
            'BAT Bern 2026 - Digital Innovation',
            'Die jährliche Konferenz der Berner Architekten zu digitaler Innovation. Themen umfassen BIM, Digital Twins, KI in der Architektur und nachhaltige Baupraktiken.',
            '2026-06-15 09:00:00+02',
            '2026-06-01 23:59:59+02',
            'Kultur Casino Bern',
            'Herrengasse 25, 3011 Bern',
            250,
            'BATbern998',
            'full_day',
            'speaker_identification',
            1,
            'nissim.buchs'
        ) ON CONFLICT (id) DO NOTHING;

        -- Insert test speakers with various statuses
        INSERT INTO speaker_pool (id, event_id, speaker_name, company, expertise, status, notes, assigned_organizer_id) VALUES
        ('11111111-1111-1111-1111-111111111111', 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d',
         'Dr. Anna Müller', 'ETH Zürich', 'Digital Twin Technology, BIM Integration',
         'confirmed', 'Keynote speaker, confirmed for morning slot', 'nissim.buchs'),
        ('22222222-2222-2222-2222-222222222222', 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d',
         'Marco Bernasconi', 'Implenia AG', 'Sustainable Construction, Carbon Footprint',
         'accepted', 'Presentation on sustainable construction practices', 'nissim.buchs'),
        ('33333333-3333-3333-3333-333333333333', 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d',
         'Sarah Weber', 'Herzog & de Meuron', 'Parametric Design, Computational Architecture',
         'contacted', 'Initial contact via LinkedIn', 'nissim.buchs'),
        ('44444444-4444-4444-4444-444444444444', 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d',
         'Thomas Keller', 'Roche Pharma', 'Smart Buildings, IoT Integration',
         'identified', 'Recommended by steering committee', NULL),
        ('55555555-5555-5555-5555-555555555555', 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d',
         'Lisa Schneider', 'Stadt Bern', 'Urban Planning, Public Infrastructure',
         'content_submitted', 'Abstract received, pending review', 'nissim.buchs'),
        ('66666666-6666-6666-6666-666666666666', 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d',
         'Pierre Dubois', 'EPFL Lausanne', 'AI in Architecture, Generative Design',
         'declined', 'Not available due to schedule conflict', 'nissim.buchs')
        ON CONFLICT (id) DO NOTHING;

        -- Insert outreach history for speakers who have been contacted
        INSERT INTO speaker_outreach_history (id, speaker_pool_id, contact_date, contact_method, notes, organizer_username) VALUES
        -- Sarah Weber - contacted via LinkedIn
        ('77777777-7777-7777-7777-777777777771', '33333333-3333-3333-3333-333333333333',
         NOW() - INTERVAL '10 days', 'linkedin', 'Initial contact via LinkedIn', 'nissim.buchs'),
        -- Dr. Anna Müller - confirmed keynote
        ('77777777-7777-7777-7777-777777777772', '11111111-1111-1111-1111-111111111111',
         NOW() - INTERVAL '30 days', 'email', 'Automated invitation email sent via speaker portal', 'system'),
        -- Marco Bernasconi - accepted
        ('77777777-7777-7777-7777-777777777773', '22222222-2222-2222-2222-222222222222',
         NOW() - INTERVAL '20 days', 'email', 'Automated invitation email sent via speaker portal', 'system'),
        -- Lisa Schneider - content submitted
        ('77777777-7777-7777-7777-777777777774', '55555555-5555-5555-5555-555555555555',
         NOW() - INTERVAL '25 days', 'email', 'Automated invitation email sent via speaker portal', 'system'),
        -- Pierre Dubois - declined
        ('77777777-7777-7777-7777-777777777775', '66666666-6666-6666-6666-666666666666',
         NOW() - INTERVAL '15 days', 'email', 'Automated invitation email sent via speaker portal', 'system')
        ON CONFLICT (id) DO NOTHING;

        -- Insert status history for speakers (tracking workflow transitions)
        INSERT INTO speaker_status_history (id, speaker_pool_id, event_id, previous_status, new_status, changed_by_username, change_reason, changed_at) VALUES
        -- Dr. Anna Müller: IDENTIFIED → INVITED → ACCEPTED → CONFIRMED
        ('88888888-8888-8888-8888-888888888881', '11111111-1111-1111-1111-111111111111',
         'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d', 'identified', 'invited', 'system', 'Invitation email sent', NOW() - INTERVAL '30 days'),
        ('88888888-8888-8888-8888-888888888882', '11111111-1111-1111-1111-111111111111',
         'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d', 'invited', 'accepted', 'anna.mueller', 'Accepted invitation via speaker portal', NOW() - INTERVAL '28 days'),
        ('88888888-8888-8888-8888-888888888883', '11111111-1111-1111-1111-111111111111',
         'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d', 'accepted', 'confirmed', 'nissim.buchs', 'Confirmed for keynote slot', NOW() - INTERVAL '14 days'),
        -- Marco Bernasconi: IDENTIFIED → INVITED → ACCEPTED
        ('88888888-8888-8888-8888-888888888884', '22222222-2222-2222-2222-222222222222',
         'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d', 'identified', 'invited', 'system', 'Invitation email sent', NOW() - INTERVAL '20 days'),
        ('88888888-8888-8888-8888-888888888885', '22222222-2222-2222-2222-222222222222',
         'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d', 'invited', 'accepted', 'marco.bernasconi', 'Accepted invitation via speaker portal', NOW() - INTERVAL '18 days'),
        -- Sarah Weber: IDENTIFIED → CONTACTED
        ('88888888-8888-8888-8888-888888888886', '33333333-3333-3333-3333-333333333333',
         'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d', 'identified', 'contacted', 'nissim.buchs', 'Initial contact via LinkedIn', NOW() - INTERVAL '10 days'),
        -- Lisa Schneider: IDENTIFIED → INVITED → ACCEPTED → CONTENT_SUBMITTED
        ('88888888-8888-8888-8888-888888888887', '55555555-5555-5555-5555-555555555555',
         'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d', 'identified', 'invited', 'system', 'Invitation email sent', NOW() - INTERVAL '25 days'),
        ('88888888-8888-8888-8888-888888888888', '55555555-5555-5555-5555-555555555555',
         'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d', 'invited', 'accepted', 'lisa.schneider', 'Accepted invitation via speaker portal', NOW() - INTERVAL '22 days'),
        ('88888888-8888-8888-8888-888888888889', '55555555-5555-5555-5555-555555555555',
         'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d', 'accepted', 'content_submitted', 'lisa.schneider', 'Content submitted via speaker portal (version 1)', NOW() - INTERVAL '5 days'),
        -- Pierre Dubois: IDENTIFIED → INVITED → DECLINED
        ('88888888-8888-8888-8888-88888888888a', '66666666-6666-6666-6666-666666666666',
         'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d', 'identified', 'invited', 'system', 'Invitation email sent', NOW() - INTERVAL '15 days'),
        ('88888888-8888-8888-8888-88888888888b', '66666666-6666-6666-6666-666666666666',
         'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d', 'invited', 'declined', 'pierre.dubois', 'Declined invitation: Not available due to schedule conflict', NOW() - INTERVAL '12 days')
        ON CONFLICT (id) DO NOTHING;

        RAISE NOTICE '✅ Development test data seeded successfully (includes history records)';
    ELSE
        RAISE NOTICE '⏭️  Skipping test data seed - events table is not empty';
    END IF;
END $$;

-- Display the created speakers and their history
SELECT
    sp.speaker_name,
    sp.status::text as current_status,
    (SELECT COUNT(*) FROM speaker_outreach_history oh WHERE oh.speaker_pool_id = sp.id) as outreach_count,
    (SELECT COUNT(*) FROM speaker_status_history sh WHERE sh.speaker_pool_id = sp.id) as status_changes
FROM speaker_pool sp
WHERE sp.event_id = 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d'
ORDER BY sp.speaker_name;
