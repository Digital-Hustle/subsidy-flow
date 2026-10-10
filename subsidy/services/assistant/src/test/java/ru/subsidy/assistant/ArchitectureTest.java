package ru.subsidy.assistant;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Правила слоёв из скилла backend-spring. Не удалять и не ослаблять без ADR.
 */
@AnalyzeClasses(packages = "ru.subsidy.assistant", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule CONTROLLERS_DO_NOT_TOUCH_PERSISTENCE = noClasses()
            .that().resideInAPackage("..api.controller..")
            .should().dependOnClassesThat().resideInAnyPackage("..repository..", "org.jooq..");

    @ArchTest
    static final ArchRule QUERIES_DO_NOT_CALL_COMMANDS = noClasses()
            .that().resideInAPackage("..query..")
            .should().dependOnClassesThat().resideInAPackage("..command..");

    @ArchTest
    static final ArchRule APPLICATION_DOES_NOT_USE_REPOSITORIES = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule SHARED_IS_INDEPENDENT = noClasses()
            .that().resideInAPackage("ru.subsidy.assistant.shared..")
            .should().dependOnClassesThat().resideInAnyPackage("ru.subsidy.assistant.features..", "ru.subsidy.assistant.api..");

    @ArchTest
    static final ArchRule FEATURES_ARE_FREE_OF_CYCLES = slices()
            .matching("ru.subsidy.assistant.features.(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule KAFKA_TEMPLATE_ONLY_IN_OUTBOX = noClasses()
            .that().resideOutsideOfPackage("..messaging.outbox..")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.kafka.core.KafkaTemplate");

    @ArchTest
    static final ArchRule NO_FIELD_INJECTION = noFields()
            .should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired");
}
