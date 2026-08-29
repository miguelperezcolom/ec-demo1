package io.mateu.ecdemo1.iacp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Scheduling is on for the GitOps reconcile: an optional poll, and the startup sync. It stays inert
// unless cp.gitops.enabled turns the reconciler beans on. See the gitops package.
@EnableScheduling
@SpringBootApplication
public class IaControlPlaneApplication {

    public static void main(String[] args) {
        SpringApplication.run(IaControlPlaneApplication.class, args);
    }

}
