package ch.batbern.events.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Story 17.14: {@code GlobalExceptionHandler} maps domain exceptions only through {@code DomainException}
 * ({@code DomainErrorResponse} builds the body); a module exception with extra fields is mapped in its
 * module's advice; every exception of {@code exception/} extends {@code DomainException} and has no
 * {@code @ResponseStatus}. Not frozen: there is no violation ({@link FrozenRuleKeysTest} pins only
 * {@link EmsModuleWallsTest}). {@code EmsModuleRulesFixtureTest} proves that the rule fires.
 */
@AnalyzeClasses(packages = EmsModuleWallsTest.ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class EmsExceptionMappingTest {

    @ArchTest
    static final ArchRule GLOBAL_HANDLER_USES_ONLY_EXCEPTION_BASE =
            EmsModuleRules.globalExceptionHandlerUsesOnlyExceptionBase(EmsModuleWallsTest.ROOT);

    @ArchTest
    static final ArchRule EXCEPTIONS_EXTEND_DOMAIN_EXCEPTION =
            EmsModuleRules.exceptionsExtendDomainException(EmsModuleWallsTest.ROOT);
}
