package ch.batbern.shared.search;

import ch.batbern.searchprobe.SearchProbe;
import ch.batbern.shared.test.SearchFoldContract;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceConfiguration;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The canonical {@code batbern_fold} and {@link SearchPredicates} against a real PostgreSQL
 *: folding, word order, literal wildcards, hostile input, blank input.
 */
@DisplayName("batbern_fold + SearchPredicates on PostgreSQL")
class SearchPredicatesSqlTest {

    @SuppressWarnings("resource") // stopped in @AfterAll
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static EntityManagerFactory emf;

    @BeforeAll
    static void startDatabase() throws Exception {
        POSTGRES.start();
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("CREATE EXTENSION IF NOT EXISTS unaccent");
            st.execute(canonicalFold());
        }
        emf = new PersistenceConfiguration("search-probe")
                .managedClass(SearchProbe.class)
                .property(PersistenceConfiguration.JDBC_URL, POSTGRES.getJdbcUrl())
                .property(PersistenceConfiguration.JDBC_USER, POSTGRES.getUsername())
                .property(PersistenceConfiguration.JDBC_PASSWORD, POSTGRES.getPassword())
                .property(PersistenceConfiguration.SCHEMAGEN_DATABASE_ACTION, "drop-and-create")
                .createEntityManagerFactory();
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.persist(new SearchProbe(1L, "Müller", null));
        em.persist(new SearchProbe(2L, "Mueller", null));
        em.persist(new SearchProbe(3L, "Muller", null));
        em.persist(new SearchProbe(4L, "Hans Peter Muster", "Architektur und Raum"));
        em.persist(new SearchProbe(5L, "100% Bio", null));
        em.persist(new SearchProbe(6L, "C++ Guild", null));
        em.persist(new SearchProbe(7L, "O'Brien", null));
        em.persist(new SearchProbe(8L, "Straße", "Zürich"));
        em.getTransaction().commit();
        em.close();
    }

    @AfterAll
    static void stopDatabase() {
        if (emf != null) {
            emf.close();
        }
        POSTGRES.stop();
    }

    @ParameterizedTest
    @CsvSource({
        "Müller,muller", "Mueller,muller", "MULLER,muller", "Straße,strasse",
        "O'Brien-Smith,o brien smith", "a__b,a b", "'  Hans   Peter ',' hans peter '", "Zoë Göthe,zo gothe"
    })
    void should_foldBothSpellingsToOneForm(String input, String folded) throws SQLException {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("SELECT batbern_fold(?)")) {
            ps.setString(1, input);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getString(1)).isEqualTo(folded);
            }
        }
    }

    @Test
    void should_foldNullToEmpty() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT batbern_fold(NULL)")) {
            rs.next();
            assertThat(rs.getString(1)).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Müller", "Mueller", "Muller", "muell", "MÜL"})
    void should_findAllThreeSpellings_forEachSpelling(String input) {
        assertThat(search(input)).containsExactly(1L, 2L, 3L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"muster hans", "peter", "hans muster", "Hans-Peter", "archi muster"})
    void should_findRecord_when_wordsAppearInAnyOrderAndAnyField(String input) {
        assertThat(search(input)).containsExactly(4L);
    }

    @Test
    void should_requireEveryTerm() {
        assertThat(search("muster mueller")).isEmpty();
    }

    @Test
    void should_matchPercentLiterally() {
        assertThat(search("100%")).containsExactly(5L);
        assertThat(search("1%")).isEmpty();
    }

    @Test
    void should_foldSharpS_and_apostrophe() {
        assertThat(search("strasse")).containsExactly(8L);
        assertThat(search("zurich")).containsExactly(8L);
        assertThat(search("O'Brien")).containsExactly(7L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"(test", "a  b", "'", "\\", "_", "%", "C++", "a & b | !c", ":*"})
    void should_neverFail_onHostileInput(String input) {
        List<Long> ids = search(input);
        if ("C++".equals(input)) {
            assertThat(ids).containsExactly(6L);
        }
        if ("%".equals(input)) {
            assertThat(ids).as("%% is literal: only \"100%% Bio\" contains it").containsExactly(5L);
        }
        if ("\\".equals(input)) {
            assertThat(ids).as("a backslash is literal and occurs nowhere").isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "-"})
    void should_notFilter_when_inputIsBlank(String input) {
        assertThat(search(input)).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
    }

    @Test
    void should_rankByFoldedEqualityAndPrefix() {
        assertThat(where((cb, r) -> SearchPredicates.foldedEquals(cb, r.get("name"), "MÜLLER")))
                .containsExactly(1L, 2L, 3L);
        assertThat(where((cb, r) -> SearchPredicates.foldedStartsWith(cb, r.get("name"), "hans p")))
                .containsExactly(4L);
        assertThat(where((cb, r) -> SearchPredicates.foldedStartsWith(cb, r.get("name"), "peter")))
                .isEmpty();
    }

    private static List<Long> search(String input) {
        SearchTerms terms = SearchTerms.parse(input);
        return where((cb, r) -> SearchPredicates.allTermsInAnyField(cb, terms, r.get("name"), r.get("note")));
    }

    private static List<Long> where(BiFunction<CriteriaBuilder, Root<SearchProbe>, Predicate> predicate) {
        EntityManager em = emf.createEntityManager();
        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Long> q = cb.createQuery(Long.class);
            Root<SearchProbe> r = q.from(SearchProbe.class);
            q.select(r.get("id")).where(predicate.apply(cb, r)).orderBy(cb.asc(r.get("id")));
            return em.createQuery(q).getResultList();
        } finally {
            em.close();
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static String canonicalFold() throws IOException {
        try (InputStream in = SearchPredicatesSqlTest.class.getClassLoader()
                .getResourceAsStream(SearchFoldContract.CANONICAL)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
