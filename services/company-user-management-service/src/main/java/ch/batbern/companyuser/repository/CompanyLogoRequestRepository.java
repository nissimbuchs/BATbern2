package ch.batbern.companyuser.repository;

import ch.batbern.companyuser.domain.CompanyLogoRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** #1246: company logo requests. */
public interface CompanyLogoRequestRepository extends JpaRepository<CompanyLogoRequest, UUID> {

    boolean existsByRequesterUsernameAndCompanyNameAndStatus(String requesterUsername, String companyName,
            CompanyLogoRequest.Status status);

    Optional<CompanyLogoRequest> findFirstByRequesterUsernameAndCompanyNameAndStatus(String requesterUsername,
            String companyName, CompanyLogoRequest.Status status);
}
