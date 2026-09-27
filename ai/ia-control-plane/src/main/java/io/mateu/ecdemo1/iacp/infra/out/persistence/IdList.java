package io.mateu.ecdemo1.iacp.infra.out.persistence;

import java.util.Arrays;
import java.util.List;

/**
 * The comma-separated id columns on {@code AgentEntity}, in one place so both directions agree.
 *
 * <p>Ids are validated at construction — see the {@code McpId}/{@code RagId} records — and cannot
 * be blank, so a comma is an unambiguous separator here. Nothing else in this service stores a
 * list in a column, and nothing else should.
 */
final class IdList {

    private IdList() {}

    static String join(List<String> ids) {
        return ids == null || ids.isEmpty() ? "" : String.join(",", ids);
    }

    static List<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
