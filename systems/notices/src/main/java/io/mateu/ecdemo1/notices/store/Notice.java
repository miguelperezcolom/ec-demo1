package io.mateu.ecdemo1.notices.store;

import io.mateu.ecdemo1.integration.model.notice.NoticeChanged.NoticeMoment;
import io.mateu.ecdemo1.integration.model.notice.NoticeChanged.NoticeType;
import io.mateu.ecdemo1.integration.model.notice.NoticeChanged.SubjectType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A reception notice: what the desk must know of a customer, a reservation or a partner, and when.
 *
 * <p>The enums are kept as plain strings, not as JPA enums: Hibernate's {@code ddl-auto: update}
 * writes a check constraint for an enum column and never rewrites it, so a value added later would be
 * refused by every database created before it.
 */
@Entity
@Table(name = "notice", indexes = {
        @Index(name = "notice_subject", columnList = "subjectType, subjectId"),
        @Index(name = "notice_source_ref", columnList = "sourceRef")})
public class Notice {

    /** Who masters a notice: this service, or Salesforce (a customer's, taken from the MDM). */
    public static final String NOTICES = "NOTICES";
    public static final String SALESFORCE = "SALESFORCE";

    @Id
    public String id;

    @Column(length = 20, nullable = false)
    public String subjectType;

    @Column(length = 64, nullable = false)
    public String subjectId;

    /** A partner's name, as the PMS names the agency on a reservation; null otherwise. */
    @Column(length = 200)
    public String subjectName;

    /** The CRS hotel; null for every hotel of the chain. */
    @Column(length = 20)
    public String hotelCode;

    @Column(length = 300)
    public String text;

    @Column(length = 20)
    public String type;

    public LocalDate fromDate;

    public LocalDate toDate;

    /** {@link NoticeMoment} names, comma separated. */
    @Column(length = 80)
    public String moments;

    public boolean active;

    /** Grows with every change: what the front office orders them by. A default, for rows before it. */
    @Column(nullable = false, columnDefinition = "bigint default 0")
    public long version;

    @Column(length = 20)
    public String source;

    /** Its record at the source, when that is another system (a Salesforce Case). */
    @Column(length = 64)
    public String sourceRef;

    @Column(length = 120)
    public String updatedBy;

    public Instant createdAt;

    public Instant updatedAt;

    public SubjectType subject() {
        return SubjectType.valueOf(subjectType);
    }

    public NoticeType noticeType() {
        return type == null ? NoticeType.INFORMATIVE : NoticeType.valueOf(type);
    }

    public Set<NoticeMoment> momentSet() {
        return moments(moments);
    }

    public boolean salesforce() {
        return SALESFORCE.equals(source);
    }

    /**
     * Whether the desk sees it for that subject, at that hotel and moment, some day of a stay from
     * {@code arrival} to {@code departure}: active, of that hotel or of the chain, shown then, and in
     * force some day of it.
     */
    public boolean appliesTo(String hotel, NoticeMoment moment, LocalDate arrival, LocalDate departure) {
        return active
                && (hotel == null || hotelCode == null || hotelCode.isBlank() || hotelCode.equalsIgnoreCase(hotel))
                && (moment == null || momentSet().contains(moment))
                && (fromDate == null || departure == null || !fromDate.isAfter(departure))
                && (toDate == null || arrival == null || !toDate.isBefore(arrival));
    }

    public static String moments(Set<NoticeMoment> moments) {
        return moments == null ? "" : moments.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    /**
     * The moments a stored value names. {@code STAY}, the customer notices' old name for the stay, is
     * read as {@code IN_HOUSE}; a name it does not know is left out rather than failing the notice.
     */
    public static Set<NoticeMoment> moments(String value) {
        var set = EnumSet.noneOf(NoticeMoment.class);
        if (value == null || value.isBlank()) {
            return set;
        }
        Arrays.stream(value.split(",")).map(String::trim).map(Notice::moment)
                .filter(java.util.Objects::nonNull).forEach(set::add);
        return set;
    }

    public static NoticeMoment moment(String name) {
        if ("STAY".equals(name)) {
            return NoticeMoment.IN_HOUSE;
        }
        try {
            return NoticeMoment.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
