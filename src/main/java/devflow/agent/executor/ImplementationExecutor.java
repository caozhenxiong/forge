package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.context.ContractView;
import devflow.agent.domain.RunRecord;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * Implementation 阶段的对外门面。
 *
 * <p>它只保留：
 * 1. 对外 execute 入口；
 * 2. 对内部 coordinator 的委托；
 * 3. tool loop 执行器生命周期管理。
 *
 * <p>具体依赖装配已经移出到独立 wiring / configuration 层，
 * 这里不再承担构造器里的工厂职责。
 */
public class ImplementationExecutor implements AutoCloseable {

    private final CoderTurnCoordinator coderTurnCoordinator;
    private final ExecutorService toolExecutor;

    public ImplementationExecutor(
            CoderTurnCoordinator coderTurnCoordinator,
            ExecutorService toolExecutor
    ) {
        this.coderTurnCoordinator = Objects.requireNonNull(coderTurnCoordinator, "coderTurnCoordinator");
        this.toolExecutor = Objects.requireNonNull(toolExecutor, "toolExecutor");
    }

    public ImplementationExecutionBundle execute(Path projectPath, RunRecord runRecord, String analysis, String prd, String design, String note) {
        return execute(projectPath, runRecord, analysis, prd, design, note, null, "", ImplementationProgressSink.noop());
    }

    public ImplementationExecutionBundle execute(
            Path projectPath,
            RunRecord runRecord,
            String analysis,
            String prd,
            String design,
            String note,
            ContractView authoritativeContractView
    ) {
        return execute(projectPath, runRecord, analysis, prd, design, note, authoritativeContractView, "", ImplementationProgressSink.noop());
    }

    public ImplementationExecutionBundle execute(
            Path projectPath,
            RunRecord runRecord,
            String analysis,
            String prd,
            String design,
            String note,
            ContractView authoritativeContractView,
            String previousStateJson
    ) {
        return execute(
                projectPath,
                runRecord,
                analysis,
                prd,
                design,
                note,
                authoritativeContractView,
                previousStateJson,
                ImplementationProgressSink.noop()
        );
    }

    public ImplementationExecutionBundle execute(
            Path projectPath,
            RunRecord runRecord,
            String analysis,
            String prd,
            String design,
            String note,
            ContractView authoritativeContractView,
            String previousStateJson,
            ImplementationProgressSink progressSink
    ) {
        return coderTurnCoordinator.execute(
                projectPath,
                runRecord,
                analysis,
                prd,
                design,
                note,
                authoritativeContractView,
                previousStateJson,
                progressSink
        );
    }

    @Override
    public void close() {
        toolExecutor.close();
    }
}
