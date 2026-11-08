package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.service.CompanyQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Internal company API for the other services.
 *
 * <p>Same reachability as {@link InternalUserController}: not JWT-protected, not routed by the API
 * gateway (which denies {@code /internal/**}), reachable only inside the VPC. Not part of the public
 * companies-api contract.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class InternalCompanyController {

    private final CompanyQueryService companyQueryService;

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
}
