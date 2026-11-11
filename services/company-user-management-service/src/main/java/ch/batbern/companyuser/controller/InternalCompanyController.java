package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.dto.generated.CompanyResponse;
import ch.batbern.companyuser.service.CompanyQueryService;
import ch.batbern.companyuser.service.CompanyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Internal company API for the other services.
 *
 * <p>Same reachability and rules as {@link InternalUserController}: not routed by the API gateway
 * (which denies {@code /internal/**}), reachable only inside the VPC, a forwarded JWT is optional and
 * never decides the answer. Not part of the public companies-api contract.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class InternalCompanyController {

    private final CompanyQueryService companyQueryService;
    private final CompanyService companyService;

    /**
     * The slugs of all companies whose slug or display name matches {@code search} (every word,
     * folded, any order), sorted by slug; every slug for a blank search. PCS uses it for the partner
     * search, because {@code partners.company_name} holds only the slug while organizers type the
     * display name. A thin route onto the public list's search ({@link CompanyQueryService}).
     */
    @GetMapping("/internal/companies")
    public ResponseEntity<List<String>> companyNames(@RequestParam(required = false) String search) {
        log.debug("Internal company names: search={}", search);
        return ResponseEntity.ok(companyQueryService.findCompanyNames(search));
    }

    /**
     * One company by its name (slug), the same {@code CompanyResponse} as the public
     * {@code GET /api/v1/companies/{name}}; 404 if unknown. EMS resolves a speaker's company display
     * name with it (Epic 17 entry 6), PCS its partner companies (6b). The public endpoint stays as it
     * is: services do not call the public API (ADR-016 decision 2).
     */
    @GetMapping("/internal/companies/{name}")
    public ResponseEntity<CompanyResponse> company(@PathVariable String name) {
        log.debug("Internal company: {}", name);
        return ResponseEntity.ok(companyService.getCompanyByName(name));
    }
}
