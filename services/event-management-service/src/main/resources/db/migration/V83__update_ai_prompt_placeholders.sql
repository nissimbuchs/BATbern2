-- V83: Update ai_prompts to use {{VAR_NAME}} named placeholders.
-- Replaces positional %s/%d format with descriptive {{VARIABLE}} tokens that
-- are resolved at runtime by BatbernAiService.applyVariables().
--
-- Variables for event_description:
--   {{EVENT_NR}}, {{EVENT_TITLE}}, {{TOPIC_TITLE}}, {{TOPIC_DESCRIPTION}},
--   {{TOPIC_CATEGORY}}, {{EVENT_DATE}}, {{EVENT_DESCRIPTION}}
--
-- Variables for theme_image:
--   {{TOPIC_TITLE}}, {{TOPIC_DESCRIPTION}}, {{TOPIC_CATEGORY}},
--   {{EVENT_TITLE}}, {{EVENT_DESCRIPTION}}
--
-- Variables for abstract_quality:
--   {{SPEAKER_NAME}}, {{SESSION_TITLE}}, {{ABSTRACT}}

UPDATE ai_prompts
SET prompt_text  = $desc$Write a German event description for BATbern#{{EVENT_NR}}, the Berner Architekten Treffen – a community evening event in Bern where local IT professionals and companies share hands-on experience with current hot topics in software architecture and engineering.

Event: {{EVENT_TITLE}}
Topic: {{TOPIC_TITLE}} (category: {{TOPIC_CATEGORY}})
Topic description: {{TOPIC_DESCRIPTION}}
Event date: {{EVENT_DATE}}
Existing description for context: {{EVENT_DESCRIPTION}}

Structure (2-3 paragraphs, 120-160 words total, in professional German):
1. Set the industry context: what is happening in the field, what trends/challenges/tools are relevant to this topic right now.
2. Describe what will happen at THIS BATbern: local companies and speakers present their real-world approaches, practical experience, and lessons learned – not academic talks, but practitioner insights.
3. End with a sentence in this style (adapt to the topic): 'An diesem BAT stellen unsere Referenten ihre Ansätze und Lessons Learned vor.'

Important: use only the exact date provided (do not invent or omit dates). If no date is provided, omit date references. The event is a single evening, not a multi-day conference. Do not say 'Konferenz' or 'Session' – say 'Veranstaltung' or 'BAT'.$desc$,
    default_text = $desc$Write a German event description for BATbern#{{EVENT_NR}}, the Berner Architekten Treffen – a community evening event in Bern where local IT professionals and companies share hands-on experience with current hot topics in software architecture and engineering.

Event: {{EVENT_TITLE}}
Topic: {{TOPIC_TITLE}} (category: {{TOPIC_CATEGORY}})
Topic description: {{TOPIC_DESCRIPTION}}
Event date: {{EVENT_DATE}}
Existing description for context: {{EVENT_DESCRIPTION}}

Structure (2-3 paragraphs, 120-160 words total, in professional German):
1. Set the industry context: what is happening in the field, what trends/challenges/tools are relevant to this topic right now.
2. Describe what will happen at THIS BATbern: local companies and speakers present their real-world approaches, practical experience, and lessons learned – not academic talks, but practitioner insights.
3. End with a sentence in this style (adapt to the topic): 'An diesem BAT stellen unsere Referenten ihre Ansätze und Lessons Learned vor.'

Important: use only the exact date provided (do not invent or omit dates). If no date is provided, omit date references. The event is a single evening, not a multi-day conference. Do not say 'Konferenz' or 'Session' – say 'Veranstaltung' or 'BAT'.$desc$,
    updated_at   = NOW()
WHERE prompt_key = 'event_description';

UPDATE ai_prompts
SET prompt_text  = $img$Abstract digital artwork that visually represents the IT topic: "{{TOPIC_TITLE}}" (category: {{TOPIC_CATEGORY}}).
Event title: {{EVENT_TITLE}}
Topic description: {{TOPIC_DESCRIPTION}}
Event description for context: {{EVENT_DESCRIPTION}}

Use abstract visual metaphors, symbols, and imagery that are directly related to this specific topic and category — not generic circuit boards. Style: dark midnight navy-to-black background, glowing neon cyan and electric blue abstract digital elements covering the full frame uniformly from corner to corner. Flat 2D digital illustration – no 3D perspective, no room, no floor, no staging, no display panel, no frame, no spotlights, no shadow on a surface. The image fills the entire 16:9 rectangle edge-to-edge. No text. No logos. No people.$img$,
    default_text = $img$Abstract digital artwork that visually represents the IT topic: "{{TOPIC_TITLE}}" (category: {{TOPIC_CATEGORY}}).
Event title: {{EVENT_TITLE}}
Topic description: {{TOPIC_DESCRIPTION}}
Event description for context: {{EVENT_DESCRIPTION}}

Use abstract visual metaphors, symbols, and imagery that are directly related to this specific topic and category — not generic circuit boards. Style: dark midnight navy-to-black background, glowing neon cyan and electric blue abstract digital elements covering the full frame uniformly from corner to corner. Flat 2D digital illustration – no 3D perspective, no room, no floor, no staging, no display panel, no frame, no spotlights, no shadow on a surface. The image fills the entire 16:9 rectangle edge-to-edge. No text. No logos. No people.$img$,
    updated_at   = NOW()
WHERE prompt_key = 'theme_image';

UPDATE ai_prompts
SET prompt_text  = $abs$Analyze this speaker abstract for BATbern – a Swiss IT architecture community event where practitioners share real-world experience and lessons learned (NOT product demos or service sales pitches).
Speaker: {{SPEAKER_NAME}}
Session title: {{SESSION_TITLE}}
Abstract: "{{ABSTRACT}}"

Evaluate these two criteria, rate each from 1 to 10 (10 = perfectly aligned, 1 = completely misaligned):
1. noPromotion: Does the abstract avoid promoting an IT product, tool, or service? (10 = purely about experience/knowledge; 1 = reads like a product advertisement)
2. lessonsLearned: Does the abstract suggest the speaker will share practical lessons learned from real-world experience? (10 = clearly hands-on experience and lessons; 1 = no indication of practical experience)

Also count the words in the abstract. If the word count exceeds 160, provide a shortened German version of maximum 150 words that preserves the key message. If 160 or fewer words, set shortenedAbstract to null.

Return JSON only (no other text):
{"noPromotionScore": <1-10>, "noPromotionFeedback": "<brief German explanation, 1-2 sentences>", "lessonsLearnedScore": <1-10>, "lessonsLearnedFeedback": "<brief German explanation, 1-2 sentences>", "wordCount": <number>, "shortenedAbstract": "<shortened German text or null>"}$abs$,
    default_text = $abs$Analyze this speaker abstract for BATbern – a Swiss IT architecture community event where practitioners share real-world experience and lessons learned (NOT product demos or service sales pitches).
Speaker: {{SPEAKER_NAME}}
Session title: {{SESSION_TITLE}}
Abstract: "{{ABSTRACT}}"

Evaluate these two criteria, rate each from 1 to 10 (10 = perfectly aligned, 1 = completely misaligned):
1. noPromotion: Does the abstract avoid promoting an IT product, tool, or service? (10 = purely about experience/knowledge; 1 = reads like a product advertisement)
2. lessonsLearned: Does the abstract suggest the speaker will share practical lessons learned from real-world experience? (10 = clearly hands-on experience and lessons; 1 = no indication of practical experience)

Also count the words in the abstract. If the word count exceeds 160, provide a shortened German version of maximum 150 words that preserves the key message. If 160 or fewer words, set shortenedAbstract to null.

Return JSON only (no other text):
{"noPromotionScore": <1-10>, "noPromotionFeedback": "<brief German explanation, 1-2 sentences>", "lessonsLearnedScore": <1-10>, "lessonsLearnedFeedback": "<brief German explanation, 1-2 sentences>", "wordCount": <number>, "shortenedAbstract": "<shortened German text or null>"}$abs$,
    updated_at   = NOW()
WHERE prompt_key = 'abstract_quality';
