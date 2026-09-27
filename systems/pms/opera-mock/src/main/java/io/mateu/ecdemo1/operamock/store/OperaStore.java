package io.mateu.ecdemo1.operamock.store;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * "Opera"'s state, in memory. Payloads are kept as they were sent — the double validates what the
 * real API validates and stores the rest untouched, so what the adapter wrote can be read back as
 * it wrote it.
 */
@Component
public class OperaStore {

    public record Call(Instant at, String method, String path, String hotelId, int status, long millis) {
    }

    final AtomicLong ids = new AtomicLong(700000);
    final Map<String, ObjectNode> reservations = new ConcurrentHashMap<>();
    final Map<String, ObjectNode> profiles = new ConcurrentHashMap<>();
    final Map<String, List<ObjectNode>> deposits = new ConcurrentHashMap<>();
    final Map<String, Instant> tokens = new ConcurrentHashMap<>();
    final ConcurrentLinkedDeque<Call> calls = new ConcurrentLinkedDeque<>();

    public String nextId() {
        return String.valueOf(ids.incrementAndGet());
    }

    public void putReservation(String id, ObjectNode reservation) {
        reservations.put(id, reservation);
    }

    public Optional<ObjectNode> reservation(String id) {
        return Optional.ofNullable(reservations.get(id));
    }

    public Collection<ObjectNode> reservations() {
        return reservations.values();
    }

    public void putProfile(String id, ObjectNode profile) {
        profiles.put(id, profile);
    }

    public Optional<ObjectNode> profile(String id) {
        return Optional.ofNullable(profiles.get(id));
    }

    public Collection<ObjectNode> profiles() {
        return profiles.values();
    }

    public void addDeposit(String reservationId, ObjectNode deposit) {
        deposits.computeIfAbsent(reservationId, k -> new ArrayList<>()).add(deposit);
    }

    public List<ObjectNode> deposits(String reservationId) {
        return deposits.getOrDefault(reservationId, List.of());
    }

    public void issueToken(String token, Instant expiresAt) {
        tokens.put(token, expiresAt);
    }

    public boolean validToken(String token, Instant now) {
        var expiry = tokens.get(token);
        return expiry != null && expiry.isAfter(now);
    }

    public void record(Call call) {
        calls.addFirst(call);
        while (calls.size() > 1000) {
            calls.removeLast();
        }
    }

    public List<Call> calls() {
        return List.copyOf(calls);
    }

    public void clear() {
        reservations.clear();
        profiles.clear();
        deposits.clear();
        calls.clear();
    }
}
