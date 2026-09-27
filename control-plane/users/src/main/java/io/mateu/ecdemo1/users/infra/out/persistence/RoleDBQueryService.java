package io.mateu.ecdemo1.users.infra.out.persistence;

import io.mateu.ecdemo1.uicommons.paging.DbPaging;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Pageable;
import io.mateu.ecdemo1.users.application.query.RoleQueryService;
import io.mateu.ecdemo1.users.application.query.dto.RoleDto;
import io.mateu.ecdemo1.users.application.query.dto.RoleRow;
import io.mateu.ecdemo1.users.domain.aggregates.role.vo.RoleId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static io.mateu.core.infra.JsonSerializer.listFromJson;

@Service
@RequiredArgsConstructor
public class RoleDBQueryService implements RoleQueryService {

    final RoleEntityRepository repository;

    private RoleRow toDomain(RoleEntity entity) {
        return new RoleRow(
                entity.id,
                entity.name,
                entity.description
        );
    }

    @Override
    public String getLabel(String id) {
        return repository.findById(id).map(RoleEntity::getName).orElse("Unknown role");
    }

    @Override
    public ListingData<RoleRow> findAll(String searchText,
                                        Object filters, Pageable pageable) {
        var page = repository.findAllByNameContainingIgnoreCase(searchText, DbPaging.pageable(pageable));
        return DbPaging.listing(searchText, page, this::toDomain);
    }

    @Override
    public Optional<RoleDto> getById(String id) {
        return repository.findById(id).map(entity -> new RoleDto(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                listFromJson(entity.getPermissionsJson(), String.class)
        ));
    }

}
