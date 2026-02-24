-- V64: Backfill email subjects on existing seeded content templates.
-- New templates seeded after this migration will have subjects set by EmailTemplateSeedService.
-- Layout templates never have a subject.

UPDATE email_templates SET subject = 'Einladung als Referent - {{eventTitle}}'
WHERE template_key = 'speaker-invitation' AND locale = 'de' AND subject IS NULL;

UPDATE email_templates SET subject = 'Speaker Invitation - {{eventTitle}}'
WHERE template_key = 'speaker-invitation' AND locale = 'en' AND subject IS NULL;

UPDATE email_templates SET subject = 'Bestätigung Ihrer Teilnahme - {{eventTitle}}'
WHERE template_key = 'speaker-acceptance' AND locale = 'de' AND subject IS NULL;

UPDATE email_templates SET subject = 'Speaker Confirmation - {{eventTitle}}'
WHERE template_key = 'speaker-acceptance' AND locale = 'en' AND subject IS NULL;

UPDATE email_templates SET subject = 'Registrierungsbestätigung - {{eventTitle}}'
WHERE template_key = 'registration-confirmation' AND locale = 'de' AND subject IS NULL;

UPDATE email_templates SET subject = 'Registration Confirmation - {{eventTitle}}'
WHERE template_key = 'registration-confirmation' AND locale = 'en' AND subject IS NULL;

UPDATE email_templates SET subject = 'Aufgabenerinnerung: {{taskName}} fällig morgen'
WHERE template_key = 'task-reminder' AND locale = 'de' AND subject IS NULL;

UPDATE email_templates SET subject = 'Task Reminder: {{taskName}} due tomorrow'
WHERE template_key = 'task-reminder' AND locale = 'en' AND subject IS NULL;

UPDATE email_templates SET subject = 'Erinnerung: Einladung als Referent - {{eventTitle}}'
WHERE template_key = 'speaker-reminder-response-tier1' AND locale = 'de' AND subject IS NULL;

UPDATE email_templates SET subject = 'Reminder: Speaker Invitation - {{eventTitle}}'
WHERE template_key = 'speaker-reminder-response-tier1' AND locale = 'en' AND subject IS NULL;

UPDATE email_templates SET subject = 'Dringende Erinnerung: Einladung als Referent - {{eventTitle}}'
WHERE template_key = 'speaker-reminder-response-tier2' AND locale = 'de' AND subject IS NULL;

UPDATE email_templates SET subject = 'Urgent Reminder: Speaker Invitation - {{eventTitle}}'
WHERE template_key = 'speaker-reminder-response-tier2' AND locale = 'en' AND subject IS NULL;

UPDATE email_templates SET subject = 'Letzte Erinnerung: Einladung als Referent - {{eventTitle}}'
WHERE template_key = 'speaker-reminder-response-tier3' AND locale = 'de' AND subject IS NULL;

UPDATE email_templates SET subject = 'Final Reminder: Speaker Invitation - {{eventTitle}}'
WHERE template_key = 'speaker-reminder-response-tier3' AND locale = 'en' AND subject IS NULL;

UPDATE email_templates SET subject = 'Erinnerung: Präsentationsunterlagen - {{eventTitle}}'
WHERE template_key = 'speaker-reminder-content-tier1' AND locale = 'de' AND subject IS NULL;

UPDATE email_templates SET subject = 'Reminder: Presentation Materials - {{eventTitle}}'
WHERE template_key = 'speaker-reminder-content-tier1' AND locale = 'en' AND subject IS NULL;

UPDATE email_templates SET subject = 'Dringende Erinnerung: Präsentationsunterlagen - {{eventTitle}}'
WHERE template_key = 'speaker-reminder-content-tier2' AND locale = 'de' AND subject IS NULL;

UPDATE email_templates SET subject = 'Urgent Reminder: Presentation Materials - {{eventTitle}}'
WHERE template_key = 'speaker-reminder-content-tier2' AND locale = 'en' AND subject IS NULL;

UPDATE email_templates SET subject = 'Letzte Erinnerung: Präsentationsunterlagen - {{eventTitle}}'
WHERE template_key = 'speaker-reminder-content-tier3' AND locale = 'de' AND subject IS NULL;

UPDATE email_templates SET subject = 'Final Reminder: Presentation Materials - {{eventTitle}}'
WHERE template_key = 'speaker-reminder-content-tier3' AND locale = 'en' AND subject IS NULL;
