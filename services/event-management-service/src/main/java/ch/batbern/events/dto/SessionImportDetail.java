package ch.batbern.events.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Detailed result for a single session import attempt
 *
 * Indicates whether the session was successfully created, skipped, or failed,
 * along with an explanatory message and the generated session slug (if successful)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionImportDetail {

    /**
     * Session title (from import request)
     */
    private String title;

    /**
     * Import status: "success", "updated", "skipped", or "failed"
     */
    private String status;

    /**
     * Human-readable message explaining the result
     * Examples:
     * - "Session created successfully"
     * - "Session already exists with this title"
     * - "Event not found with number: 999"
     */
    private String message;

    /**
     * Generated session slug (only populated on success)
     * Null if status is "skipped" or "failed"
     */
    private String sessionSlug;

    /**
     * Factory method for successful import
     */
    public static SessionImportDetail success(String title, String sessionSlug) {
        return SessionImportDetail.builder()
                .title(title)
                .status("success")
                .message("Session created successfully")
                .sessionSlug(sessionSlug)
                .build();
    }

    /**
     * Factory method for skipped import
     */
    public static SessionImportDetail skipped(String title, String reason) {
        return SessionImportDetail.builder()
                .title(title)
                .status("skipped")
                .message(reason)
                .sessionSlug(null)
                .build();
    }

    /**
     * Factory method for failed import
     */
    public static SessionImportDetail failed(String title, String errorMessage) {
        return SessionImportDetail.builder()
                .title(title)
                .status("failed")
                .message(errorMessage)
                .sessionSlug(null)
                .build();
    }

    /**
     * Factory method for updated import (existing session with materials added)
     */
    public static SessionImportDetail updated(String title, String sessionSlug, String updateMessage) {
        return SessionImportDetail.builder()
                .title(title)
                .status("updated")
                .message(updateMessage)
                .sessionSlug(sessionSlug)
                .build();
    }
}
