package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

public record TestStepSpec(
        TestStepAction action,
        String selector,
        String key,
        Integer count,
        Integer ms,
        String text,
        Boolean optional,
        TestStepSemantic semantic
) {

    public TestStepSpec(
            TestStepAction action,
            String selector,
            String key,
            Integer count,
            Integer ms,
            String text,
            Boolean optional
    ) {
        this(action, selector, key, count, ms, text, optional, null);
    }
}
