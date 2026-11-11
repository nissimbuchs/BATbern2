package ch.batbern.events.notification;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notification inbox, Story BAT-7, scoped to the caller since Story 4.10b.
 *
 * <p>Every operation works on the signed-in organizer's own notifications: another organizer's
 * notification answers 404 (one) or is ignored (many) and stays unchanged; list and count take no
 * username. The production security chain runs (17.0b3); data is committed and removed in
 * {@code @AfterEach}, no class-level {@code @Transactional} (Test fidelity rules).
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Notifications (caller-scoped, Story 4.10b)")
class NotificationControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String ANNA = "anna.organizer";
    private static final String HONG_AN = "hongan.nguyen";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    private UUID annaFirst;
    private UUID annaSecond;
    private UUID annaRead;
    private UUID hongAnsOwn;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        annaFirst = save(ANNA, "UNREAD", "Programm BATbern57 veröffentlicht");
        annaSecond = save(ANNA, "UNREAD", "Referent:in hat zugesagt");
        annaRead = save(ANNA, "READ", "Erinnerung");
        hongAnsOwn = save(HONG_AN, "UNREAD", "Nur für Hông Ân");
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
    }

    private UUID save(String recipient, String status, String subject) {
        return notificationRepository.save(Notification.builder()
                .recipientUsername(recipient)
                .eventCode("BATbern57")
                .notificationType("EVENT_PUBLISHED")
                .channel("IN_APP")
                .priority("NORMAL")
                .subject(subject)
                .body("Text")
                .status(status)
                .build()).getId();
    }

    private static RequestPostProcessor organizer(String username) {
        return user(username).roles("ORGANIZER");
    }

    private Notification reload(UUID id) {
        return notificationRepository.findById(id).orElseThrow();
    }

    @Test
    @DisplayName("should_listOnlyOwnNotifications_when_organizerListsInbox")
    void should_listOnlyOwnNotifications_when_organizerListsInbox() throws Exception {
        mockMvc.perform(get("/api/v1/notifications").param("page", "1").param("limit", "20")
                        .with(organizer(ANNA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalItems").value(3))
                .andExpect(jsonPath("$.data[*].recipientUsername").value(containsInAnyOrder(ANNA, ANNA, ANNA)));

        // Positive control: Hông Ân's notification is there, in Hông Ân's inbox.
        mockMvc.perform(get("/api/v1/notifications").with(organizer(HONG_AN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalItems").value(1))
                .andExpect(jsonPath("$.data[0].subject").value("Nur für Hông Ân"));
    }

    @Test
    @DisplayName("should_ignoreUsernameParameter_when_organizerAsksForAnotherInbox")
    void should_ignoreUsernameParameter_when_organizerAsksForAnotherInbox() throws Exception {
        // The parameter is gone from the spec; a caller that still sends it gets its own inbox.
        mockMvc.perform(get("/api/v1/notifications").param("username", HONG_AN).with(organizer(ANNA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalItems").value(3));
    }

    @Test
    @DisplayName("should_filterByStatus_when_statusGiven")
    void should_filterByStatus_when_statusGiven() throws Exception {
        mockMvc.perform(get("/api/v1/notifications").param("status", "unread").with(organizer(ANNA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalItems").value(2))
                .andExpect(jsonPath("$.data[*].status").value(containsInAnyOrder("UNREAD", "UNREAD")));
    }

    @Test
    @DisplayName("should_countOnlyOwnUnread_when_organizerCounts")
    void should_countOnlyOwnUnread_when_organizerCounts() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/count").with(organizer(ANNA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
        mockMvc.perform(get("/api/v1/notifications/count").param("status", "READ").with(organizer(ANNA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
        mockMvc.perform(get("/api/v1/notifications/count").with(organizer(HONG_AN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    @DisplayName("should_markOwnNotificationRead_when_patchedWithStatusRead")
    void should_markOwnNotificationRead_when_patchedWithStatusRead() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/{id}", annaFirst).with(organizer(ANNA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"READ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.markedCount").value(1))
                .andExpect(jsonPath("$.updatedAt").exists());

        Notification stored = reload(annaFirst);
        assertThat(stored.getStatus()).isEqualTo("READ");
        assertThat(stored.getReadAt()).isNotNull();
    }

    @Test
    @DisplayName("should_return404AndKeepUnread_when_organizerPatchesAnotherOrganizersNotification")
    void should_return404AndKeepUnread_when_organizerPatchesAnotherOrganizersNotification() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/{id}", hongAnsOwn).with(organizer(ANNA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"READ\"}"))
                .andExpect(status().isNotFound());

        Notification stored = reload(hongAnsOwn);
        assertThat(stored.getStatus()).isEqualTo("UNREAD");
        assertThat(stored.getReadAt()).isNull();
    }

    @Test
    @DisplayName("should_return400_when_statusIsMissingOrUnsupported")
    void should_return400_when_statusIsMissingOrUnsupported() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/{id}", annaFirst).with(organizer(ANNA))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/notifications/{id}", annaFirst).with(organizer(ANNA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELETED\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/notifications").with(organizer(ANNA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"READ\"}"))
                .andExpect(status().isBadRequest());

        assertThat(reload(annaFirst).getStatus()).isEqualTo("UNREAD");
    }

    @Test
    @DisplayName("should_markOnlyOwnIds_when_patchingManyWithForeignIds")
    void should_markOnlyOwnIds_when_patchingManyWithForeignIds() throws Exception {
        String body = """
                {"notificationIds": ["%s", "%s", "%s", "%s"], "status": "READ"}
                """.formatted(annaFirst, annaSecond, hongAnsOwn, UUID.randomUUID());

        mockMvc.perform(patch("/api/v1/notifications").with(organizer(ANNA))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.markedCount").value(2));

        assertThat(reload(annaFirst).getStatus()).isEqualTo("READ");
        assertThat(reload(annaSecond).getStatus()).isEqualTo("READ");
        assertThat(reload(hongAnsOwn).getStatus()).isEqualTo("UNREAD");
        assertThat(reload(hongAnsOwn).getReadAt()).isNull();
    }

    @Test
    @DisplayName("should_countAndChangeOnlyUnread_when_anIdIsAlreadyRead")
    void should_countAndChangeOnlyUnread_when_anIdIsAlreadyRead() throws Exception {
        Instant readBefore = Instant.parse("2026-11-01T08:00:00Z");
        Notification alreadyRead = reload(annaRead);
        alreadyRead.setReadAt(readBefore);
        notificationRepository.save(alreadyRead);

        String body = """
                {"notificationIds": ["%s", "%s"], "status": "READ"}
                """.formatted(annaFirst, annaRead);
        mockMvc.perform(patch("/api/v1/notifications").with(organizer(ANNA))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markedCount").value(1));

        assertThat(reload(annaFirst).getStatus()).isEqualTo("READ");
        assertThat(reload(annaRead).getReadAt()).isEqualTo(readBefore);

        // One already read: nothing marked, readAt kept.
        mockMvc.perform(patch("/api/v1/notifications/{id}", annaRead).with(organizer(ANNA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"READ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markedCount").value(0));
        assertThat(reload(annaRead).getReadAt()).isEqualTo(readBefore);
    }

    @Test
    @DisplayName("should_deleteOwnNotification_when_organizerDeletes")
    void should_deleteOwnNotification_when_organizerDeletes() throws Exception {
        mockMvc.perform(delete("/api/v1/notifications/{id}", annaFirst).with(organizer(ANNA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(notificationRepository.findById(annaFirst)).isEmpty();
    }

    @Test
    @DisplayName("should_return404AndKeepRow_when_organizerDeletesAnotherOrganizersNotification")
    void should_return404AndKeepRow_when_organizerDeletesAnotherOrganizersNotification() throws Exception {
        mockMvc.perform(delete("/api/v1/notifications/{id}", hongAnsOwn).with(organizer(ANNA)))
                .andExpect(status().isNotFound());

        assertThat(notificationRepository.findById(hongAnsOwn)).isPresent();
    }

    @Test
    @DisplayName("should_return404_when_notificationDoesNotExist")
    void should_return404_when_notificationDoesNotExist() throws Exception {
        mockMvc.perform(delete("/api/v1/notifications/{id}", UUID.randomUUID()).with(organizer(ANNA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/notifications/{id}", UUID.randomUUID()).with(organizer(ANNA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"READ\"}"))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest(name = "{0} {1} as {2} -> 403")
    @CsvSource({
        "GET,    /api/v1/notifications,            SPEAKER",
        "GET,    /api/v1/notifications/count,      PARTNER",
        "PATCH,  /api/v1/notifications,            ATTENDEE",
    })
    @DisplayName("should_return403_when_nonOrganizerUsesInbox")
    void should_return403_when_nonOrganizerUsesInbox(String method, String path, String role) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path).with(user("someone").roles(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notificationIds\":[\"" + annaFirst + "\"],\"status\":\"READ\"}"))
                .andExpect(status().isForbidden());
        assertThat(reload(annaFirst).getStatus()).isEqualTo("UNREAD");
    }

    @Test
    @DisplayName("should_return401_when_anonymous")
    void should_return401_when_anonymous() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    }

    /**
     * Story 4.10b: the verb paths and the history read are gone, no aliases. {@code PUT …/{id}/read}
     * has no handler (404); {@code PUT /notifications/batch-read} and {@code DELETE
     * /notifications/batch-delete} fall on {@code /notifications/{id}}, which has no PUT (405) and
     * whose DELETE fails the UUID conversion (404 "Invalid ID format"); {@code GET
     * /notifications/history} falls on {@code /notifications/{id}}, which has no GET (405).
     */
    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource({
        "PUT,    /api/v1/notifications/00000000-0000-0000-0000-000000000001/read, 404",
        "PUT,    /api/v1/notifications/batch-read,                                405",
        "DELETE, /api/v1/notifications/batch-delete,                              404",
        "GET,    /api/v1/notifications/history,                                   405",
    })
    @DisplayName("should_answerPinnedStatus_when_removedNotificationOperationIsCalled")
    void should_answerPinnedStatus_when_removedNotificationOperationIsCalled(String method, String path, int expected)
            throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path).with(organizer(ANNA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notificationIds\":[\"" + annaFirst + "\"]}"))
                .andExpect(status().is(expected));

        assertThat(reload(annaFirst).getStatus()).isEqualTo("UNREAD");
    }

    @Test
    @DisplayName("should_answerNoEndpoint_when_oldMarkReadPathIsCalled")
    void should_answerNoEndpoint_when_oldMarkReadPathIsCalled() throws Exception {
        mockMvc.perform(request(HttpMethod.PUT, "/api/v1/notifications/{id}/read", annaFirst).with(organizer(ANNA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(startsWith("No endpoint PUT ")));
    }
}
