package io.mateu.ecdemo1.operamock.ui.pages;

import io.mateu.ecdemo1.operamock.store.Faults;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Button;
import io.mateu.uidl.annotations.Notice;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ButtonStyle;
import io.mateu.uidl.data.State;
import io.mateu.uidl.interfaces.HttpRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/**
 * Make "Opera" fail: the next N calls whose path contains the text answer with the status. 503 or
 * 429 for what the adapter must retry; 400 for what it must not.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Faults")
public class FaultsPage {

    int status = 503;
    String pathContains = "/reservations";
    int count = 3;

    @Notice
    String armed;

    final Faults faults;

    @Button(buttonStyle = ButtonStyle.primary)
    @Action
    public Object arm(HttpRequest httpRequest) {
        faults.inject(status, pathContains, count);
        armed = "Armed: " + faults.current() + " — " + faults.injected() + " injected so far";
        return new State(this);
    }
}
