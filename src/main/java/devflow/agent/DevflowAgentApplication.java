package devflow.agent;

import devflow.agent.executor.generation.GenerationBudgetProperties;
import devflow.agent.executor.llm.OllamaProperties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DevflowAgentApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(DevflowAgentApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.run(args);
    }
}
