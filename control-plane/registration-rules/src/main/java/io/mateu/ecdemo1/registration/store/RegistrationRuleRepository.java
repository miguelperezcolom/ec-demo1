package io.mateu.ecdemo1.registration.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RegistrationRuleRepository extends JpaRepository<RegistrationRule, String>,
        JpaSpecificationExecutor<RegistrationRule> {
}
