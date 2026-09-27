package io.mateu.ecdemo1.crsintegration.rest;

import io.mateu.ecdemo1.crsintegration.router.ProcessRouter;
import io.mateu.ecdemo1.crsintegration.source.CrsSource;
import io.mateu.ecdemo1.crsintegration.translate.CrsTranslator;
import io.mateu.ecdemo1.integration.model.integration.FutureReservation;
import io.mateu.ecdemo1.integration.model.integration.FutureUsage;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a backfill needs from the CRS side (HLA, «Backfill» #10): the hotel's reservations still to
 * arrive, nearest first, a page at a time; what those reservations really use, for the pre-pass;
 * and a way to project one of them by the same path a change in the CRS takes.
 */
@RestController
@RequiredArgsConstructor
public class BackfillController {

    static final int PAGE = 200;

    final CrsSource crs;
    final ProcessRouter router;

    public record ProjectionRequest(String hotelCode, String locator, String origin) {
    }

    @GetMapping("/reservations/{hotelCode}/future")
    public List<FutureReservation> future(@PathVariable String hotelCode,
                                          @RequestParam(required = false) LocalDate afterArrival,
                                          @RequestParam(required = false) String afterLocator,
                                          @RequestParam(defaultValue = "50") int limit) {
        return crs.future(hotelCode, afterArrival, afterLocator, limit).stream()
                .map(b -> new FutureReservation(b.id(), b.arrival(), b.version()))
                .toList();
    }

    /** Every code and partner the hotel's future reservations carry, most used first. */
    @GetMapping("/reservations/{hotelCode}/future/usage")
    public FutureUsage usage(@PathVariable String hotelCode) {
        var codes = new LinkedHashMap<String, FutureUsage.CodeUsage>();
        var partners = new LinkedHashMap<String, Integer>();
        var reservations = 0;
        LocalDate afterArrival = null;
        String afterId = null;
        while (true) {
            var page = crs.future(hotelCode, afterArrival, afterId, PAGE);
            for (var booking : page) {
                var r = CrsTranslator.reservation(booking);
                reservations++;
                var used = new HashSet<Map.Entry<CodeType, String>>();
                used.add(Map.entry(CodeType.CHANNEL, r.channelCode()));
                r.rooms().forEach(room -> {
                    used.add(Map.entry(CodeType.ROOM_TYPE, room.roomTypeCode()));
                    used.add(Map.entry(CodeType.RATE_PLAN, room.ratePlanCode()));
                    used.add(Map.entry(CodeType.BOARD, room.boardCode()));
                });
                r.payments().forEach(p -> used.add(Map.entry(CodeType.PAYMENT_METHOD, p.methodCode())));
                for (var code : used) {
                    codes.merge(code.getKey() + "/" + code.getValue(), new FutureUsage.CodeUsage(code.getKey(), code.getValue(), 1),
                            (a, b) -> new FutureUsage.CodeUsage(a.type(), a.code(), a.reservations() + 1));
                }
                if (r.partnerCode() != null) {
                    partners.merge(r.partnerCode(), 1, Integer::sum);
                }
            }
            if (page.size() < PAGE) {
                break;
            }
            afterArrival = page.getLast().arrival();
            afterId = page.getLast().id();
        }
        var codeList = new ArrayList<>(codes.values());
        codeList.sort(Comparator.comparingInt(FutureUsage.CodeUsage::reservations).reversed());
        var partnerList = partners.entrySet().stream()
                .map(e -> new FutureUsage.PartnerUsage(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(FutureUsage.PartnerUsage::reservations).reversed())
                .toList();
        return new FutureUsage(hotelCode, reservations, codeList, partnerList);
    }

    /**
     * Projects one reservation the way a change in the CRS would — the backfill invokes «Proyectar
     * Reserva», it does not rewrite it. Repeating the request starts the process once: its key is
     * derived from the reservation and the origin.
     */
    @PostMapping("/projections")
    public void project(@RequestBody ProjectionRequest request) {
        router.project(request.hotelCode(), request.locator(), request.origin());
    }
}
