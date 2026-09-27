package io.mateu.ecdemo1.content.infra.in.ui.suppliers;

import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Option;
import io.mateu.ecdemo1.uicommons.paging.DbPaging;
import io.mateu.uidl.data.Pageable;
import io.mateu.uidl.interfaces.LookupOptionsSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.ecdemo1.content.application.query.LabelQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LabelIdOptionsSupplier implements LookupOptionsSupplier {

final LabelQueryService queryService;

@Override
public ListingData<Option> search(String fieldId, String searchText, Pageable pageable, HttpRequest httpRequest) {
    return DbPaging.listing(searchText, queryService.findAll(searchText, null, DbPaging.pageable(pageable)),
            label -> new Option(label.id(), label.name()));
}
}
