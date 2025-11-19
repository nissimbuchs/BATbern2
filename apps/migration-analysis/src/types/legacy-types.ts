/**
 * Legacy JSON data structures from Angular application
 * Source: apps/BATspa-old/src/api/
 */

/**
 * Legacy session data from sessions.json (302 records)
 */
export interface LegacySession {
  bat: number;                    // Event/BAT number (1-60)
  pdf: string;                    // PDF filename
  title: string;                  // Presentation title
  abstract?: string;              // Presentation abstract (77% have this)
  authoren?: string;              // Authors (for program PDFs, 23% have this)
  referenten?: LegacySpeaker[];   // Speakers array (77% have this)
}

/**
 * Legacy speaker data embedded in sessions.json
 */
export interface LegacySpeaker {
  name: string;                   // "FirstName LastName, Company"
  bio: string;                    // Speaker biography
  company: string;                // Company identifier
  portrait: string;               // Photo filename
}

/**
 * Legacy event/topic data from topics.json (60 records)
 */
export interface LegacyTopic {
  bat: number;                    // Event/BAT number
  topic: string;                  // Event topic/theme
  datum: string;                  // Date string (various German formats)
  eventType: string;              // "Abend-BAT", "Halb-BAT", "Ganztag-BAT"
  next?: number;                  // Future event flag (3% have this)
  planned?: number;               // Planned event flag (5% have this)
}

/**
 * Legacy picture data from pictures.json (163 records)
 */
export interface LegacyPicture {
  bat: number;                    // Event/BAT number
  image: string;                  // Image filename
}

/**
 * Normalized company data from docs/migration/companies.json (70 companies)
 */
export interface CompanyData {
  id: string;                     // Company identifier (will become Company.name)
  displayName: string;            // Full official name
  url?: string;                   // Company website URL
  logo?: string;                  // Logo filename (local file)
  logoUrl?: string;               // Logo URL (from company website, if available)
  logoFilePath?: string;          // Absolute path to local logo file
  speakerCount: number;           // Number of speaker mentions
  has_logo?: boolean;             // Indicates if logo file exists
  status: string;                 // "complete", "needs_logo", "pending_url", "duplicate", etc.
  note?: string;                  // Additional notes (e.g., acquisitions, duplicates)
}

/**
 * Metadata structure in companies.json
 */
export interface CompaniesMetadata {
  lastUpdated: string;            // ISO timestamp
  source: string;                 // Data source description
  totalCompanies: number;         // 70
  companiesComplete: number;      // 42 (60% - have both logo and URL)
  companiesWithLogos: number;     // 25 (local files)
  companiesWithUrls: number;      // 53 (76%)
  batches: {
    batch1: string;               // "Top 5 Companies (10+ speakers)"
    batch2: string;               // "Priority Companies (5-9 speakers)"
    batch3: string;               // "Standard Priority Companies (2-4 speakers)"
    batch4: string;               // "Low Priority Companies (1 speaker, have logos)"
  };
}

/**
 * Root structure of companies.json
 */
export interface CompaniesData {
  metadata: CompaniesMetadata;
  companies: CompanyData[];
}
