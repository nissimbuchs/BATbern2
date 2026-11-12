package ch.batbern.events.architecture.fixtures.uploads;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Breaks "no EMS entity maps logos": the shared CUMS table EMS used before Story 17.11. */
@Entity
@Table(name = "logos")
public class FixtureLogoEntity {
    @Id
    private Long id;
}
