package io.mateu.ecdemo1.iacp.application.out.repository;

import io.mateu.ecdemo1.iacp.domain.aggregates.route.Route;
import io.mateu.ecdemo1.iacp.domain.aggregates.route.vo.RouteId;

import java.util.List;

public interface RouteRepository extends Repository<Route, RouteId> {

    /** Enabled routes in priority order — what the resolver evaluates, first match wins. */
    List<Route> findEnabledOrderedByPriority();
}
