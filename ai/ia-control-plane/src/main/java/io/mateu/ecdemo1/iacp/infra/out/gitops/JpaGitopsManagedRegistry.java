package io.mateu.ecdemo1.iacp.infra.out.gitops;

import io.mateu.ecdemo1.iacp.application.out.gitops.GitopsManagedRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * The provenance registry on the control plane's own PostgreSQL, beside the catalogues it is about.
 * Kept here rather than in the engine's disposable database on purpose: losing it would make every
 * git-managed entry look console-owned, and the reconciler would then never clean any of them up.
 */
@Repository
@ConditionalOnProperty(name = "cp.gitops.enabled", havingValue = "true")
@RequiredArgsConstructor
public class JpaGitopsManagedRegistry implements GitopsManagedRegistry {

    private final GitopsManagedEntityRepository repository;

    @Override
    @Transactional(readOnly = true)
    public boolean isManaged(String kind, String id) {
        return repository.existsById(kind + "/" + id);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> managedIds(String kind) {
        return repository.findByKind(kind).stream()
                .map(GitopsManagedEntity::getEntryId)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public void markManaged(String kind, String id) {
        // save is an upsert on the primary key, so re-marking an already-managed entry is a no-op
        // write rather than a duplicate.
        repository.save(new GitopsManagedEntity(kind, id));
    }

    @Override
    @Transactional
    public void unmark(String kind, String id) {
        repository.deleteById(kind + "/" + id);
    }
}
