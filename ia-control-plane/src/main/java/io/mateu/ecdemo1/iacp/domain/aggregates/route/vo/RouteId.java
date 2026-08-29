package io.mateu.ecdemo1.iacp.domain.aggregates.route.vo;

public record RouteId(String value) {
    public RouteId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A route id is required");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
