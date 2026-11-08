package ch.batbern.shared.test.fixtures;

import ch.batbern.shared.service.SlugGenerationService;
import ch.batbern.shared.test.fixtures.ProductionShapedPeople.Person;
import ch.batbern.shared.utils.EmailAddresses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guard of the production-shaped people (Story 17.0d): every shape that production has and
 * ASCII-only test data hid stays in the set, and every username is what the backend generates.
 * The frontend twin is {@code web-frontend/src/test/fixtures/fixtures.test.ts}.
 */
@DisplayName("Production-shaped people")
class ProductionShapedPeopleTest {

    private static final int DEFAULT_PAGE_LIMIT = 20;

    private final SlugGenerationService slugs = new SlugGenerationService();

    static Stream<Arguments> shapes() {
        return shapeTable().stream().map(s -> Arguments.of(s.name(), s.present()));
    }

    private record Shape(String name, Predicate<List<Person>> present) {
    }

    private static List<Shape> shapeTable() {
        return List.of(
                new Shape("a Vietnamese name with precomposed diacritics (Hông Ân, NFC)", people -> people.stream()
                        .anyMatch(p -> p.firstName().matches(".*[ôÂâÔ].*") && isNfc(p.firstName()))),
                new Shape("a typographic apostrophe (O’Neill)", people -> people.stream()
                        .anyMatch(p -> p.fullName().contains("’"))),
                new Shape("a CJK character (王)", people -> people.stream()
                        .anyMatch(p -> p.fullName().codePoints()
                                .anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN))),
                new Shape("an umlaut in a surname (Stürmer)", people -> people.stream()
                        .anyMatch(p -> p.lastName().matches(".*[äöü].*"))),
                new Shape("a mixed-case e-mail address", people -> people.stream()
                        .anyMatch(p -> !p.email().equals(p.email().toLowerCase(Locale.ROOT)))),
                new Shape("an internationalised e-mail domain with an ASCII local part", people -> people.stream()
                        .anyMatch(p -> !isAscii(domainOf(p.email())) && isAscii(localPartOf(p.email())))),
                new Shape("two people with the identical full name", people -> people.stream()
                        .map(Person::fullName).distinct().count() < people.size()),
                new Shape("more than " + DEFAULT_PAGE_LIMIT + " people called \"Matthias\"", people -> people.stream()
                        .filter(p -> p.firstName().equals("Matthias")).count() > DEFAULT_PAGE_LIMIT),
                new Shape("a tie in the sort key (same last name, different people)", people -> people.stream()
                        .map(Person::lastName).distinct().count() < people.size()));
    }

    @ParameterizedTest(name = "contains {0}")
    @MethodSource("shapes")
    void should_containTheShape(String shape, Predicate<List<Person>> present) {
        assertThat(present.test(ProductionShapedPeople.ALL)).as("the fixture set lost: " + shape).isTrue();
    }

    @Test
    void should_rejectAsciiOnlyUniqueSmallData_positiveControl() {
        List<Person> typical = List.of(new Person("John", "Doe", "john.doe@example.com", "john.doe", null,
                Set.of()));

        assertThat(shapeTable()).allMatch(s -> !s.present().test(typical));
    }

    @Test
    void should_carryTheUsernameTheBackendGenerates_when_createdInListOrder() {
        Set<String> taken = new HashSet<>();
        List<String> mismatches = new ArrayList<>();
        for (Person p : ProductionShapedPeople.ALL) {
            String generated = slugs.ensureUniqueUsername(
                    slugs.generateUsername(p.firstName(), p.lastName(), p.email()), taken::contains);
            taken.add(generated);
            if (!generated.equals(p.username())) {
                mismatches.add(p.fullName() + ": fixture " + p.username() + ", generator " + generated);
            }
        }

        assertThat(mismatches).isEmpty();
        assertThat(ProductionShapedPeople.ALL)
                .allMatch(p -> p.username().matches(SlugGenerationService.USERNAME_PATTERN));
    }

    @Test
    void should_haveUniqueUsernamesAndEmailsIgnoringCase() {
        List<Person> all = ProductionShapedPeople.ALL;

        assertThat(all.stream().map(Person::username).collect(Collectors.toSet())).hasSize(all.size());
        assertThat(all.stream().map(p -> p.email().toLowerCase(Locale.ROOT)).collect(Collectors.toSet()))
                .hasSize(all.size());
    }

    @Test
    void should_useAddressesTheBackendAcceptsAndExampleDomainsOnly() {
        assertThat(ProductionShapedPeople.ALL).allMatch(p -> EmailAddresses.isAcceptable(p.email()));
        assertThat(ProductionShapedPeople.ALL)
                .allMatch(p -> domainOf(p.email()).toLowerCase(Locale.ROOT)
                        .matches("example\\.(com|ch)|[^.]+\\.example"));
    }

    @Test
    void should_fitTheUserProfileColumns() {
        // user_profiles.company_id is VARCHAR(12) (CUMS V4); names and e-mail are 100 and 255.
        assertThat(ProductionShapedPeople.ALL)
                .allMatch(p -> p.companyId() == null || p.companyId().length() <= 12)
                .allMatch(p -> p.firstName().length() <= 100 && p.lastName().length() <= 100);
    }

    @Test
    void should_storeEveryNameInNfc() {
        assertThat(ProductionShapedPeople.ALL).allMatch(p -> isNfc(p.firstName()) && isNfc(p.lastName()));
    }

    @Test
    void should_listTheEarlierNamesake_when_askedForTheSecond() {
        Person second = ProductionShapedPeople.IDENTICAL_NAMES.get(1);

        assertThat(ProductionShapedPeople.namesakesBefore(second))
                .containsExactly(ProductionShapedPeople.IDENTICAL_NAMES.get(0));
        assertThat(ProductionShapedPeople.namesakesBefore(ProductionShapedPeople.VIETNAMESE)).isEmpty();
    }

    private static boolean isNfc(String s) {
        return Normalizer.isNormalized(s, Normalizer.Form.NFC);
    }

    private static boolean isAscii(String s) {
        return s.chars().allMatch(c -> c < 0x80);
    }

    private static String domainOf(String email) {
        return email.substring(email.lastIndexOf('@') + 1);
    }

    private static String localPartOf(String email) {
        return email.substring(0, email.lastIndexOf('@'));
    }
}
