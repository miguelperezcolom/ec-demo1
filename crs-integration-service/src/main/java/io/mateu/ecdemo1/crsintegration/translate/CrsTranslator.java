package io.mateu.ecdemo1.crsintegration.translate;

import io.mateu.ecdemo1.crsintegration.source.BookingView;
import io.mateu.ecdemo1.crsintegration.source.CatalogView;
import io.mateu.ecdemo1.crsintegration.source.PartnerView;
import io.mateu.ecdemo1.integration.model.mapping.CodeEntry;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.integration.model.partner.Address;
import io.mateu.ecdemo1.integration.model.partner.BillingMode;
import io.mateu.ecdemo1.integration.model.partner.Partner;
import io.mateu.ecdemo1.integration.model.partner.PartnerType;
import io.mateu.ecdemo1.integration.model.reservation.GuestType;
import io.mateu.ecdemo1.integration.model.reservation.NightlyRate;
import io.mateu.ecdemo1.integration.model.reservation.Payment;
import io.mateu.ecdemo1.integration.model.reservation.PaymentType;
import io.mateu.ecdemo1.integration.model.reservation.Person;
import io.mateu.ecdemo1.integration.model.reservation.Reservation;
import io.mateu.ecdemo1.integration.model.reservation.ReservationStatus;
import io.mateu.ecdemo1.integration.model.reservation.Room;

import java.util.ArrayList;
import java.util.List;

/**
 * From the CRS's shapes to the integration's. Codes pass through untouched — they are still the
 * CRS's, and translating them to a PMS's is the mapping's job — but everything else is said in the
 * integration's own vocabulary, so nothing downstream learns what "Pending" or "NoFront" meant in
 * the systems of this side.
 */
public final class CrsTranslator {

    private CrsTranslator() {
    }

    public static Reservation reservation(BookingView b) {
        return new Reservation(
                b.hotelCode(), b.id(), b.version(), status(b.status()), b.channelCode(), b.partnerCode(),
                b.externalReference(), b.arrival(), b.departure(), b.currency(),
                new Person(b.holder().firstName(), b.holder().lastName(), GuestType.ADULT, null,
                        b.holder().email(), b.holder().phone(), b.holder().nationality(), null, null, null),
                b.rooms().stream().map(CrsTranslator::room).toList(),
                b.payments() == null ? List.of() : b.payments().stream().map(CrsTranslator::payment).toList(),
                b.totalAmount(), b.comments(),
                b.cancellation() != null ? b.cancellation().reasonCode() : null);
    }

    static ReservationStatus status(String status) {
        return switch (status) {
            case "Pending" -> ReservationStatus.PENDING;
            case "Confirmed" -> ReservationStatus.CONFIRMED;
            case "Cancelled" -> ReservationStatus.CANCELLED;
            default -> throw new IllegalArgumentException("Unknown CRS booking status " + status);
        };
    }

    static Room room(BookingView.Room r) {
        return new Room(r.line(), r.roomTypeCode(), r.ratePlanCode(), r.boardCode(), r.adults(),
                r.childrenAges() == null ? List.of() : r.childrenAges(),
                r.guests() == null ? List.of() : r.guests().stream().map(g -> new Person(g.firstName(), g.lastName(),
                        "Child".equals(g.type()) ? GuestType.CHILD : GuestType.ADULT, g.age(), null, null,
                        g.nationality(), g.birthDate(), g.documentType(), g.documentNumber())).toList(),
                r.nightlyRates().stream().map(n -> new NightlyRate(n.date(), n.amount())).toList());
    }

    static Payment payment(BookingView.Payment p) {
        return new Payment(p.paymentId(), "Prepayment".equals(p.type()) ? PaymentType.PREPAYMENT : PaymentType.DEPOSIT,
                p.methodCode(), p.amount(), p.date(), p.reference());
    }

    public static Partner partner(PartnerView p) {
        return new Partner(p.code(), partnerType(p.type()), p.name(), p.taxId(),
                p.address() == null ? null
                        : new Address(p.address().line(), p.address().city(), p.address().postalCode(),
                        p.address().countryCode()),
                p.email(), p.phone(), "NoFront".equals(p.billingMode()) ? BillingMode.NO_FRONT : BillingMode.FRONT,
                p.active(), p.version());
    }

    static PartnerType partnerType(String type) {
        return switch (type) {
            case "TravelAgent" -> PartnerType.TRAVEL_AGENT;
            case "TourOperator" -> PartnerType.TOUR_OPERATOR;
            case "OnlineAgency" -> PartnerType.ONLINE_AGENCY;
            case "Company" -> PartnerType.COMPANY;
            default -> throw new IllegalArgumentException("Unknown partner type " + type);
        };
    }

    /**
     * The CRS's catalog as the mapping pairs it. Room types belong to a hotel; everything else is
     * chain-wide. Partner types are the integration's own and have no catalog in the CRS.
     */
    public static List<CodeEntry> catalog(CatalogView c) {
        var entries = new ArrayList<CodeEntry>();
        for (var hotel : c.hotels()) {
            entries.add(new CodeEntry(CodeType.HOTEL, null, hotel.code(), hotel.name()));
            hotel.roomTypes().forEach(r -> entries.add(new CodeEntry(CodeType.ROOM_TYPE, hotel.code(), r.code(), r.name())));
        }
        c.ratePlans().forEach(x -> entries.add(new CodeEntry(CodeType.RATE_PLAN, null, x.code(), x.name())));
        c.boards().forEach(x -> entries.add(new CodeEntry(CodeType.BOARD, null, x.code(), x.name())));
        c.channels().forEach(x -> entries.add(new CodeEntry(CodeType.CHANNEL, null, x.code(), x.name())));
        c.cancellationReasons().forEach(x -> entries.add(new CodeEntry(CodeType.CANCELLATION_REASON, null, x.code(), x.name())));
        c.paymentMethods().forEach(x -> entries.add(new CodeEntry(CodeType.PAYMENT_METHOD, null, x.code(), x.name())));
        for (var type : PartnerType.values()) {
            entries.add(new CodeEntry(CodeType.PARTNER_TYPE, null, type.name(), type.name().replace('_', ' ').toLowerCase()));
        }
        return entries;
    }
}
