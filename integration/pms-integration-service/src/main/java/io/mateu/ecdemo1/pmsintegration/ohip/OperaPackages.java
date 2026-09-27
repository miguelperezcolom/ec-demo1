package io.mateu.ecdemo1.pmsintegration.ohip;

import io.mateu.ecdemo1.pmsintegration.write.PackageRules;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A property's packages as Opera configures them (rtp), kept for a while: which it sells separately
 * — {@code postingAttributes.sellSeparate} — and which each rate plan carries. A package a property
 * does not sell separately can only reach a reservation inside a rate plan or a package group; sent
 * on its own, Opera answers RSV10047 «Package … cannot be sold separately». Checked against OHIP UAT,
 * property XMAR (2026-09-27): BKF and BKFCH are not sold separately, and none of the rate plans the
 * CRS sells MRU01 with carries them; EXP_BB and AGRO carry BRKFST, which is.
 */
@Component
public class OperaPackages implements PackageRules {

    static final Duration KEEP = Duration.ofMinutes(10);

    record Kept<T>(T value, Instant at) {
    }

    final OhipClient ohip;
    final Clock clock;
    final Map<String, Kept<Map<String, Boolean>>> sellSeparate = new ConcurrentHashMap<>();
    final Map<String, Kept<Set<String>>> included = new ConcurrentHashMap<>();

    public OperaPackages(OhipClient ohip, Clock clock) {
        this.ohip = ohip;
        this.clock = clock;
    }

    @Override
    public boolean soldSeparately(String hotelId, String packageCode) {
        return fresh(sellSeparate, hotelId, () -> readSellSeparate(hotelId)).getOrDefault(packageCode, true);
    }

    @Override
    public Set<String> includedIn(String hotelId, String ratePlanCode) {
        return fresh(included, hotelId + "|" + ratePlanCode, () -> readIncluded(hotelId, ratePlanCode));
    }

    /** Every package of the property, and whether it sells it separately. Not said, it does. */
    Map<String, Boolean> readSellSeparate(String hotelId) {
        var answer = new HashMap<String, Boolean>();
        for (var group : ohip.get(hotelId, "/rtp/v1/packages?hotelId={h}&limit=200", hotelId).body()
                .path("packageCodesList").path("packageCodes")) {
            for (var p : group.path("packageCodeShortInfo")) {
                answer.put(p.path("code").asText(), p.path("postingAttributes").path("sellSeparate").asBoolean(true));
            }
        }
        return answer;
    }

    /** The rate plan's packages and its package groups' members. A rate plan Opera does not have carries none. */
    Set<String> readIncluded(String hotelId, String ratePlanCode) {
        var packages = new HashSet<String>();
        ohip.find(hotelId, "/rtp/v1/hotels/{h}/ratePlans/{r}?fetchInstructions=Packages", hotelId, ratePlanCode)
                .ifPresent(body -> {
                    for (var plan : body.path("ratePlans")) {
                        var carried = plan.path("ratePackages");
                        carried.path("packages").forEach(p -> packages.add(p.path("code").asText()));
                        for (var group : carried.path("packageGroups")) {
                            group.path("packages").forEach(p -> packages.add(p.path("code").asText()));
                        }
                    }
                });
        return Set.copyOf(packages);
    }

    <T> T fresh(Map<String, Kept<T>> kept, String key, java.util.function.Supplier<T> read) {
        var current = kept.get(key);
        if (current != null && current.at().plus(KEEP).isAfter(clock.instant())) {
            return current.value();
        }
        var value = read.get();
        kept.put(key, new Kept<>(value, clock.instant()));
        return value;
    }
}
