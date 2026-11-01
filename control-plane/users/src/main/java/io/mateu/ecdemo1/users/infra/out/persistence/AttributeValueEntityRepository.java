package io.mateu.ecdemo1.users.infra.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttributeValueEntityRepository extends JpaRepository<AttributeValueEntity, String> {

    /** All attributes, matching the text in the label or the code. */
    @Query("""
            select v from AttributeValueEntity v
            where lower(v.label) like lower(concat('%', :text, '%'))
               or lower(v.code) like lower(concat('%', :text, '%'))
            order by v.attribute, v.label""")
    Page<AttributeValueEntity> search(@Param("text") String text, Pageable pageable);

    /** One attribute's values, matching the text in the label or the code. */
    @Query("""
            select v from AttributeValueEntity v
            where v.attribute = :attribute
              and (lower(v.label) like lower(concat('%', :text, '%'))
                   or lower(v.code) like lower(concat('%', :text, '%')))
            order by v.label""")
    Page<AttributeValueEntity> searchByAttribute(@Param("attribute") String attribute, @Param("text") String text,
                                                 Pageable pageable);
}
