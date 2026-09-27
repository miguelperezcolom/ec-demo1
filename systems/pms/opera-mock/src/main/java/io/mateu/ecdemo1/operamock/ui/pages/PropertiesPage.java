package io.mateu.ecdemo1.operamock.ui.pages;

import io.mateu.ecdemo1.operamock.config.OperaCatalog;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Button;
import io.mateu.uidl.annotations.Notice;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ButtonStyle;
import io.mateu.uidl.data.State;
import io.mateu.uidl.interfaces.HttpRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * What each property has configured in "Opera" — and the hand of the hotel's Opera administrator:
 * configuring a property that arrived empty, which is outside the integration (HLA F010, R10).
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Properties")
public class PropertiesPage {

    @Notice
    String configured = "";

    String hotelId = "RIUNEW";

    final OperaCatalog catalog;

    @Button(buttonStyle = ButtonStyle.secondary)
    @Action
    public Object refresh(HttpRequest httpRequest) {
        configured = summary();
        return new State(this);
    }

    @Button(buttonStyle = ButtonStyle.primary)
    @Action
    public Object configure(HttpRequest httpRequest) {
        catalog.configure(hotelId);
        configured = "Configured " + hotelId + ". " + summary();
        return new State(this);
    }

    String summary() {
        return catalog.all().stream()
                .sorted(Comparator.comparing(OperaCatalog.Property::hotelId))
                .map(p -> "%s: %d room types, %d rate plans".formatted(p.hotelId(), p.roomTypes().size(), p.ratePlans().size()))
                .collect(Collectors.joining(" · "));
    }
}
