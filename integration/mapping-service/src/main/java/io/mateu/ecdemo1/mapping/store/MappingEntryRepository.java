package io.mateu.ecdemo1.mapping.store;

import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MappingEntryRepository extends JpaRepository<MappingEntry, String>, JpaSpecificationExecutor<MappingEntry> {

    @Query("""
            select e from MappingEntry e
            where e.type = :type and e.sourceCode = :code and e.status = 'APPROVED'
              and (e.hotelCode = :hotel or e.hotelCode is null)
            """)
    List<MappingEntry> approvedFor(@Param("type") CodeType type, @Param("hotel") String hotelCode,
                                   @Param("code") String code);

    /**
     * The version in force for this code in this scope, locked until the deciding transaction ends:
     * a second approval of the same code waits here, and then finds it no longer APPROVED.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e from MappingEntry e
            where e.type = :type and e.sourceCode = :code and e.status = 'APPROVED'
              and ((:hotel is null and e.hotelCode is null) or e.hotelCode = :hotel)
            """)
    Optional<MappingEntry> lockApprovedInScope(@Param("type") CodeType type, @Param("hotel") String hotelCode,
                                               @Param("code") String code);

    /** An entry, locked until the deciding transaction ends: two decisions on it are taken one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from MappingEntry e where e.id = :id")
    Optional<MappingEntry> lockById(@Param("id") String id);

    @Query("""
            select coalesce(max(e.entryVersion), 0) from MappingEntry e
            where e.type = :type and e.sourceCode = :code
              and ((:hotel is null and e.hotelCode is null) or e.hotelCode = :hotel)
            """)
    int lastVersion(@Param("type") CodeType type, @Param("hotel") String hotelCode, @Param("code") String code);

    /** The same equivalence, still waiting for a person: same code, same scope, same PMS code. */
    @Query("""
            select e from MappingEntry e
            where e.type = :type and e.sourceCode = :code and e.targetCode = :target and e.status = 'PROPOSED'
              and ((:hotel is null and e.hotelCode is null) or e.hotelCode = :hotel)
            """)
    List<MappingEntry> pendingProposal(@Param("type") CodeType type, @Param("hotel") String hotelCode,
                                       @Param("code") String code, @Param("target") String target);

    List<MappingEntry> findByStatusOrderByCreatedAtDesc(EntryStatus status);

    boolean existsByStatus(EntryStatus status);

    List<MappingEntry> findAllByOrderByTypeAscSourceCodeAscEntryVersionDesc();
}
