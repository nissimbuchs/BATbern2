-- Story 7.3 hardening: the "slides-online" template is a REGISTRANT-targeted notice, NOT a
-- subscriber newsletter. It was originally seeded with category NEWSLETTER, which made it
-- selectable in the Event → Newsletter tab — where sending it would blast ALL newsletter
-- subscribers instead of the event's registrants. Recategorise it to REGISTRANT_NOTICE so it
-- no longer appears in the subscriber-newsletter template picker (it now lives in the dedicated
-- "Registrant Notices" tab). Mirrors V68 (which fixed the newsletter category the same way).
--
-- EmailTemplateSeedService is insert-only (it never updates an existing row's category), so the
-- deriveCategory() change alone would only affect fresh environments — this migration fixes the
-- rows already seeded in staging/production.
UPDATE email_templates
SET category = 'REGISTRANT_NOTICE'
WHERE template_key = 'slides-online'
  AND category != 'REGISTRANT_NOTICE';
