-- Story 16.5: own text (no template) for the two async composer sends, newsletter and registrant
-- notices. The job and the newsletter retry render from the row, so the text is fixed per send.
-- Own-text rows use template_key 'own-text-newsletter' or 'own-text-registrants' (NOT NULL column;
-- both audiences share this table).
ALTER TABLE newsletter_sends
    ADD COLUMN content_source VARCHAR(10) NOT NULL DEFAULT 'TEMPLATE',
    ADD COLUMN own_subject TEXT,
    ADD COLUMN own_body_html TEXT,
    ADD CONSTRAINT chk_newsletter_sends_content_source CHECK (content_source IN ('TEMPLATE', 'OWN_TEXT')),
    ADD CONSTRAINT chk_newsletter_sends_own_text CHECK (
        content_source = 'TEMPLATE' OR (own_subject IS NOT NULL AND own_body_html IS NOT NULL));

-- Registrant one-shot guard for own text: same event, same subject (Story 16.5 AC9).
CREATE INDEX idx_newsletter_sends_own_subject
    ON newsletter_sends (event_id, own_subject)
    WHERE content_source = 'OWN_TEXT';
