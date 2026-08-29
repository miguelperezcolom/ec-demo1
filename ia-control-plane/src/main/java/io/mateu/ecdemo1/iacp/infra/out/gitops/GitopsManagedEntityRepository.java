package io.mateu.ecdemo1.iacp.infra.out.gitops;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GitopsManagedEntityRepository extends JpaRepository<GitopsManagedEntity, String> {

    List<GitopsManagedEntity> findByKind(String kind);
}
