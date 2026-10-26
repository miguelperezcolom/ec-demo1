package io.mateu.ecdemo1.pmsintegration.demo;

import java.time.Clock;

import io.mateu.ecdemo1.pmsintegration.config.OperaContext;
import io.mateu.ecdemo1.pmsintegration.connections.Connections;
import io.mateu.ecdemo1.pmsintegration.ohip.OhipClient;
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

    /** The new context's prefix: ECDEMO1 on ec1, ECLOCAL on deploy/local (OPERA_RUN_PREFIX, set by up.sh). */
    @Bean
    NewOperaContext newOperaContext(OperaContext context, RunConfig runConfig, Clock clock,
                                    @Value("${pms-integration.demo.run-prefix:ECDEMO1}") String prefix) {
        return new NewOperaContext(context, runConfig, clock, prefix);
    }

    /**
     * What the reset cancels in Opera besides the integrations' properties (OPERA_CLEANUP_PROPERTIES —
     * XMAR in the manifest: a fresh environment has no integration), and with which of Opera's
     * cancellation reasons.
     */
    @Bean
    EarlierRuns earlierRuns(OhipClient ohip, Connections connections, Clock clock,
                            @Value("${pms-integration.demo.cleanup-properties:}") java.util.List<String> properties,
                            @Value("${pms-integration.demo.cleanup-reason:OTROS}") String reason) {
        return new EarlierRuns(ohip, connections, properties, reason, clock);
    }
}
