package io.mateu.ecdemo1.iacp.application.out.gitops;

import io.mateu.ecdemo1.iacp.application.out.gitops.manifest.AgentManifest;
import io.mateu.ecdemo1.iacp.application.out.gitops.manifest.LlmManifest;
import io.mateu.ecdemo1.iacp.application.out.gitops.manifest.McpManifest;
import io.mateu.ecdemo1.iacp.application.out.gitops.manifest.RagManifest;

import java.util.List;

/**
 * The whole of what the repo declares, parsed: every catalogue entry across every file, sorted into
 * its four kinds. This is the desired state the reconciler compares the live catalogues against.
 */
public record DesiredCatalogue(List<LlmManifest> llms, List<McpManifest> mcps,
                               List<RagManifest> rags, List<AgentManifest> agents) {
}
