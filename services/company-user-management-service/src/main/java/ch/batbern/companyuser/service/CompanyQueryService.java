package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.dto.*;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.companyuser.specification.CompanySpecification;
import ch.batbern.shared.api.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Advanced query service for companies with filter, sort, pagination, and resource expansion
 * AC14: Filter, sort, pagination, field selection
 * AC15: Resource expansion (include=statistics,logo)
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class CompanyQueryService {

    private final CompanyRepository companyRepository;

    /**
     * Query companies with advanced filters, sorting, pagination, field selection, and resource expansion
     */
    public PaginatedCompanyResponse queryCompanies(
            String filterJson,
            String sortStr,
            Integer page,
            Integer limit,
            String fieldsStr,
            String includeStr) {

        log.debug("Querying companies - filter: {}, sort: {}, page: {}, limit: {}, fields: {}, include: {}",
                filterJson, sortStr, page, limit, fieldsStr, includeStr);

        // Parse query parameters
        FilterCriteria filterCriteria = FilterParser.parse(filterJson);
        List<SortCriteria> sortCriteria = SortParser.parse(sortStr);
        PaginationParams paginationParams = PaginationUtils.parseParams(page, limit);
        Set<String> selectedFields = FieldSelector.parse(fieldsStr);
        Set<String> includes = IncludeParser.parse(includeStr);

        // Build JPA Specification from FilterCriteria
        Specification<Company> spec = CompanySpecification.fromFilterCriteria(filterCriteria);

        // Build Pageable from sort and pagination
        Pageable pageable = buildPageable(sortCriteria, paginationParams);

        // Query database
        Page<Company> companyPage = companyRepository.findAll(spec, pageable);

        // Map to response DTOs with field selection and resource expansion
        List<CompanyResponse> companyResponses = companyPage.getContent().stream()
                .map(company -> mapToResponse(company, selectedFields, includes))
                .collect(Collectors.toList());

        // Generate pagination metadata
        PaginationMetadata paginationMetadata = PaginationUtils.generateMetadata(
                paginationParams.getPage(),
                paginationParams.getLimit(),
                companyPage.getTotalElements()
        );

        return PaginatedCompanyResponse.builder()
                .data(companyResponses)
                .pagination(paginationMetadata)
                .build();
    }

    /**
     * Build Spring Data Pageable from sort and pagination parameters
     */
    private Pageable buildPageable(List<SortCriteria> sortCriteria, PaginationParams paginationParams) {
        // Calculate zero-indexed offset for Spring Data (page parameter is 1-indexed)
        int zeroIndexedPage = paginationParams.getPage() - 1;

        // Build Sort from SortCriteria
        if (sortCriteria.isEmpty()) {
            // Default sort: createdAt DESC (most recent first)
            return PageRequest.of(zeroIndexedPage, paginationParams.getLimit(), Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        // Convert SortCriteria to Spring Data Sort
        List<Sort.Order> orders = sortCriteria.stream()
                .map(sc -> {
                    Sort.Direction direction = sc.getDirection() == SortDirection.ASC
                            ? Sort.Direction.ASC
                            : Sort.Direction.DESC;
                    return new Sort.Order(direction, sc.getField());
                })
                .collect(Collectors.toList());

        return PageRequest.of(zeroIndexedPage, paginationParams.getLimit(), Sort.by(orders));
    }

    /**
     * Map Company entity to response DTO with field selection and resource expansion
     */
    private CompanyResponse mapToResponse(Company company, Set<String> selectedFields, Set<String> includes) {
        // Build base response
        CompanyResponse.CompanyResponseBuilder builder = CompanyResponse.builder()
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
                .createdBy(company.getCreatedBy());

        // Apply field selection if specified
        if (selectedFields != null) {
            builder = applyFieldSelection(builder, selectedFields);
        }

        CompanyResponse response = builder.build();

        // Apply resource expansion
        if (includes != null && !includes.isEmpty()) {
            applyResourceExpansion(response, company, includes);
        }

        return response;
    }

    /**
     * Apply field selection to response builder
     * Only include fields specified in selectedFields set
     */
    private CompanyResponse.CompanyResponseBuilder applyFieldSelection(
            CompanyResponse.CompanyResponseBuilder builder, Set<String> selectedFields) {

        // Convert DTO to map for field selection
        CompanyResponse tempResponse = builder.build();

        // Manually build new response with only selected fields
        CompanyResponse.CompanyResponseBuilder filteredBuilder = CompanyResponse.builder();

        if (selectedFields.contains("id")) filteredBuilder.id(tempResponse.getId());
        if (selectedFields.contains("name")) filteredBuilder.name(tempResponse.getName());
        if (selectedFields.contains("displayName")) filteredBuilder.displayName(tempResponse.getDisplayName());
        if (selectedFields.contains("swissUID")) filteredBuilder.swissUID(tempResponse.getSwissUID());
        if (selectedFields.contains("website")) filteredBuilder.website(tempResponse.getWebsite());
        if (selectedFields.contains("industry")) filteredBuilder.industry(tempResponse.getIndustry());
        if (selectedFields.contains("description")) filteredBuilder.description(tempResponse.getDescription());
        if (selectedFields.contains("isVerified")) filteredBuilder.isVerified(tempResponse.isVerified());
        if (selectedFields.contains("createdAt")) filteredBuilder.createdAt(tempResponse.getCreatedAt());
        if (selectedFields.contains("updatedAt")) filteredBuilder.updatedAt(tempResponse.getUpdatedAt());
        if (selectedFields.contains("createdBy")) filteredBuilder.createdBy(tempResponse.getCreatedBy());

        return filteredBuilder;
    }

    /**
     * Apply resource expansion (include=statistics,logo)
     * Mutates response object to add expanded resources
     */
    private void applyResourceExpansion(CompanyResponse response, Company company, Set<String> includes) {
        // Include statistics
        if (includes.contains("statistics")) {
            CompanyStatistics statistics = CompanyStatistics.builder()
                    .totalEvents(0) // TODO: Query actual count when event management is integrated
                    .totalSpeakers(0) // TODO: Query actual count when speaker coordination is integrated
                    .totalPartners(0) // TODO: Query actual count when partner coordination is integrated
                    .build();
            response.setStatistics(statistics);
        }

        // Include logo
        if (includes.contains("logo")) {
            if (company.getLogoUrl() != null) {
                CompanyLogo logo = CompanyLogo.builder()
                        .url(company.getLogoUrl())
                        .s3Key(company.getLogoS3Key())
                        .fileId(company.getLogoFileId())
                        .build();
                response.setLogo(logo);
            } else {
                // No logo available, set to null (will be excluded by @JsonInclude)
                response.setLogo(null);
            }
        }
    }
}
