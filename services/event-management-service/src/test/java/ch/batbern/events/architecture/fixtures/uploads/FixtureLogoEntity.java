package ch.batbern.events.architecture.fixtures.uploads;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Breaks "no EMS entity maps an upload table of another service": the shared table, dropped by #1307. */
@Entity
@Table(name = "logos")
public class FixtureLogoEntity {
    @Id
    private Long id;
}
