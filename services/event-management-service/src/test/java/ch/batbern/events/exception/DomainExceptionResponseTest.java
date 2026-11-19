package ch.batbern.events.exception;

import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.testprobe.ems.DomainExceptionProbeController;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Pins the error response of every exception in {@code ch.batbern.events.exception} (Story 17.14).
 *
 * <p>Each row throws one exception from a probe controller through the real {@code DispatcherServlet}
 * resolver chain, with every {@code @ControllerAdvice} of {@code ch.batbern.events} found on the
 * classpath (a new module advice joins without an edit here; a scanned advice needs a no-argument
 * constructor). It asserts the status and the body exactly: the set of top-level fields, {@code error},
 * {@code message}, {@code severity}, {@code path} and {@code details}.
 *
 * <p>The guard counts rows, not constructors: every concrete exception class of the package needs at
 * least one row, and at least as many rows as it has public constructors. It does not check that each
 * row uses a different constructor; the rows here do.
 */
@DisplayName("Domain exception responses")
class DomainExceptionResponseTest {

    private static final String PACKAGE = "ch.batbern.events.exception";
    private static final UUID EVENT_ID = UUID.fromString("0b6f6a8e-3c1d-4f43-9a55-6d0c2b7e9f10");
    private static final UUID SESSION_ID = UUID.fromString("7d2e4c10-5b8a-4e0f-8c31-2a9f6b1d3e44");
    private static final Instant AT = Instant.parse("2026-11-20T17:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<Object> ADVICES = findAdvices();

    record Row(String name, Supplier<? extends RuntimeException> exception, int status, String error, String message,
            String severity, Map<String, Object> details) {

        @Override
        public String toString() {
            return name;
        }
    }

    static List<Row> rows() {
        return List.of(
                // Answered 500 CRITICAL until 17.14: no handler, so their @ResponseStatus never applied.
                row("AgendaFull", () -> new AgendaFullException("BATbern99"), 409, "Conflict",
                        "Agenda is full for event 'BATbern99' — add a slot in Edit event type before inserting.",
                        "LOW", null),
                row("InvalidSlotAssignment", () -> new InvalidSlotAssignmentException("Slot taken"), 400,
                        "Bad Request", "Slot taken", "MEDIUM", null),

                row("AlreadyResponded with time",
                        () -> new AlreadyRespondedException(SpeakerWorkflowState.ACCEPTED, AT), 409, "Conflict",
                        "You have already responded to this invitation with: ACCEPTED", "MEDIUM",
                        details("previousResponse", "ACCEPTED", "respondedAt", AT.toString())),
                row("AlreadyResponded without time",
                        () -> new AlreadyRespondedException(SpeakerWorkflowState.DECLINED), 409, "Conflict",
                        "You have already responded to this invitation with: DECLINED", "MEDIUM",
                        details("previousResponse", "DECLINED")),

                row("BusinessValidation", () -> new BusinessValidationException("Too many slots"), 422,
                        "Unprocessable Entity", "Too many slots", "MEDIUM", null),
                row("BusinessValidation field", () -> new BusinessValidationException("title", "is blank"), 422,
                        "Unprocessable Entity", "Validation failed for field 'title': is blank", "MEDIUM", null),
                row("BusinessValidation details",
                        () -> new BusinessValidationException("Bad slot", Map.of("slot", 3)), 422,
                        "Unprocessable Entity", "Bad slot", "MEDIUM", null),

                row("DeregistrationClosed", () -> new DeregistrationClosedException("BATbern99"), 409, "Conflict",
                        "Unregistering from event BATbern99 is closed from the event day on", "LOW",
                        details("code", "DEREGISTRATION_CLOSED")),
                row("DuplicateNewsletterSend", () -> new DuplicateNewsletterSendException("Send running"), 409,
                        "Conflict", "Send running", "LOW", null),
                row("DuplicateSelfNomination", () -> new DuplicateSelfNominationException("Already nominated"), 409,
                        "Conflict", "Already nominated", "LOW", details("code", "DUPLICATE_SELF_NOMINATION")),
                row("EventHasRealRegistrations", () -> new EventHasRealRegistrationsException("BATbern99", 3), 409,
                        "Conflict", "Event 'BATbern99' has 3 real attendee registration(s) and cannot be deleted. "
                                + "Cancel the event instead.", "LOW", null),
                row("EventNotFound id", () -> new EventNotFoundException(EVENT_ID), 404, "Not Found",
                        "Event with ID '" + EVENT_ID + "' not found", "LOW", null),
                row("EventNotFound message", () -> new EventNotFoundException("Event BATbern99 not found"), 404,
                        "Not Found", "Event BATbern99 not found", "LOW", null),
                row("EventPhotoNotFound", () -> new EventPhotoNotFoundException("p-1"), 404, "Not Found",
                        "Event photo not found with ID: p-1", "LOW", null),
                row("InvalidPromotionState",
                        () -> new InvalidPromotionStateException(SpeakerWorkflowState.ACCEPTED, "Cannot promote"), 409,
                        "Conflict", "Cannot promote", "MEDIUM",
                        details("code", "INVALID_PROMOTION_STATE", "currentState", "ACCEPTED")),
                row("MaterialNotFound", () -> new MaterialNotFoundException("m-1"), 404, "Not Found",
                        "Session material not found with ID: m-1", "LOW", null),
                row("QnaWindowFrozen", () -> new QnaWindowFrozenException("Q&A closed"), 409, "Conflict",
                        "Q&A closed", "LOW", details("code", "QNA_WINDOW_FROZEN")),
                row("ReadyRequiresPromote", () -> new ReadyRequiresPromoteException("BATbern99"), 400, "Bad Request",
                        "Speakers cannot be transitioned to READY via PUT /status"
                                + " — READY requires an email payload for provisioning."
                                + " Use POST /api/v1/events/BATbern99/speakers/{speakerId}/promote"
                                + " (Story 11.D.1) instead.", "MEDIUM",
                        details("code", "READY_REQUIRES_PROMOTE_ENDPOINT", "rejectedValue", "READY",
                                "alternativeEndpoint",
                                "POST /api/v1/events/{eventCode}/speakers/{speakerId}/promote")),
                row("RegistrationAlreadyProcessed",
                        () -> new RegistrationAlreadyProcessedException("BATbern99-reg-A3X9K2", "cancelled"), 409,
                        "Conflict", "Registration BATbern99-reg-A3X9K2 is cancelled and can no longer be confirmed",
                        "LOW", details("code", "REGISTRATION_ALREADY_PROCESSED")),
                row("RegistrationCapacityExceeded",
                        () -> new RegistrationCapacityExceededException("BATbern99", 253, 253), 409, "Conflict",
                        "Event BATbern99 is at capacity (253/253); pass force=true to add over capacity", "LOW",
                        details("code", "capacity_exceeded", "activeCount", 253, "capacity", 253)),
                row("RegistrationCapacityExceeded for confirmation",
                        () -> RegistrationCapacityExceededException.forConfirmation("BATbern99", 254, 253), 409,
                        "Conflict", "Event BATbern99 holds 254 registrations for 253 places; confirming this one"
                                + " would keep it over capacity", "LOW",
                        details("code", "capacity_exceeded", "activeCount", 254, "capacity", 253)),
                row("RegistrationClosed with deadline", () -> new RegistrationClosedException("BATbern99", AT), 409,
                        "Conflict", "Registration for event BATbern99 closed on " + AT, "LOW",
                        details("code", "REGISTRATION_CLOSED", "deadline", AT.toString())),
                row("RegistrationClosed without deadline",
                        () -> new RegistrationClosedException("BATbern99", null), 409, "Conflict",
                        "Registration for event BATbern99 closed on null", "LOW",
                        details("code", "REGISTRATION_CLOSED")),
                row("RegistrationNotFound", () -> new RegistrationNotFoundException("BATbern99-0001"), 404,
                        "Not Found", "Registration with ID 'BATbern99-0001' not found", "LOW", null),
                row("RegistrationNotOnWaitlist", () -> new RegistrationNotOnWaitlistException("BATbern99-0001"), 409,
                        "Conflict", "Registration is not on the waitlist: BATbern99-0001", "MEDIUM", null),
                row("RegistrationProfileIncomplete",
                        () -> new RegistrationProfileIncompleteException("hong.an", true, false), 409, "Conflict",
                        "Profile incomplete — please add your first and last name before registering", "LOW",
                        details("code", "profile_incomplete", "username", "hong.an", "missingFirstName", true,
                                "missingLastName", false)),
                row("RegistrationStatusTransition",
                        () -> new RegistrationStatusTransitionException("BATbern99-reg-A3X9K2", "cancelled",
                                "confirmed"), 409, "Conflict",
                        "Registration BATbern99-reg-A3X9K2 cannot change from cancelled to confirmed", "LOW",
                        details("code", "REGISTRATION_TRANSITION_NOT_ALLOWED")),
                row("ReservedEmailDomain", () -> new ReservedEmailDomainException("anna@example.com"), 400,
                        "Bad Request",
                        "Email domain is reserved for testing/documentation and cannot subscribe: anna@example.com",
                        "LOW", null),
                row("SelfNominationNotAllowed", () -> new SelfNominationNotAllowedException("No topic yet"), 409,
                        "Conflict", "No topic yet", "LOW", details("code", "SELF_NOMINATION_NOT_ALLOWED")),
                row("SessionNotFound slug", () -> new SessionNotFoundException("keynote"), 404, "Not Found",
                        "Session not found: keynote", "LOW", null),
                row("SessionNotFound slug and event", () -> new SessionNotFoundException("keynote", "BATbern99"),
                        404, "Not Found", "sessionSlug 'keynote' not found in event 'BATbern99'", "LOW", null),
                row("SessionNotFound cause",
                        () -> new SessionNotFoundException("No session", new IllegalStateException("db")), 404,
                        "Not Found", "No session", "LOW", null),
                row("SlidesOnlineAlreadySent", () -> new SlidesOnlineAlreadySentException("Already sent"), 409,
                        "Conflict", "Already sent", "LOW", null),
                row("SlotCapacityReached", () -> new SlotCapacityReachedException(EVENT_ID, 6, 2, 8), 409, "Conflict",
                        "Slot capacity reached for event " + EVENT_ID + ": 6 accepted + 2 invited >= 8 slots", "MEDIUM",
                        details("code", "SLOT_CAPACITY_REACHED", "eventId", EVENT_ID.toString(),
                                "acceptedCount", 6, "invitedCount", 2, "maxSlots", 8)),
                row("SpeakerAssignmentNotFound ids",
                        () -> new SpeakerAssignmentNotFoundException(SESSION_ID, "matthias.keller"), 404, "Not Found",
                        "Speaker assignment not found for user matthias.keller and session " + SESSION_ID, "LOW",
                        null),
                row("SpeakerAssignmentNotFound message",
                        () -> new SpeakerAssignmentNotFoundException("No assignment"), 404, "Not Found",
                        "No assignment", "LOW", null),
                row("SpeakerPortalAccessDenied", () -> new SpeakerPortalAccessDeniedException("Not your pool row"),
                        403, "Forbidden", "Not your pool row", "MEDIUM", null),
                row("StructuralSessionsAlreadyExist",
                        () -> new StructuralSessionsAlreadyExistException("BATbern99"), 409, "Conflict",
                        "Structural sessions already exist for event 'BATbern99'. Use overwrite=true to replace them.",
                        "MEDIUM", null),
                row("TeaserImageLimitExceeded event",
                        () -> new TeaserImageLimitExceededException("BATbern99", 10), 409, "Conflict",
                        "Maximum of 10 teaser images reached for event: BATbern99", "LOW", null),
                row("TeaserImageLimitExceeded global", () -> new TeaserImageLimitExceededException(10), 409,
                        "Conflict", "Maximum of 10 global teaser images reached", "LOW", null),
                row("TeaserImageNotFound", () -> new TeaserImageNotFoundException("t-1"), 404, "Not Found",
                        "Teaser image not found with ID: t-1", "LOW", null),
                row("ThanksNotAllowed", () -> new ThanksNotAllowedException("Not live yet"), 409, "Conflict",
                        "Not live yet", "LOW", details("code", "THANKS_NOT_ALLOWED")),
                row("ThanksNotFeaturable", () -> new ThanksNotFeaturableException("Anonymous"), 409, "Conflict",
                        "Anonymous", "LOW", details("code", "THANKS_NOT_FEATURABLE")),
                row("ThanksNotFound", () -> new ThanksNotFoundException("No such thanks"), 404, "Not Found",
                        "No such thanks", "LOW", details("code", "THANKS_NOT_FOUND")),
                row("ThanksRateLimited", () -> new ThanksRateLimitedException("Slow down"), 429, "Too Many Requests",
                        "Slow down", "LOW", details("code", "THANKS_RATE_LIMITED")),
                row("TopicNotFound", () -> new TopicNotFoundException("Topic t-1 not found"), 404, "Not Found",
                        "Topic t-1 not found", "LOW", null),
                row("TopicNotFound cause",
                        () -> new TopicNotFoundException("Topic gone", new IllegalStateException("db")), 404,
                        "Not Found", "Topic gone", "LOW", null),
                row("UserNotFound", () -> new UserNotFoundException("matthias.keller"), 404, "Not Found",
                        "User not found: matthias.keller", "LOW", null),
                row("UserNotFound cause",
                        () -> new UserNotFoundException("matthias.keller", new IllegalStateException("cums")), 404,
                        "Not Found", "User not found: matthias.keller", "LOW", null),
                row("UserService no status", () -> new UserServiceException("CUMS down"), 502, "Bad Gateway",
                        "CUMS down", "HIGH", null),
                row("UserService cause no status",
                        () -> new UserServiceException("CUMS down", new IllegalStateException("io")), 502,
                        "Bad Gateway", "CUMS down", "HIGH", null),
                row("UserService 400", () -> new UserServiceException("Bio too long", 400), 400, "Bad Request",
                        "Bio too long", "MEDIUM", null),
                row("UserService 302", () -> new UserServiceException("Redirected", 302), 502, "Bad Gateway",
                        "Redirected", "HIGH", null),
                row("UserService 503 cause",
                        () -> new UserServiceException("CUMS busy", 503, new IllegalStateException("io")), 503,
                        "Service Unavailable", "CUMS busy", "HIGH", null),
                row("WorkflowValidation", () -> new WorkflowValidationException("Not ready"), 422,
                        "Unprocessable Entity", "Not ready", "HIGH", Map.of()),
                row("WorkflowValidation context",
                        () -> new WorkflowValidationException("Not ready", Map.of("missing", List.of("topic"))), 422,
                        "Unprocessable Entity", "Not ready", "HIGH", details("missing", List.of("topic"))),
                row("WorkflowValidation null context",
                        () -> new WorkflowValidationException("Not ready", (Map<String, Object>) null), 422,
                        "Unprocessable Entity", "Not ready", "HIGH", Map.of()),
                row("WorkflowValidation cause",
                        () -> new WorkflowValidationException("Not ready", new IllegalStateException("x")), 422,
                        "Unprocessable Entity", "Not ready", "HIGH", Map.of()),
                row("WorkflowValidation context and cause",
                        () -> new WorkflowValidationException("Not ready", Map.of("state", "CREATED"),
                                new IllegalStateException("x")), 422,
                        "Unprocessable Entity", "Not ready", "HIGH", details("state", "CREATED")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    @DisplayName("should_answerPinnedResponse_when_exceptionIsThrown")
    void should_answerPinnedResponse_when_exceptionIsThrown(Row row) throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DomainExceptionProbeController(row.exception()))
                .setControllerAdvice(ADVICES.toArray())
                .build();

        var response = mvc.perform(get(DomainExceptionProbeController.PATH)).andReturn().getResponse();
        Map<String, Object> body = JSON.readValue(response.getContentAsString(), new TypeReference<>() { });

        Set<String> expectedFields = new LinkedHashSet<>(
                List.of("timestamp", "path", "status", "error", "message", "correlationId", "severity"));
        if (row.details() != null) {
            expectedFields.add("details");
        }
        assertThat(response.getStatus()).as("HTTP status").isEqualTo(row.status());
        assertThat(body.keySet()).as("top-level fields (no errorCode)").containsExactlyInAnyOrderElementsOf(
                expectedFields);
        assertThat(body.get("status")).isEqualTo(row.status());
        assertThat(body.get("error")).isEqualTo(row.error());
        assertThat(body.get("message")).isEqualTo(row.message());
        assertThat(body.get("severity")).isEqualTo(row.severity());
        assertThat(body.get("path")).isEqualTo(DomainExceptionProbeController.PATH);
        assertThat(body.get("details")).as("details").isEqualTo(row.details());
    }

    @Test
    @DisplayName("should_haveRows_when_exceptionClassIsInPackage")
    void should_haveRows_when_exceptionClassIsInPackage() {
        Map<Class<?>, Long> rowsPerClass = rows().stream()
                .collect(Collectors.groupingBy(r -> r.exception().get().getClass(), Collectors.counting()));
        Map<Class<?>, Long> requiredRowsPerClass = exceptionClasses().stream()
                .collect(Collectors.toMap(Function.identity(), c -> Math.max(1L, Arrays.stream(c.getConstructors())
                        .filter(k -> Modifier.isPublic(k.getModifiers())).count())));

        // Positive control: the scan finds the package's exceptions.
        assertThat(requiredRowsPerClass).containsKey(EventNotFoundException.class);
        assertThat(requiredRowsPerClass).allSatisfy((type, required) ->
                assertThat(rowsPerClass.getOrDefault(type, 0L))
                        .as("rows for %s (required: at least 1 and one per public constructor, %d)",
                                type.getSimpleName(), required)
                        .isGreaterThanOrEqualTo(required));
    }

    private static List<Class<?>> exceptionClasses() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return beanDefinition.getMetadata().isConcrete() && beanDefinition.getMetadata().isIndependent();
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(Throwable.class));
        return scanner.findCandidateComponents(PACKAGE).stream()
                .map(DomainExceptionResponseTest::load)
                .collect(Collectors.toList());
    }

    private static List<Object> findAdvices() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(ControllerAdvice.class));
        return scanner.findCandidateComponents("ch.batbern.events").stream()
                .map(DomainExceptionResponseTest::load)
                .map(DomainExceptionResponseTest::instantiate)
                .collect(Collectors.toList());
    }

    private static Class<?> load(BeanDefinition definition) {
        try {
            return Class.forName(definition.getBeanClassName());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object instantiate(Class<?> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Advice " + type.getName() + " needs a no-argument constructor", e);
        }
    }

    private static Row row(String name, Supplier<? extends RuntimeException> exception, int status, String error,
            String message, String severity, Map<String, Object> details) {
        return new Row(name, exception, status, error, message, severity, details);
    }

    private static Map<String, Object> details(Object... keysAndValues) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            details.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return details;
    }
}
