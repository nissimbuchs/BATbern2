package ch.batbern.companyuser.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * ADR-016: CUMS is upstream of every service, so it calls none of them. A service-to-service client
 * here is always a {@code client/} interface named after its target (plus its implementations, e.g.
 * {@code …ClientImpl}), so the rule goes by name (ArchUnit cannot see URLs). Built for any root
 * package so the fixture test can prove it fires. The description is the key of the frozen store:
 * do not change it.
 */
final class ServiceDirectionRules {

    private ServiceDirectionRules() {
    }

    static ArchRule noClientTowardDownstreamService(String root) {
        return classes().that().resideInAPackage(root + "..")
                .and().areTopLevelClasses()
                .and().haveNameMatching(".*\\.[^.]*(EventManagement|Partner)[^.]*Client[^.]*")
                .should(notExist())
                .as("Service direction: CUMS has no client toward event-management or partner-coordination "
                        + "(downstream)")
                .allowEmptyShould(true);
    }

    private static ArchCondition<JavaClass> notExist() {
        return new ArchCondition<>("not exist") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                events.add(SimpleConditionEvent.violated(javaClass, "Class <" + javaClass.getName()
                        + "> is a client toward a service downstream of CUMS (ADR-016)"));
            }
        };
    }
}
