package io.mateu.ecdemo1.content.infra.in.ui.pages.content;

import io.mateu.uidl.annotations.*;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import io.mateu.ecdemo1.content.application.query.dto.ContentDto;
import io.mateu.ecdemo1.content.application.usecases.content.ContentValueDto;
import io.mateu.ecdemo1.content.application.usecases.content.create.CreateContentCommand;
import io.mateu.ecdemo1.content.application.usecases.content.create.CreateContentUseCase;
import io.mateu.ecdemo1.content.application.usecases.content.update.UpdateContentCommand;
import io.mateu.ecdemo1.content.application.usecases.content.update.UpdateContentUseCase;
import io.mateu.ecdemo1.content.infra.in.ui.suppliers.ContentTypeIdLabelSupplier;
import io.mateu.ecdemo1.content.infra.in.ui.suppliers.ContentTypeIdOptionsSupplier;
import io.mateu.ecdemo1.content.infra.in.ui.suppliers.LabelIdLabelSupplier;
import io.mateu.ecdemo1.content.infra.in.ui.suppliers.LabelIdOptionsSupplier;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Scope("prototype")
@RequiredArgsConstructor
public class ContentViewModel implements Identifiable {
    @HiddenInCreate
    @ReadOnly
    String id;
    @NotEmpty String name;
    // Required, because CreateContentUseCase does Long.valueOf(command.contentType()) and the
    // aggregate has no notion of a content without a type. Without this the form submits happily
    // and dies one layer down, which is the same shape as the null-list crash below.
    @NotEmpty
    @Lookup(search = ContentTypeIdOptionsSupplier.class, label = ContentTypeIdLabelSupplier.class)
    String contentType;
    @Lookup(search = LabelIdOptionsSupplier.class, label = LabelIdLabelSupplier.class)
    List<String> labels;
    @MasterDetail(minHeightWhenDetailVisible = "26rem;")
    @Colspan(2)
    List<ContentValueViewModel> values;

    final CreateContentUseCase createContentUseCase;
    final UpdateContentUseCase updateContentUseCase;

    public String create(HttpRequest httpRequest) {
        return createContentUseCase.handle(
                new CreateContentCommand(name, contentType, labelsOrEmpty(), valuesOrEmpty()));
    }

    public void save(HttpRequest httpRequest) {
        updateContentUseCase.handle(
                new UpdateContentCommand(id, name, contentType, labelsOrEmpty(), valuesOrEmpty()));
    }

    /**
     * An untouched collection field arrives null, not empty.
     *
     * <p>Mateu hydrates a view model from the state the browser round-trips, and a
     * {@code @MasterDetail} grid with no rows — or a {@code @Lookup} with nothing picked — sends
     * null. {@code ActualValueExtractor} returns that null unchanged and {@code ValueWriter} writes
     * it, so the field is null rather than the empty list the initializer would have left.
     *
     * <p>Creating a content without adding a value therefore crashed on {@code values.stream()},
     * and would have crashed on {@code labels.stream()} in {@code CreateContentUseCase} the moment
     * that one was fixed. Both are normalised here, at the boundary where UI-shaped data becomes
     * domain-shaped: nothing below this class should have to know that a collection can arrive
     * absent, and "the user added no rows" is an empty list, not a missing one.
     */
    private List<String> labelsOrEmpty() {
        return labels != null ? labels : List.of();
    }

    private List<ContentValueDto> valuesOrEmpty() {
        return (values != null ? values : List.<ContentValueViewModel>of()).stream()
                .map(value -> new ContentValueDto(value.country(), value.language(), value.value()))
                .toList();
    }

    @Override
    public String id() {
        return id;
    }

    public ContentViewModel load(ContentDto content) {
        id = String.valueOf(content.id());
        name = content.name();
        contentType = content.contentType();
        labels = content.labels();
        values = content.values().stream()
                .map(value -> new ContentValueViewModel(
                        value.country(),
                        value.language(),
                        value.value()
                        )
                ).toList();
        return this;
    }

    @Override
    public String toString() {
        return id != null ? name : "New content";
    }
}
