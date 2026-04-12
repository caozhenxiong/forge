package devflow.agent.context;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductContractTests {

    @Test
    void projectedPrdSectionsSeparateRequiredOptionalAndAcceptanceObligations() {
        ProductContract contract = ProductContract.projectedFromPrdSections(
                List.of("实现俄罗斯方块"),
                List.of("浏览器用户"),
                List.of("支持移动与旋转方块", "显示当前得分"),
                List.of("显示下一个方块预览"),
                List.of(),
                List.of("页面可直接打开运行"),
                List.of()
        );

        List<RequirementReference> references = contract.bindingRequirements();

        assertEquals(4, references.size());
        assertEquals(CoverageObligation.PLANNING_REQUIRED, references.get(0).obligation());
        assertEquals(CoverageObligation.PLANNING_REQUIRED, references.get(1).obligation());
        assertEquals(CoverageObligation.OPTIONAL, references.get(2).obligation());
        assertEquals(CoverageObligation.FINAL_ACCEPTANCE, references.get(3).obligation());
        assertTrue(contract.optionalCapabilities().contains("显示下一个方块预览"));
        assertTrue(contract.planningCoverageRequirements().stream().anyMatch(reference -> "CAP-1".equals(reference.id())));
        assertTrue(contract.planningCoverageRequirements().stream().noneMatch(reference -> "CAP-3".equals(reference.id())));
    }

    @Test
    void explicitRequirementReferencesRemainAuthoritative() {
        ProductContract contract = new ProductContract(
                List.of("实现俄罗斯方块"),
                List.of("浏览器用户"),
                List.of("支持移动与旋转方块"),
                List.of("显示当前得分"),
                List.of(),
                List.of("页面可直接打开运行"),
                List.of(),
                List.of(
                        new RequirementReference("CAP-1", "required-capability", "支持移动与旋转方块", CoverageObligation.PLANNING_REQUIRED),
                        new RequirementReference("CAP-2", "optional-capability", "显示当前得分", CoverageObligation.OPTIONAL),
                        new RequirementReference("ACC-1", "acceptance-criterion", "页面可直接打开运行", CoverageObligation.FINAL_ACCEPTANCE)
                )
        );

        List<RequirementReference> references = contract.bindingRequirements();

        assertEquals(3, references.size());
        assertEquals(CoverageObligation.PLANNING_REQUIRED, references.get(0).obligation());
        assertEquals(CoverageObligation.OPTIONAL, references.get(1).obligation());
        assertEquals(CoverageObligation.FINAL_ACCEPTANCE, references.get(2).obligation());
        assertTrue(contract.planningCoverageRequirements().stream().anyMatch(reference -> "CAP-1".equals(reference.id())));
        assertTrue(contract.planningCoverageRequirements().stream().noneMatch(reference -> "CAP-2".equals(reference.id())));
    }
}
