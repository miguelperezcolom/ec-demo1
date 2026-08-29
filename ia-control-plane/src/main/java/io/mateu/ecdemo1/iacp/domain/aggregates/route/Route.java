package io.mateu.ecdemo1.iacp.domain.aggregates.route;

import io.mateu.ecdemo1.iacp.domain.aggregates.route.vo.RouteId;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Enabled;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Name;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Time;
import io.mateu.workflow.ddd.AggregateRoot;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * A rule that picks which agent answers, given who is asking and where from.
 *
 * <p>Four conditions, each optional: a required {@code role}, a {@code tenant}, a {@code locale} and
 * a {@code routePrefix} (the UI screen the prompt came from). A null condition means "don't care", so
 * a rule with none set matches everything — a catch-all default — and a rule with several set is an
 * AND of them. The routes are tried in {@code priority} order and the first match wins, which is
 * what makes a specific rule beat a general one: give it a lower number.
 *
 * <p>The match lives in the aggregate because it is the rule's own logic, not the resolver's — the
 * resolver's job is only to try the rules in order and take the first {@link #matches} that returns
 * true.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class Route extends AggregateRoot {

    RouteId id;
    Name name;
    /** Lower is tried first. Ties are broken by nothing in particular, so avoid them. */
    int priority;
    String role;
    String tenant;
    String locale;
    String routePrefix;
    String targetAgentId;
    Enabled enabled;
    Time created;

    public static Route of(RouteId id, Name name, int priority, String role, String tenant,
                           String locale, String routePrefix, String targetAgentId) {
        var route = new Route();
        route.id = id;
        route.name = name;
        route.priority = priority;
        route.role = blankToNull(role);
        route.tenant = blankToNull(tenant);
        route.locale = blankToNull(locale);
        route.routePrefix = blankToNull(routePrefix);
        route.targetAgentId = targetAgentId;
        route.enabled = Enabled.yes();
        route.created = new Time(LocalDateTime.now());
        return route;
    }

    public void update(Name name, int priority, String role, String tenant, String locale,
                       String routePrefix, String targetAgentId, Enabled enabled) {
        this.name = name;
        this.priority = priority;
        this.role = blankToNull(role);
        this.tenant = blankToNull(tenant);
        this.locale = blankToNull(locale);
        this.routePrefix = blankToNull(routePrefix);
        this.targetAgentId = targetAgentId;
        this.enabled = enabled;
    }

    public boolean isUsable() {
        return enabled.value();
    }

    /** True when every condition this route sets is satisfied by the request. Unset conditions pass. */
    public boolean matches(Collection<String> roles, String reqTenant, String reqLocale, String reqRoute) {
        if (role != null && (roles == null || !roles.contains(role))) {
            return false;
        }
        if (tenant != null && !tenant.equals(reqTenant)) {
            return false;
        }
        if (locale != null && (reqLocale == null || !locale.equalsIgnoreCase(reqLocale))) {
            return false;
        }
        if (routePrefix != null && (reqRoute == null || !reqRoute.startsWith(routePrefix))) {
            return false;
        }
        return true;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
