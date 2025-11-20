package ch.batbern.migration.model.legacy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Legacy Companies Data Root Structure
 *
 * Root structure of docs/migration/companies.json
 * Contains metadata and array of companies
 *
 * Story: 3.2.1 - Migration Tool Implementation, AC 17-20
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LegacyCompaniesData {

    @JsonProperty("metadata")
    private Metadata metadata;

    @JsonProperty("companies")
    private List<LegacyCompany> companies;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metadata {
        @JsonProperty("lastUpdated")
        private String lastUpdated;

        @JsonProperty("source")
        private String source;

        @JsonProperty("totalCompanies")
        private Integer totalCompanies;

        @JsonProperty("companiesComplete")
        private Integer companiesComplete;

        @JsonProperty("companiesWithLogos")
        private Integer companiesWithLogos;

        @JsonProperty("companiesWithUrls")
        private Integer companiesWithUrls;
    }
}
