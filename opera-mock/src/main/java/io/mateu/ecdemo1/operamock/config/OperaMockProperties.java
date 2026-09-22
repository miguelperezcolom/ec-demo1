package io.mateu.ecdemo1.operamock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * The double's own credentials and settings. Fake on purpose: this service never talks to Oracle,
 * and nothing it accepts is a real Opera credential.
 *
 * @param capacityPerRoomType how many rooms of each type a hotel has, per night: past it, a
 *                            reservation is refused for lack of availability, as the Property
 *                            APIs do and the Distribution API would not (R19)
 */
@ConfigurationProperties("opera-mock")
public record OperaMockProperties(String appKey, String clientId, String clientSecret, String enterpriseId,
                                  Duration tokenTtl, List<String> hotels, int capacityPerRoomType) {

    public OperaMockProperties {
        if (tokenTtl == null) tokenTtl = Duration.ofHours(1);
        if (hotels == null) hotels = List.of("RIUPMI", "RIUCUN");
        if (capacityPerRoomType <= 0) capacityPerRoomType = 20;
    }
}
