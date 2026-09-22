package io.mateu.ecdemo1.operamock.api;

import io.mateu.ecdemo1.operamock.config.OperaCatalog;
import io.mateu.ecdemo1.operamock.store.Faults;
import io.mateu.ecdemo1.operamock.store.OperaStore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** The double's own controls — not part of OHIP. Faults on demand, and a look inside. */
@RestController
@RequiredArgsConstructor
public class MockAdminController {

    final Faults faults;
    final OperaStore store;
    final OperaCatalog catalog;

    @PostMapping("/_mock/faults")
    public Map<String, Object> inject(@RequestParam int status, @RequestParam(defaultValue = "") String pathContains,
                                      @RequestParam(defaultValue = "1") int count) {
        faults.inject(status, pathContains, count);
        return Map.of("armed", String.valueOf(faults.current()));
    }

    @PostMapping("/_mock/properties/{hotelId}/configure")
    public Map<String, Object> configure(@PathVariable String hotelId) {
        var p = catalog.configure(hotelId);
        return Map.of("hotelId", hotelId, "roomTypes", p.roomTypes().size(), "ratePlans", p.ratePlans().size());
    }

    @GetMapping("/_mock/reservations")
    public Collection<?> reservations() {
        return store.reservations();
    }

    @GetMapping("/_mock/profiles")
    public Collection<?> profiles() {
        return store.profiles();
    }

    @GetMapping("/_mock/calls")
    public List<OperaStore.Call> calls() {
        return store.calls();
    }

    @DeleteMapping("/_mock")
    public void clear() {
        store.clear();
    }
}
