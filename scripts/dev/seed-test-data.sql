-- Development seed data for local testing
-- Run with: docker exec batbern-dev-postgres psql -U postgres -d batbern_development -f /path/to/seed-test-data.sql
-- Or: make dev-seed-data
--
-- This creates a sample event with speakers for local UI development.
-- Only runs inserts if no events exist (safe to run multiple times).
--
-- Story 6.3 Test Data:
--   Speaker Response URL: http://localhost:8100/respond/testtoken63speaker
--   Speaker Email: test.speaker@example.com
--   After accepting, "Create Account" CTA appears with pre-filled email

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
            'BAT-SEED-2026',
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
         'declined', 'Not available due to schedule conflict', 'nissim.buchs'),
        -- Story 6.3: Speaker with email for testing invitation/account linking flow
        ('aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee', 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d',
         'Test Speaker Account', 'Test Company AG', 'Software Architecture, Cloud Computing',
         'contacted', 'Test speaker for Story 6.3 account linking', 'nissim.buchs')
        ON CONFLICT (id) DO NOTHING;

        -- Update email for Story 6.3 test speaker (email column added later)
        UPDATE speaker_pool
        SET email = 'test.speaker@example.com'
        WHERE id = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee';

        -- Story 6.3: Speaker invitation for testing response flow
        -- Test URL: http://localhost:8100/respond/testtoken63speaker
        INSERT INTO speaker_invitations (
            id, speaker_pool_id, speaker_email, speaker_name, event_code,
            response_token, invitation_status, sent_at, expires_at, created_by, personal_message
        ) VALUES (
            'bbbbbbbb-cccc-dddd-eeee-ffffffffffff',
            'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
            'test.speaker@example.com',
            'Test Speaker Account',
            'BAT-SEED-2026',
            'testtoken63speaker',
            'sent',
            NOW(),
            NOW() + INTERVAL '365 days',
            'nissim.buchs',
            'We would love to have you speak at BAT Bern 2026! Your expertise in software architecture would be perfect for our digital innovation theme.'
        ) ON CONFLICT (id) DO NOTHING;

        RAISE NOTICE '✅ Development test data seeded successfully';
    ELSE
        RAISE NOTICE '⏭️  Skipping test data seed - events table is not empty';
    END IF;
END $$;
