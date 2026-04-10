package devflow.agent;

import devflow.agent.executor.OllamaProperties;
import devflow.agent.executor.GenerationBudgetProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({OllamaProperties.class, GenerationBudgetProperties.class})
public class DevflowAgentApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(DevflowAgentApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.run(args);
    }
}
