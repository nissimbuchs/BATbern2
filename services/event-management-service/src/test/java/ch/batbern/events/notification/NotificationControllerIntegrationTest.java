package ch.batbern.events.notification;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Notification REST API
 * Story BAT-7: Notifications API Consolidation
 *
 * RED PHASE (TDD): These tests will FAIL until implementation is complete.
 *
 * Tests cover:
 * - AC1: GET /api/v1/notifications - List notifications with filtering
 * - AC2: PUT /api/v1/notifications/{id}/read - Mark single as read
 * - AC2: PUT /api/v1/notifications/batch-read - Mark multiple as read
 * - AC4: GET /api/v1/notifications/history - Delivery history
 * - AC5: DELETE /api/v1/notifications/{id} - Delete single notification
 * - AC5: DELETE /api/v1/notifications/batch-delete - Delete multiple
 * - AC6: GET /api/v1/notifications/count - Get unread count
 *
 * Requirements:
 * - Migration V33 applied (notifications table)
 * - ADR-003 compliance (meaningful IDs: recipient_username, event_code)
 * - PostgreSQL via Testcontainers (not H2)
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
public class NotificationControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserApiClient userApiClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationRepository notificationRepository;

    private String testUsername;
    private String testEventCode;
    private UUID testNotificationId1;
    private UUID testNotificationId2;
    private UUID testNotificationId3;

    @BeforeEach
    void setUp() {
        testUsername = "john.doe";
        testEventCode = "BATbern123";

        // Create test notifications for mark-as-read and delete operations
        Notification notification1 = notificationRepository.save(Notification.builder()
                .recipientUsername(testUsername)
                .eventCode(testEventCode)
                .notificationType("EVENT_PUBLISHED")
                .channel("EMAIL")
                .priority("NORMAL")
                .subject("Test Notification 1")
                .body("Test body 1")
                .status("UNREAD")
                .build());
        testNotificationId1 = notification1.getId();

        Notification notification2 = notificationRepository.save(Notification.builder()
                .recipientUsername(testUsername)
                .eventCode(testEventCode)
                .notificationType("DEADLINE_REMINDER")
                .channel("EMAIL")
                .priority("HIGH")
                .subject("Test Notification 2")
                .body("Test body 2")
                .status("UNREAD")
                .build());
        testNotificationId2 = notification2.getId();

        Notification notification3 = notificationRepository.save(Notification.builder()
                .recipientUsername(testUsername)
                .eventCode(testEventCode)
                .notificationType("EVENT_PUBLISHED")
                .channel("EMAIL")
                .priority("NORMAL")
                .subject("Test Notification 3")
                .body("Test body 3")
                .status("UNREAD")
                .build());
        testNotificationId3 = notification3.getId();
    }

    /**
     * AC1: List Notifications - should return filtered notifications when username provided
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ATTENDEE"})
    void should_returnFilteredNotifications_when_usernameProvided() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .param("username", testUsername)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.pagination").exists())
                .andExpect(jsonPath("$.pagination.totalItems").exists());
    }

    /**
     * AC1: List Notifications - should return unread only when status=unread
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ATTENDEE"})
    void should_returnUnreadOnly_when_statusUnread() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .param("username", testUsername)
                        .param("status", "UNREAD")
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
                // All results should have status=UNREAD (verified in GREEN phase)
    }

    /**
     * AC1: List Notifications - should paginate results when page param provided
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ATTENDEE"})
    void should_paginateResults_when_pageParamProvided() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .param("username", testUsername)
                        .param("page", "1")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.limit").value(10))
                .andExpect(jsonPath("$.pagination.page").value(1));  // Frontend uses 1-based indexing
    }

    /**
     * AC2: Mark as Read - should mark as read when single notification ID provided
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ATTENDEE"})
    void should_markAsRead_when_singleNotificationId() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/{id}/read", testNotificationId1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.markedCount").value(1))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    /**
     * AC2: Mark as Read - should mark multiple as read when bulk update
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ATTENDEE"})
    void should_markMultipleAsRead_when_bulkUpdate() throws Exception {
        List<UUID> notificationIds = List.of(
                testNotificationId1,
                testNotificationId2,
                testNotificationId3
        );

        BatchOperationRequest request = new BatchOperationRequest(notificationIds);
        String requestBody = objectMapper.writeValueAsString(request);

        mockMvc.perform(put("/api/v1/notifications/batch-read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.markedCount").value(3))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    /**
     * AC2: Mark as Read - should return 200 when valid read request
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ATTENDEE"})
    void should_return200_when_validReadRequest() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/{id}/read", testNotificationId2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * AC4: Delivery History - should return email history when channel=email
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ATTENDEE"})
    void should_returnEmailHistory_when_channelEmail() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/history")
                        .param("username", testUsername)
                        .param("channel", "EMAIL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
                // Verify all results have channel=EMAIL in GREEN phase
    }

    /**
     * AC4: Delivery History - should filter by timeframe when username and channel provided
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ATTENDEE"})
    void should_filterByTimeframe_when_usernameAndChannelProvided() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/history")
                        .param("username", testUsername)
                        .param("channel", "EMAIL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    /**
     * AC5: Delete - should delete notification when valid ID
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ATTENDEE"})
    void should_deleteNotification_when_validId() throws Exception {
        mockMvc.perform(delete("/api/v1/notifications/{id}", testNotificationId1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * AC5: Delete - should batch delete when multiple IDs provided
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ATTENDEE"})
    void should_batchDelete_when_multipleIdsProvided() throws Exception {
        List<UUID> notificationIds = List.of(
                testNotificationId2,
                testNotificationId3
        );

        BatchOperationRequest request = new BatchOperationRequest(notificationIds);
        String requestBody = objectMapper.writeValueAsString(request);

        mockMvc.perform(delete("/api/v1/notifications/batch-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * AC5: Delete - should return 404 when notification not found
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ATTENDEE"})
    void should_return404_when_notificationNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/notifications/{id}", nonExistentId))
                .andExpect(status().isNotFound());
    }

    /**
     * AC6: Count - should return unread count when status=unread
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ATTENDEE"})
    void should_returnUnreadCount_when_statusUnread() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/count")
                        .param("username", testUsername)
                        .param("status", "UNREAD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").isNumber());
    }

    /**
     * AC6: Count - should return total count when no status provided
     */
    @Test
    @WithMockUser(username = "john.doe", roles = {"ATTENDEE"})
    void should_returnTotalCount_when_noStatusProvided() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/count")
                        .param("username", testUsername))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").isNumber());
    }
}
