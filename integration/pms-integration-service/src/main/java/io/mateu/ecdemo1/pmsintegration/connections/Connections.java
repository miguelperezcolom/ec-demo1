package io.mateu.ecdemo1.pmsintegration.connections;

import io.mateu.ecdemo1.integration.model.integration.IntegrationView;
import io.mateu.ecdemo1.integration.model.integration.OhipConnection;

import java.util.List;
import java.util.Optional;

/**
 * Where the connector learns how to reach each Opera property. Each hotel's integration carries its
 * own connection (HLA, «Aislamiento por hotel»): nothing about a tenant is configuration here.
 */
public interface Connections {

    /** How to reach this Opera property, or empty if no integration names it. */
    Optional<OhipConnection> of(String pmsHotelCode);

    /** Every hotel's integration, whatever its state. */
    List<IntegrationView> integrations();
}
