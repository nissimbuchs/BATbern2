package ch.batbern.events.controller;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.dto.EventPhotoResponseDto;
import ch.batbern.events.dto.EventPhotoUploadResponseDto;
import ch.batbern.events.exception.EventPhotoNotFoundException;
import ch.batbern.events.service.EventPhotoService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for EventPhotoController.
 * Story 10.21: Event Photos Gallery — AC9 (TDD)
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class EventPhotoControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    EventPhotoService photoService;

    private static final String EVENT_CODE = "BATbern42";

    // ── Public endpoints ─────────────────────────────────────────────────────────

    @Test
    void listPhotos_asAnonymous_returns200WithEmptyList() throws Exception {
        when(photoService.listPhotos(EVENT_CODE)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/events/{eventCode}/photos", EVENT_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getRecentPhotos_asAnonymous_returns200WithList() throws Exception {
        EventPhotoResponseDto photo = EventPhotoResponseDto.builder()
                .id(UUID.randomUUID())
                .eventCode(EVENT_CODE)
                .displayUrl("https://cdn.batbern.ch/events/BATbern42/photos/test.jpg")
                .uploadedAt(Instant.now())
                .sortOrder(0)
                .build();
        when(photoService.getRecentPhotos(anyInt(), anyInt())).thenReturn(List.of(photo));

        mockMvc.perform(get("/api/v1/events/recent-photos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].displayUrl").isNotEmpty());
    }

    // ── ORGANIZER-only endpoints ─────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void confirmUpload_asOrganizer_returns200WithPhoto() throws Exception {
        UUID photoId = UUID.randomUUID();
        String s3Key = "events/" + EVENT_CODE + "/photos/" + photoId + ".jpg";

        EventPhotoResponseDto photo = EventPhotoResponseDto.builder()
                .id(photoId)
                .eventCode(EVENT_CODE)
                .displayUrl("https://cdn.batbern.ch/" + s3Key)
                .uploadedAt(Instant.now())
                .sortOrder(0)
                .build();

        when(photoService.confirmUpload(eq(EVENT_CODE), any(), anyString())).thenReturn(photo);

        mockMvc.perform(post("/api/v1/events/{eventCode}/photos/confirm", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"photoId\":\"" + photoId + "\",\"s3Key\":\"" + s3Key + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.displayUrl").isNotEmpty());
    }

    @Test
    void confirmUpload_asAnonymous_returns403() throws Exception {
        UUID photoId = UUID.randomUUID();
        String s3Key = "events/" + EVENT_CODE + "/photos/" + photoId + ".jpg";

        mockMvc.perform(post("/api/v1/events/{eventCode}/photos/confirm", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"photoId\":\"" + photoId + "\",\"s3Key\":\"" + s3Key + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void requestUploadUrl_asOrganizer_returns200WithUploadUrlAndPhotoId() throws Exception {
        UUID photoId = UUID.randomUUID();
        EventPhotoUploadResponseDto response = EventPhotoUploadResponseDto.builder()
                .photoId(photoId)
                .uploadUrl("https://s3.amazonaws.com/test-bucket/key?sig=x")
                .s3Key("events/" + EVENT_CODE + "/photos/" + photoId + ".jpg")
                .expiresIn(900)
                .build();

        when(photoService.requestUploadUrl(eq(EVENT_CODE), any(), anyString()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/events/{eventCode}/photos/upload-url", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"photo.jpg\",\"contentType\":\"image/jpeg\",\"fileSize\":1048576}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoId").isNotEmpty())
                .andExpect(jsonPath("$.uploadUrl").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    @WithMockUser(roles = "PARTNER")
    void requestUploadUrl_asPartner_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/photos/upload-url", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"photo.jpg\",\"contentType\":\"image/jpeg\",\"fileSize\":1048576}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestUploadUrl_asAnonymous_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/photos/upload-url", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"photo.jpg\",\"contentType\":\"image/jpeg\",\"fileSize\":1048576}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void deletePhoto_forNonexistentPhoto_returns404() throws Exception {
        UUID photoId = UUID.randomUUID();
        doThrow(new EventPhotoNotFoundException(photoId.toString()))
                .when(photoService).deletePhoto(EVENT_CODE, photoId);

        mockMvc.perform(delete("/api/v1/events/{eventCode}/photos/{photoId}", EVENT_CODE, photoId))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void deletePhoto_asOrganizer_returns204() throws Exception {
        UUID photoId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/events/{eventCode}/photos/{photoId}", EVENT_CODE, photoId))
                .andExpect(status().isNoContent());
    }
}
