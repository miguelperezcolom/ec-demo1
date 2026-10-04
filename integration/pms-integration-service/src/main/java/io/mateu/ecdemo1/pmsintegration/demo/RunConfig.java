package io.mateu.ecdemo1.pmsintegration.demo;

import java.util.Optional;

/**
 * Where the run's Opera context outlives the process: the {@code ec-demo-run} ConfigMap, which the
 * deployment reads at startup (envFrom) and the demo scripts read too. In memory only, locally.
 */
public interface RunConfig {

    /** The context, its custom reference, and the reset-demo process that set it. */
    record Stored(String externalSystemCode, String customReference, String processKey) {
    }

    Optional<Stored> read();

    void write(Stored stored);

    /** Nowhere: a local run keeps the context in memory only. */
    RunConfig NONE = new RunConfig() {
        @Override
        public Optional<Stored> read() {
            return Optional.empty();
        }

        @Override
        public void write(Stored stored) {
        }
    };
}
