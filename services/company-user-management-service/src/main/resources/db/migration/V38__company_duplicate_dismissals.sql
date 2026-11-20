-- V38__company_duplicate_dismissals.sql
-- #1251 (issue sweep epic 4, story 5): organizers work through the list of candidate duplicate
-- companies (GET /companies/duplicate-candidates, computed live, nothing stored) and mark pairs that
-- are two companies ("Kanton Bern" / "Kanton Zürich") as no duplicate, so the list shrinks for good.
-- One row per pair, the smaller company id first. A merge or a delete of either company removes its
-- rows. The model is user_duplicate_dismissals (V26).
CREATE TABLE company_duplicate_dismissals (
    company_a_id UUID         NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    company_b_id UUID         NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    dismissed_by VARCHAR(255) NOT NULL,
    dismissed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (company_a_id, company_b_id),
    CONSTRAINT chk_company_duplicate_dismissals_order CHECK (company_a_id < company_b_id)
);
CREATE INDEX idx_company_duplicate_dismissals_b ON company_duplicate_dismissals (company_b_id);
