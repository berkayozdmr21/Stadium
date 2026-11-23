package com.stadyum;

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
@AnalyzeClasses(packages = "com.stadyum", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainIsPlainJava = classes()
            .that().resideInAPackage("com.stadyum.domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("com.stadyum.domain..", "java..")
            .because("iş kuralları hiçbir çerçeveye bağlı olmamalı");

    @ArchTest
    static final ArchRule applicationKnowsNothingAboutDeliveryOrStorage = noClasses()
            .that().resideInAPackage("com.stadyum.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.stadyum.api..", "com.stadyum.infrastructure..",
                    "jakarta.persistence..", "jakarta.servlet..",
                    "org.springframework.web..", "org.springframework.data..", "org.springframework.jdbc..")
            .because("kullanım senaryoları HTTP'yi ve veritabanı teknolojisini bilmemeli");

    @ArchTest
    static final ArchRule applicationUsesSpringOnlyForWiringAndTransactions = classes()
            .that().resideInAPackage("com.stadyum.application..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "com.stadyum.application..", "com.stadyum.domain..", "java..",
                    "org.springframework.stereotype..", "org.springframework.transaction.annotation..");

    @ArchTest
    static final ArchRule apiTalksOnlyToTheApplicationLayer = noClasses()
            .that().resideInAPackage("com.stadyum.api..")
            .should().dependOnClassesThat().resideInAnyPackage("com.stadyum.infrastructure..", "jakarta.persistence..")
            .because("denetleyiciler depolara doğrudan erişmemeli");

    @ArchTest
    static final ArchRule infrastructureDoesNotDependOnApi = noClasses()
            .that().resideInAPackage("com.stadyum.infrastructure..")
            .should().dependOnClassesThat().resideInAPackage("com.stadyum.api..");

    @ArchTest
    static final ArchRule portsAreInterfaces = classes()
            .that().resideInAPackage("com.stadyum.application.port..").and().areTopLevelClasses()
            .should().beInterfaces();
}
