package io.mateu.ecdemo1.customerhistory.infra.in.ui.pages;

import io.mateu.ecdemo1.customerhistory.application.CustomerHistory;
import io.mateu.ecdemo1.customerhistory.store.CustomerStay;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.State;
import io.mateu.uidl.interfaces.HttpRequest;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * «Buscar»: a customer's history by their MDM code — the summary, and every stay with what it spent
 * per kind of charge. A merged code shows its survivor's history. A page of its own (not a Crud): the
 * history is read by one customer, never browsed whole.
 *
 * <p>Every shown field is a String that starts non-null: a null renders as the word "null". The summary
 * also goes in the notice and the message, which a {@code State} always repaints.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Getter
@Setter
@Title("Historial de clientes")
public class HistorySearch {

    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Section("Cliente")
    @Label("Código de cliente")
    @Help("El código del MDM (C-…). Uno fusionado con otro muestra el historial del superviviente.")
    String code = "";

    @io.mateu.uidl.annotations.Notice(theme = "info")
    String result = "Escribe el código del cliente y pulsa «Buscar»: sus estancias en la cadena y lo que gastó en recepción.";

    @Section("Resumen")
    @ReadOnly
    @Label("Cliente")
    String customer = "";

    @ReadOnly
    @Label("Estancias")
    String stays = "";

    @ReadOnly
    @Label("Noches")
    String nights = "";

    @ReadOnly
    @Label("Primera estancia")
    String firstStay = "";

    @ReadOnly
    @Label("Última estancia")
    String lastStay = "";

    @ReadOnly
    @Label("Hoteles")
    String hotels = "";

    @ReadOnly
    @Label("Hotel más repetido")
    String topHotel = "";

    @ReadOnly
    @Label("Gasto en recepción")
    @Help("Extras, late check-out y consumos; el alojamiento no cuenta.")
    String spend = "";

    @Section("Estancias")
    @ReadOnly
    @Stereotype(FieldStereotype.grid)
    @Label("Estancias, de la más reciente a la más antigua")
    List<StayRow> history = new ArrayList<>();

    @Getter(lombok.AccessLevel.NONE)
    final CustomerHistory customerHistory;

    @Toolbar
    @Action
    public Object buscar(HttpRequest httpRequest) {
        if (code == null || code.isBlank()) {
            return List.of(Message.error("Escribe un código de cliente (C-…)"), new State(this));
        }
        var summary = customerHistory.summary(code);
        load(summary, customerHistory.stays(code, 0, 100));
        return List.of(new Message(result), new State(this));
    }

    HistorySearch load(CustomerHistory.Summary s, CustomerHistory.StayPage page) {
        customer = s.customerId();
        stays = String.valueOf(s.stays());
        nights = String.valueOf(s.nights());
        firstStay = day(s.firstStay());
        lastStay = day(s.lastStay());
        hotels = String.valueOf(s.hotels());
        topHotel = s.topHotel() == null ? "—" : s.topHotel();
        spend = money(s.spend().amount(), s.spend().currency());
        var merged = code.trim().equalsIgnoreCase(s.customerId()) ? "" : " (" + code.trim() + " se fusionó en " + s.customerId() + ")";
        result = s.stays() == 0
                ? "Sin estancias de " + s.customerId() + " en la cadena" + merged + "."
                : "%s%s: %d estancia(s), %d noche(s), en %d hotel(es); gasto en recepción %s.".formatted(
                        s.customerId(), merged, s.stays(), s.nights(), s.hotels(), spend);
        history = new ArrayList<>(page.items().stream().map(HistorySearch::row).toList());
        return this;
    }

    static StayRow row(CustomerHistory.StayItem i) {
        return new StayRow(i.hotelCode() == null ? "" : i.hotelCode(), day(i.arrival()), day(i.departure()), i.nights(),
                text(i.roomNumber()), text(i.roomType()), text(i.board()),
                money(i.addOnTotal(), null), money(i.lateCheckOutTotal(), null),
                money(i.consumptionTotal(), null), money(i.total(), null), text(i.currency()),
                i.customerId() + (i.holder() ? " · titular" : ""),
                CustomerStay.DEMO.equals(i.source()) ? "Demo" : "Recepción");
    }

    static String day(LocalDate d) {
        return d == null ? "—" : DAY.format(d);
    }

    static String text(String s) {
        return s == null ? "" : s;
    }

    static String money(BigDecimal amount, String currency) {
        var value = amount == null ? BigDecimal.ZERO : amount;
        return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + (currency == null ? "" : " " + currency);
    }
}
