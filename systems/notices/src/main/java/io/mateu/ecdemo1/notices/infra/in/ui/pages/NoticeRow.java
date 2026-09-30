package io.mateu.ecdemo1.notices.infra.in.ui.pages;

import io.mateu.uidl.data.Status;

/** One notice in the listing: what it is about, what it says, when and where the desk sees it. */
public record NoticeRow(String id, String sobre, String hotel, String aviso, Status tipo, String momentos,
                        String vigencia, Status estado, String origen) {
}
