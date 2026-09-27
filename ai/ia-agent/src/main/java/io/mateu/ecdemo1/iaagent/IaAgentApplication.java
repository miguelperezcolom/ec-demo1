package io.mateu.ecdemo1.iaagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The chat agent.
 *
 * <p>It configures itself from the IA control plane rather than from its own properties file:
 * which model, which credential, which system prompt and which MCP servers all arrive per prompt
 * from {@code /internal/agents/{id}/config}. This pod knows only two things about itself — where
 * the control plane is, and which agent it is.
 */
@SpringBootApplication
public class IaAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(IaAgentApplication.class, args);
    }

}
