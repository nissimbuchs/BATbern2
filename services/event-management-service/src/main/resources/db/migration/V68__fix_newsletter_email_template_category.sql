-- Fix newsletter email template category from LAYOUT → NEWSLETTER
-- Templates were seeded before the NEWSLETTER category was added to EmailTemplateSeedService.deriveCategory()
UPDATE email_templates
SET category = 'NEWSLETTER'
WHERE template_key = 'newsletter-event'
  AND category != 'NEWSLETTER';
