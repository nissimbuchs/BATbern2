-- V69: Force re-seed of newsletter email templates.
--
-- Changes: sessions now rendered as a table (title | speaker | company),
-- structural sessions (moderation, break, lunch) filtered out,
-- body text paragraphs made left-aligned.
--
-- The EmailTemplateSeedService (@PostConstruct) will re-insert the templates
-- from the updated HTML files on the next application startup.
DELETE FROM email_templates WHERE template_key = 'newsletter-event';
