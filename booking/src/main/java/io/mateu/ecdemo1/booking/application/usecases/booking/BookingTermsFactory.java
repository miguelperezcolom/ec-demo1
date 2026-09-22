package io.mateu.ecdemo1.booking.application.usecases.booking;

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
        var channel = catalog.channel(request.channelCode());
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

    private BookedRoom room(String hotelCode, int line, RoomRequest request, Stay stay) {
        var roomType = catalog.roomType(hotelCode, request.roomTypeCode());
        var ratePlan = catalog.ratePlan(request.ratePlanCode());
        var board = catalog.board(request.boardCode());
        var childrenAges = request.childrenAges() == null ? List.<Integer>of() : request.childrenAges();
        return new BookedRoom(line, roomType.code(), ratePlan.code(), board.code(), request.adults(),
                childrenAges, request.guests(),
                pricing.price(roomType, ratePlan, board, request.adults(), childrenAges, stay));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
