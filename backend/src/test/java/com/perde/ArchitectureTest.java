package com.perde;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Katman sınırlarını derlenmiş kod üzerinde denetler; biri yanlış yöne bağımlılık eklerse test kırılır.
 *
 * <pre>
 *   api ──► application ──► domain
 *                ▲
 *   infrastructure (application'daki port arayüzlerini gerçekleştirir)
 * </pre>
 */
@AnalyzeClasses(packages = "com.perde", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainIsPlainJava = classes()
            .that().resideInAPackage("com.perde.domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("com.perde.domain..", "java..")
            .because("iş kuralları hiçbir çerçeveye bağlı olmamalı");

    @ArchTest
    static final ArchRule applicationKnowsNothingAboutDeliveryOrStorage = noClasses()
            .that().resideInAPackage("com.perde.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.perde.api..", "com.perde.infrastructure..",
                    "jakarta.persistence..", "jakarta.servlet..",
                    "org.springframework.web..", "org.springframework.data..", "org.springframework.jdbc..")
            .because("kullanım senaryoları HTTP'yi ve veritabanı teknolojisini bilmemeli");

    @ArchTest
    static final ArchRule applicationUsesSpringOnlyForWiringAndTransactions = classes()
            .that().resideInAPackage("com.perde.application..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "com.perde.application..", "com.perde.domain..", "java..",
                    "org.springframework.stereotype..", "org.springframework.transaction.annotation..");

    @ArchTest
    static final ArchRule apiTalksOnlyToTheApplicationLayer = noClasses()
            .that().resideInAPackage("com.perde.api..")
            .should().dependOnClassesThat().resideInAnyPackage("com.perde.infrastructure..", "jakarta.persistence..")
            .because("denetleyiciler depolara doğrudan erişmemeli");

    @ArchTest
    static final ArchRule infrastructureDoesNotDependOnApi = noClasses()
            .that().resideInAPackage("com.perde.infrastructure..")
            .should().dependOnClassesThat().resideInAPackage("com.perde.api..");

    @ArchTest
    static final ArchRule portsAreInterfaces = classes()
            .that().resideInAPackage("com.perde.application.port..").and().areTopLevelClasses()
            .should().beInterfaces();
}
