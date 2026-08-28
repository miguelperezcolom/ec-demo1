package io.mateu.ecdemo1.users.infra.in.ui.suppliers;

import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.LookupLabelSupplier;
import io.mateu.ecdemo1.users.application.query.RoleQueryService;
import io.mateu.ecdemo1.users.domain.aggregates.role.vo.RoleId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleIdLabelSupplier implements LookupLabelSupplier {

    final RoleQueryService queryService;

    @Override
    public String label(String fieldId, Object id, HttpRequest httpRequest) {
        return queryService.getLabel((String) id);
    }
}
