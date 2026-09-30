package io.mateu.ecdemo1.notices.infra.in.ui.pages;

import io.mateu.ecdemo1.integration.model.notice.NoticeChanged.NoticeType;
import io.mateu.ecdemo1.integration.model.notice.NoticeChanged.SubjectType;
import io.mateu.uidl.annotations.Label;

import java.util.Set;

/** The search bar: each field narrows the notices, with the free text (its text, subject or name). */
public class NoticeFilters {

    public enum State { Activo, Inactivo }

    // Sets: the bar offers every enum filter as a multi-select and sends a list.
    @Label("Sobre")
    Set<SubjectType> subject;

    @Label("Tipo")
    Set<NoticeType> type;

    @Label("Estado")
    Set<State> status;
}
