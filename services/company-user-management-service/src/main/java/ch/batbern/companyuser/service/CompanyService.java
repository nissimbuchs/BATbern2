package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.domain.Logo;
import ch.batbern.companyuser.dto.CompanyLogo;
import ch.batbern.companyuser.dto.CompanyResponse;
import ch.batbern.companyuser.dto.CreateCompanyRequest;
import ch.batbern.companyuser.dto.UpdateCompanyRequest;
import ch.batbern.companyuser.event.*;
import ch.batbern.companyuser.exception.CompanyNotFoundException;
import ch.batbern.companyuser.exception.CompanyValidationException;
import ch.batbern.companyuser.exception.InvalidUIDException;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.companyuser.repository.LogoRepository;
import ch.batbern.companyuser.security.SecurityContextHelper;
import ch.batbern.shared.events.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for company management business logic
 * AC4: Company CRUD operations
 * AC7: Domain event publishing
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
    private final LogoRepository logoRepository;
    private final SwissUIDValidationService uidValidationService;
    private final DomainEventPublisher eventPublisher;
    private final CompanySearchService searchService;
    private final SecurityContextHelper securityContextHelper;
    private final GenericLogoService genericLogoService;

    /**
     * Creates a new company with validation
     * AC4: Company creation
     * AC7: Publishes CompanyCreatedEvent
     * AC8: Invalidates search cache
     */
    public CompanyResponse createCompany(CreateCompanyRequest request) {
        log.info("Creating company: {}", request.getName());

        // Validate Swiss UID if provided (AC3)
        if (request.getSwissUID() != null) {
            if (!uidValidationService.isValidUID(request.getSwissUID())) {
                throw new InvalidUIDException(request.getSwissUID());
            }
        }

        // Check for duplicate company name (AC3)
        if (companyRepository.existsByName(request.getName())) {
            throw new CompanyValidationException("Company with name '" + request.getName() + "' already exists");
        }

        // Build company entity (AC10: Get username from security context)
        // Story 1.16.2: Use username instead of UUID for createdBy field
        String currentUsername = securityContextHelper.getCurrentUsername();

        Company company = Company.builder()
                .name(request.getName())
                .displayName(request.getDisplayName() != null ? request.getDisplayName() : request.getName())
                .swissUID(request.getSwissUID())
                .website(request.getWebsite())
                .industry(request.getIndustry())
                .description(request.getDescription())
                .isVerified(false)
                .createdBy(currentUsername)
                .build();

        Company savedCompany = companyRepository.save(company);

        // Associate logo if provided (Story 1.16.3: Generic File Upload Service)
        if (request.getLogoUploadId() != null) {
            try {
                String finalS3Key = generateFinalS3Key(savedCompany.getName(), request.getLogoUploadId());
                String logoUrl = genericLogoService.associateLogoWithEntity(
                        request.getLogoUploadId(),
                        "COMPANY",
                        savedCompany.getName(),
                        finalS3Key
                );

                savedCompany.setLogoUrl(logoUrl);
                savedCompany.setLogoS3Key(finalS3Key);
                savedCompany.setLogoFileId(request.getLogoUploadId());
                companyRepository.save(savedCompany);

                log.info("Logo associated with company: {}, logoUrl: {}", savedCompany.getName(), logoUrl);
            } catch (Exception e) {
                log.error("Failed to associate logo with company: {}", savedCompany.getName(), e);
                // Don't fail company creation if logo association fails
                // Logo can be added later via update
            }
        }

        // Invalidate search cache (AC8)
        searchService.invalidateCache();

        // Publish domain event (AC7) using shared-kernel DomainEventPublisher
        // Story 1.16.2: Use company.name as aggregate ID, username as String
        CompanyCreatedEvent event = new CompanyCreatedEvent(
            savedCompany.getName(),  // companyName as aggregate ID
            savedCompany.getName(),
            savedCompany.getDisplayName(),
            savedCompany.getSwissUID(),
            savedCompany.getWebsite(),
            savedCompany.getIndustry(),
            savedCompany.getDescription(),
            savedCompany.getCreatedBy(),
            savedCompany.getCreatedAt(),
            currentUsername  // username as String
        );
        eventPublisher.publish(event);

        log.info("Company created successfully: {}", savedCompany.getId());
        return mapToResponse(savedCompany);
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
     * AC7: Publishes CompanyUpdatedEvent
     * AC8: Invalidates search cache
     * Story 1.16.2: Use company name as identifier instead of UUID
     */
    public CompanyResponse updateCompany(String name, UpdateCompanyRequest request) {
        log.info("Updating company: {}", name);

        Company company = companyRepository.findByName(name)
                .orElseThrow(() -> new CompanyNotFoundException(name));

        // Update only provided fields
        if (request.getName() != null) {
            // Check for duplicate company name if name is being changed (AC3)
            if (!request.getName().equals(company.getName()) &&
                companyRepository.existsByName(request.getName())) {
                throw new CompanyValidationException("Company with name '" + request.getName() + "' already exists");
            }
            company.setName(request.getName());
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
        Company updatedCompany = companyRepository.save(company);

        // Handle logo changes (Story 1.16.3: Generic File Upload)
        // Check if logoUploadId was provided in the request (including empty string to clear it)
        if (request.getLogoUploadId() != null) {
            if (!request.getLogoUploadId().isBlank()) {
                // Associate new logo
                try {
                    String finalS3Key = generateFinalS3Key(updatedCompany.getName(), request.getLogoUploadId());
                    String logoUrl = genericLogoService.associateLogoWithEntity(
                            request.getLogoUploadId(),
                            "COMPANY",
                            updatedCompany.getName(),
                            finalS3Key
                    );
                    updatedCompany.setLogoUrl(logoUrl);
                    updatedCompany.setLogoS3Key(finalS3Key);
                    updatedCompany.setLogoFileId(request.getLogoUploadId());
                    updatedCompany = companyRepository.save(updatedCompany);
                    log.info("Logo updated for company: {}, logoUrl: {}", updatedCompany.getName(), logoUrl);
                } catch (Exception e) {
                    log.error("Failed to associate logo with company: {}", updatedCompany.getName(), e);
                    // Don't fail company update if logo association fails
                }
            } else {
                // Clear logo (blank/empty string means remove)
                log.info("Clearing logo for company: {}", updatedCompany.getName());
                updatedCompany.setLogoUrl(null);
                updatedCompany.setLogoS3Key(null);
                updatedCompany.setLogoFileId(null);
                updatedCompany = companyRepository.save(updatedCompany);
            }
        }

        // Invalidate search cache (AC8)
        searchService.invalidateCache();

        // Publish domain event (AC7) using shared-kernel DomainEventPublisher
        // Story 1.16.2: Use company.name as aggregate ID, username as String
        CompanyUpdatedEvent event = new CompanyUpdatedEvent(
            updatedCompany.getName(),  // companyName as aggregate ID
            updatedCompany.getName(),
            updatedCompany.getDisplayName(),
            updatedCompany.getSwissUID(),
            updatedCompany.getWebsite(),
            updatedCompany.getIndustry(),
            updatedCompany.getDescription(),
            updatedCompany.getUpdatedAt(),
            securityContextHelper.getCurrentUserId()  // username as String
        );
        eventPublisher.publish(event);

        log.info("Company updated successfully: {}", name);
        return mapToResponse(updatedCompany);
    }

    /**
     * Deletes company
     * AC4: Company deletion
     * AC7: Publishes CompanyDeletedEvent
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

        // Publish domain event (AC7) using shared-kernel DomainEventPublisher
        // Story 1.16.2: Use company.name as aggregate ID, username as String
        CompanyDeletedEvent event = new CompanyDeletedEvent(
            company.getName(),  // companyName as aggregate ID
            company.getName(),
            Instant.now(),
            securityContextHelper.getCurrentUserId()  // username as String
        );
        eventPublisher.publish(event);

        log.info("Company deleted successfully: {}", name);
    }

    /**
     * Marks company as verified
     * AC6: Company verification workflow
     * AC7: Publishes CompanyVerifiedEvent
     * Story 1.16.2: Use company name as identifier instead of UUID
     */
    public CompanyResponse markAsVerified(String name) {
        log.info("Marking company as verified: {}", name);

        Company company = companyRepository.findByName(name)
                .orElseThrow(() -> new CompanyNotFoundException(name));

        company.markAsVerified();
        Company verifiedCompany = companyRepository.save(company);

        // Publish domain event (AC7) using shared-kernel DomainEventPublisher
        // Story 1.16.2: Use company.name as aggregate ID, username as String
        CompanyVerifiedEvent event = new CompanyVerifiedEvent(
            verifiedCompany.getName(),  // companyName as aggregate ID
            verifiedCompany.getName(),
            verifiedCompany.getSwissUID(),
            verifiedCompany.isVerified(),
            verifiedCompany.getUpdatedAt(),
            securityContextHelper.getCurrentUserId()  // username as String
        );
        eventPublisher.publish(event);

        log.info("Company marked as verified: {}", name);
        return mapToResponse(verifiedCompany);
    }

    /**
     * Verifies a company (public API method)
     * AC13: Company verification workflow endpoint
     * AC7: Publishes CompanyVerifiedEvent
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
        // Build CompanyLogo if logo URL is present
        CompanyLogo logo = null;
        if (company.getLogoUrl() != null) {
            logo = CompanyLogo.builder()
                    .url(company.getLogoUrl())
                    .s3Key(company.getLogoS3Key())
                    .fileId(company.getLogoFileId())
                    .build();
        }

        return CompanyResponse.builder()
                .name(company.getName())  // Story 1.16.2: name is the unique identifier
                .displayName(company.getDisplayName())
                .swissUID(company.getSwissUID())
                .website(company.getWebsite())
                .industry(company.getIndustry())
                .description(company.getDescription())
                .verified(company.isVerified())
                .createdAt(company.getCreatedAt())
                .updatedAt(company.getUpdatedAt())
                .createdBy(company.getCreatedBy())
                .logo(logo)
                .build();
    }

    /**
     * Generate final S3 key for company logo
     * Pattern: logos/{year}/companies/{company-name}/logo-{uploadId}.{ext}
     * Story 1.16.3: Generic File Upload Service
     *
     * Fetches Logo entity to get actual file extension (png, jpg, jpeg, svg)
     * instead of hardcoding to .png
     */
    private String generateFinalS3Key(String companyName, String uploadId) {
        // Fetch Logo to get file extension (preserve original file type)
        Logo logo = logoRepository.findByUploadId(uploadId)
                .orElseThrow(() -> new RuntimeException("Logo not found for uploadId: " + uploadId));

        String fileExtension = logo.getFileExtension();
        log.debug("Retrieved file extension from Logo: {}", fileExtension);

        int year = java.time.LocalDate.now().getYear();
        return String.format("logos/%d/companies/%s/logo-%s.%s",
                year, companyName, uploadId, fileExtension);
    }
}
