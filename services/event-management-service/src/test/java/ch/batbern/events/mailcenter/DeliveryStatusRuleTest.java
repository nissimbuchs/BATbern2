package ch.batbern.events.mailcenter;

import ch.batbern.events.domain.MailRecipient.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Delivery status never moves backwards (Story 16.1b AC6)")
class DeliveryStatusRuleTest {

    @Test
    @DisplayName("SENT moves to DELIVERED or FAILED")
    void sentMovesForward() {
        assertThat(DeliveryStatusRule.mayMove(Status.SENT, Status.DELIVERED)).isTrue();
        assertThat(DeliveryStatusRule.mayMove(Status.SENT, Status.FAILED)).isTrue();
    }

    @Test
    @DisplayName("DELIVERED becomes FAILED on a later complaint")
    void deliveredMayFail() {
        assertThat(DeliveryStatusRule.mayMove(Status.DELIVERED, Status.FAILED)).isTrue();
    }

    @Test
    @DisplayName("FAILED is final: no later DELIVERED, and the first reason is kept")
    void failedIsFinal() {
        assertThat(DeliveryStatusRule.mayMove(Status.FAILED, Status.DELIVERED)).isFalse();
        assertThat(DeliveryStatusRule.mayMove(Status.FAILED, Status.FAILED)).isFalse();
        assertThat(DeliveryStatusRule.mayMove(Status.FAILED, Status.SENT)).isFalse();
    }

    @Test
    @DisplayName("a repeated event is a no-op")
    void sameStatusIsNoOp() {
        assertThat(DeliveryStatusRule.mayMove(Status.DELIVERED, Status.DELIVERED)).isFalse();
        assertThat(DeliveryStatusRule.mayMove(Status.DELIVERED, Status.SENT)).isFalse();
    }

    @Test
    @DisplayName("the SQL guard lists exactly the statuses the rule allows")
    void sourcesMatchRule() {
        assertThat(DeliveryStatusRule.sourcesFor(Status.DELIVERED)).containsExactlyInAnyOrder(Status.SENT);
        assertThat(DeliveryStatusRule.sourcesFor(Status.FAILED))
                .containsExactlyInAnyOrder(Status.SENT, Status.DELIVERED);
    }
}
