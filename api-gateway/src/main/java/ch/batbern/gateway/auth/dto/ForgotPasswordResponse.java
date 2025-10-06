package ch.batbern.gateway.auth.dto;

/**
 * Response DTO for forgot password endpoint
 *
 * Story 1.2.2 - AC12: Email enumeration prevention
 * Always returns the same success message regardless of whether email exists
 */
public class ForgotPasswordResponse {

    private boolean success;
    private String message;

    public ForgotPasswordResponse() {
    }

    public ForgotPasswordResponse(boolean success, String message) {
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
