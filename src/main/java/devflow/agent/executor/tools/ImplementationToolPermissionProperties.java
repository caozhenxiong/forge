package devflow.agent.executor.tools;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "devflow.implementation.tool-permission")
public record ImplementationToolPermissionProperties(
        List<String> allowedTools
) {

    public ImplementationToolPermissionProperties() {
        this(List.of());
    }

    public ImplementationToolPermissionProperties {
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
    }
}
