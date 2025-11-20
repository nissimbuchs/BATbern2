package ch.batbern.migration.writer;

import ch.batbern.migration.model.target.CompanyDto;
import ch.batbern.migration.model.target.CompanyResponse;
import ch.batbern.migration.service.EntityIdMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * Company API Writer
 *
 * Writes company data to Company Management Service via REST API.
 * POST /api/companies
 *
 * Handles:
 * - Idempotency: Skip if company already exists (409 Conflict)
 * - ID mapping: Store legacy id → new UUID for foreign key resolution
 * - Error handling: Log errors for manual review
 *
 * Story: 3.2.1 - Migration Tool Implementation, AC 17-20
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CompanyApiWriter implements ItemWriter<CompanyDto> {

    private final RestTemplate restTemplate;
    private final EntityIdMappingService idMappingService;

    @Value("${migration.target-api.company-management.base-url}")
    private String companyApiUrl;

    @Override
    public void write(Chunk<? extends CompanyDto> chunk) throws Exception {
        for (CompanyDto company : chunk) {
            try {
                // POST to Company Management API
                HttpEntity<CompanyDto> request = new HttpEntity<>(company);
                ResponseEntity<CompanyResponse> response = restTemplate.exchange(
                    companyApiUrl + "/api/companies",
                    HttpMethod.POST,
                    request,
                    CompanyResponse.class
                );

                // Extract UUID from response
                CompanyResponse createdCompany = response.getBody();
                if (createdCompany != null && createdCompany.getId() != null) {
                    UUID newId = createdCompany.getId();

                    // Store ID mapping for User.companyId lookup
                    idMappingService.storeMapping("Company", company.getName(), newId);

                    log.info("Migrated company: {} ({}) → UUID: {}",
                        company.getName(), company.getDisplayName(), newId);
                } else {
                    log.error("Company API returned null response for: {}", company.getName());
                    throw new RuntimeException("Company API returned null response");
                }

            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.CONFLICT) {
                    // Company already exists - idempotency handling
                    log.warn("Company already exists: {} ({}). Skipping (idempotent).",
                        company.getName(), company.getDisplayName());

                    // For idempotency: Query existing company to get UUID
                    // This would require GET /api/companies/{name} endpoint
                    // For now, skip without storing mapping
                    // TODO: Implement GET endpoint query in future iteration

                    // Don't throw - allow job to continue (idempotent behavior)
                } else {
                    log.error("Failed to migrate company {}: {} - {}",
                        company.getName(), e.getStatusCode(), e.getMessage());
                    throw e; // Let retry/skip policy handle
                }
            }
        }
    }
}
