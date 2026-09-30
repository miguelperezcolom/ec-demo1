package io.mateu.ecdemo1.iacp.infra.out.gitops;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The catalogue is read from the branch's tarball in one request: the YAML files under the path, by
 * their path in the repo — GitHub's top directory dropped, a path too long for the tar header read
 * from its pax record, and nothing outside the path or that is not YAML.
 */
class GitHubCatalogueSourceTest {

    @Test
    void theYamlFilesUnderThePathComeFromTheTarballByTheirPathInTheRepo() throws Exception {
        byte[] archive;
        try (var in = getClass().getResourceAsStream("/gitops/catalogue.tar.gz")) {
            archive = in.readAllBytes();
        }
        var files = GitHubCatalogueSource.yamlFilesIn(archive, "ia");
        var longPath = "ia/" + "x".repeat(60) + "/deep/" + "y".repeat(60) + "/r1.yml";
        assertThat(files).containsOnlyKeys("ia/agents/a1.yaml", longPath);
        assertThat(files.get("ia/agents/a1.yaml")).contains("kind: agent");
        assertThat(files.get(longPath)).contains("kind: route");
    }
}
