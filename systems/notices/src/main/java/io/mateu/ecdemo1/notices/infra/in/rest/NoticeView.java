package io.mateu.ecdemo1.notices.infra.in.rest;

import io.mateu.ecdemo1.notices.store.Notice;

import java.time.LocalDate;
import java.util.List;

/** A notice as the REST API and the agent's tools give it. */
public record NoticeView(String id, String subjectType, String subjectId, String subjectName, String hotelCode,
                         String text, String type, LocalDate from, LocalDate to, List<String> moments, boolean active,
                         long version, String source, String sourceRef) {

    public static NoticeView of(Notice n) {
        return new NoticeView(n.id, n.subjectType, n.subjectId, n.subjectName, n.hotelCode, n.text, n.noticeType().name(),
                n.fromDate, n.toDate, n.momentSet().stream().sorted().map(Enum::name).toList(), n.active, n.version,
                n.source, n.sourceRef);
    }
}
