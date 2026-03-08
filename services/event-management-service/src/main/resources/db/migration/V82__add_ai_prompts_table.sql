-- V82: Create ai_prompts table for organizer-editable OpenAI prompts
-- Stores the three prompts used by BatbernAiService, editable via Admin UI.
-- default_text column preserves the original prompt to support "Reset to default".

CREATE TABLE ai_prompts (
    prompt_key   VARCHAR(50)  PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    prompt_text  TEXT         NOT NULL,
    default_text TEXT         NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO ai_prompts (prompt_key, display_name, prompt_text, default_text) VALUES
(
  'event_description',
  'Event Description',
  'Write a German event description for BATbern#%d, the Berner Architekten Treffen – a community evening event in Bern where local IT professionals and companies share hands-on experience with current hot topics in software architecture and engineering.

This BATbern event is entirely dedicated to the topic: "%s" (category: %s).
%s

Structure (2-3 paragraphs, 120-160 words total, in professional German):
1. Set the industry context: what is happening in the field, what trends/challenges/tools are relevant to this topic right now.
2. Describe what will happen at THIS BATbern: local companies and speakers present their real-world approaches, practical experience, and lessons learned – not academic talks, but practitioner insights.
3. End with a sentence in this style (adapt to the topic): ''An diesem BAT stellen unsere Referenten ihre Ansätze und Lessons Learned vor.''

Important: use only the exact date provided (do not invent or omit dates). The event is a single evening, not a multi-day conference. Do not say ''Konferenz'' or ''Session'' – say ''Veranstaltung'' or ''BAT''.',
  'Write a German event description for BATbern#%d, the Berner Architekten Treffen – a community evening event in Bern where local IT professionals and companies share hands-on experience with current hot topics in software architecture and engineering.

This BATbern event is entirely dedicated to the topic: "%s" (category: %s).
%s

Structure (2-3 paragraphs, 120-160 words total, in professional German):
1. Set the industry context: what is happening in the field, what trends/challenges/tools are relevant to this topic right now.
2. Describe what will happen at THIS BATbern: local companies and speakers present their real-world approaches, practical experience, and lessons learned – not academic talks, but practitioner insights.
3. End with a sentence in this style (adapt to the topic): ''An diesem BAT stellen unsere Referenten ihre Ansätze und Lessons Learned vor.''

Important: use only the exact date provided (do not invent or omit dates). The event is a single evening, not a multi-day conference. Do not say ''Konferenz'' or ''Session'' – say ''Veranstaltung'' or ''BAT''.'
),
(
  'theme_image',
  'Theme Image (DALL-E)',
  'Abstract digital artwork that visually represents the IT topic: "%s" (category: %s). %sUse abstract visual metaphors, symbols, and imagery that are directly related to this specific topic and category — not generic circuit boards. Style: dark midnight navy-to-black background, glowing neon cyan and electric blue abstract digital elements covering the full frame uniformly from corner to corner. Flat 2D digital illustration – no 3D perspective, no room, no floor, no staging, no display panel, no frame, no spotlights, no shadow on a surface. The image fills the entire 16:9 rectangle edge-to-edge. No text. No logos. No people.',
  'Abstract digital artwork that visually represents the IT topic: "%s" (category: %s). %sUse abstract visual metaphors, symbols, and imagery that are directly related to this specific topic and category — not generic circuit boards. Style: dark midnight navy-to-black background, glowing neon cyan and electric blue abstract digital elements covering the full frame uniformly from corner to corner. Flat 2D digital illustration – no 3D perspective, no room, no floor, no staging, no display panel, no frame, no spotlights, no shadow on a surface. The image fills the entire 16:9 rectangle edge-to-edge. No text. No logos. No people.'
),
(
  'abstract_quality',
  'Abstract Quality Review',
  'Analyze this speaker abstract for BATbern – a Swiss IT architecture community event where practitioners share real-world experience and lessons learned (NOT product demos or service sales pitches). Speaker: %s. Abstract: "%s".

Evaluate these two criteria, rate each from 1 to 10 (10 = perfectly aligned, 1 = completely misaligned):
1. noPromotion: Does the abstract avoid promoting an IT product, tool, or service? (10 = purely about experience/knowledge; 1 = reads like a product advertisement)
2. lessonsLearned: Does the abstract suggest the speaker will share practical lessons learned from real-world experience? (10 = clearly hands-on experience and lessons; 1 = no indication of practical experience)

Also count the words in the abstract. If the word count exceeds 160, provide a shortened German version of maximum 150 words that preserves the key message. If 160 or fewer words, set shortenedAbstract to null.

Return JSON only (no other text):
{"noPromotionScore": <1-10>, "noPromotionFeedback": "<brief German explanation, 1-2 sentences>", "lessonsLearnedScore": <1-10>, "lessonsLearnedFeedback": "<brief German explanation, 1-2 sentences>", "wordCount": <number>, "shortenedAbstract": "<shortened German text or null>"}',
  'Analyze this speaker abstract for BATbern – a Swiss IT architecture community event where practitioners share real-world experience and lessons learned (NOT product demos or service sales pitches). Speaker: %s. Abstract: "%s".

Evaluate these two criteria, rate each from 1 to 10 (10 = perfectly aligned, 1 = completely misaligned):
1. noPromotion: Does the abstract avoid promoting an IT product, tool, or service? (10 = purely about experience/knowledge; 1 = reads like a product advertisement)
2. lessonsLearned: Does the abstract suggest the speaker will share practical lessons learned from real-world experience? (10 = clearly hands-on experience and lessons; 1 = no indication of practical experience)

Also count the words in the abstract. If the word count exceeds 160, provide a shortened German version of maximum 150 words that preserves the key message. If 160 or fewer words, set shortenedAbstract to null.

Return JSON only (no other text):
{"noPromotionScore": <1-10>, "noPromotionFeedback": "<brief German explanation, 1-2 sentences>", "lessonsLearnedScore": <1-10>, "lessonsLearnedFeedback": "<brief German explanation, 1-2 sentences>", "wordCount": <number>, "shortenedAbstract": "<shortened German text or null>"}'
);
