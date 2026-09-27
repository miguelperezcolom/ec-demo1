package io.mateu.ecdemo1.users.infra.out.persistence;

import io.mateu.ecdemo1.uicommons.paging.DbPaging;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Pageable;
import io.mateu.ecdemo1.users.application.query.PermissionQueryService;
import io.mateu.ecdemo1.users.application.query.dto.PermissionDto;
import io.mateu.ecdemo1.users.application.query.dto.PermissionRow;
import io.mateu.ecdemo1.users.domain.aggregates.permission.vo.PermissionId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PermissionDBQueryService implements PermissionQueryService {

    final PermissionEntityRepository repository;

    private PermissionRow toDomain(PermissionEntity entity) {
        return new PermissionRow(
                entity.id.toString(),
                entity.name,
                entity.description,
                entity.scope
        );
    }

    @Override
    public String getLabel(String id) {
        return repository.findById(Long.valueOf(id)).map(PermissionEntity::getName).orElse("Unknown permission");
    }

    @Override
    public Optional<PermissionDto> getById(String id) {
        return repository.findById(Long.valueOf(id)).map(this::toDto);
    }

    private PermissionDto toDto(PermissionEntity entity) {
        return new PermissionDto(
                entity.id.toString(),
                entity.name,
                entity.description,
                entity.scope
        );
    }

    @Override
    public ListingData<PermissionRow> findAll(String searchText,
                                        Object filters, Pageable pageable) {
        var page = repository.findAllByNameContainingIgnoreCase(searchText, DbPaging.pageable(pageable));
        return DbPaging.listing(searchText, page, this::toDomain);
    }

}
