package org.example.ai_agent.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "org.example.ai_agent", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleArchitectureTest {

    @ArchTest
    static final ArchRule controllersDoNotAccessRepositories = noClasses()
            .that().resideInAnyPackage("..controller..")
            .should().dependOnClassesThat().resideInAnyPackage("..repository..")
            .because("HTTP Adapter must call a domain Module Interface rather than persistence adapters");

    @ArchTest
    static final ArchRule kernelHasNoFrameworkOrInfrastructureDependencies = noClasses()
            .that().resideInAnyPackage("..kernel..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "org.springframework.amqp..",
                    "org.springframework.data..",
                    "io.lettuce..",
                    "co.elastic.clients..",
                    "com.aliyun.oss..")
            .because("kernel must remain a framework-free Module that supplies stable domain types");
}
