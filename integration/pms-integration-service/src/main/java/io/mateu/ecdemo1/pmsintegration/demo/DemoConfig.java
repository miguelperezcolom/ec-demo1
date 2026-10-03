package io.mateu.ecdemo1.pmsintegration.demo;

import java.time.Clock;

import io.mateu.ecdemo1.pmsintegration.config.OperaContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** What the demo's control page and its reset need of the connector. */
@Configuration
public class DemoConfig {

    /**
     * The ConfigMap in the cluster (RUN_CONFIG_KUBERNETES=true, the manifest's); nowhere locally.
     */
    @Bean
    RunConfig runConfig(@Value("${pms-integration.run-config.kubernetes:false}") boolean kubernetes) {
        return kubernetes ? KubernetesRunConfig.inCluster() : RunConfig.NONE;
    }

    @Bean
    NewOperaContext newOperaContext(OperaContext context, RunConfig runConfig, Clock clock) {
        return new NewOperaContext(context, runConfig, clock);
    }
}
