package io.mateu.ecdemo1.demoreset;

import java.util.List;

/**
 * What a service empties when the demo goes back to zero (process reset-demo): its own tables, and
 * whatever else puts its own state back — all in one transaction.
 *
 * <p>Only what the integration made goes; what is set up stays (deploy/demo/zero.sh draws that line:
 * the ERP's partners, the front office's rooms and catalogs, the registration rules…). A table the
 * database does not have (yet, or any more) is skipped, so a plan can name a legacy one.
 *
 * @param service    the service, for the logs and the step's outcome
 * @param tables     emptied with one {@code TRUNCATE … CASCADE}
 * @param statements run after it, in the same transaction — e.g. {@code update room set occupancy = 'FREE'}
 */
public record DemoResetPlan(String service, List<String> tables, List<String> statements) {

    public DemoResetPlan {
        tables = tables == null ? List.of() : List.copyOf(tables);
        statements = statements == null ? List.of() : List.copyOf(statements);
        for (var table : tables) {
            if (!table.matches("[a-z_][a-z0-9_]*")) {
                throw new IllegalArgumentException("Not a table name: " + table);
            }
        }
    }

    public static DemoResetPlan truncate(String service, String... tables) {
        return new DemoResetPlan(service, List.of(tables), List.of());
    }

    public DemoResetPlan then(String... more) {
        var all = new java.util.ArrayList<>(statements);
        all.addAll(List.of(more));
        return new DemoResetPlan(service, tables, all);
    }
}
