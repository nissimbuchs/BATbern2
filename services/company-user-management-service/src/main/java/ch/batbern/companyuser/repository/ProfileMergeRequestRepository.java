package ch.batbern.companyuser.repository;

import ch.batbern.companyuser.domain.ProfileMergeRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** #1237. */
public interface ProfileMergeRequestRepository extends JpaRepository<ProfileMergeRequest, UUID> {

    boolean existsByRequesterUsernameAndSourceUsernameAndStatus(String requesterUsername, String sourceUsername,
            ProfileMergeRequest.Status status);

    boolean existsByRequesterUsernameAndSourceUsernameAndStatusAndDecidedAtAfter(String requesterUsername,
            String sourceUsername, ProfileMergeRequest.Status status, Instant after);

    List<ProfileMergeRequest> findByRequesterUsernameAndStatusAndSourceUsernameIn(String requesterUsername,
            ProfileMergeRequest.Status status, Collection<String> sourceUsernames);
}
