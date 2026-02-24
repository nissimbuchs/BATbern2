package ch.batbern.events.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static ch.batbern.events.service.BatbernCluster.AI_ML;
import static ch.batbern.events.service.BatbernCluster.ARCHITECTURE;
import static ch.batbern.events.service.BatbernCluster.BUSINESS_OTHER;
import static ch.batbern.events.service.BatbernCluster.CLOUD_INFRA;
import static ch.batbern.events.service.BatbernCluster.DATA;
import static ch.batbern.events.service.BatbernCluster.MOBILE;
import static ch.batbern.events.service.BatbernCluster.SECURITY;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BatbernTopicClusterService (Story 10.4 Task 2).
 *
 * TDD RED phase: These tests should FAIL until BatbernTopicClusterService is implemented.
 *
 * Verifies:
 * - getCluster() returns correct cluster for all 30 known event numbers
 * - getCluster() returns BUSINESS_OTHER for unknown event numbers
 * - matchCluster() keyword matching maps topic text to correct cluster
 * - matchCluster() falls back to BUSINESS_OTHER for unrecognised topics
 */
class BatbernTopicClusterServiceTest {

    private BatbernTopicClusterService service;

    @BeforeEach
    void setUp() {
        service = new BatbernTopicClusterService();
    }

    // ==================== getCluster() — known AI_ML events ====================

    @ParameterizedTest(name = "event {0} → AI_ML")
    @CsvSource({"40", "44", "49", "56", "58"})
    @DisplayName("should_returnAiMl_when_knownAiMlEventNumber")
    void should_returnAiMl_when_knownAiMlEventNumber(int eventNumber) {
        assertThat(service.getCluster(eventNumber)).isEqualTo(AI_ML);
    }

    // ==================== getCluster() — known SECURITY events ====================

    @ParameterizedTest(name = "event {0} → SECURITY")
    @CsvSource({"16", "27", "38", "48", "57"})
    @DisplayName("should_returnSecurity_when_knownSecurityEventNumber")
    void should_returnSecurity_when_knownSecurityEventNumber(int eventNumber) {
        assertThat(service.getCluster(eventNumber)).isEqualTo(SECURITY);
    }

    // ==================== getCluster() — known ARCHITECTURE events ====================

    @ParameterizedTest(name = "event {0} → ARCHITECTURE")
    @CsvSource({"2", "3", "12", "13", "30", "41", "43", "55"})
    @DisplayName("should_returnArchitecture_when_knownArchitectureEventNumber")
    void should_returnArchitecture_when_knownArchitectureEventNumber(int eventNumber) {
        assertThat(service.getCluster(eventNumber)).isEqualTo(ARCHITECTURE);
    }

    // ==================== getCluster() — known DATA events ====================

    @ParameterizedTest(name = "event {0} → DATA")
    @CsvSource({"15", "18", "29", "33", "52", "53"})
    @DisplayName("should_returnData_when_knownDataEventNumber")
    void should_returnData_when_knownDataEventNumber(int eventNumber) {
        assertThat(service.getCluster(eventNumber)).isEqualTo(DATA);
    }

    // ==================== getCluster() — known CLOUD_INFRA events ====================

    @ParameterizedTest(name = "event {0} → CLOUD_INFRA")
    @CsvSource({"36", "39", "51", "54"})
    @DisplayName("should_returnCloudInfra_when_knownCloudInfraEventNumber")
    void should_returnCloudInfra_when_knownCloudInfraEventNumber(int eventNumber) {
        assertThat(service.getCluster(eventNumber)).isEqualTo(CLOUD_INFRA);
    }

    // ==================== getCluster() — known MOBILE events ====================

    @ParameterizedTest(name = "event {0} → MOBILE")
    @CsvSource({"22", "26"})
    @DisplayName("should_returnMobile_when_knownMobileEventNumber")
    void should_returnMobile_when_knownMobileEventNumber(int eventNumber) {
        assertThat(service.getCluster(eventNumber)).isEqualTo(MOBILE);
    }

    // ==================== getCluster() — unknown events fall back to BUSINESS_OTHER ====================

    @ParameterizedTest(name = "event {0} → BUSINESS_OTHER")
    @CsvSource({"1", "5", "10", "20", "25", "35", "50", "60", "100"})
    @DisplayName("should_returnBusinessOther_when_unknownEventNumber")
    void should_returnBusinessOther_when_unknownEventNumber(int eventNumber) {
        assertThat(service.getCluster(eventNumber)).isEqualTo(BUSINESS_OTHER);
    }

    // ==================== matchCluster() — keyword-based text matching ====================

    @Test
    @DisplayName("should_matchAiMl_when_topicContainsAiKeyword")
    void should_matchAiMl_when_topicContainsAiKeyword() {
        assertThat(service.matchCluster("AI in Swiss Enterprise")).isEqualTo(AI_ML);
    }

    @Test
    @DisplayName("should_matchAiMl_when_topicContainsMachineLearning")
    void should_matchAiMl_when_topicContainsMachineLearning() {
        assertThat(service.matchCluster("Machine Learning Operations")).isEqualTo(AI_ML);
    }

    @Test
    @DisplayName("should_matchAiMl_when_topicContainsLlm")
    void should_matchAiMl_when_topicContainsLlm() {
        assertThat(service.matchCluster("LLM Fine-Tuning in Production")).isEqualTo(AI_ML);
    }

    @Test
    @DisplayName("should_matchSecurity_when_topicContainsZeroTrust")
    void should_matchSecurity_when_topicContainsZeroTrust() {
        assertThat(service.matchCluster("Zero Trust Network Architecture")).isEqualTo(SECURITY);
    }

    @Test
    @DisplayName("should_matchSecurity_when_topicContainsCyber")
    void should_matchSecurity_when_topicContainsCyber() {
        assertThat(service.matchCluster("Cybersecurity Mesh")).isEqualTo(SECURITY);
    }

    @Test
    @DisplayName("should_matchArchitecture_when_topicContainsMicroservices")
    void should_matchArchitecture_when_topicContainsMicroservices() {
        assertThat(service.matchCluster("Microservices at Scale")).isEqualTo(ARCHITECTURE);
    }

    @Test
    @DisplayName("should_matchArchitecture_when_topicContainsDdd")
    void should_matchArchitecture_when_topicContainsDdd() {
        assertThat(service.matchCluster("Domain-Driven Design Patterns")).isEqualTo(ARCHITECTURE);
    }

    @Test
    @DisplayName("should_matchData_when_topicContainsDataPipeline")
    void should_matchData_when_topicContainsDataPipeline() {
        assertThat(service.matchCluster("Data Pipeline Best Practices")).isEqualTo(DATA);
    }

    @Test
    @DisplayName("should_matchData_when_topicContainsAnalytics")
    void should_matchData_when_topicContainsAnalytics() {
        assertThat(service.matchCluster("Real-Time Analytics with Kafka")).isEqualTo(DATA);
    }

    @Test
    @DisplayName("should_matchCloudInfra_when_topicContainsKubernetes")
    void should_matchCloudInfra_when_topicContainsKubernetes() {
        assertThat(service.matchCluster("Kubernetes in Production")).isEqualTo(CLOUD_INFRA);
    }

    @Test
    @DisplayName("should_matchCloudInfra_when_topicContainsCloud")
    void should_matchCloudInfra_when_topicContainsCloud() {
        assertThat(service.matchCluster("Cloud Cost Optimisation")).isEqualTo(CLOUD_INFRA);
    }

    @Test
    @DisplayName("should_matchMobile_when_topicContainsMobile")
    void should_matchMobile_when_topicContainsMobile() {
        assertThat(service.matchCluster("Mobile App Development in 2026")).isEqualTo(MOBILE);
    }

    @Test
    @DisplayName("should_matchMobile_when_topicContainsFlutter")
    void should_matchMobile_when_topicContainsFlutter() {
        assertThat(service.matchCluster("Flutter vs React Native")).isEqualTo(MOBILE);
    }

    @Test
    @DisplayName("should_returnBusinessOther_when_topicIsCompletelyUnknown")
    void should_returnBusinessOther_when_topicIsCompletelyUnknown() {
        assertThat(service.matchCluster("Quantum Entanglement in Blockchain")).isEqualTo(BUSINESS_OTHER);
    }

    @Test
    @DisplayName("should_returnBusinessOther_when_topicIsBlank")
    void should_returnBusinessOther_when_topicIsBlank() {
        assertThat(service.matchCluster("")).isEqualTo(BUSINESS_OTHER);
        assertThat(service.matchCluster("   ")).isEqualTo(BUSINESS_OTHER);
    }

    @Test
    @DisplayName("should_matchCaseInsensitive_when_topicUsesUpperCase")
    void should_matchCaseInsensitive_when_topicUsesUpperCase() {
        assertThat(service.matchCluster("ARTIFICIAL INTELLIGENCE AGENTS")).isEqualTo(AI_ML);
    }
}
