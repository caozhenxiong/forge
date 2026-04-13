package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.i18n.PlaceholderValues;
import java.nio.file.Path;
import java.util.List;

/**
 * 负责新建代码文件脚手架单元完成后的后续符号扩展计划。
 * 这样 precise-code 执行器不再同时承担“单元执行”和“脚手架扩展判断”。
 */
public final class CodeScaffoldExpansionPlanner {

    private final EditUnitPlanner editUnitPlanner;

    public CodeScaffoldExpansionPlanner(EditUnitPlanner editUnitPlanner) {
        this.editUnitPlanner = editUnitPlanner;
    }

    public boolean isBootstrapUnit(EditUnit unit, String currentContent) {
        return unit != null
                && !unit.restrictsSymbols()
                && (currentContent == null || currentContent.isBlank());
    }

    public List<EditUnit> planFollowUpCodeUnitsAfterScaffold(Path relativePath, String currentContent) {
        List<EditUnit> followUpUnits = editUnitPlanner.planCodeUnits(relativePath, currentContent);
        if (followUpUnits.isEmpty()) {
            return List.of();
        }
        if (followUpUnits.size() == 1 && !followUpUnits.getFirst().restrictsSymbols()) {
            return List.of();
        }
        return followUpUnits;
    }

    public String renderUnitLabels(List<EditUnit> units) {
        if (units == null || units.isEmpty()) {
            return PlaceholderValues.machineNone();
        }
        return units.stream()
                .map(EditUnit::label)
                .reduce((left, right) -> left + "," + right)
                .orElse(PlaceholderValues.machineNone());
    }
}
