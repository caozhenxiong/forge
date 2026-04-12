package devflow.agent.context;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductContractTests {

    @Test
    void defaultRequirementReferencesPromoteRequiredCapabilitiesIntoPlanningCoverage() {
        ProductContract contract = new ProductContract(
                List.of("实现俄罗斯方块"),
                List.of("浏览器用户"),
                List.of("支持移动与旋转方块", "显示当前得分"),
                List.of(),
                List.of("页面可直接打开运行"),
                List.of()
        );

        List<RequirementReference> references = contract.bindingRequirements();

        assertEquals(3, references.size());
        assertTrue(references.get(0).planningRequired());
        assertTrue(references.get(1).planningRequired());
        assertFalse(references.get(2).planningRequired());
        assertTrue(contract.planningCoverageRequirements().stream().anyMatch(reference -> "CAP-1".equals(reference.id())));
    }

    @Test
    void explicitRequirementReferencesOverrideFallbackReferences() {
        ProductContract contract = new ProductContract(
                List.of("实现俄罗斯方块"),
                List.of("浏览器用户"),
                List.of("支持移动与旋转方块", "显示当前得分"),
                List.of(),
                List.of("页面可直接打开运行"),
                List.of(),
                List.of(
                        new RequirementReference("CAP-1", "required-capability", "支持移动与旋转方块", true),
                        new RequirementReference("ACC-1", "acceptance-criterion", "页面可直接打开运行", false)
                )
        );

        List<RequirementReference> references = contract.bindingRequirements();

        assertEquals(2, references.size());
        assertTrue(references.get(0).planningRequired());
        assertFalse(references.get(1).planningRequired());
        assertTrue(contract.planningCoverageRequirements().stream().anyMatch(reference -> "CAP-1".equals(reference.id())));
    }
}
