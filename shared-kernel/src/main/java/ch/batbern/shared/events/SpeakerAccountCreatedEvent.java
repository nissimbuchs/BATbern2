package ch.batbern.shared.events;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Story 9.2 AC4: Speaker Account Created Domain Event
 *
 * Published by SpeakerAccountCreationService when a speaker Cognito account
 * is created (NEW) or an existing account's SPEAKER role is extended (EXTENDED).
 *
 * Published via Spring ApplicationEventPublisher (internal), not EventBridge.
 *
 * @see DomainEvent
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SpeakerAccountCreatedEvent extends DomainEvent<UUID> {

    public enum AccountAction {
        /** New Cognito account created */
        NEW,
        /** Existing account extended with SPEAKER role */
        EXTENDED
    }

    @JsonProperty("speakerPoolId")
    @NonNull
    private UUID speakerPoolId;

    /**
     * SHA-256 hex digest of the speaker email — stored instead of plain email to avoid PII in event bus.
     * Consistent with the audit table which also stores email_hash (not plain email).
     */
    @JsonProperty("emailHash")
    @NonNull
    private String emailHash;

    @JsonProperty("cognitoUserId")
    private String cognitoUserId;

    @JsonProperty("accountAction")
    @NonNull
    private AccountAction accountAction;

    @JsonProperty("createdAt")
    @NonNull
    private Instant createdAt;

    public SpeakerAccountCreatedEvent(
            UUID speakerPoolId,
            String emailHash,
            String cognitoUserId,
            AccountAction accountAction
    ) {
        super(speakerPoolId, "SpeakerAccountCreated", "system");

        this.speakerPoolId = speakerPoolId;
        this.emailHash = emailHash;
        this.cognitoUserId = cognitoUserId;
        this.accountAction = accountAction;
        this.createdAt = Instant.now();
    }

    @Override
    @JsonIgnore
    public String getEventName() {
        return "SpeakerAccountCreatedEvent";
    }

    @Override
    @JsonIgnore
    public String getAggregateType() {
        return "SpeakerPool";
    }
}
