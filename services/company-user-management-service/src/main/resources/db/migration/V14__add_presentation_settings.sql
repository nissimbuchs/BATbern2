-- Story 10.8a: Moderator Presentation Page — Functional
-- Single-row table for presentation settings used by the moderator projection page.
-- The single row (id=1) is always present; service falls back to defaults if absent.

CREATE TABLE presentation_settings (
    id           SERIAL PRIMARY KEY,
    about_text   TEXT         NOT NULL,
    partner_count INT         NOT NULL DEFAULT 0
);

-- Seed the single authoritative row with BATbern defaults.
INSERT INTO presentation_settings (id, about_text, partner_count)
VALUES (
    1,
    'BATbern ist eine unabhängige Plattform, die Berner Architekten und Ingenieure vernetzt. Wir veranstalten regelmässige Treffen, bei denen Fachleute Erfahrungen austauschen und neue Impulse gewinnen.',
    9
);
