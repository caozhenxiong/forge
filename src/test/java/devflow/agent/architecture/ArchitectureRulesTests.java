package devflow.agent.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ArchitectureRulesTests {

    private static final Set<String> EXECUTOR_ROOT_WHITELIST = Set.of(
            "ImplementationExecutor",
            "ImplementationExecutionBundle",
            "ImplementationProgressSink",
            "ImplementationExecutorConfiguration",
            "ImplementationExecutorWiring",
            "FileChange",
            "SelfCheckResult",
            "ChangeAction",
            "DeliveryMode",
            "DeliveryPolicyEnvelope"
    );

    private final JavaClasses importedClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("devflow.agent");

    @Test
    void executorRootPackageRemainsOnWhitelist() {
        Set<String> actual = importedClasses.stream()
                .filter(javaClass -> "devflow.agent.executor".equals(javaClass.getPackageName()))
                .map(JavaClass::getSimpleName)
                .collect(Collectors.toSet());
        assertEquals(EXECUTOR_ROOT_WHITELIST, actual);
    }

    @Test
    void qualityMustNotDependOnExecutorTesting() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("devflow.agent.quality..")
                .should().dependOnClassesThat().resideInAnyPackage("devflow.agent.executor.testing..");
        rule.check(importedClasses);
    }

    @Test
    void preciseEditingMustNotDependOnExecutorPackages() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("devflow.agent.editing.precise..")
                .should().dependOnClassesThat().resideInAnyPackage("devflow.agent.executor..");
        rule.check(importedClasses);
    }

    @Test
    void mainCodeMustNotReadSystemProperties() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("devflow.agent..")
                .should().callMethod(System.class, "getProperty", String.class);
        rule.check(importedClasses);
    }
}
