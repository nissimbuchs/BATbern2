package ch.batbern.searchprobe;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Throw-away entity for {@code SearchPredicatesSqlTest}. Lives outside {@code ch.batbern.shared}
 * so the shared-kernel Spring Boot tests never scan it.
 */
@Entity
@Table(name = "search_probe")
public class SearchProbe {

    @Id
    private Long id;

    private String name;

    private String note;

    protected SearchProbe() {
    }

    public SearchProbe(Long id, String name, String note) {
        this.id = id;
        this.name = name;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNote() {
        return note;
    }
}
