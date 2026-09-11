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

    @ArchTest
    static final ArchRule ingestionDomainTypesHaveNoFrameworkOrAdapterDependencies = noClasses()
            .that().resideInAnyPackage("..knowledge.ingestion..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "com.rabbitmq..",
                    "co.elastic.clients..",
                    "com.aliyun.oss..",
                    "io.r2dbc..")
            .because("IngestionRun decisions are domain Interface types, not transport or persistence types");

    @ArchTest
    static final ArchRule retrievalDomainTypesHaveNoFrameworkOrAdapterDependencies = noClasses()
            .that().resideInAnyPackage("..knowledge.retrieval..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "com.rabbitmq..",
                    "co.elastic.clients..",
                    "com.aliyun.oss..",
                    "io.r2dbc..")
            .because("retrieval outcomes belong to the knowledge domain and are implemented by adapters");

    @ArchTest
    static final ArchRule conversationContextDomainTypesHaveNoFrameworkOrAdapterDependencies = noClasses()
            .that().resideInAnyPackage("..conversation..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "org.springframework.data..",
                    "io.r2dbc..",
                    "io.lettuce..")
            .because("completed conversation context is a framework-free domain contract");
}
