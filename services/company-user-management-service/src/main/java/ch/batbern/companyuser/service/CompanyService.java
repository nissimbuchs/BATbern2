package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.dto.CompanyLogo;
import ch.batbern.companyuser.dto.CompanyResponse;
import ch.batbern.companyuser.dto.CreateCompanyRequest;
import ch.batbern.companyuser.dto.UpdateCompanyRequest;
import ch.batbern.companyuser.exception.CompanyNotFoundException;
import ch.batbern.companyuser.exception.CompanyValidationException;
import ch.batbern.companyuser.exception.InvalidUIDException;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.companyuser.security.SecurityContextHelper;
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
    private final SwissUIDValidationService uidValidationService;
    private final CompanyEventPublisher eventPublisher;
    private final CompanySearchService searchService;
    private final SecurityContextHelper securityContextHelper;

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

        // Build company entity (AC10: Get userId from security context)
        String currentUserId = securityContextHelper.getCurrentUserId();

        Company company = Company.builder()
                .name(request.getName())
                .displayName(request.getDisplayName() != null ? request.getDisplayName() : request.getName())
                .swissUID(request.getSwissUID())
                .website(request.getWebsite())
                .industry(request.getIndustry())
                .description(request.getDescription())
                .isVerified(false)
                .createdBy(currentUserId)
                .build();

        Company savedCompany = companyRepository.save(company);

        // Invalidate search cache (AC8)
        searchService.invalidateCache();

        // Publish domain event (AC7)
        eventPublisher.publishCompanyCreatedEvent(savedCompany);

        log.info("Company created successfully: {}", savedCompany.getId());
        return mapToResponse(savedCompany);
    }

    /**
     * Retrieves company by ID
     * AC4: Company retrieval
     */
    @Transactional(readOnly = true)
    public CompanyResponse getCompanyById(UUID id) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new CompanyNotFoundException(id.toString()));
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
     */
    public CompanyResponse updateCompany(UUID id, UpdateCompanyRequest request) {
        log.info("Updating company: {}", id);

        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new CompanyNotFoundException(id.toString()));

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

        // Invalidate search cache (AC8)
        searchService.invalidateCache();

        // Publish domain event (AC7)
        eventPublisher.publishCompanyUpdatedEvent(updatedCompany);

        log.info("Company updated successfully: {}", id);
        return mapToResponse(updatedCompany);
    }

    /**
     * Deletes company
     * AC4: Company deletion
     * AC7: Publishes CompanyDeletedEvent
     * AC8: Invalidates search cache
     */
    public void deleteCompany(UUID id) {
        log.info("Deleting company: {}", id);

        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new CompanyNotFoundException(id.toString()));

        companyRepository.delete(company);

        // Invalidate search cache (AC8)
        searchService.invalidateCache();

        // Publish domain event (AC7)
        eventPublisher.publishCompanyDeletedEvent(company);

        log.info("Company deleted successfully: {}", id);
    }

    /**
     * Marks company as verified
     * AC6: Company verification workflow
     * AC7: Publishes CompanyVerifiedEvent
     */
    public CompanyResponse markAsVerified(UUID id) {
        log.info("Marking company as verified: {}", id);

        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new CompanyNotFoundException(id.toString()));

        company.markAsVerified();
        Company verifiedCompany = companyRepository.save(company);

        // Publish domain event (AC7)
        eventPublisher.publishCompanyVerifiedEvent(verifiedCompany);

        log.info("Company marked as verified: {}", id);
        return mapToResponse(verifiedCompany);
    }

    /**
     * Verifies a company (public API method)
     * AC13: Company verification workflow endpoint
     * AC7: Publishes CompanyVerifiedEvent
     * Idempotent operation - can be called multiple times safely
     */
    public CompanyResponse verifyCompany(UUID id) {
        return markAsVerified(id);
    }

    /**
     * Maps Company entity to CompanyResponse DTO
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
                .id(company.getId())
                .name(company.getName())
                .displayName(company.getDisplayName())
                .swissUID(company.getSwissUID())
                .website(company.getWebsite())
                .industry(company.getIndustry())
                .description(company.getDescription())
                .isVerified(company.isVerified())
                .createdAt(company.getCreatedAt())
                .updatedAt(company.getUpdatedAt())
                .createdBy(company.getCreatedBy())
                .logo(logo)
                .build();
    }
}
