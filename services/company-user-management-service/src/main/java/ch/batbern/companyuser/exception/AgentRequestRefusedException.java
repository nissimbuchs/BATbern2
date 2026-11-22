package ch.batbern.companyuser.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * An agent grant or eligibility request refused (agent identity, story 5): 401 when a credential the
 * request needs is missing or invalid (caller secret, organizer login), 403 when policy refuses
 * (switch off, linked run, scheduler job, principal), 400 for a body that does not fit its trigger.
 * {@link GlobalExceptionHandler} renders {@code code} as {@code details.code}.
 */
@Getter
public class AgentRequestRefusedException extends RuntimeException {

    public static final String CALLER_SECRET_INVALID = "CALLER_SECRET_INVALID";
    public static final String ORGANIZER_LOGIN_REQUIRED = "ORGANIZER_LOGIN_REQUIRED";
    public static final String AGENT_SWITCHED_OFF = "AGENT_SWITCHED_OFF";
    public static final String LINKED_RUN_INVALID = "LINKED_RUN_INVALID";
    public static final String SCHEDULER_JOB_UNKNOWN = "SCHEDULER_JOB_UNKNOWN";
    public static final String PRINCIPAL_NOT_ELIGIBLE = "PRINCIPAL_NOT_ELIGIBLE";
    public static final String INVALID_REQUEST = "INVALID_AGENT_REQUEST";

    private final HttpStatus status;
    private final String code;

    public AgentRequestRefusedException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /** 401 {@value #CALLER_SECRET_INVALID}. */
    public static AgentRequestRefusedException callerSecretInvalid() {
        return new AgentRequestRefusedException(HttpStatus.UNAUTHORIZED, CALLER_SECRET_INVALID,
                "Caller secret missing or invalid");
    }

    /** 400 for a body or parameter that does not fit the operation ({@value #INVALID_REQUEST}). */
    public static AgentRequestRefusedException badRequest(String message) {
        return new AgentRequestRefusedException(HttpStatus.BAD_REQUEST, INVALID_REQUEST, message);
    }

    /** 403 with {@code code}. */
    public static AgentRequestRefusedException forbidden(String code, String message) {
        return new AgentRequestRefusedException(HttpStatus.FORBIDDEN, code, message);
    }
}
