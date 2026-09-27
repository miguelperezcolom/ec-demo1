package io.mateu.ecdemo1.booking.application.usecases.booking;

import io.mateu.ecdemo1.booking.application.usecases.booking.quote.Quote;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookedRoom;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingTerms;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Stay;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.mateu.ecdemo1.booking.domain.services.RoomPricing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Turns a request into terms the booking can hold: checks every code against the catalog and
 * prices each room night by night. Rooms are numbered in the order they were asked for.
 */
@Service
@RequiredArgsConstructor
public class BookingTermsFactory {

    final CrsCatalog catalog;
    final RoomPricing pricing;

    public BookingTerms terms(String hotelCode, BookingRequest request) {
        var channel = catalog.channel(hotelCode, request.channelCode());
        var partnerCode = blankToNull(request.partnerCode());
        if (channel.requiresPartner() && partnerCode == null) {
            throw new IllegalArgumentException(
                    "Channel %s sells through a partner: the booking needs a partner code".formatted(channel.code()));
        }
        var stay = new Stay(request.arrival(), request.departure());
        var rooms = request.rooms() == null ? List.<RoomRequest>of() : request.rooms();
        return new BookingTerms(
                channel.code(),
                partnerCode,
                blankToNull(request.externalReference()),
                stay,
                request.holder(),
                IntStream.range(0, rooms.size())
                        .mapToObj(i -> room(hotelCode, i + 1, rooms.get(i), stay))
                        .toList(),
                blankToNull(request.comments()));
    }

    /**
     * The price the terms would have, room by room, without the holder a booking needs: each code
     * checked and each night priced as {@link #terms} does it, so a booking made from the quote costs
     * what it said. A room its people do not fit in is refused here.
     */
    public Quote quote(CrsCatalog.Hotel hotel, BookingRequest request) {
        var channel = catalog.channel(hotel.code(), request.channelCode());
        if (channel.requiresPartner() && blankToNull(request.partnerCode()) == null) {
            throw new IllegalArgumentException(
                    "Channel %s sells through a partner: the booking needs a partner code".formatted(channel.code()));
        }
        var stay = new Stay(request.arrival(), request.departure());
        var requested = request.rooms() == null ? List.<RoomRequest>of() : request.rooms();
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("A booking needs at least one room");
        }
        var rooms = IntStream.range(0, requested.size()).mapToObj(i -> {
            var r = requested.get(i);
            var roomType = catalog.roomType(hotel.code(), r.roomTypeCode());
            var people = r.adults() + (r.childrenAges() == null ? 0 : r.childrenAges().size());
            if (people > roomType.maxOccupancy()) {
                throw new IllegalArgumentException("Room %d: %s takes at most %d people, not %d"
                        .formatted(i + 1, roomType.name(), roomType.maxOccupancy(), people));
            }
            var booked = room(hotel.code(), i + 1, r, stay);
            return new Quote.QuotedRoom(booked.line(), roomType.code(), roomType.name(),
                    booked.ratePlanCode(), catalog.ratePlan(hotel.code(), booked.ratePlanCode()).name(),
                    booked.boardCode(), catalog.board(hotel.code(), booked.boardCode()).name(),
                    booked.adults(), booked.childrenAges(), booked.nightlyRates(), booked.total());
        }).toList();
        return new Quote(hotel.code(), hotel.currency(), channel.code(), stay.arrival(), stay.departure(), stay.nights(),
                rooms, rooms.stream().map(Quote.QuotedRoom::total).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
    }

    private BookedRoom room(String hotelCode, int line, RoomRequest request, Stay stay) {
        var roomType = catalog.roomType(hotelCode, request.roomTypeCode());
        var ratePlan = catalog.ratePlan(hotelCode, request.ratePlanCode());
        var board = catalog.board(hotelCode, request.boardCode());
        var childrenAges = request.childrenAges() == null ? List.<Integer>of() : request.childrenAges();
        return new BookedRoom(line, roomType.code(), ratePlan.code(), board.code(), request.adults(),
                childrenAges, request.guests(),
                pricing.price(roomType, ratePlan, board, request.adults(), childrenAges, stay));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
