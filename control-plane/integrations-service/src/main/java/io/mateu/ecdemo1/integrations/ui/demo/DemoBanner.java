package io.mateu.ecdemo1.integrations.ui.demo;

import io.mateu.ecdemo1.integrations.demo.OperaOutage;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.Trigger;
import io.mateu.uidl.annotations.TriggerType;
import io.mateu.uidl.annotations.UI;
import io.mateu.uidl.data.State;
import io.mateu.uidl.data.Text;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.ComponentTreeSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Hydratable;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The consoles' banner while an Opera outage is simulated: «Simulación: Opera no responde», in every
 * shell's header (ui-commons' UserWidget puts it before the inbox) and in the front office's; nothing
 * at all otherwise. Asked again every few seconds, like the inbox badge. Its own base URL, on every
 * host, for every signed-in user — /_demo is the administrators'.
 */
@UI("/_demo-banner/banner")
@Title("")
@Service
@RequiredArgsConstructor
@Trigger(type = TriggerType.OnLoad, actionId = "refresh", timeoutMillis = 10000)
@Trigger(type = TriggerType.OnSuccess, actionId = "refresh", calledActionId = "refresh", timeoutMillis = 10000)
@Action(id = "refresh")
public class DemoBanner implements Hydratable, ComponentTreeSupplier {

    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Madrid"));

    final OperaOutage outage;

    String content = "";

    Object refresh() {
        return new State(this);
    }

    @Override
    public void hydrate(HttpRequest httpRequest) {
        content = outage.status().filter(OperaOutage.Status::active)
                .map(s -> html(s.outage().until() == null ? null : TIME.format(s.outage().until())))
                .orElse("");
    }

    /** A red pill: what is simulated, and until when. Plain HTML, so both renderers draw it. */
    static String html(String until) {
        var text = "Simulación: Opera no responde" + (until == null ? "" : " (hasta las " + until + ")");
        return "<span role=\"status\" title=\"" + text + "\" style=\"display: inline-flex; align-items: center; gap: 0.35em;"
                + " margin-inline-end: 0.75rem; padding: 0.2em 0.75em; border-radius: 1em; white-space: nowrap;"
                + " font-size: var(--lumo-font-size-s, 0.875rem); font-weight: 600;"
                + " background: var(--lumo-error-color, #c62828); color: var(--lumo-error-contrast-color, #fff);\">"
                + "<span aria-hidden=\"true\">⚠</span>" + text + "</span>";
    }

    @Override
    public Component component(HttpRequest httpRequest) {
        return Text.builder().text("${state.content}").build();
    }
}
