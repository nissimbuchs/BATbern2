package ch.batbern.events.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.List;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The EMS module rules, built for any root package so the fixture tests can prove that each
 * rule fires. {@link EmsModuleWallsTest} freezes them against {@code ch.batbern.events}.
 *
 * <p>The rule descriptions are the keys of the frozen store
 * ({@code src/test/resources/archunit_store/stored.rules}). A new or renamed description is stored
 * on its first run with all its current violations accepted; {@link FrozenRuleKeysTest} fails on any
 * key that is not in the committed store, so such a change cannot slip through.
 */
final class EmsModuleRules {

    /** Mail center (L6 in the module map): {@code forwarding} belongs to it. */
    static final List<String> MAIL_CENTER = List.of("mailcenter", "forwarding");

    /**
     * Mail core (L1 in the module map: {@code MailPipeline}, rendering, variables) still lives inside
     * {@code mailcenter}. Every mail-sending service uses it, as the module map tells it to, so it is
     * not part of the mail-center wall. Once mail core leaves the package, this exception goes.
     */
    static final List<String> MAIL_CORE_IN_MAILCENTER = List.of("mailcenter.render", "mailcenter.variables");

    private EmsModuleRules() {
    }

    /**
     * No class outside the given L6 packages depends on them (mail core inside mailcenter excepted).
     * The packages form one module: they may use each other.
     */
    static ArchRule edgeModuleIsolated(String root, String name, List<String> modulePackages) {
        String[] pkgs = packages(root, modulePackages);
        String exception = modulePackages.contains("mailcenter")
                ? ", mail core in mailcenter.render and mailcenter.variables excepted" : "";
        DescribedPredicate<JavaClass> edge = resideInAnyPackage(pkgs)
                .and(not(resideInAnyPackage(packages(root, MAIL_CORE_IN_MAILCENTER))));
        return noClasses().that().resideOutsideOfPackages(pkgs)
                .should().dependOnClassesThat(edge.as("reside in " + name + exception))
                .as("EMS module wall: no class outside " + name + " (L6, packages " + modulePackages
                        + ") depends on it" + exception)
                .allowEmptyShould(true);
    }

    /**
     * Mail core ({@code mailcenter.render}, {@code mailcenter.variables}) is L1: it uses nothing else
     * of the mail center. Without this rule the mail-core exception of the wall would hide such an
     * upward edge, because mail core still sits inside the {@code mailcenter} package.
     */
    static ArchRule mailCoreUsesNoMailCenter(String root) {
        DescribedPredicate<JavaClass> center = resideInAnyPackage(packages(root, MAIL_CENTER))
                .and(not(resideInAnyPackage(packages(root, MAIL_CORE_IN_MAILCENTER))));
        return noClasses().that().resideInAnyPackage(packages(root, MAIL_CORE_IN_MAILCENTER))
                .should().dependOnClassesThat(center.as("reside in the mail center outside mail core"))
                .as("EMS module wall: mail core (mailcenter.render, mailcenter.variables, L1) uses no other"
                        + " mail center class (mailcenter, forwarding)")
                .allowEmptyShould(true);
    }

    /**
     * {@code notification} (organizer notifications and notify core) uses no L6 module: dependencies
     * point down, never sideways. Mail core inside mailcenter is allowed.
     */
    static ArchRule notificationUsesNoEdgeModule(String root) {
        DescribedPredicate<JavaClass> edge = resideInAnyPackage(
                packages(root, List.of("mailcenter", "forwarding", "watch", "usermerge")))
                .and(not(resideInAnyPackage(packages(root, MAIL_CORE_IN_MAILCENTER))));
        return noClasses().that().resideInAPackage(root + ".notification..")
                .should().dependOnClassesThat(edge.as("reside in mail center, watch or usermerge"))
                .as("EMS module wall: notification uses no L6 module (mail center, watch, usermerge),"
                        + " mail core in mailcenter.render and mailcenter.variables excepted")
                .allowEmptyShould(true);
    }

    /**
     * ADR-016: EMS is upstream of PCS, so it must not call PCS. A service-to-service client here is
     * always a {@code client/} interface named after its target (plus its {@code …ClientImpl}), so the
     * rule goes by name.
     */
    static ArchRule noClientTowardPartnerService(String root) {
        return classes().that().resideInAPackage(root + "..")
                .and().areTopLevelClasses()
                .and().haveNameMatching(".*\\.[^.]*Partner[^.]*Client[^.]*")
                .should(notExist("a client toward partner-coordination, which is downstream of EMS (ADR-016)"))
                .as("Service direction: EMS has no client toward partner-coordination (downstream)")
                .allowEmptyShould(true);
    }

    /**
     * Story 17.11: EMS keeps its files in its own table ({@code event_management_uploads}). No entity
     * of this service maps an upload table of another service: {@code company_user_uploads}
     * (company-user-management's, ADR-002 decision 2026-11-07) or the former shared {@code logos},
     * dropped by #1307 (CUMS V31, EMS V154). Not frozen: there is no violation to accept.
     */
    static ArchRule noEntityMapsForeignUploadTable(String root) {
        return classes().that().resideInAPackage(root + "..")
                .and().areAnnotatedWith(Entity.class)
                .should(notMapTable("company_user_uploads"))
                .andShould(notMapTable("logos"))
                .as("Data ownership: no EMS entity maps an upload table of another service (Story 17.11, #1307)")
                .allowEmptyShould(true);
    }

    static ArchCondition<JavaClass> notMapTable(String table) {
        return new ArchCondition<>("not map the table " + table) {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                javaClass.tryGetAnnotationOfType(Table.class)
                        .filter(t -> t.name().equalsIgnoreCase(table))
                        .ifPresent(t -> events.add(SimpleConditionEvent.violated(javaClass,
                                "Entity <" + javaClass.getName() + "> maps the table " + table)));
            }
        };
    }

    static ArchCondition<JavaClass> notExist(String reason) {
        return new ArchCondition<>("not exist") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                events.add(SimpleConditionEvent.violated(javaClass,
                        "Class <" + javaClass.getName() + "> is " + reason));
            }
        };
    }

    private static String[] packages(String root, List<String> modules) {
        return modules.stream().map(m -> root + "." + m + "..").toArray(String[]::new);
    }
}
