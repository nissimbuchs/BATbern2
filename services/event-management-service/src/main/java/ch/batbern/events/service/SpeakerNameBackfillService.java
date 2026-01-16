package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.repository.SessionUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for backfilling speaker names in session_users table
 * Bug Fix: V38 migration added speaker_first_name and speaker_last_name columns,
 * but existing data was not populated. This service backfills the missing data.
 *
 * Usage: Call backfillSpeakerNames() once to populate all existing records
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpeakerNameBackfillService {

    private final SessionUserRepository sessionUserRepository;
    private final UserApiClient userApiClient;

    /**
     * Backfill speaker names for all session_users with NULL speaker_first_name
     * This enables full-text search on speaker names for archive browsing.
     *
     * @return Number of records updated
     */
    @Transactional
    public int backfillSpeakerNames() {
        log.info("Starting speaker name backfill for session_users");

        // Find all SessionUser records with NULL speaker_first_name
        List<SessionUser> usersToBackfill = sessionUserRepository.findAll().stream()
                .filter(su -> su.getSpeakerFirstName() == null)
                .toList();

        log.info("Found {} session_users with NULL speaker names", usersToBackfill.size());

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (SessionUser sessionUser : usersToBackfill) {
            try {
                // Fetch user data from User Management Service
                UserResponse user = userApiClient.getUserByUsername(sessionUser.getUsername());

                if (user != null) {
                    // Update speaker names
                    sessionUser.setSpeakerFirstName(user.getFirstName());
                    sessionUser.setSpeakerLastName(user.getLastName());
                    sessionUserRepository.save(sessionUser);

                    successCount.incrementAndGet();
                    log.debug("Updated speaker names for username: {}", sessionUser.getUsername());
                } else {
                    log.warn("User not found for username: {}", sessionUser.getUsername());
                    failureCount.incrementAndGet();
                }

            } catch (Exception e) {
                log.error("Failed to backfill speaker names for username: {} - {}",
                        sessionUser.getUsername(), e.getMessage());
                failureCount.incrementAndGet();
            }
        }

        log.info("Speaker name backfill complete: {} successful, {} failed, {} total",
                successCount.get(), failureCount.get(), usersToBackfill.size());

        return successCount.get();
    }
}
