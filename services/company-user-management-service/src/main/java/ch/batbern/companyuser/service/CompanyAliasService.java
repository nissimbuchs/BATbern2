package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.domain.CompanyAlias;
import ch.batbern.companyuser.exception.CompanyNotFoundException;
import ch.batbern.companyuser.repository.CompanyAliasRepository;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.shared.service.SlugGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Optional;

/**
 * Old company keys and display names that still lead to the company (#1251, owner decision
 * 2026-11-07). A merge (the source's key and display name) and a rename (the old ones) record an
 * alias; company search and the typeahead match aliases through {@code CompanySpecification}, and
 * get-or-create resolves a typed name through {@link #resolve} before it creates a company.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class CompanyAliasService {

    private final CompanyAliasRepository aliasRepository;
    private final CompanyRepository companyRepository;
    private final CompanySearchService searchService;
    private final SlugGenerationService slugService;

    /**
     * Records {@code aliasKey} (and the display name people knew it by) as an alias of the company
     * {@code companyKey}. Recording a key that is already an alias points it at this company: a merge
     * chain A into B into C ends at C. The search cache is cleared after the commit.
     *
     * @throws IllegalArgumentException when {@code aliasKey} is not a company key shape, or a company
     *                                  holds it (rename or delete that company first)
     * @throws CompanyNotFoundException when {@code companyKey} is no company
     */
    public CompanyAlias record(String companyKey, String aliasKey, String aliasDisplayName,
            CompanyAlias.Reason reason, String performedBy) {
        if (aliasKey == null || !aliasKey.matches(SlugGenerationService.COMPANY_NAME_PATTERN)) {
            throw new IllegalArgumentException("An alias key must be 1 to 12 letters or digits: " + aliasKey);
        }
        if (companyRepository.existsByName(aliasKey)) {
            throw new IllegalArgumentException("The company key " + aliasKey + " is in use, it cannot be an alias");
        }
        Company company = companyRepository.findByName(companyKey)
                .orElseThrow(() -> new CompanyNotFoundException(companyKey));

        CompanyAlias alias = aliasRepository.findById(aliasKey)
                .orElseGet(() -> CompanyAlias.builder().aliasKey(aliasKey).build());
        alias.setCompanyId(company.getId());
        alias.setAliasDisplayName(aliasDisplayName == null || aliasDisplayName.isBlank()
                ? null : aliasDisplayName.strip());
        alias.setReason(reason);
        alias.setCreatedBy(performedBy);
        alias.setCreatedAt(Instant.now());
        CompanyAlias saved = aliasRepository.save(alias);
        log.info("Company alias {} ({}) now leads to {} ({}, by {})", aliasKey, saved.getAliasDisplayName(),
                companyKey, reason, performedBy);

        clearSearchCacheAfterCommit();
        return saved;
    }

    /**
     * The company a typed name reaches through an alias: the alias whose key is the typed name's slug,
     * else the alias whose display name equals the typed name, folded as the search folds it.
     */
    @Transactional(readOnly = true)
    public Optional<Company> resolve(String typedName) {
        if (typedName == null || typedName.isBlank()) {
            return Optional.empty();
        }
        Optional<CompanyAlias> byKey;
        try {
            byKey = aliasRepository.findById(slugService.generateCompanyName(typedName));
        } catch (IllegalArgumentException e) {
            byKey = Optional.empty(); // no letter or digit: no key to look up
        }
        return byKey
                .or(() -> aliasRepository.findByFoldedDisplayName(typedName.strip()))
                .flatMap(alias -> companyRepository.findById(alias.getCompanyId()));
    }

    /** Whether {@code key} is an alias: a new company must not take it (it would hide the alias). */
    @Transactional(readOnly = true)
    public boolean isAliasKey(String key) {
        return aliasRepository.existsById(key);
    }

    /** Whether people knew a company by this display name before a merge or rename (folded). */
    @Transactional(readOnly = true)
    public boolean isAliasDisplayName(String displayName) {
        return displayName != null
                && aliasRepository.findByFoldedDisplayName(displayName.strip())
                        .isPresent();
    }

    private void clearSearchCacheAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            searchService.invalidateCache();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                searchService.invalidateCache();
            }
        });
    }
}
