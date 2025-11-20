package ch.batbern.migration.processor;

import ch.batbern.migration.model.legacy.LegacyCompany;
import ch.batbern.migration.model.target.CompanyDto;
import ch.batbern.migration.service.EntityIdMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Company Mapping Processor
 *
 * Transforms legacy company data to target CompanyDto format.
 * Implements mapping logic from Story 3.1.2:
 * - AC 17: Skip duplicates (status="duplicate")
 * - AC 18: Set isVerified = false for all migrated companies
 * - AC 19: Normalize company names (max 12 chars, alphanumeric only)
 * - AC 20: Generate S3 keys for logos
 *
 * Story: 3.2.1 - Migration Tool Implementation
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CompanyMappingProcessor implements ItemProcessor<LegacyCompany, CompanyDto> {

    private final EntityIdMappingService idMappingService;

    @Override
    public CompanyDto process(LegacyCompany legacy) throws Exception {
        // AC 17: Skip duplicates
        if ("duplicate".equals(legacy.getStatus())) {
            log.info("Skipping duplicate company: {} ({})", legacy.getId(), legacy.getDisplayName());
            return null; // Returning null skips this item
        }

        // Check if already migrated (idempotency)
        if (idMappingService.isMigrated("Company", legacy.getId())) {
            log.info("Company already migrated: {}. Skipping.", legacy.getId());
            return null;
        }

        // AC 19: Normalize company name (max 12 chars, alphanumeric only)
        String normalizedName = normalizeCompanyName(legacy.getId());

        CompanyDto company = new CompanyDto();
        company.setName(normalizedName);
        company.setDisplayName(legacy.getDisplayName());
        company.setWebsite(legacy.getUrl());

        // AC 18: All migrated companies are unverified
        company.setIsVerified(false);

        // AC 20: Generate S3 key for logo if exists
        if (legacy.getLogo() != null || legacy.getLogoUrl() != null) {
            String fileId = UUID.randomUUID().toString();
            String extension = extractExtension(legacy.getLogo());
            String s3Key = String.format("company-logos/%s/%s.%s", normalizedName, fileId, extension);

            company.setLogoS3Key(s3Key);
            company.setLogoFileId(fileId);
            // CDN URL will be set after S3 upload in FileMigrationJob
        }

        // Add notes as description
        if (legacy.getNote() != null) {
            company.setDescription(legacy.getNote());
        }

        log.debug("Mapped company: {} → {}", legacy.getId(), normalizedName);

        return company;
    }

    /**
     * Normalize company name to max 12 chars, alphanumeric only
     * AC 19: Normalize company names (max 12 chars)
     *
     * Examples:
     * - "sbb" → "sbb" (3 chars)
     * - "mobiliar" → "mobiliar" (8 chars)
     * - "verylongcompanynamethatneedstruncation" → "verylongcomp" (12 chars)
     */
    private String normalizeCompanyName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Company name cannot be null");
        }

        // Convert to lowercase and remove non-alphanumeric characters
        String normalized = name.toLowerCase()
            .replaceAll("[^a-z0-9]", "");

        // Truncate to 12 chars
        if (normalized.length() > 12) {
            normalized = normalized.substring(0, 12);
        }

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Company name must contain at least one alphanumeric character");
        }

        return normalized;
    }

    /**
     * Extract file extension from filename
     */
    private String extractExtension(String filename) {
        if (filename == null) {
            return "jpg"; // Default extension
        }

        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1).toLowerCase();
        }

        return "jpg"; // Default extension
    }
}
