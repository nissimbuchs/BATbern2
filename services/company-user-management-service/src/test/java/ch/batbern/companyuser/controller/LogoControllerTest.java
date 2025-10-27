package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.dto.LogoUploadConfirmRequest;
import ch.batbern.companyuser.dto.LogoUploadRequest;
import ch.batbern.companyuser.dto.PresignedUploadUrl;
import ch.batbern.companyuser.exception.FileSizeExceededException;
import ch.batbern.companyuser.exception.InvalidFileTypeException;
import ch.batbern.companyuser.exception.LogoNotFoundException;
import ch.batbern.companyuser.service.GenericLogoService;
import ch.batbern.companyuser.service.LogoCleanupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.HashMap;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for LogoController
 * Story 1.16.3: Generic File Upload Service
 * Tests all REST endpoints with security, validation, and error handling
 */
@WebMvcTest(controllers = LogoController.class)
@Import(ch.batbern.companyuser.config.SecurityConfig.class)
class LogoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GenericLogoService logoService;

    @MockitoBean
    private LogoCleanupService cleanupService;

    // ============ POST /presigned-url Tests ============

    @Test
    @DisplayName("AC1: Should generate presigned URL for valid request")
    void shouldGeneratePresignedUrl_whenValidRequest() throws Exception {
        // Arrange
        LogoUploadRequest request = LogoUploadRequest.builder()
                .fileName("company-logo.png")
                .fileSize(1024L * 1024L) // 1 MB
                .mimeType("image/png")
                .build();

        PresignedUploadUrl expectedResponse = PresignedUploadUrl.builder()
                .uploadUrl("https://s3.amazonaws.com/bucket/logos/temp/upload-123/logo.png?signature=xyz")
                .fileId("file-123")
                .fileExtension("png")
                .expiresInMinutes(15)
                .requiredHeaders(new HashMap<>() {{
                    put("Content-Type", "image/png");
                }})
                .build();

        when(logoService.generatePresignedUrl(eq("company-logo.png"), eq(1024L * 1024L), eq("image/png")))
                .thenReturn(expectedResponse);

        // Act & Assert
        mockMvc.perform(post("/api/v1/logos/presigned-url")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value(expectedResponse.getUploadUrl()))
                .andExpect(jsonPath("$.fileId").value("file-123"))
                .andExpect(jsonPath("$.fileExtension").value("png"))
                .andExpect(jsonPath("$.expiresInMinutes").value(15));

        verify(logoService).generatePresignedUrl("company-logo.png", 1024L * 1024L, "image/png");
    }

    @Test
    @DisplayName("Should return 400 when file size exceeds limit")
    void shouldReturn400_whenFileSizeExceedsLimit() throws Exception {
        // Arrange
        LogoUploadRequest request = LogoUploadRequest.builder()
                .fileName("large-logo.png")
                .fileSize(10L * 1024L * 1024L) // 10 MB (exceeds 5 MB limit)
                .mimeType("image/png")
                .build();

        when(logoService.generatePresignedUrl(anyString(), anyLong(), anyString()))
                .thenThrow(new FileSizeExceededException("File size exceeds 5MB limit"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/logos/presigned-url")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(logoService).generatePresignedUrl("large-logo.png", 10L * 1024L * 1024L, "image/png");
    }

    @Test
    @DisplayName("Should return 400 when file type is invalid")
    void shouldReturn400_whenFileTypeInvalid() throws Exception {
        // Arrange
        LogoUploadRequest request = LogoUploadRequest.builder()
                .fileName("document.pdf")
                .fileSize(1024L)
                .mimeType("application/pdf")
                .build();

        when(logoService.generatePresignedUrl(anyString(), anyLong(), anyString()))
                .thenThrow(new InvalidFileTypeException("Only PNG, JPEG, and SVG files are allowed"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/logos/presigned-url")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(logoService).generatePresignedUrl("document.pdf", 1024L, "application/pdf");
    }

    @Test
    @DisplayName("Should return 400 when request body is invalid")
    void shouldReturn400_whenRequestBodyInvalid() throws Exception {
        // Arrange - Missing required fields
        String invalidRequest = "{\"fileName\": \"\"}";

        // Act & Assert
        mockMvc.perform(post("/api/v1/logos/presigned-url")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(logoService);
    }

    // ============ POST /{uploadId}/confirm Tests ============

    @Test
    @DisplayName("AC2: Should confirm upload for valid upload ID")
    void shouldConfirmUpload_whenValidUploadId() throws Exception {
        // Arrange
        String uploadId = "upload-123";
        LogoUploadConfirmRequest request = LogoUploadConfirmRequest.builder()
                .fileId("file-123")
                .fileExtension("png")
                .checksum("abc123def456")
                .build();

        doNothing().when(logoService).confirmUpload(eq(uploadId), eq("abc123def456"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/logos/{uploadId}/confirm", uploadId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(logoService).confirmUpload(uploadId, "abc123def456");
    }

    @Test
    @DisplayName("Should return 404 when upload ID not found")
    void shouldReturn404_whenUploadIdNotFound() throws Exception {
        // Arrange
        String uploadId = "nonexistent-upload";
        LogoUploadConfirmRequest request = LogoUploadConfirmRequest.builder()
                .fileId("file-123")
                .fileExtension("png")
                .checksum("abc123")
                .build();

        doThrow(new LogoNotFoundException("Upload ID not found: " + uploadId))
                .when(logoService).confirmUpload(eq(uploadId), anyString());

        // Act & Assert
        mockMvc.perform(post("/api/v1/logos/{uploadId}/confirm", uploadId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());

        verify(logoService).confirmUpload(uploadId, "abc123");
    }

    @Test
    @DisplayName("Should return 400 when confirm request is invalid")
    void shouldReturn400_whenConfirmRequestInvalid() throws Exception {
        // Arrange - Missing required fields
        String uploadId = "upload-123";
        String invalidRequest = "{\"fileId\": \"\"}";

        // Act & Assert
        mockMvc.perform(post("/api/v1/logos/{uploadId}/confirm", uploadId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(logoService);
    }

    // ============ DELETE /{uploadId} Tests ============

    @Test
    @WithMockUser
    @DisplayName("AC3: Should delete unused logo when authenticated")
    void shouldDeleteUnusedLogo_whenAuthenticated() throws Exception {
        // Arrange
        String uploadId = "upload-123";
        doNothing().when(logoService).deleteUnusedLogo(eq(uploadId));

        // Act & Assert
        mockMvc.perform(delete("/api/v1/logos/{uploadId}", uploadId)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(logoService).deleteUnusedLogo(uploadId);
    }

    @Test
    @DisplayName("Should return 401 when deleting logo without authentication")
    void shouldReturn401_whenDeletingLogoWithoutAuth() throws Exception {
        // Arrange
        String uploadId = "upload-123";

        // Act & Assert
        mockMvc.perform(delete("/api/v1/logos/{uploadId}", uploadId)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(logoService);
    }

    @Test
    @WithMockUser
    @DisplayName("Should return 404 when deleting nonexistent logo")
    void shouldReturn404_whenDeletingNonexistentLogo() throws Exception {
        // Arrange
        String uploadId = "nonexistent-upload";
        doThrow(new LogoNotFoundException("Logo not found: " + uploadId))
                .when(logoService).deleteUnusedLogo(eq(uploadId));

        // Act & Assert
        mockMvc.perform(delete("/api/v1/logos/{uploadId}", uploadId)
                        .with(csrf()))
                .andExpect(status().isNotFound());

        verify(logoService).deleteUnusedLogo(uploadId);
    }

    @Test
    @WithMockUser
    @DisplayName("Should return 400 when deleting associated logo")
    void shouldReturn400_whenDeletingAssociatedLogo() throws Exception {
        // Arrange
        String uploadId = "upload-123";
        doThrow(new IllegalStateException("Cannot delete ASSOCIATED logo"))
                .when(logoService).deleteUnusedLogo(eq(uploadId));

        // Act & Assert
        mockMvc.perform(delete("/api/v1/logos/{uploadId}", uploadId)
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verify(logoService).deleteUnusedLogo(uploadId);
    }

    // ============ GET /cleanup/statistics Tests ============

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("AC7: Should return cleanup statistics for ORGANIZER")
    void shouldReturnCleanupStatistics_whenOrganizer() throws Exception {
        // Arrange
        LogoCleanupService.CleanupStatistics stats = new LogoCleanupService.CleanupStatistics(
                10L,  // pendingCount
                5L,   // confirmedCount
                100L, // associatedCount
                2L,   // expiredPendingCount
                1L    // expiredConfirmedCount
        );

        when(cleanupService.getCleanupStatistics()).thenReturn(stats);

        // Act & Assert
        mockMvc.perform(get("/api/v1/logos/cleanup/statistics")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCount").value(10))
                .andExpect(jsonPath("$.confirmedCount").value(5))
                .andExpect(jsonPath("$.associatedCount").value(100))
                .andExpect(jsonPath("$.expiredPendingCount").value(2))
                .andExpect(jsonPath("$.expiredConfirmedCount").value(1));

        verify(cleanupService).getCleanupStatistics();
    }

    @Test
    @WithMockUser(roles = "SPEAKER")
    @DisplayName("Should return 403 when non-ORGANIZER requests statistics")
    void shouldReturn403_whenNonOrganizerRequestsStatistics() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/logos/cleanup/statistics")
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cleanupService);
    }

    @Test
    @DisplayName("Should return 401 when requesting statistics without authentication")
    void shouldReturn401_whenRequestingStatisticsWithoutAuth() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/logos/cleanup/statistics")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(cleanupService);
    }

    // ============ POST /cleanup/trigger Tests ============

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("AC7: Should trigger manual cleanup for ORGANIZER")
    void shouldTriggerManualCleanup_whenOrganizer() throws Exception {
        // Arrange
        doNothing().when(cleanupService).triggerManualCleanup();

        // Act & Assert
        mockMvc.perform(post("/api/v1/logos/cleanup/trigger")
                        .with(csrf()))
                .andExpect(status().isAccepted());

        verify(cleanupService).triggerManualCleanup();
    }

    @Test
    @WithMockUser(roles = "SPEAKER")
    @DisplayName("Should return 403 when non-ORGANIZER triggers cleanup")
    void shouldReturn403_whenNonOrganizerTriggersCleanup() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v1/logos/cleanup/trigger")
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cleanupService);
    }

    @Test
    @DisplayName("Should return 401 when triggering cleanup without authentication")
    void shouldReturn401_whenTriggeringCleanupWithoutAuth() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v1/logos/cleanup/trigger")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(cleanupService);
    }
}
