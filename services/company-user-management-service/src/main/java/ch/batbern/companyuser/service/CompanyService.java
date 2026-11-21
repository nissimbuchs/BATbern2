package ch.batbern.companyuser.service;

import ch.batbern.companyuser.client.CompanyReferenceContract;
import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.domain.CompanyAlias;
import ch.batbern.companyuser.domain.CompanyUserUpload;
import ch.batbern.companyuser.dto.generated.CompanyLogo;
import ch.batbern.companyuser.dto.generated.CompanyResponse;
import ch.batbern.companyuser.dto.generated.CreateCompanyRequest;
import ch.batbern.companyuser.dto.generated.UpdateCompanyRequest;
import ch.batbern.companyuser.exception.CompanyNotFoundException;
import ch.batbern.companyuser.exception.CompanyValidationException;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.companyuser.security.SecurityContextHelper;
import ch.batbern.shared.exception.ValidationException;
import ch.batbern.shared.security.Actors;
import ch.batbern.shared.service.SlugGenerationService;
import ch.batbern.shared.upload.UploadRecord;
import ch.batbern.shared.upload.UploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for company management business logic
 * AC4: Company CRUD operations
 * AC8: Cache invalidation
 *
 * GREEN Phase: Implementation to make tests pass
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final CompanySearchService searchService;
    private final SecurityContextHelper securityContextHelper;
    private final UploadService<CompanyUserUpload> uploadService;
    private final SlugGenerationService slugService;
    private final CompanyInserter companyInserter;
    private final CompanyAliasService aliasService;
    private final CompanyKeyMover keyMover;

    /**
     * Creates a new company with validation
     * AC4: Company creation
     * AC8: Invalidates search cache
     *
     * <p>Owner decision 2026-11-09 (option A): the key ({@code name}, the meaningful ID) is the slug
     * of the typed name by the rule get-or-create uses ({@link
     * ch.batbern.shared.service.SlugGenerationService#generateCompanyName}), made unique within 12
     * characters; the typed name is the display name unless one is given. A company with the same
     * display name (ignoring case) is a duplicate (409); a different company whose name slugs alike
     * gets the next free key.
     */
    public CompanyResponse createCompany(CreateCompanyRequest request) {
        log.info("Creating company: {}", request.getName());

        String displayName = request.getDisplayName() != null && !request.getDisplayName().isBlank()
                ? request.getDisplayName().strip()
                : request.getName().strip();
        String baseKey;
        try {
            baseKey = slugService.generateCompanyName(request.getName());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("name", "Company name must contain at least one letter or digit");
        }

        // Duplicate company (AC3): the same name as people see it, also as an old name kept as an
        // alias after a merge or rename (#1251)
        if (companyRepository.existsByDisplayNameIgnoreCase(displayName)) {
            throw new CompanyValidationException("Company with name '" + displayName + "' already exists");
        }
        if (aliasService.isAliasDisplayName(displayName)) {
            throw new CompanyValidationException("Company with name '" + displayName
                    + "' already exists under another name");
        }
        // An alias key is taken: a company with it would hide the alias
        String companyKey = slugService.ensureUniqueCompanyName(baseKey,
                key -> companyRepository.existsByName(key) || aliasService.isAliasKey(key));

        // Build company entity (AC10: Get username from security context)
        // Story 1.16.2: Use username instead of UUID for createdBy field
        String currentUsername = securityContextHelper.getCurrentUsername();

        Company company = Company.builder()
                .name(companyKey)
                .displayName(displayName)
                .website(request.getWebsite())
                .industry(request.getIndustry())
                .description(request.getDescription())
                .isVerified(false)
                .createdBy(Actors.forUsername(currentUsername))
                .build();

        Company savedCompany = companyRepository.save(company);

        // The logo of the create form (Story 17.11): a CONFIRMED company-logo upload. A logo that
        // cannot be attached (unknown, expired, not confirmed) fails the create with 404/409, so a
        // company is never created without the logo its creator chose.
        if (request.getLogoUploadId() != null && !request.getLogoUploadId().isBlank()) {
            setLogo(savedCompany, uploadService.associate(CompanyLogoUploadHandler.PURPOSE,
                    request.getLogoUploadId(), savedCompany.getName()));
            // Flush: setting the logo updates the new row, and its @PreUpdate moves updatedAt,
            // which the response must show.
            companyRepository.saveAndFlush(savedCompany);
            log.info("Logo associated with company: {}, logoUrl: {}", savedCompany.getName(),
                    savedCompany.getLogoUrl());
        }

        // Invalidate search cache (AC8)
        searchService.invalidateCache();

        log.info("Company created successfully: {}", savedCompany.getId());
        return mapToResponse(savedCompany);
    }

    /**
     * Story 4.10d: the result of {@code POST /companies/get-or-create}, with whether the company was
     * created (201) or found (200), ADR-013 §2.
     */
    public record GetOrCreateResult(CompanyResponse company, boolean created) {
    }

    /**
     * Get existing company or create new one from display name, returning the API response DTO
     * and whether it was created, for the {@code POST /companies/get-or-create} endpoint.
     *
     * @param displayName Full company display name (e.g., "Test Co")
     * @return Existing or newly created company as a response DTO, with the created flag
     */
    public GetOrCreateResult getOrCreateCompanyResponse(String displayName) {
        Resolved resolved = resolveOrCreate(displayName);
        return new GetOrCreateResult(mapToResponse(resolved.company()), resolved.created());
    }

    /**
     * Get existing company or create new one from display name.
     * ADR-003: Uses display name to generate meaningful company name (slug).
     *
     * @param displayName Full company display name (e.g., "Test Co")
     * @return Existing or newly created company
     */
    public Company getOrCreateCompany(String displayName) {
        return resolveOrCreate(displayName).company();
    }

    private record Resolved(Company company, boolean created) {
    }

    private Resolved resolveOrCreate(String displayName) {
        log.debug("Get-or-create company for display name: {}", displayName);

        // Generate company name (slug) from display name
        // "Test Co" → "testco" (max 12 chars, alphanumeric only)
        String companyName = slugService.generateCompanyName(displayName);

        // The generated name is the key: an existing company with it is returned, never suffixed.
        // Else a company people know by this display name (case, accents and umlaut spelling folded
        // as the search folds them), whose key differs: "Die Post"
        // slugs to "diepost" while the company is "post" (#1251); POST /companies calls it a duplicate.
        // Last, an old key or display name kept as an alias after a merge or rename (#1251), so the
        // duplicate is not created again.
        Optional<Company> existing = companyRepository.findByName(companyName)
                .or(() -> companyRepository.findByFoldedDisplayName(displayName.strip()))
                .or(() -> aliasService.resolve(displayName));

        if (existing.isPresent()) {
            log.debug("Company found for display name {}: {}", displayName, existing.get().getName());
            return new Resolved(existing.get(), false);
        }

        // Create new company
        log.info("Creating new company: {} (display: {})", companyName, displayName);

        String currentUsername = securityContextHelper.getCurrentUsername();

        Company company = Company.builder()
                .name(companyName)  // Short slug (e.g., "testco")
                .displayName(displayName)  // Full name (e.g., "Test Co")
                .isVerified(false)
                .createdBy(currentUsername != null ? Actors.forUsername(currentUsername) : "anonymous")
                .build();

        Company savedCompany;
        try {
            savedCompany = companyInserter.insert(company);
        } catch (DataIntegrityViolationException e) {
            // A concurrent get-or-create of the same name committed between the read above and this
            // insert (unique companies.name). The insert ran in its own transaction, so this one can
            // still read: return that company, as get-or-create is idempotent (200, not created).
            Company concurrent = companyRepository.findByName(companyName).orElseThrow(() -> e);
            log.info("Company {} created concurrently; returning it", companyName);
            return new Resolved(concurrent, false);
        }

        log.info("Company created: {} (name: {}, display: {})",
                 savedCompany.getId(), savedCompany.getName(), savedCompany.getDisplayName());

        return new Resolved(savedCompany, true);
    }

    /**
     * Retrieves company by name
     * AC4: Company retrieval
     * Story 1.16.2: Use company name as identifier instead of UUID
     */
    @Transactional(readOnly = true)
    public CompanyResponse getCompanyByName(String name) {
        Company company = companyRepository.findByName(name)
                .orElseThrow(() -> new CompanyNotFoundException(name));
        return mapToResponse(company);
    }

    /**
     * Same lookup without throwing. Callers that tolerate a missing company must use this: a
     * {@link CompanyNotFoundException} thrown through this transactional bean marks the caller's
     * transaction rollback-only, even when the caller catches it (#1142).
     */
    @Transactional(readOnly = true)
    public Optional<CompanyResponse> findCompanyByName(String name) {
        return companyRepository.findByName(name).map(this::mapToResponse);
    }

    /**
     * Retrieves all companies
     * AC4: Company listing
     */
    @Transactional(readOnly = true)
    public List<CompanyResponse> getAllCompanies() {
        return companyRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Updates company details
     * AC4: Company update
     * AC8: Invalidates search cache
     * Story 1.16.2: Use company name as identifier instead of UUID
     */
    public CompanyResponse updateCompany(String name, UpdateCompanyRequest request) {
        log.info("Updating company: {}", name);

        // Locked: a rename moves the key other rows hold, and must not interleave with another
        Company company = companyRepository.findByNameForUpdate(name)
                .orElseThrow(() -> new CompanyNotFoundException(name));
        String oldKey = company.getName();
        String oldDisplayName = company.getDisplayName();
        String newKey = request.getName();
        boolean rename = newKey != null && !newKey.equals(oldKey);

        // Update only provided fields
        if (rename && !newKey.matches(SlugGenerationService.COMPANY_NAME_PATTERN)) {
            // The key is the meaningful ID other services hold: it keeps the slug shape (owner 2026-11-09)
            throw new ValidationException("name",
                    "Company name (key) must be 1 to 12 letters or digits: " + newKey);
        }
        if (rename && companyRepository.existsByName(newKey)) {
            // Check for duplicate company name if name is being changed (AC3)
            throw new CompanyValidationException("Company with name '" + newKey + "' already exists");
        }
        if (rename && aliasService.ownerOfKey(newKey).filter(owner -> !owner.equals(company.getId())).isPresent()) {
            // An old key of another company still leads there (#1251)
            throw new CompanyValidationException("Company name '" + newKey + "' is an old name of another company");
        }
        boolean displayNameChanges = request.getDisplayName() != null
                && !request.getDisplayName().strip().equalsIgnoreCase(String.valueOf(oldDisplayName));
        if (displayNameChanges && aliasService.ownerOfDisplayName(request.getDisplayName())
                .filter(owner -> !owner.equals(company.getId())).isPresent()) {
            throw new CompanyValidationException("Company name '" + request.getDisplayName()
                    + "' is an old name of another company");
        }
        if (request.getDisplayName() != null) {
            company.setDisplayName(request.getDisplayName());
        }
        if (request.getWebsite() != null) {
            company.setWebsite(request.getWebsite());
        }
        if (request.getIndustry() != null) {
            company.setIndustry(request.getIndustry());
        }
        if (request.getDescription() != null) {
            company.setDescription(request.getDescription());
        }

        company.setUpdatedAt(Instant.now());
        // Flush so that @PreUpdate has set the updatedAt the row gets before the response and the
        // logo handling read it.
        Company updatedCompany = companyRepository.saveAndFlush(company);

        // Handle logo changes (Story 1.16.3: Generic File Upload)
        // Check if logoUploadId was provided in the request (including empty string to clear it)
        if (request.getLogoUploadId() != null) {
            if (request.getLogoUploadId().equals(updatedCompany.getLogoFileId())) {
                log.debug("Logo upload {} is already the logo of {}", request.getLogoUploadId(),
                        updatedCompany.getName());
            } else if (!request.getLogoUploadId().isBlank()) {
                // A new logo: a CONFIRMED company-logo upload (Story 17.11; 404/409 otherwise)
                setLogo(updatedCompany, uploadService.associate(CompanyLogoUploadHandler.PURPOSE,
                        request.getLogoUploadId(), updatedCompany.getName()));
                updatedCompany = companyRepository.saveAndFlush(updatedCompany);
                log.info("Logo updated for company: {}, logoUrl: {}", updatedCompany.getName(),
                        updatedCompany.getLogoUrl());
            } else {
                // Clear logo (blank/empty string means remove)
                log.info("Clearing logo for company: {}", updatedCompany.getName());
                updatedCompany.setLogoUrl(null);
                updatedCompany.setLogoS3Key(null);
                updatedCompany.setLogoFileId(null);
                updatedCompany = companyRepository.saveAndFlush(updatedCompany);
            }
        }

        // The rename is the last step: the fields and the logo (under the old key, whose upload the
        // move carries along) cannot fail any more once other services have moved their rows (#1251)
        if (rename) {
            updatedCompany = rename(updatedCompany, oldKey, oldDisplayName, newKey);
        }

        // Invalidate search cache (AC8)
        searchService.invalidateCache();

        log.info("Company updated successfully: {}", name);
        return mapToResponse(updatedCompany);
    }

    /**
     * #1251: every reference moves to the new key first (EMS and PCS through the contract, then the
     * CUMS rows, {@link CompanyKeyMover}), then the company takes the key and its old key and display
     * name become an alias. A failure here leaves the company under its old key; once the holders have
     * moved, it is reported as {@link CompanyKeyMover.MoveIncompleteException} (502), and the same rename
     * again finishes the move.
     */
    private Company rename(Company company, String oldKey, String oldDisplayName, String newKey) {
        String performedBy = securityContextHelper.getCurrentActor();
        keyMover.move(oldKey, newKey, performedBy, CompanyReferenceContract.Trigger.RENAME);
        try {
            aliasService.forget(newKey); // its own old key: the company takes it back
            company.setName(newKey);
            Company renamed = companyRepository.saveAndFlush(company);
            keyMover.keepAsAlias(newKey, oldKey, oldDisplayName, CompanyAlias.Reason.RENAME, performedBy);
            return renamed;
        } catch (RuntimeException e) {
            throw new CompanyKeyMover.MoveIncompleteException(
                    "The references of " + oldKey + " moved to " + newKey + ", but the rename did not complete", e);
        }
    }

    /**
     * Deletes company
     * AC4: Company deletion
     * AC8: Invalidates search cache
     * Story 1.16.2: Use company name as identifier instead of UUID
     */
    public void deleteCompany(String name) {
        log.info("Deleting company: {}", name);

        Company company = companyRepository.findByName(name)
                .orElseThrow(() -> new CompanyNotFoundException(name));

        companyRepository.delete(company);

        // Invalidate search cache (AC8)
        searchService.invalidateCache();

        log.info("Company deleted successfully: {}", name);
    }

    /**
     * Marks company as verified
     * AC6: Company verification workflow
     * Story 1.16.2: Use company name as identifier instead of UUID
     */
    public CompanyResponse markAsVerified(String name) {
        log.info("Marking company as verified: {}", name);

        Company company = companyRepository.findByName(name)
                .orElseThrow(() -> new CompanyNotFoundException(name));

        company.markAsVerified();
        // Flush: @PreUpdate sets the stored updatedAt, which the response and the event carry.
        Company verifiedCompany = companyRepository.saveAndFlush(company);

        log.info("Company marked as verified: {}", name);
        return mapToResponse(verifiedCompany);
    }

    /**
     * Verifies a company (public API method)
     * AC13: Company verification workflow endpoint
     * Idempotent operation - can be called multiple times safely
     * Story 1.16.2: Use company name as identifier instead of UUID
     */
    public CompanyResponse verifyCompany(String name) {
        return markAsVerified(name);
    }

    /**
     * Maps Company entity to CompanyResponse DTO
     * Story 1.16.2: Use company name as unique identifier (no separate id field)
     */
    private CompanyResponse mapToResponse(Company company) {
        // Build CompanyLogo if logo URL is present (generated DTO uses URI; entity stores String)
        CompanyLogo logo = null;
        if (company.getLogoUrl() != null) {
            logo = CompanyLogo.builder()
                    .url(toUri(company.getLogoUrl(), company.getName()))
                    .s3Key(company.getLogoS3Key())
                    .fileId(company.getLogoFileId())
                    .build();
        }

        return CompanyResponse.builder()
                .name(company.getName())  // Story 1.16.2: name is the unique identifier
                .displayName(company.getDisplayName())
                .website(company.getWebsite())
                .industry(company.getIndustry())
                .description(company.getDescription())
                .isVerified(company.isVerified())
                .createdAt(company.getCreatedAt() != null
                        ? company.getCreatedAt().atOffset(ZoneOffset.UTC) : null)
                .updatedAt(company.getUpdatedAt() != null
                        ? company.getUpdatedAt().atOffset(ZoneOffset.UTC) : null)
                .createdBy(company.getCreatedBy())
                .logo(logo)
                .build();
    }

    /**
     * Convert a nullable stored URL string to a {@link URI} for the generated DTOs.
     * Mirrors {@code UserResponseMapper}: returns {@code null} on null/blank or a malformed
     * value (logged) rather than throwing, so a bad row never 500s a read.
     */
    private static URI toUri(String value, String companyName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return URI.create(value);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid URL for company {}: {}", companyName, value);
            return null;
        }
    }

    /**
     * #1246: gives the company the logo of an accepted logo request. Same effect as an organizer
     * setting the logo in the company edit: final key, cache invalidation.
     *
     * @return the company's new logo URL
     */
    public String applyRequestedLogo(String companyName, String uploadId) {
        Company company = companyRepository.findByName(companyName)
                .orElseThrow(() -> new CompanyNotFoundException(companyName));
        CompanyUserUpload upload = uploadService.find(CompanyLogoUploadHandler.PURPOSE, uploadId)
                .orElseThrow(() -> new CompanyValidationException("Logo upload not found: " + uploadId));
        UploadRecord moved = uploadService.reassign(CompanyLogoUploadHandler.PURPOSE, uploadId,
                CompanyLogoUploadHandler.TARGET_TYPE, company.getName(),
                CompanyLogoUploadHandler.logoKey(company.getName(), upload));
        return logoChanged(company, moved);
    }

    /**
     * Story 17.11: an upload confirmed with the target {@code companyName} becomes the company logo,
     * with the same effects as the edit (cache invalidation).
     */
    public String applyLogo(String companyName, UploadRecord upload) {
        Company company = companyRepository.findByName(companyName)
                .orElseThrow(() -> new CompanyNotFoundException(companyName));
        return logoChanged(company, upload);
    }

    private String logoChanged(Company company, UploadRecord upload) {
        setLogo(company, upload);
        company.setUpdatedAt(Instant.now());
        Company saved = companyRepository.save(company);
        searchService.invalidateCache();
        log.info("Logo applied to company: {}, logoUrl: {}", saved.getName(), saved.getLogoUrl());
        return saved.getLogoUrl();
    }

    private static void setLogo(Company company, UploadRecord upload) {
        company.setLogoUrl(upload.getFileUrl());
        company.setLogoS3Key(upload.getS3Key());
        company.setLogoFileId(upload.getUploadId());
    }
}
