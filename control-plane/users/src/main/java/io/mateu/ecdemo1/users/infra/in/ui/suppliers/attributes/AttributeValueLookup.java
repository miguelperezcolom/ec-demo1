package io.mateu.ecdemo1.users.infra.in.ui.suppliers.attributes;

import io.mateu.ecdemo1.users.application.query.AttributeValueQueryService;
import io.mateu.ecdemo1.users.application.query.dto.AttributeValueDto;
import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.Attribute;
import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.AttributeValueId;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.Pageable;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.LookupLabelSupplier;
import io.mateu.uidl.interfaces.LookupOptionsSupplier;

/**
 * The options and the label of a field holding one employee attribute: the catalogue's values of that
 * attribute, the code as the value and the label as what is read. One subclass per attribute, each a
 * bean, because a {@code @Lookup} names a class — and the attribute is what tells the hotel field's
 * options from the department field's.
 *
 * <p>A code the catalogue no longer has (a value deleted after it was given) is labelled with the code
 * itself, so the form still shows what the user holds instead of a blank.
 */
public abstract class AttributeValueLookup implements LookupOptionsSupplier, LookupLabelSupplier {

    final AttributeValueQueryService queryService;
    final Attribute attribute;

    protected AttributeValueLookup(AttributeValueQueryService queryService, Attribute attribute) {
        this.queryService = queryService;
        this.attribute = attribute;
    }

    @Override
    public ListingData<Option> search(String fieldId, String searchText, Pageable pageable, HttpRequest httpRequest) {
        var found = queryService.findByAttribute(attribute, searchText, pageable);
        return new ListingData<>(new Page<>(
                searchText,
                found.page().pageSize(),
                found.page().pageNumber(),
                found.page().totalElements(),
                found.page().content().stream().map(value -> new Option(value.code(), value.label())).toList()));
    }

    @Override
    public String label(String fieldId, Object id, HttpRequest httpRequest) {
        if (id == null) {
            return null;
        }
        var code = String.valueOf(id);
        return queryService.getById(AttributeValueId.of(attribute, code).id())
                .map(AttributeValueDto::label)
                .orElse(code);
    }
}
