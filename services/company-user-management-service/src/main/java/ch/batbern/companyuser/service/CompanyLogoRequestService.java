package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.domain.CompanyLogoRequest;
import ch.batbern.companyuser.domain.Logo;
import ch.batbern.companyuser.domain.LogoStatus;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.exception.UserNotFoundException;
import ch.batbern.companyuser.repository.CompanyLogoRequestRepository;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.companyuser.repository.LogoRepository;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.security.SecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * #1246: a participant asks the organizers for a new logo of the company on their profile. The
 * uploaded logo is associated with the request right away (entity {@code LOGO_REQUEST}), so it has a
 * CDN URL for the Mail Center and the logo cleanup leaves it alone. Any organizer accepts (the
 * company gets the logo through {@link CompanyService#applyRequestedLogo}) or declines with an
 * optional reason; the requester is mailed either way.
 *
 * <p>Guard rails: one open request per person and company; only the caller's own company; a
 * declined person may ask again.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyLogoRequestService {

    static final String ENTITY_TYPE = "LOGO_REQUEST";

    private final CompanyLogoRequestRepository requests;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final LogoRepository logoRepository;
    private final GenericLogoService logoService;
    private final CompanyService companyService;
    private final CompanyLogoRequestEmailService emailService;
    private final SecurityContextHelper securityContext;

    /** The request with the requester and the company, for the views. */
    public record Details(CompanyLogoRequest request, User requester, Company company) {
    }

    /**
     * The caller asks for a new logo of their company. Event-management's Mail Center picks the
     * request up from the feed ({@link MailCenterRequestFeedService}) and opens its conversation.
     */
    @Transactional
    public Details request(String logoUploadId, String note) {
        User caller = currentUser();
        String companyName = caller.getCompanyId();
        if (companyName == null || companyName.isBlank()) {
            throw new RequestConflictException("NO_COMPANY", "Your profile has no company");
        }
        Company company = companyRepository.findByName(companyName)
                .orElseThrow(() -> new RequestConflictException("NO_COMPANY", "Your profile's company is unknown"));
        if (requests.existsByRequesterUsernameAndCompanyNameAndStatus(caller.getUsername(), companyName,
                CompanyLogoRequest.Status.OPEN)) {
            throw new RequestConflictException("LOGO_REQUEST_OPEN", "A logo request for this company is already open");
        }
        Logo logo = logoRepository.findByUploadId(logoUploadId)
                .filter(l -> l.getStatus() == LogoStatus.CONFIRMED)
                .orElseThrow(() -> new LogoNotReadyException("The logo upload is unknown or not confirmed"));

        String key = String.format("logos/requests/%s/logo.%s", logoUploadId, logo.getFileExtension());
        String newLogoUrl = logoService.associateLogoWithEntity(logoUploadId, ENTITY_TYPE, companyName, key);
        String trimmedNote = note == null || note.isBlank() ? null : note.trim();
        CompanyLogoRequest saved = requests.saveAndFlush(CompanyLogoRequest.builder()
                .requesterUsername(caller.getUsername())
                .companyName(companyName)
                .oldLogoUrl(company.getLogoUrl())
                .newLogoUploadId(logoUploadId)
                .newLogoUrl(newLogoUrl)
                .note(trimmedNote)
                .status(CompanyLogoRequest.Status.OPEN)
                .createdAt(Instant.now())
                .build());
        log.info("COMPANY_LOGO_REQUESTED id={} requester={} company={}", saved.getId(), caller.getUsername(),
                companyName);
        return new Details(saved, caller, company);
    }

    /** The caller's open request for the company on their profile, if any. */
    @Transactional(readOnly = true)
    public Optional<Details> openForCaller() {
        User caller = currentUser();
        if (caller.getCompanyId() == null) {
            return Optional.empty();
        }
        return requests.findFirstByRequesterUsernameAndCompanyNameAndStatus(caller.getUsername(),
                        caller.getCompanyId(), CompanyLogoRequest.Status.OPEN)
                .map(r -> new Details(r, caller, companyRepository.findByName(r.getCompanyName()).orElse(null)));
    }

    /** One request (organizers). */
    @Transactional(readOnly = true)
    public Details get(UUID id) {
        return details(load(id));
    }

    /** The company gets the requested logo; the requester is mailed. */
    @Transactional
    public Details approve(UUID id) {
        CompanyLogoRequest request = openRequest(id);
        String organizer = securityContext.getCurrentUsername();
        companyService.applyRequestedLogo(request.getCompanyName(), request.getNewLogoUploadId());
        decide(request, CompanyLogoRequest.Status.APPROVED, organizer, null);
        Details d = details(request);
        afterDecision(request, () -> emailService.sendApproved(d.requester().getEmail(),
                d.requester().getFirstName(), locale(d.requester()), displayName(d.company(), request)));
        log.info("COMPANY_LOGO_REQUEST_APPROVED id={} by={}", id, organizer);
        return d;
    }

    /** Nothing changes on the company; the requester is mailed, with the reason when one is given. */
    @Transactional
    public Details decline(UUID id, String reason) {
        CompanyLogoRequest request = openRequest(id);
        String organizer = securityContext.getCurrentUsername();
        String trimmed = reason == null || reason.isBlank() ? null : reason.trim();
        decide(request, CompanyLogoRequest.Status.DECLINED, organizer, trimmed);
        Details d = details(request);
        afterDecision(request, () -> emailService.sendDeclined(d.requester().getEmail(),
                d.requester().getFirstName(), locale(d.requester()), displayName(d.company(), request), trimmed));
        log.info("COMPANY_LOGO_REQUEST_DECLINED id={} by={}", id, organizer);
        return d;
    }

    private void afterDecision(CompanyLogoRequest request, Runnable mail) {
        try {
            mail.run();
        } catch (RuntimeException e) {
            log.warn("COMPANY_LOGO_REQUEST mail failed id={} cause={}", request.getId(),
                    e.getClass().getSimpleName());
        }
    }

    private void decide(CompanyLogoRequest request, CompanyLogoRequest.Status status, String by, String reason) {
        request.setStatus(status);
        request.setDecidedBy(by);
        request.setDecidedAt(Instant.now());
        request.setReason(reason);
        requests.saveAndFlush(request);
    }

    private Details details(CompanyLogoRequest request) {
        User requester = userRepository.findByUsername(request.getRequesterUsername())
                .orElseThrow(() -> new UserNotFoundException("Requester not found", true));
        return new Details(request, requester, companyRepository.findByName(request.getCompanyName()).orElse(null));
    }

    private CompanyLogoRequest openRequest(UUID id) {
        CompanyLogoRequest request = load(id);
        if (request.getStatus() != CompanyLogoRequest.Status.OPEN) {
            throw new RequestConflictException("LOGO_REQUEST_DECIDED", "This request is already decided");
        }
        return request;
    }

    private CompanyLogoRequest load(UUID id) {
        return requests.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Logo request not found", true));
    }

    private User currentUser() {
        String username = securityContext.getCurrentUsername();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found", true));
    }

    public static String fullName(User user) {
        return (nullToEmpty(user.getFirstName()) + " " + nullToEmpty(user.getLastName())).trim();
    }

    private static String displayName(Company company) {
        return company.getDisplayName() != null && !company.getDisplayName().isBlank()
                ? company.getDisplayName()
                : company.getName();
    }

    private static String displayName(Company company, CompanyLogoRequest request) {
        return company == null ? request.getCompanyName() : displayName(company);
    }

    private static String locale(User user) {
        if (user.getPreferences() != null && user.getPreferences().getLanguage() != null) {
            return user.getPreferences().getLanguage().toLowerCase(Locale.ROOT).startsWith("de") ? "de" : "en";
        }
        return "de";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** 409 with an error code: NO_COMPANY, LOGO_REQUEST_OPEN or LOGO_REQUEST_DECIDED. */
    public static class RequestConflictException extends RuntimeException {
        private final String code;

        public RequestConflictException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** 400 LOGO_NOT_READY: the upload is unknown or was not confirmed. */
    public static class LogoNotReadyException extends RuntimeException {
        public LogoNotReadyException(String message) {
            super(message);
        }
    }
}
