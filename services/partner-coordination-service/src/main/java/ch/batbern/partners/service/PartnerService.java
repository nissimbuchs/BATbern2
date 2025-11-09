package ch.batbern.partners.service;

import ch.batbern.partners.client.CompanyServiceClient;
import ch.batbern.partners.client.company.dto.CompanyResponse;
import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnershipLevel;
import ch.batbern.partners.dto.generated.*;
import ch.batbern.partners.events.PartnerCreatedEvent;
import ch.batbern.partners.events.PartnerUpdatedEvent;
import ch.batbern.partners.exception.PartnerAlreadyExistsException;
import ch.batbern.partners.exception.PartnerNotFoundException;
import ch.batbern.partners.repository.PartnerRepository;
import ch.batbern.partners.security.SecurityContextHelper;
import ch.batbern.shared.events.DomainEventPublisher;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service layer for Partner CRUD operations.
 *
 * Implements business logic for:
 * - Partner creation with Company Service validation
 * - Partner retrieval with HTTP enrichment
 * - Partner updates and soft delete
 * - Filtering and pagination
 *
 * ADR-003 Compliance: Validates companyName via HTTP call to Company Service
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PartnerService {

    private final PartnerRepository partnerRepository;
    private final CompanyServiceClient companyServiceClient;
    private final DomainEventPublisher eventPublisher;
    private final SecurityContextHelper securityContextHelper;

    /**
     * List all partners with optional filtering.
     *
     * @param filter Filter expression (e.g., "partnershipLevel=gold,isActive=true")
     * @param sort Sort expression (e.g., "partnershipLevel,desc")
     * @param page Page number (0-indexed)
     * @param size Page size
     * @return List of partner responses
     */
    @Timed(value = "partner.list", description = "Time taken to list partners")
    public List<PartnerResponse> listPartners(String filter, String sort, int page, int size) {
        log.debug("Listing partners with filter: {}, sort: {}, page: {}, size: {}", filter, sort, page, size);

        List<Partner> partners;

        // Simple filtering logic
        if (filter != null && filter.contains("partnershipLevel=")) {
            String level = extractFilterValue(filter, "partnershipLevel");
            PartnershipLevel partnershipLevel = PartnershipLevel.valueOf(level.toUpperCase());
            partners = partnerRepository.findByPartnershipLevel(partnershipLevel);
        } else if (filter != null && filter.contains("isActive=")) {
            boolean isActive = Boolean.parseBoolean(extractFilterValue(filter, "isActive"));
            partners = isActive
                    ? partnerRepository.findActivePartners(java.time.LocalDate.now())
                    : partnerRepository.findInactivePartners(java.time.LocalDate.now());
        } else {
            partners = partnerRepository.findAll();
        }

        return partners.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get partner by company name with optional HTTP enrichment.
     *
     * @param companyName Company name (meaningful ID)
     * @param includes Set of resources to include (e.g., "company", "contacts")
     * @return Partner response
     */
    @Timed(value = "partner.get", description = "Time taken to get partner by company name")
    public PartnerResponse getPartnerByCompanyName(String companyName, Set<String> includes) {
        log.debug("Getting partner by company name: {} with includes: {}", companyName, includes);

        Partner partner = partnerRepository.findByCompanyName(companyName)
                .orElseThrow(() -> new PartnerNotFoundException("Partner not found for company: " + companyName));

        PartnerResponse response = mapToResponse(partner);

        // HTTP enrichment per ADR-004
        if (includes.contains("company")) {
            CompanyResponse companyDTO = companyServiceClient.getCompany(companyName);
            CompanyInfo companyInfo = new CompanyInfo();
            companyInfo.setCompanyName(companyDTO.getName());  // Generated DTO uses getName()
            companyInfo.setDisplayName(companyDTO.getDisplayName());
            // CompanyLogo is an object - extract URL if present
            if (companyDTO.getLogo() != null && companyDTO.getLogo().getUrl() != null) {
                companyInfo.setLogoUrl(companyDTO.getLogo().getUrl().toString());
            }
            response.setCompany(companyInfo);
        }

        return response;
    }

    /**
     * Create new partnership with Company Service validation.
     *
     * @param request Create partner request
     * @return Created partner response
     */
    @Timed(value = "partner.create", description = "Time taken to create partner")
    public PartnerResponse createPartner(CreatePartnerRequest request) {
        log.info("Creating partner for company: {}", request.getCompanyName());

        // Check if partner already exists
        Optional<Partner> existing = partnerRepository.findByCompanyName(request.getCompanyName());
        if (existing.isPresent()) {
            throw new PartnerAlreadyExistsException("Partner already exists for company: " + request.getCompanyName());
        }

        // Validate company exists via HTTP call (ADR-003)
        CompanyResponse companyDTO = companyServiceClient.getCompany(request.getCompanyName());
        log.debug("Company validated: {}", companyDTO.getName());

        // Create partner entity
        Partner partner = new Partner();
        partner.setCompanyName(request.getCompanyName());
        partner.setPartnershipLevel(mapPartnershipLevel(request.getPartnershipLevel()));
        partner.setPartnershipStartDate(request.getPartnershipStartDate());
        partner.setPartnershipEndDate(request.getPartnershipEndDate());
        partner.setCreatedAt(Instant.now());
        partner.setUpdatedAt(Instant.now());

        Partner saved = partnerRepository.save(partner);
        log.info("Partner created with ID: {}", saved.getId());

        // Publish domain event
        publishPartnerCreatedEvent(saved);

        return mapToResponse(saved);
    }

    /**
     * Update partner details.
     *
     * @param companyName Company name
     * @param request Update request
     * @return Updated partner response
     */
    @Timed(value = "partner.update", description = "Time taken to update partner")
    public PartnerResponse updatePartner(String companyName, UpdatePartnerRequest request) {
        log.info("Updating partner for company: {}", companyName);

        Partner partner = partnerRepository.findByCompanyName(companyName)
                .orElseThrow(() -> new PartnerNotFoundException("Partner not found for company: " + companyName));

        // Update fields if provided
        if (request.getPartnershipLevel() != null) {
            partner.setPartnershipLevel(mapPartnershipLevel(request.getPartnershipLevel()));
        }
        if (request.getPartnershipEndDate() != null) {
            partner.setPartnershipEndDate(request.getPartnershipEndDate());
        }
        partner.setUpdatedAt(Instant.now());

        Partner saved = partnerRepository.save(partner);
        log.info("Partner updated: {}", saved.getId());

        // Publish domain event
        publishPartnerUpdatedEvent(saved);

        return mapToResponse(saved);
    }

    /**
     * Soft delete partner (deactivate by setting end date).
     *
     * @param companyName Company name
     */
    @Timed(value = "partner.delete", description = "Time taken to soft delete partner")
    public void deletePartner(String companyName) {
        log.info("Soft deleting partner for company: {}", companyName);

        Partner partner = partnerRepository.findByCompanyName(companyName)
                .orElseThrow(() -> new PartnerNotFoundException("Partner not found for company: " + companyName));

        // Soft delete: set end date to today (makes isActive() return false since endDate must be > now)
        partner.setPartnershipEndDate(java.time.LocalDate.now());
        partner.setUpdatedAt(Instant.now());

        partnerRepository.save(partner);
        log.info("Partner soft deleted: {}", partner.getId());

        // Publish domain event
        publishPartnerUpdatedEvent(partner);
    }

    // Helper methods

    private PartnerResponse mapToResponse(Partner partner) {
        PartnerResponse response = new PartnerResponse();
        response.setId(partner.getId());  // UUID, not String
        response.setCompanyName(partner.getCompanyName());
        response.setPartnershipLevel(mapPartnershipLevelToDto(partner.getPartnershipLevel()));
        response.setPartnershipStartDate(partner.getPartnershipStartDate());
        response.setPartnershipEndDate(partner.getPartnershipEndDate());
        response.setIsActive(partner.isActive());
        return response;
    }

    private PartnershipLevel mapPartnershipLevel(ch.batbern.partners.dto.generated.PartnershipLevel level) {
        return PartnershipLevel.valueOf(level.name().toUpperCase());
    }

    private ch.batbern.partners.dto.generated.PartnershipLevel mapPartnershipLevelToDto(PartnershipLevel level) {
        // Both enums have same constant names (BRONZE, SILVER, GOLD, PLATINUM, STRATEGIC)
        return ch.batbern.partners.dto.generated.PartnershipLevel.valueOf(level.name());
    }

    private String extractFilterValue(String filter, String key) {
        String[] parts = filter.split(",");
        for (String part : parts) {
            if (part.startsWith(key + "=")) {
                return part.substring(key.length() + 1);
            }
        }
        return null;
    }

    private void publishPartnerCreatedEvent(Partner partner) {
        String userId = securityContextHelper.getCurrentUserIdOrSystem();

        PartnerCreatedEvent event = new PartnerCreatedEvent(
                partner.getId(),
                partner.getCompanyName(),
                partner.getPartnershipLevel().name(),
                userId
        );
        event.setCorrelationId(UUID.randomUUID().toString());
        event.setCausationId(UUID.randomUUID().toString());

        eventPublisher.publish(event);
        log.debug("Published PartnerCreatedEvent for partner: {} by user: {}", partner.getId(), userId);
    }

    private void publishPartnerUpdatedEvent(Partner partner) {
        String userId = securityContextHelper.getCurrentUserIdOrSystem();

        PartnerUpdatedEvent event = new PartnerUpdatedEvent(
                partner.getId(),
                partner.getCompanyName(),
                partner.getPartnershipLevel().name(),
                partner.isActive(),
                userId
        );
        event.setCorrelationId(UUID.randomUUID().toString());
        event.setCausationId(UUID.randomUUID().toString());

        eventPublisher.publish(event);
        log.debug("Published PartnerUpdatedEvent for partner: {} by user: {}", partner.getId(), userId);
    }
}
