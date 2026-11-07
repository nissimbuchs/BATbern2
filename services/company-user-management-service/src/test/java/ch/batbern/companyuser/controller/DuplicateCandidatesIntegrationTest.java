package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Organizers get a list of profile pairs that are probably one person, so they can merge them with
 * the existing organizer merge (#1183 C) instead of searching 2500 profiles by hand. Rules measured
 * on production 2026-11-07: same name (257 pairs), swapped first and last name (24), same address
 * name at another domain (22).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("Duplicate profile candidates")
class DuplicateCandidatesIntegrationTest extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/users/duplicate-candidates";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        save("orga.nizer", "Orga", "Nizer", "orga@example.com", null, true);
    }

    private User save(String username, String first, String last, String email, String company, boolean login) {
        return userRepository.saveAndFlush(User.builder().username(username).firstName(first).lastName(last)
                .email(email).companyId(company).cognitoUserId(login ? "cog-" + username : null)
                .roles(new HashSet<>(Set.of(login && username.startsWith("orga") ? Role.ORGANIZER : Role.ATTENDEE)))
                .build());
    }

    private List<JsonNode> list() throws Exception {
        String body = mockMvc.perform(get(URL).with(organizer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<JsonNode> pairs = new ArrayList<>();
        objectMapper.readTree(body).forEach(pairs::add);
        return pairs;
    }

    private static Set<String> names(JsonNode pair) {
        return Set.of(pair.path("target").path("username").asText(), pair.path("source").path("username").asText());
    }

    private JsonNode pairOf(List<JsonNode> pairs, String a, String b) {
        return pairs.stream().filter(p -> names(p).equals(Set.of(a, b))).findFirst().orElse(null);
    }

    @Test
    @DisplayName("same name ignoring case, umlauts, accents and hyphens; the login profile is the target")
    void sameName_listed_loginIsTarget() throws Exception {
        save("hans.mueller", "Hans-Peter", "Müller", "hp@firma.example.com", "firma", false);
        save("hans.mueller.2", "hans peter", "Mueller", "hp.mueller@gmail.example.com", null, true);
        save("rene.favre", "René", "Favre", "rene@a.example.com", null, false);
        save("rene.favre.2", "Rene", "FAVRE", "rf@b.example.com", null, false);

        List<JsonNode> pairs = list();

        JsonNode hans = pairOf(pairs, "hans.mueller", "hans.mueller.2");
        assertThat(hans).isNotNull();
        assertThat(hans.path("rule").asText()).isEqualTo("SAME_NAME");
        assertThat(hans.path("confidence").asText()).isEqualTo("MEDIUM");
        assertThat(hans.path("target").path("username").asText()).isEqualTo("hans.mueller.2");
        assertThat(hans.path("target").path("hasLogin").asBoolean()).isTrue();
        assertThat(hans.path("source").path("email").asText()).isEqualTo("hp@firma.example.com");
        assertThat(hans.path("organizerCanMerge").asBoolean()).isTrue();
        assertThat(pairOf(pairs, "rene.favre", "rene.favre.2")).isNotNull();
    }

    @Test
    @DisplayName("same name with an imported placeholder address or the same company: HIGH; the placeholder is the source")
    void sameName_placeholderOrCompany_high() throws Exception {
        save("kilian.stillhard", "Kilian", "Stillhard", "kilian.stillhard@batbern.ch", "elca", false);
        save("kilian.stillhard.2", "Kilian", "Stillhard", "k.stillhard@docu.example.com", null, false);
        save("eva.roth", "Eva", "Roth", "eva@sbb.example.com", "sbb", false);
        save("eva.roth.2", "Eva", "Roth", "eva.roth@sbb2.example.com", "sbb", false);

        List<JsonNode> pairs = list();

        JsonNode kilian = pairOf(pairs, "kilian.stillhard", "kilian.stillhard.2");
        assertThat(kilian.path("confidence").asText()).isEqualTo("HIGH");
        assertThat(kilian.path("source").path("username").asText()).isEqualTo("kilian.stillhard");
        assertThat(kilian.path("source").path("placeholderAddress").asBoolean()).isTrue();
        assertThat(pairOf(pairs, "eva.roth", "eva.roth.2").path("confidence").asText()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("first and last name swapped: SWAPPED_NAME, HIGH")
    void swapped_listed() throws Exception {
        save("fred.furrer", "Fred", "Furrer", "fred@adesso.example.com", null, false);
        save("furrer.fred", "Furrer", "Fred", "furrer@ims.example.com", null, false);

        JsonNode pair = pairOf(list(), "fred.furrer", "furrer.fred");

        assertThat(pair.path("rule").asText()).isEqualTo("SWAPPED_NAME");
        assertThat(pair.path("confidence").asText()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("same vorname.nachname before the @ at another domain, different name: SAME_ADDRESS_NAME, LOW")
    void sameAddressName_listed_low() throws Exception {
        save("thomas.klingl", "Thomas", "Klingl", "tom.klingl@bluewin.example.com", null, false);
        save("tom.klingl", "Tom", "Klingl", "tom.klingl@bafu.example.com", null, false);
        save("info.a", "Anna", "A", "info@a.example.com", null, false);
        save("info.b", "Bert", "B", "info@b.example.com", null, false);

        List<JsonNode> pairs = list();

        JsonNode pair = pairOf(pairs, "thomas.klingl", "tom.klingl");
        assertThat(pair.path("rule").asText()).isEqualTo("SAME_ADDRESS_NAME");
        assertThat(pair.path("confidence").asText()).isEqualTo("LOW");
        // no dot before the @ (info@, office@): too generic to mean one person
        assertThat(pairOf(pairs, "info.a", "info.b")).isNull();
    }

    @Test
    @DisplayName("both with a login: listed, but only the person can merge")
    void bothLogin_notMergeableByOrganizer() throws Exception {
        save("markus.maeder", "Markus", "Maeder", "mm@ejpd.example.com", null, true);
        save("markus.maeder.2", "Markus", "Maeder", "mm@foto.example.com", null, true);

        JsonNode pair = pairOf(list(), "markus.maeder", "markus.maeder.2");

        assertThat(pair.path("organizerCanMerge").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("inactive profiles and different names are not listed")
    void inactiveAndDifferent_notListed() throws Exception {
        save("anna.keller", "Anna", "Keller", "anna@a.example.com", null, false);
        User inactive = save("anna.keller.2", "Anna", "Keller", "anna@b.example.com", null, false);
        inactive.setActive(false);
        userRepository.saveAndFlush(inactive);
        save("anna.kellner", "Anna", "Kellner", "anna@c.example.com", null, false);

        List<JsonNode> pairs = list();

        assertThat(pairOf(pairs, "anna.keller", "anna.keller.2")).isNull();
        assertThat(pairOf(pairs, "anna.keller", "anna.kellner")).isNull();
    }

    @Test
    @DisplayName("'not a duplicate' hides the pair for good, in both directions")
    void dismiss_hidesPair() throws Exception {
        save("simon.weber", "Simon", "Weber", "sw@a.example.com", null, false);
        save("simon.weber.2", "Simon", "Weber", "sw@b.example.com", null, true);

        mockMvc.perform(post(URL + "/dismissals").with(organizer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameA\":\"simon.weber.2\",\"usernameB\":\"simon.weber\"}"))
                .andExpect(status().isNoContent());
        // twice is fine
        mockMvc.perform(post(URL + "/dismissals").with(organizer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameA\":\"simon.weber\",\"usernameB\":\"simon.weber.2\"}"))
                .andExpect(status().isNoContent());

        assertThat(pairOf(list(), "simon.weber", "simon.weber.2")).isNull();
    }

    @Test
    @DisplayName("dismissing an unknown profile is 404")
    void dismiss_unknown_notFound() throws Exception {
        save("simon.weber", "Simon", "Weber", "sw@a.example.com", null, false);
        mockMvc.perform(post(URL + "/dismissals").with(organizer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameA\":\"simon.weber\",\"usernameB\":\"nobody.here\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("only organizers see the list and dismiss pairs")
    void participant_forbidden() throws Exception {
        RequestPostProcessor attendee = user("anna.keller").roles("ATTENDEE");
        mockMvc.perform(get(URL).with(attendee)).andExpect(status().isForbidden());
        mockMvc.perform(post(URL + "/dismissals").with(attendee).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameA\":\"a\",\"usernameB\":\"b\"}"))
                .andExpect(status().isForbidden());
    }

    private static RequestPostProcessor organizer() {
        return user("orga.nizer").roles("ORGANIZER");
    }
}
