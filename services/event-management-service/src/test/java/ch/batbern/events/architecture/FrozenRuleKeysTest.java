package ch.batbern.events.architecture;

import ch.batbern.shared.test.FrozenArchRuleKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the keys of the committed {@code archunit_store/stored.rules} to exactly the frozen rules
 * {@link EmsModuleWallsTest} declares, so a renamed, added or removed rule fails here instead of being
 * stored with all its current violations accepted. The check lives in shared-kernel test fixtures.
 */
@DisplayName("Frozen ArchUnit rules match the committed store")
class FrozenRuleKeysTest {

    @Test
    @DisplayName("should_matchStoreKeys_when_rulesAreDeclared")
    void should_matchStoreKeys_when_rulesAreDeclared() {
        FrozenArchRuleKeys.assertStoreMatches(EmsModuleWallsTest.class, FrozenArchRuleKeys.DEFAULT_STORE);
    }
}
