package io.mateu.ecdemo1.contenthub.domain;

import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.visibility.VisibilityPolicy;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** A block's audience as the editors write it: a narrowing of its page's, never a widening. */
class BlockAudienceTest {

    @Test
    void a_narrowing_is_everyone_or_an_AND_of_department_hotel_and_job_title() {
        assertThat(Block.isNarrowing(null)).isTrue();
        assertThat(Block.isNarrowing(Audience.EVERYONE)).isTrue();
        assertThat(Block.isNarrowing(Audience.in(Attribute.DEPARTMENT, "RECEPCION"))).isTrue();
        assertThat(Block.isNarrowing(Audience.allOf(Audience.in(Attribute.HOTEL, "TFS01"),
                Audience.in(Attribute.JOB_TITLE, "JEFE")))).isTrue();
    }

    @Test
    void an_OR_a_named_audience_or_another_attribute_is_not_a_narrowing() {
        assertThat(Block.isNarrowing(Audience.anyOf(Audience.in(Attribute.DEPARTMENT, "RECEPCION"),
                Audience.in(Attribute.HOTEL, "TFS01")))).isFalse();
        assertThat(Block.isNarrowing(new Audience.Named("canarias"))).isFalse();
        assertThat(Block.isNarrowing(Audience.in(Attribute.COUNTRY, "ES"))).isFalse();
        assertThat(Block.isNarrowing(new Audience.AttributeIn(Attribute.DEPARTMENT, Set.of()))).isFalse();
        assertThat(Block.isNarrowing(Audience.allOf(Audience.in(Attribute.DEPARTMENT, "RECEPCION"),
                new Audience.Named("canarias")))).isFalse();
    }

    @Test
    void whoever_sees_a_block_sees_its_page() {
        var policy = new VisibilityPolicy(id -> null);
        var page = Audience.in(Attribute.HOTEL, "TFS01");
        var block = Audience.in(Attribute.DEPARTMENT, "RECEPCION");
        var effective = Audience.effective(page, block);
        for (var hotel : List.of("TFS01", "CUN01")) {
            for (var department : List.of("RECEPCION", "COCINA")) {
                var who = employee(hotel, department);
                if (policy.admits(effective, who)) {
                    assertThat(policy.admits(page, who)).as(hotel + "/" + department).isTrue();
                }
            }
        }
        assertThat(policy.admits(effective, employee("TFS01", "RECEPCION"))).isTrue();
        assertThat(policy.admits(effective, employee("CUN01", "RECEPCION"))).as("reception, but not the page's hotel").isFalse();
        assertThat(policy.admits(effective, employee("TFS01", "COCINA"))).isFalse();
    }

    static EmployeeAttributes employee(String hotel, String department) {
        var values = new EnumMap<Attribute, String>(Attribute.class);
        values.put(Attribute.HOTEL, hotel);
        values.put(Attribute.DEPARTMENT, department);
        return new EmployeeAttributes(Map.copyOf(values), "es");
    }
}
