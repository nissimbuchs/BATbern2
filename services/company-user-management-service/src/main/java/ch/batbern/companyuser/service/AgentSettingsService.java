package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.AgentAllowlistEntry;
import ch.batbern.companyuser.domain.AgentSettingsState;
import ch.batbern.companyuser.dto.generated.AgentSettings;
import ch.batbern.companyuser.dto.generated.UpdateAgentSettingsRequest;
import ch.batbern.companyuser.exception.UserValidationException;
import ch.batbern.companyuser.repository.AgentAllowlistRepository;
import ch.batbern.companyuser.repository.AgentSettingsStateRepository;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.types.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The organizer agent's switch, allowlist and delegate (agentic interaction, epic agent identity,
 * story 4), {@code GET} / {@code PATCH /api/v1/agent-settings}, stored in {@code agent_settings} and
 * {@code agent_allowlist} (V41).
 *
 * <p>A second settings store beside EMS {@code AdminSettingsService} ({@code /admin/settings/{key}}),
 * the deliberate exception recorded in {@code docs/architecture/module-map.md} ("Reuse before you
 * build"): CUMS issues the agent grants and tokens and cannot call EMS (epic decision 2026-11-21).
 *
 * <p>Off by default: V41 inserts the singleton switched off, and a missing row reads as off, an
 * empty allowlist and no delegate. {@link #isEnabled()}, {@link #isAllowlisted(String)} and
 * {@link #delegateUsername()} are the reads the grant and token issuing (stories 5-6) check; the
 * last two also require that the person still holds ORGANIZER.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSettingsService {

    private final AgentSettingsStateRepository stateRepository;
    private final AgentAllowlistRepository allowlistRepository;
    private final UserRepository userRepository;

    /** The whole settings object; off, empty and without a delegate when nothing is stored. */
    @Transactional(readOnly = true)
    public AgentSettings get() {
        return toDto(stateRepository.findById(AgentSettingsState.SINGLETON_ID).orElse(null));
    }

    /**
     * Applies the fields present in the request (ADR-013), all or nothing.
     *
     * <ul>
     *   <li>{@code enabled}: switches the agent on or off;</li>
     *   <li>{@code allowlist}: replaces the whole list, duplicates collapse; an entry that stays keeps
     *       when and by whom it was added;</li>
     *   <li>{@code delegateUsername}: names the delegate; an empty string clears it.</li>
     * </ul>
     *
     * @param actor who changes it ({@code SecurityContextHelper.getCurrentActor()}), recorded as
     *              {@code updated_by} and, for a new allowlist entry, {@code added_by}
     * @throws UserValidationException (400) when an allowlisted username or the delegate is no
     *         existing user holding ORGANIZER; the message and {@code details.usernames} name them
     */
    @Transactional
    public AgentSettings update(UpdateAgentSettingsRequest request, String actor) {
        SortedSet<String> allowlist = request.getAllowlist() == null ? null : new TreeSet<>(request.getAllowlist());
        String delegate = request.getDelegateUsername();
        boolean clearDelegate = delegate != null && delegate.isEmpty();

        Set<String> toCheck = new HashSet<>();
        if (allowlist != null) {
            toCheck.addAll(allowlist);
        }
        if (delegate != null && !clearDelegate) {
            toCheck.add(delegate);
        }
        requireOrganizers(toCheck);

        AgentSettingsState state = stateRepository.findByIdForUpdate(AgentSettingsState.SINGLETON_ID)
                .orElseGet(AgentSettingsState::new);
        Instant now = Instant.now();
        if (request.getEnabled() != null) {
            state.setEnabled(request.getEnabled());
        }
        if (delegate != null) {
            state.setDelegateUsername(clearDelegate ? null : delegate);
        }
        if (allowlist != null) {
            replaceAllowlist(allowlist, actor, now);
        }
        state.setUpdatedAt(now);
        state.setUpdatedBy(actor);
        AgentSettingsState saved = stateRepository.saveAndFlush(state);
        log.info("Agent settings changed by {}: enabled={}, delegate set={}, allowlist replaced={}",
                actor, saved.isEnabled(), saved.getDelegateUsername() != null, allowlist != null);
        return toDto(saved);
    }

    /** Whether the agent is switched on; {@code false} when nothing is stored. */
    @Transactional(readOnly = true)
    public boolean isEnabled() {
        return stateRepository.findById(AgentSettingsState.SINGLETON_ID)
                .map(AgentSettingsState::isEnabled)
                .orElse(false);
    }

    /**
     * Whether {@code username} is on the allowlist and still holds ORGANIZER. A role removed after
     * the person was allowlisted touches only {@code role_assignments}, so the stored row alone is
     * not enough (C3).
     */
    @Transactional(readOnly = true)
    public boolean isAllowlisted(String username) {
        return username != null && allowlistRepository.existsById(username) && holdsOrganizer(username);
    }

    /**
     * The organizer delegate's username, empty when there is none or when the stored delegate no
     * longer holds ORGANIZER (C3).
     */
    @Transactional(readOnly = true)
    public Optional<String> delegateUsername() {
        return stateRepository.findById(AgentSettingsState.SINGLETON_ID)
                .map(AgentSettingsState::getDelegateUsername)
                .filter(this::holdsOrganizer);
    }

    private boolean holdsOrganizer(String username) {
        return !userRepository.findUsernamesWithRole(Set.of(username), Role.ORGANIZER).isEmpty();
    }

    private void requireOrganizers(Set<String> usernames) {
        if (usernames.isEmpty()) {
            return;
        }
        Set<String> offending = new TreeSet<>(usernames);
        userRepository.findUsernamesWithRole(usernames, Role.ORGANIZER).forEach(offending::remove);
        if (!offending.isEmpty()) {
            throw new UserValidationException(
                    "Not an existing user holding ORGANIZER: " + String.join(", ", offending),
                    Map.of("usernames", new ArrayList<>(offending)));
        }
    }

    private void replaceAllowlist(SortedSet<String> wanted, String actor, Instant now) {
        List<AgentAllowlistEntry> current = allowlistRepository.findAll();
        allowlistRepository.deleteAll(current.stream()
                .filter(entry -> !wanted.contains(entry.getUsername()))
                .toList());
        Set<String> kept = new HashSet<>();
        current.forEach(entry -> kept.add(entry.getUsername()));
        allowlistRepository.saveAll(wanted.stream()
                .filter(username -> !kept.contains(username))
                .map(username -> new AgentAllowlistEntry(username, now, actor))
                .toList());
    }

    private AgentSettings toDto(AgentSettingsState state) {
        List<String> allowlist = allowlistRepository.findAllByOrderByUsernameAsc().stream()
                .map(AgentAllowlistEntry::getUsername)
                .toList();
        AgentSettings dto = new AgentSettings()
                .enabled(state != null && state.isEnabled())
                .allowlist(new ArrayList<>(allowlist));
        dto.setDelegateUsername(state == null ? null : state.getDelegateUsername());
        dto.setUpdatedAt(state == null || state.getUpdatedAt() == null
                ? null : state.getUpdatedAt().atOffset(ZoneOffset.UTC));
        dto.setUpdatedBy(state == null ? null : state.getUpdatedBy());
        return dto;
    }
}
