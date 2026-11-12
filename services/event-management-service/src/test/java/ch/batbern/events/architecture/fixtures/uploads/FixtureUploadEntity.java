package ch.batbern.events.architecture.fixtures.uploads;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Keeps the rule: an entity on the service's own uploads table. */
@Entity
@Table(name = "event_management_uploads")
public class FixtureUploadEntity {
    @Id
    private Long id;
}
