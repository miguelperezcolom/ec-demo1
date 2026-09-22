package io.mateu.ecdemo1.pmsintegration.rest;

import io.mateu.ecdemo1.integration.model.integration.ConnectivityCheck;
import io.mateu.ecdemo1.integration.model.integration.OhipConnection;
import io.mateu.ecdemo1.pmsintegration.ohip.OhipClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tries a connection to an Opera property for the integrations service, before any traffic relies
 * on it. The answer is always 200: a connection that does not work is a result, not an error.
 */
@RestController
@RequiredArgsConstructor
public class ConnectionController {

    final OhipClient ohip;

    @PostMapping("/connections/verify")
    public ConnectivityCheck verify(@RequestBody OhipConnection connection) {
        return ohip.verify(connection);
    }
}
