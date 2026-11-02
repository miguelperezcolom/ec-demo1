package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import io.mateu.uidl.data.Text;
import io.mateu.uidl.fluent.Form;
import io.mateu.uidl.interfaces.HttpRequest;
import org.junit.jupiter.api.Test;

/** A portal page drawn as a {@link Form} names the browser tab after its title (Mateu does not, for those). */
class TabTitleTest {

    @Test
    void a_titled_page_names_the_tab() {
        var request = mock(HttpRequest.class);
        PortalComponents.namingTheTab(request, Form.builder().title("Bienvenidos a la nueva intranet").build());
        verify(request).setAttribute("windowTitle", "Bienvenidos a la nueva intranet");
    }

    @Test
    void an_untitled_page_or_another_component_leaves_it_alone() {
        var request = mock(HttpRequest.class);
        PortalComponents.namingTheTab(request, Form.builder().title(" ").build());
        PortalComponents.namingTheTab(request, new Text("hola"));
        verify(request, never()).setAttribute(eq("windowTitle"), any());
    }
}
