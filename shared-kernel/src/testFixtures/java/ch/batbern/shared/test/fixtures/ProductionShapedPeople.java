package ch.batbern.shared.test.fixtures;

import ch.batbern.shared.types.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Production-shaped people (Story 17.0d): the shared backend set, exposed by
 * {@code AbstractIntegrationTest#PEOPLE}.
 *
 * <p>Production has {@code Hông Ân}, {@code O’Neill}, {@code 王}, mixed-case e-mail addresses,
 * internationalised domains, namesakes and more than 20 people called "Matthias". Test data that
 * is ASCII, lower case, unique and small hid #1034 (umlaut search, 32 "Matthias", no
 * {@code ORDER BY}), #1104 ("Hông Ân" → {@code chk_username_format} 500) and #1107 (mixed-case
 * e-mails). Use this set for new and touched tests of anything that stores, searches, sorts or
 * pages people; an ASCII-only builder is the exception. {@code ProductionShapedPeopleTest} guards
 * that every shape stays in it. The frontend twin is {@code web-frontend/src/test/fixtures/people.ts}
 * (same people, same usernames).
 *
 * <p>The set is data, not rows: nothing is seeded. A test persists the people it needs.
 *
 * <p>Invented names only. E-mail addresses use {@code example.com} or an IDN
 * under the reserved {@code .example} TLD (ASCII local part: SES has no SMTPUTF8). Names are NFC.
 * {@link Person#username()} is what {@code SlugGenerationService} generates for the person, with
 * {@code ensureUniqueUsername} applied in list order, so the second namesake is {@code .2}.
 */
public final class ProductionShapedPeople {

    /**
     * One person as production stores them.
     *
     * @param firstName  as entered, NFC
     * @param lastName   as entered, NFC
     * @param email      as entered (case kept)
     * @param username   the username the backend generates for this person in list order
     * @param companyId  company name, or {@code null}
     * @param roles      the person's roles
     */
    public record Person(String firstName, String lastName, String email, String username,
                         String companyId, Set<Role> roles) {

        /** {@code "firstName lastName"}. */
        public String fullName() {
            return firstName + " " + lastName;
        }
    }

    /** Vietnamese diacritics, NFC. Generated {@code hong.an.meier} once and broke the constraint. */
    public static final Person VIETNAMESE = person("Hông Ân", "Meier", "hongan.meier@example.com",
            "hongan.meier", null, Role.SPEAKER);

    /** Typographic apostrophe (U+2019), as phones type it. */
    public static final Person APOSTROPHE = person("Jean-Luc", "O’Neill", "jeanluc.oneill@example.com",
            "jeanluc.oneill", null, Role.SPEAKER, Role.ATTENDEE);

    /** A CJK name: the username comes from the e-mail. */
    public static final Person CJK = person("芳", "王", "fang.wang@example.com", "fang.wang", null,
            Role.ATTENDEE);

    /** Capitals in the stored address (#1107: 58 production rows). */
    public static final Person MIXED_CASE_EMAIL = person("Anna", "Keller", "Anna.Keller@Example.COM",
            "anna.keller", null, Role.ORGANIZER);

    /** An internationalised domain; the local part stays ASCII. */
    public static final Person IDN_EMAIL = person("Jürg", "Brunner", "juerg.brunner@bücherstube.example",
            "juerg.brunner", "buecherstube", Role.PARTNER);

    /** The #1034 target: an umlaut surname among many "Matthias". */
    public static final Person BRUENNIMANN = person("Matthias", "Brünnimann", "matthias.bruennimann@example.com",
            "matthias.bruennimann", null, Role.SPEAKER);

    /** Two people with the identical full name; the second username gets {@code .2}. */
    public static final List<Person> IDENTICAL_NAMES = List.of(
            person("Matthias", "Keller", "matthias.keller@example.com", "matthias.keller", null,
                    Role.ATTENDEE),
            person("Matthias", "Keller", "m.keller@example.com", "matthias.keller.2", null, Role.SPEAKER));

    private static final List<String[]> MORE_MATTHIAS = List.of(
            new String[] {"Aebischer", "aebischer"},
            new String[] {"Bachmann", "bachmann"},
            new String[] {"Christen", "christen"},
            new String[] {"Dällenbach", "daellenbach"},
            new String[] {"Egger", "egger"},
            new String[] {"Fankhauser", "fankhauser"},
            new String[] {"Gfeller", "gfeller"},
            new String[] {"Hänni", "haenni"},
            new String[] {"Ingold", "ingold"},
            new String[] {"Jaggi", "jaggi"},
            new String[] {"Kämpf", "kaempf"},
            new String[] {"Lehmann", "lehmann"},
            new String[] {"Moser", "moser"},
            new String[] {"Nussbaum", "nussbaum"},
            new String[] {"Oberli", "oberli"},
            new String[] {"Probst", "probst"},
            new String[] {"Rüfenacht", "ruefenacht"},
            new String[] {"Schenk", "schenk"},
            new String[] {"Trachsel", "trachsel"},
            new String[] {"Untersee", "untersee"},
            new String[] {"Vögeli", "voegeli"},
            new String[] {"Wüthrich", "wuethrich"},
            new String[] {"Zaugg", "zaugg"},
            new String[] {"Zürcher", "zuercher"},
            new String[] {"Bärtschi", "baertschi"});

    /** Everyone called "Matthias": more than the default page limit of 20. */
    public static final List<Person> NAMESAKES;

    /** The whole set, in a fixed order (insertion order, as an unsorted query returns it). */
    public static final List<Person> ALL;

    static {
        List<Person> namesakes = new ArrayList<>();
        namesakes.add(BRUENNIMANN);
        namesakes.addAll(IDENTICAL_NAMES);
        for (int i = 0; i < MORE_MATTHIAS.size(); i++) {
            String[] entry = MORE_MATTHIAS.get(i);
            String username = "matthias." + entry[1];
            namesakes.add(person("Matthias", entry[0], username + "@example.com",
                    username, null, i % 3 == 0 ? Role.SPEAKER : Role.ATTENDEE));
        }
        NAMESAKES = List.copyOf(namesakes);
        ALL = Stream.concat(Stream.of(MIXED_CASE_EMAIL, VIETNAMESE, APOSTROPHE, CJK, IDN_EMAIL),
                NAMESAKES.stream()).toList();
    }

    private ProductionShapedPeople() {
    }

    /** The people before {@code person} in {@link #ALL} with the same full name (ignoring case). */
    public static List<Person> namesakesBefore(Person person) {
        int index = ALL.indexOf(person);
        String name = person.fullName().toLowerCase(Locale.ROOT);
        return ALL.subList(0, Math.max(index, 0)).stream()
                .filter(p -> p.fullName().toLowerCase(Locale.ROOT).equals(name))
                .toList();
    }

    private static Person person(String firstName, String lastName, String email, String username,
                                 String companyId, Role... roles) {
        return new Person(firstName, lastName, email, username, companyId, Set.of(roles));
    }
}
