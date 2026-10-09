package io.mateu.ecdemo1.loyalty.infra.in.ui.pages;

import io.mateu.ecdemo1.loyalty.application.Loyalty;
import io.mateu.ecdemo1.loyalty.store.Member;
import io.mateu.ecdemo1.loyalty.store.Tier;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.EditableOnlyWhenCreating;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import io.mateu.uidl.interfaces.OptionsSupplier;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A member's form: its card, its tier and points — set by hand here, as a demo allows — and, once it
 * exists, what each stay earned it. The tier set here is kept as it is; stays only raise it.
 *
 * <p>The status starts non-null (a null Status renders as text), every required field is one the
 * create form shows (a required hidden one would make it unsubmittable), and each section starts on a
 * field visible where the section is.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class MemberViewModel implements Identifiable, OptionsSupplier {

    @ReadOnly
    @HiddenInCreate
    Status status = new Status(StatusType.NONE, "Nuevo");

    @Section("Socio")
    @NotEmpty
    @EditableOnlyWhenCreating
    @Label("Número de socio")
    @Help("El de la tarjeta Riu Class, p. ej. RC12345678.")
    String memberNumber;

    @NotEmpty
    @Label("Código de cliente")
    @Help("El del MDM (C-…): por él lo encuentra el front office.")
    String customerCode;

    @Label("Socio desde")
    @Help("Vacío: hoy.")
    LocalDate memberSince;

    @Section("Nivel y puntos")
    @NotEmpty
    @Stereotype(FieldStereotype.select)
    @Label("Nivel")
    @Help("Silver por debajo de 10.000 puntos, Gold desde 10.000, Platinum desde 40.000. El que se pone aquí "
            + "se respeta; las estancias solo lo suben.")
    String tier = Tier.SILVER.name();

    @Label("Puntos")
    long points;

    @Section("Registro")
    @ReadOnly
    @HiddenInCreate
    @Label("Último cambio")
    String updated = "";

    @Section("Acumulaciones")
    @ReadOnly
    @HiddenInCreate
    @Label("")
    @Colspan(2)
    List<AccrualRow> accruals = new ArrayList<>();

    final Loyalty loyalty;

    public String create() {
        return loyalty.create(memberNumber, update()).memberNumber;
    }

    public String save() {
        return loyalty.upsert(memberNumber, update()).memberNumber;
    }

    Loyalty.MemberUpdate update() {
        return new Loyalty.MemberUpdate(customerCode, Tier.parse(tier), points, memberSince);
    }

    public MemberViewModel load(Member m) {
        status = Formats.tier(m.tier());
        memberNumber = m.memberNumber;
        customerCode = m.customerCode;
        memberSince = m.memberSince;
        tier = m.tier;
        points = m.points;
        updated = Formats.when(m.updatedAt);
        accruals = new ArrayList<>(loyalty.accrualsOf(m.memberNumber).stream().map(Formats::row).toList());
        return this;
    }

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        return switch (fieldName) {
            case "tier" -> List.of(new Option(Tier.SILVER.name(), "Silver"), new Option(Tier.GOLD.name(), "Gold"),
                    new Option(Tier.PLATINUM.name(), "Platinum"));
            default -> List.of();
        };
    }

    @Override
    public String id() {
        return memberNumber;
    }

    @Override
    public String toString() {
        return memberNumber == null ? "Nuevo socio" : "Socio " + memberNumber;
    }
}
