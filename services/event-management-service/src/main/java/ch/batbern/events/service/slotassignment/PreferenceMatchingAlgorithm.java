package ch.batbern.events.service.slotassignment;

import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerSlotPreference;
import ch.batbern.events.repository.SpeakerSlotPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Algorithm for matching speakers with session slots based on preferences
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 *
 * Calculates match scores and performs auto-assignment optimization
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PreferenceMatchingAlgorithm {

    private final SpeakerSlotPreferenceRepository speakerPreferenceRepository;

    /**
     * Calculate match score between speaker and slot (0-100%)
     */
    public int calculateMatchScore(UUID speakerId, Session slot) {
        Optional<SpeakerSlotPreference> preferenceOpt = speakerPreferenceRepository.findBySpeakerId(speakerId);

        if (preferenceOpt.isEmpty() || slot.getStartTime() == null) {
            return 50; // Neutral score when no preference
        }

        SpeakerSlotPreference preference = preferenceOpt.get();
        int score = 0;

        // Time of day match (60% weight)
        score += calculateTimeOfDayScore(preference.getPreferredTimeOfDay(), slot.getStartTime());

        // A/V requirements match (40% weight)
        score += calculateAvRequirementsScore(preference.getAvRequirements(), slot);

        return Math.min(100, score);
    }

    /**
     * Find best slots for speaker ranked by match score
     */
    public List<SlotMatchResult> findBestSlots(UUID speakerId, List<Session> availableSlots) {
        return availableSlots.stream()
                .map(slot -> {
                    int matchScore = calculateMatchScore(speakerId, slot);
                    String reason = generateMatchReason(speakerId, slot, matchScore);

                    return SlotMatchResult.builder()
                            .session(slot)
                            .matchScore(matchScore)
                            .matchReason(reason)
                            .build();
                })
                .sorted((a, b) -> Integer.compare(b.getMatchScore(), a.getMatchScore()))
                .toList();
    }

    /**
     * Auto-assign speakers to slots using specified strategy
     */
    public Map<UUID, Session> autoAssignSpeakers(List<UUID> speakerIds,
                                                 List<Session> availableSlots,
                                                 AssignmentStrategy strategy) {
        log.info("Auto-assigning {} speakers to {} slots using {} strategy",
                speakerIds.size(), availableSlots.size(), strategy);

        Map<UUID, Session> assignments = new HashMap<>();

        switch (strategy) {
            case PREFERENCE_OPTIMIZED:
                assignments = assignByPreferenceOptimization(speakerIds, availableSlots);
                break;

            case BALANCED:
                assignments = assignBalanced(speakerIds, availableSlots);
                break;

            case EXPERTISE_OPTIMIZED:
                // Not implemented in this phase
                assignments = assignByPreferenceOptimization(speakerIds, availableSlots);
                break;

            default:
                throw new IllegalArgumentException("Unknown assignment strategy: " + strategy);
        }

        log.info("Auto-assignment complete: {} speakers assigned", assignments.size());
        return assignments;
    }

    /**
     * Calculate time of day match score (0-60 points)
     */
    private int calculateTimeOfDayScore(String preferredTimeOfDay, Instant startTime) {
        if (preferredTimeOfDay == null || preferredTimeOfDay.equals("any")) {
            return 50; // Neutral
        }

        ZonedDateTime zonedTime = startTime.atZone(ZoneId.of("Europe/Zurich"));
        int hour = zonedTime.getHour();

        String actualTimeOfDay;
        if (hour >= 8 && hour < 12) {
            actualTimeOfDay = "morning";
        } else if (hour >= 12 && hour < 17) {
            actualTimeOfDay = "afternoon";
        } else if (hour >= 17 && hour < 21) {
            actualTimeOfDay = "evening";
        } else {
            actualTimeOfDay = "other";
        }

        if (actualTimeOfDay.equals(preferredTimeOfDay.toLowerCase())) {
            return 60; // Perfect match
        } else {
            return 20; // Poor match
        }
    }

    /**
     * Calculate A/V requirements match score (0-40 points)
     */
    private int calculateAvRequirementsScore(Map<String, Object> avRequirements, Session slot) {
        if (avRequirements == null || avRequirements.isEmpty()) {
            return 40; // Neutral - no special requirements
        }

        // Simplified: assume all rooms can meet A/V requirements
        // In real implementation, would check room capabilities
        return 40;
    }

    /**
     * Generate human-readable match reason
     */
    private String generateMatchReason(UUID speakerId, Session slot, int matchScore) {
        if (matchScore >= 80) {
            return "Excellent match - slot aligns with speaker preferences";
        } else if (matchScore >= 60) {
            return "Good match - slot partially matches preferences";
        } else if (matchScore >= 40) {
            return "Acceptable match - consider alternatives if available";
        } else {
            return "Poor match - slot conflicts with speaker preferences";
        }
    }

    /**
     * Assign speakers using preference optimization (greedy algorithm)
     */
    private Map<UUID, Session> assignByPreferenceOptimization(List<UUID> speakerIds,
                                                              List<Session> availableSlots) {
        Map<UUID, Session> assignments = new HashMap<>();
        Set<Session> assignedSlots = new HashSet<>();

        // Sort speakers by strongest preference (speakers with narrow time windows first)
        List<UUID> sortedSpeakers = new ArrayList<>(speakerIds);

        for (UUID speakerId : sortedSpeakers) {
            List<Session> remainingSlots = availableSlots.stream()
                    .filter(slot -> !assignedSlots.contains(slot))
                    .toList();

            if (remainingSlots.isEmpty()) {
                break;
            }

            // Find best available slot for this speaker
            List<SlotMatchResult> rankedSlots = findBestSlots(speakerId, remainingSlots);
            if (!rankedSlots.isEmpty()) {
                Session bestSlot = rankedSlots.get(0).getSession();
                assignments.put(speakerId, bestSlot);
                assignedSlots.add(bestSlot);
            }
        }

        return assignments;
    }

    /**
     * Assign speakers using balanced strategy
     */
    private Map<UUID, Session> assignBalanced(List<UUID> speakerIds, List<Session> availableSlots) {
        // For MVP, use same algorithm as preference optimization
        // Future: could incorporate additional factors like topic flow, audience engagement
        return assignByPreferenceOptimization(speakerIds, availableSlots);
    }
}
