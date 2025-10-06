package ch.batbern.gateway.auth.dto;

/**
 * Response DTO for resend reset link endpoint
 *
 * Story 1.2.2 - AC7: Resend functionality with 60-second cooldown
 */
public class ResendResetLinkResponse {

    private boolean success;
    private String message;

    public ResendResetLinkResponse() {
    }

    public ResendResetLinkResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
