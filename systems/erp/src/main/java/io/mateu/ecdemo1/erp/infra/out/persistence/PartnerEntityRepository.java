package io.mateu.ecdemo1.erp.infra.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PartnerEntityRepository extends JpaRepository<PartnerEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PartnerEntity p where p.code = :code")
    Optional<PartnerEntity> findByCodeForUpdate(@Param("code") String code);

    @Query("""
            select p from PartnerEntity p
            where :text is null or :text = ''
               or lower(p.code) like lower(concat('%', :text, '%'))
               or lower(p.name) like lower(concat('%', :text, '%'))
            order by p.code
            """)
    Page<PartnerEntity> search(@Param("text") String text, Pageable pageable);

    @Query("""
            select p from PartnerEntity p
            where (:text is null or :text = ''
                   or lower(p.code) like lower(concat('%', :text, '%'))
                   or lower(p.name) like lower(concat('%', :text, '%')))
              and (:anyType = true or p.type in :types)
              and (:anyBillingMode = true or p.billingMode in :billingModes)
              and (:active is null or p.active = :active)
            order by p.code
            """)
    Page<PartnerEntity> search(@Param("text") String text,
                               @Param("anyType") boolean anyType, @Param("types") List<String> types,
                               @Param("anyBillingMode") boolean anyBillingMode, @Param("billingModes") List<String> billingModes,
                               @Param("active") Boolean active, Pageable pageable);
}
