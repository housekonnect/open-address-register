package org.ugaddress.register;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Project rules from CLAUDE.md that the compiler cannot check.
 */
@AnalyzeClasses(packages = "org.ugaddress.register", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule noLombok = noClasses().should().dependOnClassesThat().resideInAPackage("lombok..")
        .because("Lombok is not used in this project");

    @ArchTest
    static final ArchRule noOrm = noClasses().should().dependOnClassesThat()
        .resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
        .because("data access uses jOOQ and plain SQL, never an ORM (ADR 0004)");

    @ArchTest
    static final ArchRule noMinio = noClasses().should().dependOnClassesThat().resideInAPackage("io.minio..")
        .because("object storage is accessed only through the S3 API via AWS SDK v2 (ADR 0007)");

    @ArchTest
    static final ArchRule controllersAreInternalAndNamed = classes().that().areAnnotatedWith(RestController.class)
        .should().haveSimpleNameEndingWith("Controller")
        .andShould().resideInAPackage("..internal..");

    @ArchTest
    static final ArchRule controllersDoNotUseTheDatabase = noClasses().that().areAnnotatedWith(RestController.class)
        .should().dependOnClassesThat().resideInAnyPackage("org.jooq..", "org.ugaddress.db..")
        .because("controllers delegate to services; only repositories talk to the database");

    @ArchTest
    static final ArchRule servicesAreNamed = classes().that().areAnnotatedWith(Service.class)
        .should().haveSimpleNameEndingWith("Service");

    @ArchTest
    static final ArchRule repositoriesAreNamed = classes().that().areAnnotatedWith(Repository.class)
        .should().haveSimpleNameEndingWith("Repository");

    @ArchTest
    static final ArchRule configurationsAreNamed = classes().that().areAnnotatedWith(Configuration.class)
        .should().haveSimpleNameEndingWith("Config");

    @ArchTest
    static final ArchRule adviceIsNamed = classes().that().areAnnotatedWith(RestControllerAdvice.class)
        .should().haveSimpleNameEndingWith("ControllerAdvice");

    @ArchTest
    static final ArchRule onlyAuditTouchesAuditTable = noClasses().that().resideOutsideOfPackage("..audit..")
        .should().dependOnClassesThat().haveFullyQualifiedName("org.ugaddress.db.generated.tables.AuditEvent")
        .because("every write to the audit log goes through the audit module");

    @ArchTest
    static final ArchRule generatedTypesDoNotLeakIntoModuleApis = noClasses()
        .that().resideOutsideOfPackage("..internal..")
        .and().haveSimpleNameNotEndingWith("Service")
        .should().dependOnClassesThat().resideInAPackage("org.ugaddress.db.generated..")
        .because("module APIs expose DTOs, never generated jOOQ types");

    @ArchTest
    static final ArchRule noStandardStreams = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    static final ArchRule noJavaUtilLogging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

    @ArchTest
    static final ArchRule noFieldInjection = NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
}
