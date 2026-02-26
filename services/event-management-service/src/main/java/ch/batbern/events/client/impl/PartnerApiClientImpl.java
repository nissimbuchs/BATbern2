package ch.batbern.events.client.impl;

import ch.batbern.events.client.PartnerApiClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RestTemplate-based implementation of PartnerApiClient (Story 10.4).
 *
 * Calls GET /api/v1/partners/topics on the partner-coordination-service.
 * Groups the flat topic list by company name.
 * Falls back to empty list on any communication failure (topic blob works without partner data).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PartnerApiClientImpl implements PartnerApiClient {

    private final RestTemplate restTemplate;

    @Value("${partner-service.base-url:http://partner-coordination:8080}")
    private String partnerServiceBaseUrl;

    @Override
    public List<PartnerTopicGroup> getPartnerTopics() {
        try {
            HttpHeaders headers = createHeadersWithJwtToken();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<List<PartnerServiceTopicItem>> response = restTemplate.exchange(
                    partnerServiceBaseUrl + "/api/v1/partners/topics",
                    HttpMethod.GET,
                    request,
                    new ParameterizedTypeReference<List<PartnerServiceTopicItem>>() {});

            List<PartnerServiceTopicItem> items = response.getBody();
            if (items == null || items.isEmpty()) {
                return List.of();
            }

            // Group by company name; preserve insertion order within each group.
            // Retain voteCount and createdAt so TopicSessionDataService can compute attraction strengths.
            Map<String, List<PartnerApiClient.PartnerTopicItem>> byCompany = items.stream()
                    .collect(Collectors.groupingBy(
                            item -> item.suggestedByCompany() != null ? item.suggestedByCompany() : "Unknown",
                            Collectors.mapping(
                                    item -> new PartnerApiClient.PartnerTopicItem(
                                            item.title(),
                                            item.voteCount() != null ? item.voteCount() : 0,
                                            item.createdAt() != null ? item.createdAt() : Instant.EPOCH),
                                    Collectors.toList())
                    ));

            // Fetch company logos in a second call (company.logoUrl lives on the partner entity)
            Map<String, String> logoMap = fetchLogoMap();

            return byCompany.entrySet().stream()
                    .map(e -> new PartnerTopicGroup(e.getKey(), logoMap.get(e.getKey()), e.getValue()))
                    .toList();

        } catch (RestClientException e) {
            log.warn("Failed to fetch partner topics — returning empty list: {}", e.getMessage());
            return List.of();
        }
    }

    private HttpHeaders createHeadersWithJwtToken() {
        HttpHeaders headers = new HttpHeaders();
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof Jwt jwt) {
                headers.setBearerAuth(jwt.getTokenValue());
            }
        } catch (Exception e) {
            log.debug("Could not extract JWT for partner service call: {}", e.getMessage());
        }
        return headers;
    }

    /**
     * Fetches company logos by calling GET /api/v1/partners?include=company.
     * Returns an empty map on any failure — logos are optional for the blob canvas.
     */
    private Map<String, String> fetchLogoMap() {
        try {
            HttpHeaders headers = createHeadersWithJwtToken();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<PartnerListResponse> response = restTemplate.exchange(
                    partnerServiceBaseUrl + "/api/v1/partners?include=company&size=200",
                    HttpMethod.GET,
                    request,
                    PartnerListResponse.class);

            PartnerListResponse body = response.getBody();
            if (body == null || body.data() == null) {
                return Map.of();
            }

            Map<String, String> logos = new HashMap<>();
            for (PartnerListItem item : body.data()) {
                if (item.companyName() != null && item.company() != null && item.company().logoUrl() != null) {
                    logos.put(item.companyName(), item.company().logoUrl());
                }
            }
            return logos;

        } catch (Exception e) {
            log.debug("Could not fetch partner logos — blobs will render without logos: {}", e.getMessage());
            return Map.of();
        }
    }

    /** Local DTO matching the partner service's TopicDTO structure. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PartnerServiceTopicItem(String title, String suggestedByCompany,
                                           Integer voteCount, Instant createdAt) {}

    /** Minimal projection of PartnerListResponse — only fields needed for logo lookup. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PartnerListResponse(List<PartnerListItem> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PartnerListItem(String companyName, CompanyInfo company) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CompanyInfo(String logoUrl) {}
}
