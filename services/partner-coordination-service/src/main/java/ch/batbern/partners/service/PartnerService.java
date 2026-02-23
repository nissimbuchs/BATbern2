package ch.batbern.partners.service;

import ch.batbern.partners.client.CompanyServiceClient;
import ch.batbern.partners.client.company.dto.CompanyResponse;
import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnershipLevel;
import ch.batbern.partners.dto.generated.CompanyInfo;
import ch.batbern.partners.dto.generated.CreatePartnerRequest;
import ch.batbern.partners.dto.generated.PartnerContactResponse;
import ch.batbern.partners.dto.generated.PartnerResponse;
import ch.batbern.partners.dto.generated.PartnerStatistics;
import ch.batbern.partners.dto.generated.PartnerStatisticsTierCounts;
import ch.batbern.partners.dto.generated.UpdatePartnerRequest;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
    private final PartnerContactService partnerContactService;

    /**
     * List all partners with optional filtering and HTTP enrichment.
     *
     * @param filter Filter expression (e.g., "partnershipLevel=gold,isActive=true")
     * @param sort Sort expression (e.g., "partnershipLevel,desc")
     * @param page Page number (0-indexed)
     * @param size Page size
     * @param includes Set of resources to include (e.g., "company", "contacts")
     * @return List of partner responses
     */
    @Timed(value = "partner.list", description = "Time taken to list partners")
    public List<PartnerResponse> listPartners(String filter, String sort, int page, int size,
            Set<String> includes) {
        log.debug("Listing partners with filter: {}, sort: {}, page: {}, size: {}, includes: {}",
                filter, sort, page, size, includes);

        List<Partner> partners;

        // Simple filtering logic - support both : and = syntax
        if (filter != null && (filter.contains("partnershipLevel:") || filter.contains("partnershipLevel="))) {
            String level = extractFilterValue(filter, "partnershipLevel");
            PartnershipLevel partnershipLevel = PartnershipLevel.valueOf(level.toUpperCase());
            partners = partnerRepository.findByPartnershipLevel(partnershipLevel);
        } else if (filter != null && (filter.contains("isActive:") || filter.contains("isActive="))) {
            boolean isActive = Boolean.parseBoolean(extractFilterValue(filter, "isActive"));
            partners = isActive
                    ? partnerRepository.findActivePartners(java.time.LocalDate.now())
                    : partnerRepository.findInactivePartners(java.time.LocalDate.now());
        } else {
            partners = partnerRepository.findAll();
        }

        // Sort alphabetically by company name for better UX
        return partners.stream()
                .sorted((p1, p2) -> {
                    String name1 = p1.getCompanyName() != null ? p1.getCompanyName() : "";
                    String name2 = p2.getCompanyName() != null ? p2.getCompanyName() : "";
                    return name1.compareToIgnoreCase(name2);
                })
                .map(partner -> {
                    PartnerResponse response = mapToResponse(partner);

                    // HTTP enrichment per ADR-004
                    if (includes != null && includes.contains("company")) {
                        enrichWithCompanyData(response, partner.getCompanyName());
                    }
                    if (includes != null && includes.contains("contacts")) {
                        enrichWithContacts(response, partner.getCompanyName());
                    }

                    return response;
                })
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
            enrichWithCompanyData(response, companyName);
        }
        if (includes.contains("contacts")) {
            enrichWithContacts(response, companyName);
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
        // ADR-003: companyName is the meaningful ID, no UUID exposed in API
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

    /**
     * Enrich partner response with company data via HTTP enrichment (ADR-004).
     *
     * @param response Partner response to enrich
     * @param companyName Company name to fetch
     */
    private void enrichWithCompanyData(PartnerResponse response, String companyName) {
        try {
            CompanyResponse companyDTO = companyServiceClient.getCompany(companyName);
            CompanyInfo companyInfo = new CompanyInfo();
            companyInfo.setCompanyName(companyDTO.getName());
            companyInfo.setDisplayName(companyDTO.getDisplayName());
            companyInfo.setName(companyDTO.getName());
            if (companyDTO.getIndustry() != null) {
                companyInfo.setIndustry(companyDTO.getIndustry());
            }
            if (companyDTO.getWebsite() != null) {
                companyInfo.setWebsite(companyDTO.getWebsite().toString());
            }
            // CompanyLogo is an object - extract URL if present
            if (companyDTO.getLogo() != null && companyDTO.getLogo().getUrl() != null) {
                companyInfo.setLogoUrl(companyDTO.getLogo().getUrl().toString());
            }
            response.setCompany(companyInfo);
        } catch (Exception e) {
            log.warn("Failed to enrich partner {} with company data: {}", companyName, e.getMessage());
            // Continue without enrichment - fail gracefully
        }
    }

    /**
     * Enrich partner response with contacts via User Service (ADR-004).
     * Contacts are derived: any PARTNER user whose companyId matches.
     */
    private void enrichWithContacts(PartnerResponse response, String companyName) {
        try {
            List<PartnerContactResponse> contacts = partnerContactService.getPartnerContacts(companyName);
            response.setContacts(contacts);
        } catch (Exception e) {
            log.warn("Failed to enrich partner {} with contacts: {}", companyName, e.getMessage());
            // Continue without enrichment - fail gracefully
        }
    }

    private String extractFilterValue(String filter, String key) {
        String[] parts = filter.split(",");
        for (String part : parts) {
            // Support both colon (:) and equals (=) syntax for flexibility
            if (part.startsWith(key + ":")) {
                return part.substring(key.length() + 1);
            } else if (part.startsWith(key + "=")) {
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

    public PartnerStatistics getPartnerStatistics() {
        log.debug("Getting partner statistics");

        List<Partner> allPartners = partnerRepository.findAll();
        List<Partner> activePartners = allPartners.stream()
                .filter(Partner::isActive)
                .toList();

        // Count by tier
        int bronzeCount = (int) activePartners.stream()
                .filter(p -> p.getPartnershipLevel() == PartnershipLevel.BRONZE)
                .count();
        int silverCount = (int) activePartners.stream()
                .filter(p -> p.getPartnershipLevel() == PartnershipLevel.SILVER)
                .count();
        int goldCount = (int) activePartners.stream()
                .filter(p -> p.getPartnershipLevel() == PartnershipLevel.GOLD)
                .count();
        int platinumCount = (int) activePartners.stream()
                .filter(p -> p.getPartnershipLevel() == PartnershipLevel.PLATINUM)
                .count();
        int strategicCount = (int) activePartners.stream()
                .filter(p -> p.getPartnershipLevel() == PartnershipLevel.STRATEGIC)
                .count();

        PartnerStatisticsTierCounts tierCounts = new PartnerStatisticsTierCounts();
        tierCounts.setBRONZE(bronzeCount);
        tierCounts.setSILVER(silverCount);
        tierCounts.setGOLD(goldCount);
        tierCounts.setPLATINUM(platinumCount);
        tierCounts.setSTRATEGIC(strategicCount);

        PartnerStatistics statistics = new PartnerStatistics();
        statistics.setTotalPartners(allPartners.size());
        statistics.setActivePartners(activePartners.size());
        statistics.setTierCounts(tierCounts);

        return statistics;
    }
}
