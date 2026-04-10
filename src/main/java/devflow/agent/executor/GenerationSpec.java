package devflow.agent.executor;

import java.time.Duration;
import java.util.function.Supplier;

public record GenerationSpec<T>(
        String operation,
        int maxAttempts,
        Duration heartbeatInterval,
        Duration attemptTimeout,
        GenerationObserver observer,
        GenerationAttemptExecutor<T> attemptExecutor,
        GenerationExceptionClassifier exceptionClassifier,
        GenerationFailureFactory failureFactory,
        Supplier<GenerationTelemetry> telemetrySupplier
) {
    public GenerationSpec(
            String operation,
            int maxAttempts,
            Duration heartbeatInterval,
            GenerationObserver observer,
            GenerationAttemptExecutor<T> attemptExecutor,
            GenerationExceptionClassifier exceptionClassifier,
            GenerationFailureFactory failureFactory
    ) {
        this(
                operation,
                maxAttempts,
                heartbeatInterval,
                GenerationExecutionPolicy.defaultAttemptTimeout(),
                observer,
                attemptExecutor,
                exceptionClassifier,
                failureFactory,
                () -> null
        );
    }

    public GenerationSpec(
            String operation,
            int maxAttempts,
            Duration heartbeatInterval,
            GenerationObserver observer,
            GenerationAttemptExecutor<T> attemptExecutor,
            GenerationExceptionClassifier exceptionClassifier,
            GenerationFailureFactory failureFactory,
            Supplier<GenerationTelemetry> telemetrySupplier
    ) {
        this(
                operation,
                maxAttempts,
                heartbeatInterval,
                GenerationExecutionPolicy.defaultAttemptTimeout(),
                observer,
                attemptExecutor,
                exceptionClassifier,
                failureFactory,
                telemetrySupplier
        );
    }

    public GenerationSpec(
            String operation,
            int maxAttempts,
            Duration heartbeatInterval,
            Duration attemptTimeout,
            GenerationObserver observer,
            GenerationAttemptExecutor<T> attemptExecutor,
            GenerationExceptionClassifier exceptionClassifier,
            GenerationFailureFactory failureFactory
    ) {
        this(
                operation,
                maxAttempts,
                heartbeatInterval,
                attemptTimeout,
                observer,
                attemptExecutor,
                exceptionClassifier,
                failureFactory,
                () -> null
        );
    }

    public GenerationSpec {
        operation = operation == null || operation.isBlank() ? "generation" : operation;
        heartbeatInterval = heartbeatInterval == null || heartbeatInterval.isNegative() || heartbeatInterval.isZero()
                ? Duration.ZERO
                : heartbeatInterval;
        attemptTimeout = attemptTimeout == null || attemptTimeout.isNegative() || attemptTimeout.isZero()
                ? GenerationExecutionPolicy.defaultAttemptTimeout()
                : attemptTimeout;
        observer = observer == null ? new GenerationObserver() { } : observer;
        telemetrySupplier = telemetrySupplier == null ? () -> null : telemetrySupplier;
    }
}
