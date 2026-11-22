package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.domain.CompanyAlias;
import ch.batbern.companyuser.exception.CompanyNotFoundException;
import ch.batbern.companyuser.repository.CompanyAliasRepository;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.shared.security.Actors;
import ch.batbern.shared.service.SlugGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
        alias.setCreatedBy(Actors.forUsername(performedBy));
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

    /** The company (CUMS id) an alias key leads to. */
    @Transactional(readOnly = true)
    public Optional<UUID> ownerOfKey(String key) {
        return aliasRepository.findById(key).map(CompanyAlias::getCompanyId);
    }

    /** The company (CUMS id) an alias display name leads to, folded as the search folds it. */
    @Transactional(readOnly = true)
    public Optional<UUID> ownerOfDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return Optional.empty();
        }
        return aliasRepository.findByFoldedDisplayName(displayName.strip()).map(CompanyAlias::getCompanyId);
    }

    /**
     * Removes the alias {@code key}: its company takes the key back (a rename to an old key). The
     * search cache is cleared after the commit.
     */
    public void forget(String key) {
        if (aliasRepository.existsById(key)) {
            aliasRepository.deleteById(key);
            aliasRepository.flush();
            clearSearchCacheAfterCommit();
        }
    }

    /**
     * Every alias of the company {@code fromId} now leads to the company {@code toId}: a merge, before
     * it deletes the source (the database would remove the source's aliases with it). The search cache
     * is cleared after the commit.
     *
     * @return the aliases re-pointed
     */
    public List<CompanyAlias> repoint(UUID fromId, UUID toId) {
        List<CompanyAlias> aliases = aliasRepository.findByCompanyId(fromId);
        aliases.forEach(alias -> alias.setCompanyId(toId));
        aliasRepository.saveAllAndFlush(aliases);
        if (!aliases.isEmpty()) {
            clearSearchCacheAfterCommit();
        }
        return aliases;
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
