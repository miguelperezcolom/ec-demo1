package io.mateu.ecdemo1.integrations.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface FrontOfficeIntegrationRepository extends JpaRepository<FrontOfficeIntegration, String>,
        JpaSpecificationExecutor<FrontOfficeIntegration> {

    Optional<FrontOfficeIntegration> findFirstByPmsHotelCodeAndStatusNot(String pmsHotelCode, FoIntegrationStatus status);

    List<FrontOfficeIntegration> findByPmsHotelCodeOrderByCreatedAtDesc(String pmsHotelCode);

    List<FrontOfficeIntegration> findByGateIsNotNull();

    List<FrontOfficeIntegration> findByStatus(FoIntegrationStatus status);

    List<FrontOfficeIntegration> findAllByOrderByPmsHotelCodeAsc();
}
