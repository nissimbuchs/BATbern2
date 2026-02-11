-- Development seed data for local testing
-- Run with: docker exec batbern-dev-postgres psql -U postgres -d batbern_development -f /path/to/seed-test-data.sql
-- Or: make dev-seed-data
--
-- This creates a sample event with speakers for local UI development.
-- Includes Story 6.5 reminder test data (INVITED/ACCEPTED speakers with deadlines).
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
        -- Story 6.5: Added email, deadlines, invited_at, accepted_at, content_status fields
        -- Create user profiles for speakers who have accepted/submitted content
        INSERT INTO user_profiles (
            id, username, email, first_name, last_name, is_active
        ) VALUES
        ('ff001111-ff00-ff00-ff00-ff0000000001', 'anna.mueller', 'anna.mueller@ethz.ch', 'Anna', 'Müller', true),
        ('ff001111-ff00-ff00-ff00-ff0000000002', 'lisa.schneider', 'lisa.schneider@bern.ch', 'Lisa', 'Schneider', true)
        ON CONFLICT (id) DO NOTHING;

        -- Create speaker profiles
        INSERT INTO speakers (
            id, username, first_name, last_name, availability, workflow_state,
            expertise_areas, speaking_topics, languages
        ) VALUES
        ('ff002222-ff00-ff00-ff00-ff0000000001', 'anna.mueller', 'Anna', 'Müller',
         'available', 'confirmed',
         ARRAY['Digital Twin Technology', 'BIM Integration'],
         ARRAY['Digital Twins in der Bauindustrie', 'BIM-Workflows'],
         ARRAY['de', 'en']),
        ('ff002222-ff00-ff00-ff00-ff0000000002', 'lisa.schneider', 'Lisa', 'Schneider',
         'available', 'identified',
         ARRAY['Urban Planning', 'Public Infrastructure'],
         ARRAY['Smart City', 'Digitale Stadtplanung'],
         ARRAY['de', 'fr'])
        ON CONFLICT (id) DO NOTHING;

        INSERT INTO speaker_pool (
            id, event_id, speaker_name, company, expertise, status,
            notes, assigned_organizer_id, email, username,
            invited_at, response_deadline, content_deadline,
            accepted_at, content_status, reminders_disabled
        ) VALUES
        -- Dr. Anna Müller: CONFIRMED keynote (past all deadlines)
        ('11111111-1111-1111-1111-111111111111', 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d',
         'Dr. Anna Müller', 'ETH Zürich', 'Digital Twin Technology, BIM Integration',
         'confirmed', 'Keynote speaker, confirmed for morning slot', 'nissim.buchs',
         'anna.mueller@ethz.ch', 'anna.mueller',
         NOW() - INTERVAL '30 days', CURRENT_DATE - 16, CURRENT_DATE + 14,
         NOW() - INTERVAL '28 days', 'SUBMITTED', false),

        -- Marco Bernasconi: ACCEPTED, content PENDING, deadline in 7 days (TIER_2 trigger)
        ('22222222-2222-2222-2222-222222222222', 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d',
         'Marco Bernasconi', 'Implenia AG', 'Sustainable Construction, Carbon Footprint',
         'accepted', 'Presentation on sustainable construction practices', 'nissim.buchs',
         'marco.bernasconi@implenia.com', NULL,
         NOW() - INTERVAL '20 days', CURRENT_DATE - 6, CURRENT_DATE + 7,
         NOW() - INTERVAL '18 days', 'PENDING', false),

        -- Sarah Weber: CONTACTED (not yet invited, no deadlines)
        ('33333333-3333-3333-3333-333333333333', 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d',
         'Sarah Weber', 'Herzog & de Meuron', 'Parametric Design, Computational Architecture',
         'contacted', 'Initial contact via LinkedIn', 'nissim.buchs',
         'sarah.weber@herzogdemeuron.com', NULL,
         NULL, NULL, NULL,
         NULL, 'PENDING', false),

        -- Thomas Keller: IDENTIFIED (no contact yet)
        ('44444444-4444-4444-4444-444444444444', 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d',
         'Thomas Keller', 'Roche Pharma', 'Smart Buildings, IoT Integration',
         'identified', 'Recommended by steering committee', NULL,
         'thomas.keller@roche.com', NULL,
         NULL, NULL, NULL,
         NULL, 'PENDING', false),

        -- Lisa Schneider: CONTENT_SUBMITTED (no reminder needed)
        ('55555555-5555-5555-5555-555555555555', 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d',
         'Lisa Schneider', 'Stadt Bern', 'Urban Planning, Public Infrastructure',
         'content_submitted', 'Abstract received, pending review', 'nissim.buchs',
         'lisa.schneider@bern.ch', 'lisa.schneider',
         NOW() - INTERVAL '25 days', CURRENT_DATE - 11, CURRENT_DATE + 10,
         NOW() - INTERVAL '22 days', 'SUBMITTED', false),

        -- Pierre Dubois: DECLINED (no reminder needed)
        ('66666666-6666-6666-6666-666666666666', 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d',
         'Pierre Dubois', 'EPFL Lausanne', 'AI in Architecture, Generative Design',
         'declined', 'Not available due to schedule conflict', 'nissim.buchs',
         'pierre.dubois@epfl.ch', NULL,
         NOW() - INTERVAL '15 days', CURRENT_DATE - 1, NULL,
         NULL, 'PENDING', false),

        -- Story 6.5: Additional speakers for reminder testing scenarios

        -- Eva Hofmann: INVITED, response deadline in 14 days (TIER_1 trigger)
        ('aaaa1111-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d',
         'Eva Hofmann', 'SBB Immobilien', 'Railway Architecture, Transit-Oriented Development',
         'invited', 'Invited for afternoon session on transit architecture', 'nissim.buchs',
         'eva.hofmann@sbb.ch', NULL,
         NOW() - INTERVAL '5 days', CURRENT_DATE + 14, CURRENT_DATE + 42,
         NULL, 'PENDING', false),

        -- Jan Meier: INVITED, response deadline in 3 days (TIER_3 - final reminder)
        ('aaaa2222-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d',
         'Jan Meier', 'Bern Fachhochschule', 'Timber Construction, Wood Engineering',
         'invited', 'URGENT: No response yet, final reminder due', 'nissim.buchs',
         'jan.meier@bfh.ch', NULL,
         NOW() - INTERVAL '25 days', CURRENT_DATE + 3, CURRENT_DATE + 35,
         NULL, 'PENDING', false),

        -- Claudia Roth: ACCEPTED, content PENDING, deadline in 14 days (TIER_1 content reminder)
        ('aaaa3333-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d',
         'Claudia Roth', 'Emch+Berger', 'Structural Engineering, Seismic Design',
         'accepted', 'Accepted, waiting for presentation materials', 'nissim.buchs',
         'claudia.roth@emchberger.ch', NULL,
         NOW() - INTERVAL '30 days', CURRENT_DATE - 16, CURRENT_DATE + 14,
         NOW() - INTERVAL '20 days', 'PENDING', false),

        -- Felix Brunner: INVITED, reminders DISABLED (should be skipped by scheduler)
        ('aaaa4444-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d',
         'Felix Brunner', 'Swisscom', 'Smart City, 5G Infrastructure',
         'invited', 'Reminders disabled per speaker request', 'nissim.buchs',
         'felix.brunner@swisscom.com', NULL,
         NOW() - INTERVAL '10 days', CURRENT_DATE + 7, CURRENT_DATE + 35,
         NULL, 'PENDING', true),

        -- Nina Zimmermann: INVITED, NO EMAIL (should be skipped by scheduler)
        ('aaaa5555-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d',
         'Nina Zimmermann', 'Zürcher Kantonalbank', 'Real Estate Valuation, PropTech',
         'invited', 'No email on file - contacted via phone only', 'nissim.buchs',
         NULL, NULL,
         NOW() - INTERVAL '8 days', CURRENT_DATE + 7, CURRENT_DATE + 35,
         NULL, 'PENDING', false),

        -- Roberto Frei: ACCEPTED, content PENDING, deadline in 3 days (TIER_3 content - escalation trigger)
        ('aaaa6666-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d',
         'Roberto Frei', 'Basler & Hofmann', 'Energy Efficiency, MINERGIE Standards',
         'accepted', 'URGENT: Content deadline approaching, no submission yet', 'nissim.buchs',
         'roberto.frei@baslerhofmann.ch', NULL,
         NOW() - INTERVAL '40 days', CURRENT_DATE - 26, CURRENT_DATE + 3,
         NOW() - INTERVAL '35 days', 'PENDING', false)

        ON CONFLICT (id) DO NOTHING;

        -- Insert outreach history for speakers who have been contacted
        INSERT INTO speaker_outreach_history (id, speaker_pool_id, contact_date, contact_method, notes, organizer_username) VALUES
        -- Sarah Weber - contacted via LinkedIn
        ('77777777-7777-7777-7777-777777777771', '33333333-3333-3333-3333-333333333333',
         NOW() - INTERVAL '10 days', 'email', 'Initial contact via LinkedIn', 'nissim.buchs'),
        -- Dr. Anna Müller - confirmed keynote
        ('77777777-7777-7777-7777-777777777772', '11111111-1111-1111-1111-111111111111',
         NOW() - INTERVAL '30 days', 'email', 'Automated invitation email sent via speaker portal', 'SYSTEM'),
        -- Marco Bernasconi - accepted
        ('77777777-7777-7777-7777-777777777773', '22222222-2222-2222-2222-222222222222',
         NOW() - INTERVAL '20 days', 'email', 'Automated invitation email sent via speaker portal', 'SYSTEM'),
        -- Lisa Schneider - content submitted
        ('77777777-7777-7777-7777-777777777774', '55555555-5555-5555-5555-555555555555',
         NOW() - INTERVAL '25 days', 'email', 'Automated invitation email sent via speaker portal', 'SYSTEM'),
        -- Pierre Dubois - declined
        ('77777777-7777-7777-7777-777777777775', '66666666-6666-6666-6666-666666666666',
         NOW() - INTERVAL '15 days', 'email', 'Automated invitation email sent via speaker portal', 'SYSTEM'),
        -- Eva Hofmann - invited
        ('77777777-7777-7777-7777-777777777776', 'aaaa1111-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
         NOW() - INTERVAL '5 days', 'email', 'Automated invitation email sent via speaker portal', 'SYSTEM'),
        -- Jan Meier - invited (overdue response)
        ('77777777-7777-7777-7777-777777777777', 'aaaa2222-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
         NOW() - INTERVAL '25 days', 'email', 'Automated invitation email sent via speaker portal', 'SYSTEM'),
        -- Claudia Roth - accepted
        ('77777777-7777-7777-7777-777777777778', 'aaaa3333-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
         NOW() - INTERVAL '30 days', 'email', 'Automated invitation email sent via speaker portal', 'SYSTEM'),
        -- Felix Brunner - invited (reminders disabled)
        ('77777777-7777-7777-7777-777777777779', 'aaaa4444-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
         NOW() - INTERVAL '10 days', 'email', 'Automated invitation email sent via speaker portal', 'SYSTEM'),
        -- Roberto Frei - accepted (content overdue)
        ('77777777-7777-7777-7777-77777777777a', 'aaaa6666-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
         NOW() - INTERVAL '40 days', 'email', 'Automated invitation email sent via speaker portal', 'SYSTEM')
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
         'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d', 'invited', 'declined', 'pierre.dubois', 'Declined invitation: Not available due to schedule conflict', NOW() - INTERVAL '12 days'),
        -- Eva Hofmann: IDENTIFIED → INVITED
        ('88888888-8888-8888-8888-88888888888c', 'aaaa1111-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
         'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d', 'identified', 'invited', 'system', 'Invitation email sent', NOW() - INTERVAL '5 days'),
        -- Jan Meier: IDENTIFIED → INVITED (no response for 25 days)
        ('88888888-8888-8888-8888-88888888888d', 'aaaa2222-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
         'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d', 'identified', 'invited', 'system', 'Invitation email sent', NOW() - INTERVAL '25 days'),
        -- Claudia Roth: IDENTIFIED → INVITED → ACCEPTED
        ('88888888-8888-8888-8888-88888888888e', 'aaaa3333-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
         'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d', 'identified', 'invited', 'system', 'Invitation email sent', NOW() - INTERVAL '30 days'),
        ('88888888-8888-8888-8888-88888888888f', 'aaaa3333-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
         'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d', 'invited', 'accepted', 'claudia.roth', 'Accepted invitation via speaker portal', NOW() - INTERVAL '20 days'),
        -- Felix Brunner: IDENTIFIED → INVITED (reminders disabled)
        ('88888888-8888-8888-8888-888888888890', 'aaaa4444-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
         'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d', 'identified', 'invited', 'system', 'Invitation email sent', NOW() - INTERVAL '10 days'),
        -- Roberto Frei: IDENTIFIED → INVITED → ACCEPTED
        ('88888888-8888-8888-8888-888888888891', 'aaaa6666-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
         'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d', 'identified', 'invited', 'system', 'Invitation email sent', NOW() - INTERVAL '40 days'),
        ('88888888-8888-8888-8888-888888888892', 'aaaa6666-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
         'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d', 'invited', 'accepted', 'roberto.frei', 'Accepted invitation via speaker portal', NOW() - INTERVAL '35 days')
        ON CONFLICT (id) DO NOTHING;

        -- Sessions and content submissions for speakers with SUBMITTED content
        -- Dr. Anna Müller: Keynote session with submitted content
        INSERT INTO sessions (
            id, event_id, event_code, title, description, session_type, session_slug
        ) VALUES (
            'cccc1111-cccc-cccc-cccc-cccccccccccc',
            'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d',
            'BATbern998',
            'Digital Twins in der Schweizer Bauindustrie',
            'Wie Digital Twin Technology die Planung, den Bau und den Betrieb von Gebäuden in der Schweiz revolutioniert. Praxisbeispiele von ETH-Forschungsprojekten und Industriepartnerschaften.',
            'presentation',
            'digital-twins-schweizer-bauindustrie'
        ) ON CONFLICT (id) DO NOTHING;

        -- Lisa Schneider: Urban planning session with submitted content
        INSERT INTO sessions (
            id, event_id, event_code, title, description, session_type, session_slug
        ) VALUES (
            'cccc2222-cccc-cccc-cccc-cccccccccccc',
            'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d',
            'BATbern998',
            'Smart City Bern: Digitale Stadtplanung der Zukunft',
            'Die Stadt Bern setzt auf innovative digitale Werkzeuge für die Stadtplanung. Erfahren Sie, wie GIS, BIM und Bürgerbeteiligungsplattformen die öffentliche Infrastrukturplanung transformieren.',
            'presentation',
            'smart-city-bern-digitale-stadtplanung'
        ) ON CONFLICT (id) DO NOTHING;

        -- Link sessions to speakers via session_users
        INSERT INTO session_users (id, session_id, username, speaker_role, is_confirmed) VALUES
        ('dddd1111-dddd-dddd-dddd-dddddddddddd', 'cccc1111-cccc-cccc-cccc-cccccccccccc',
         'anna.mueller', 'primary_speaker', true),
        ('dddd2222-dddd-dddd-dddd-dddddddddddd', 'cccc2222-cccc-cccc-cccc-cccccccccccc',
         'lisa.schneider', 'primary_speaker', false)
        ON CONFLICT (id) DO NOTHING;

        -- Update speaker_pool entries with session references and content_submitted_at
        UPDATE speaker_pool SET
            session_id = 'cccc1111-cccc-cccc-cccc-cccccccccccc',
            content_submitted_at = NOW() - INTERVAL '10 days'
        WHERE id = '11111111-1111-1111-1111-111111111111';

        UPDATE speaker_pool SET
            session_id = 'cccc2222-cccc-cccc-cccc-cccccccccccc',
            content_submitted_at = NOW() - INTERVAL '5 days'
        WHERE id = '55555555-5555-5555-5555-555555555555';

        -- Content submissions for Dr. Anna Müller
        INSERT INTO speaker_content_submissions (
            id, speaker_pool_id, session_id, title, abstract, abstract_char_count,
            submission_version, submitted_at
        ) VALUES (
            'eeee1111-eeee-eeee-eeee-eeeeeeeeeeee',
            '11111111-1111-1111-1111-111111111111',
            'cccc1111-cccc-cccc-cccc-cccccccccccc',
            'Digital Twins in der Schweizer Bauindustrie',
            'Wie Digital Twin Technology die Planung, den Bau und den Betrieb von Gebäuden in der Schweiz revolutioniert. Praxisbeispiele von ETH-Forschungsprojekten und Industriepartnerschaften.',
            186,
            1,
            NOW() - INTERVAL '10 days'
        ) ON CONFLICT (id) DO NOTHING;

        -- Content submissions for Lisa Schneider
        INSERT INTO speaker_content_submissions (
            id, speaker_pool_id, session_id, title, abstract, abstract_char_count,
            submission_version, submitted_at
        ) VALUES (
            'eeee2222-eeee-eeee-eeee-eeeeeeeeeeee',
            '55555555-5555-5555-5555-555555555555',
            'cccc2222-cccc-cccc-cccc-cccccccccccc',
            'Smart City Bern: Digitale Stadtplanung der Zukunft',
            'Die Stadt Bern setzt auf innovative digitale Werkzeuge für die Stadtplanung. Erfahren Sie, wie GIS, BIM und Bürgerbeteiligungsplattformen die öffentliche Infrastrukturplanung transformieren.',
            191,
            1,
            NOW() - INTERVAL '5 days'
        ) ON CONFLICT (id) DO NOTHING;

        -- PDF presentation materials for speakers with submitted content
        INSERT INTO session_materials (
            id, session_id, upload_id, s3_key, cloudfront_url,
            file_name, file_extension, file_size, mime_type,
            material_type, uploaded_by, content_extracted, extraction_status
        ) VALUES
        -- Dr. Anna Müller: Keynote presentation PDF
        ('ff003333-ff00-ff00-ff00-ff0000000001',
         'cccc1111-cccc-cccc-cccc-cccccccccccc',
         'upload-anna-mueller-001',
         'materials/BATbern998/anna-mueller/digital-twins-bauindustrie.pdf',
         'https://cdn.batbern.ch/materials/BATbern998/anna-mueller/digital-twins-bauindustrie.pdf',
         'Digital_Twins_Bauindustrie_Mueller.pdf', 'pdf', 2458624, 'application/pdf',
         'PRESENTATION', 'anna.mueller', false, 'PENDING'),
        -- Lisa Schneider: Urban planning presentation PDF
        ('ff003333-ff00-ff00-ff00-ff0000000002',
         'cccc2222-cccc-cccc-cccc-cccccccccccc',
         'upload-lisa-schneider-001',
         'materials/BATbern998/lisa-schneider/smart-city-bern-stadtplanung.pdf',
         'https://cdn.batbern.ch/materials/BATbern998/lisa-schneider/smart-city-bern-stadtplanung.pdf',
         'Smart_City_Bern_Schneider.pdf', 'pdf', 1843200, 'application/pdf',
         'PRESENTATION', 'lisa.schneider', false, 'PENDING')
        ON CONFLICT (id) DO NOTHING;

        -- Story 6.5: Sample reminder log entries (showing past reminders already sent)
        INSERT INTO speaker_reminder_log (
            id, speaker_pool_id, event_id, reminder_type, tier,
            email_address, deadline_date, triggered_by, sent_at
        ) VALUES
        -- Jan Meier already received TIER_1 and TIER_2 response reminders
        ('99999999-9999-9999-9999-999999999991', 'aaaa2222-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
         'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d', 'RESPONSE', 'TIER_1',
         'jan.meier@bfh.ch', CURRENT_DATE + 3, 'SYSTEM', NOW() - INTERVAL '11 days'),
        ('99999999-9999-9999-9999-999999999992', 'aaaa2222-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
         'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d', 'RESPONSE', 'TIER_2',
         'jan.meier@bfh.ch', CURRENT_DATE + 3, 'SYSTEM', NOW() - INTERVAL '4 days'),
        -- Roberto Frei already received TIER_1 and TIER_2 content reminders
        ('99999999-9999-9999-9999-999999999993', 'aaaa6666-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
         'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d', 'CONTENT', 'TIER_1',
         'roberto.frei@baslerhofmann.ch', CURRENT_DATE + 3, 'SYSTEM', NOW() - INTERVAL '11 days'),
        ('99999999-9999-9999-9999-999999999994', 'aaaa6666-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
         'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d', 'CONTENT', 'TIER_2',
         'roberto.frei@baslerhofmann.ch', CURRENT_DATE + 3, 'SYSTEM', NOW() - INTERVAL '4 days'),
        -- Marco Bernasconi received TIER_1 content reminder + a manual organizer reminder
        ('99999999-9999-9999-9999-999999999995', '22222222-2222-2222-2222-222222222222',
         'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d', 'CONTENT', 'TIER_1',
         'marco.bernasconi@implenia.com', CURRENT_DATE + 7, 'SYSTEM', NOW() - INTERVAL '7 days'),
        ('99999999-9999-9999-9999-999999999996', '22222222-2222-2222-2222-222222222222',
         'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d', 'CONTENT', 'TIER_1',
         'marco.bernasconi@implenia.com', CURRENT_DATE + 7, 'nissim.buchs', NOW() - INTERVAL '5 days')
        ON CONFLICT (id) DO NOTHING;

        -- Story 6.5: Automated reminder outreach history entries
        INSERT INTO speaker_outreach_history (id, speaker_pool_id, contact_date, contact_method, notes, organizer_username) VALUES
        ('77777777-7777-7777-7777-77777777777b', 'aaaa2222-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
         NOW() - INTERVAL '11 days', 'automated_email', 'RESPONSE deadline reminder (TIER_1) sent for event BATbern998. Deadline: ' || (CURRENT_DATE + 3)::text, 'SYSTEM'),
        ('77777777-7777-7777-7777-77777777777c', 'aaaa2222-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
         NOW() - INTERVAL '4 days', 'automated_email', 'RESPONSE deadline reminder (TIER_2) sent for event BATbern998. Deadline: ' || (CURRENT_DATE + 3)::text, 'SYSTEM'),
        ('77777777-7777-7777-7777-77777777777d', '22222222-2222-2222-2222-222222222222',
         NOW() - INTERVAL '7 days', 'automated_email', 'CONTENT deadline reminder (TIER_1) sent for event BATbern998. Deadline: ' || (CURRENT_DATE + 7)::text, 'SYSTEM'),
        ('77777777-7777-7777-7777-77777777777e', '22222222-2222-2222-2222-222222222222',
         NOW() - INTERVAL '5 days', 'manual_email', 'CONTENT deadline reminder (TIER_1) sent for event BATbern998. Deadline: ' || (CURRENT_DATE + 7)::text, 'nissim.buchs')
        ON CONFLICT (id) DO NOTHING;

        RAISE NOTICE '✅ Development test data seeded successfully (includes reminder test data)';
    ELSE
        RAISE NOTICE '⏭️  Skipping test data seed - events table is not empty';
    END IF;
END $$;

-- Display the created speakers, their status, and reminder info
SELECT
    sp.speaker_name,
    sp.status::text as current_status,
    sp.email,
    sp.response_deadline,
    sp.content_deadline,
    sp.content_status,
    sp.reminders_disabled as reminders_off,
    (SELECT COUNT(*) FROM speaker_reminder_log rl WHERE rl.speaker_pool_id = sp.id) as reminders_sent,
    (SELECT COUNT(*) FROM speaker_outreach_history oh WHERE oh.speaker_pool_id = sp.id) as outreach_count
FROM speaker_pool sp
WHERE sp.event_id = 'a1b2c3d4-e5f6-4a5b-8c9d-0e1f2a3b4c5d'
ORDER BY sp.speaker_name;
