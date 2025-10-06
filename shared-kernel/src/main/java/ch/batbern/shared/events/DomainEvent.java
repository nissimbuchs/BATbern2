package ch.batbern.shared.events;

import ch.batbern.shared.types.EventId;
import ch.batbern.shared.types.UserId;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
public abstract class DomainEvent<T> {

    @JsonProperty("eventId")
    private final String eventId;

    @JsonProperty("aggregateId")
    private final T aggregateId;

    @JsonProperty("eventType")
    private final String eventType;

    @JsonProperty("userId")
    private final UserId userId;

    @JsonProperty("occurredAt")
    private final Instant occurredAt;

    @JsonProperty("version")
    private final String version;

    @JsonProperty("metadata")
    private final Map<String, String> metadata;

    @Setter
    @JsonProperty("correlationId")
    private String correlationId;

    @Setter
    @JsonProperty("causationId")
    private String causationId;

    protected DomainEvent(T aggregateId, String eventType, UserId userId) {
        this.eventId = UUID.randomUUID().toString();
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.userId = userId;
        this.occurredAt = Instant.now();
        this.version = "1.0";
        this.metadata = new HashMap<>();
        this.metadata.put("source", "shared-kernel");
    }

    protected DomainEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.aggregateId = null;
        this.eventType = null;
        this.userId = null;
        this.occurredAt = Instant.now();
        this.version = "1.0";
        this.metadata = new HashMap<>();
        this.metadata.put("source", "shared-kernel");
    }

    @JsonIgnore
    public String getEventName() {
        return this.getClass().getSimpleName();
    }

    @JsonIgnore
    public String getAggregateType() {
        // Default implementation - can be overridden by subclasses
        if (aggregateId == null) {
            return "Unknown";
        }
        String className = aggregateId.getClass().getSimpleName();
        if (className.endsWith("Id")) {
            return className.substring(0, className.length() - 2);
        }
        return className;
    }

    @JsonIgnore
    public int getEventVersion() {
        // Default event version
        return 1;
    }
}