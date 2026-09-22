package io.mateu.ecdemo1.integrations.store;

import io.mateu.ecdemo1.integration.model.integration.IntegrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IntegrationRepository extends JpaRepository<Integration, String> {

    Optional<Integration> findByCrsHotelCode(String crsHotelCode);

    Optional<Integration> findFirstByPmsHotelCodeAndStatusNot(String pmsHotelCode, IntegrationStatus status);

    List<Integration> findByGateIsNotNull();

    List<Integration> findAllByOrderByCrsHotelCodeAsc();
}
