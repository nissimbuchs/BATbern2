-- V3__Create_companies_schema.sql
-- Company Management domain schema

CREATE TABLE IF NOT EXISTS companies (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    company_code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    company_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    description TEXT,
    website VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(50),
    address TEXT,
    city VARCHAR(100),
    country VARCHAR(2),
    tax_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    version INTEGER DEFAULT 0 NOT NULL
);

CREATE INDEX idx_companies_company_code ON companies(company_code);
CREATE INDEX idx_companies_status ON companies(status);
CREATE INDEX idx_companies_company_type ON companies(company_type);

-- Trigger for automatic updated_at
CREATE TRIGGER update_companies_updated_at BEFORE UPDATE ON companies
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE companies IS 'Companies registered on the platform (organizers, partners)';
COMMENT ON COLUMN companies.company_type IS 'Company type: ORGANIZER, PARTNER, VENDOR';
COMMENT ON COLUMN companies.status IS 'Company status: ACTIVE, SUSPENDED, INACTIVE';
COMMENT ON COLUMN companies.version IS 'Optimistic locking version';
