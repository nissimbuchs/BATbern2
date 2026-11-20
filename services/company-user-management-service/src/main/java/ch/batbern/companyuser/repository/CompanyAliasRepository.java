package ch.batbern.companyuser.repository;

import ch.batbern.companyuser.domain.CompanyAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Aliases of companies (#1251), by alias key. */
@Repository
public interface CompanyAliasRepository extends JpaRepository<CompanyAlias, String> {

    /**
     * The alias people know by this display name, folded by {@code batbern_fold} as the search reads
     * it (case, accents, umlaut spelling); the newest when several share it.
     */
    @Query(value = "SELECT * FROM company_aliases WHERE batbern_fold(alias_display_name) = batbern_fold(:displayName)"
            + " ORDER BY created_at DESC, alias_key LIMIT 1", nativeQuery = true)
    Optional<CompanyAlias> findByFoldedDisplayName(@Param("displayName") String displayName);

    /** Every alias of one company. */
    List<CompanyAlias> findByCompanyId(UUID companyId);
}
