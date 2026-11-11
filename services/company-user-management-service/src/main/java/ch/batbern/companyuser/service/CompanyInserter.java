package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Story 4.10d: the insert of get-or-create in its own transaction. When a concurrent get-or-create
 * of the same name commits first, the unique constraint on {@code companies.name} fails this insert
 * and only this transaction rolls back; the caller's transaction stays usable, so it can read the
 * other company by name and return it (get-or-create stays idempotent).
 */
@Component
@RequiredArgsConstructor
public class CompanyInserter {

    private final CompanyRepository companyRepository;

    /**
     * Inserts and flushes the company in a new transaction.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException when the name is taken
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Company insert(Company company) {
        Company saved = companyRepository.save(company);
        companyRepository.flush();
        return saved;
    }
}
