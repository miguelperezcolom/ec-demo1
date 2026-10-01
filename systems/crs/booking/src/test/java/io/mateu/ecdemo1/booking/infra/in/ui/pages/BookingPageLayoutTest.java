package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.uidl.annotations.FoldoutDetail;
import io.mateu.uidl.annotations.HiddenInEditor;
import io.mateu.uidl.annotations.HiddenInView;
import io.mateu.uidl.annotations.KPI;
import io.mateu.uidl.annotations.PanelWidth;
import io.mateu.uidl.annotations.Section;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The booking's page: the amounts in the header, the comments with the rooms and guests, the
 * tracking with the payments — each page-only copy hidden in the editor, each original hidden on the page.
 */
class BookingPageLayoutTest {

    static java.lang.reflect.Field field(String name) throws NoSuchFieldException {
        return BookingViewModel.class.getDeclaredField(name);
    }

    /** The section a field is drawn in: the last {@code @Section} declared before it, or on it. */
    static String sectionOf(String name) {
        String section = null;
        for (var f : BookingViewModel.class.getDeclaredFields()) {
            if (f.isAnnotationPresent(Section.class)) {
                section = f.getAnnotation(Section.class).value();
            }
            if (f.getName().equals(name)) {
                return section;
            }
        }
        throw new IllegalArgumentException(name);
    }

    @Test
    void theAmountsAreTheHeadersKpis() throws Exception {
        for (var name : List.of("total", "paid", "pending")) {
            assertThat(field(name).isAnnotationPresent(KPI.class)).as(name).isTrue();
        }
        var foldout = BookingViewModel.class.getAnnotation(FoldoutDetail.class);
        assertThat(foldout.overview()).containsExactly("Booking");
    }

    @Test
    void theCommentsGoWithTheRoomsAndTheTrackingWithThePayments() throws Exception {
        assertThat(sectionOf("commentsOnPage")).isEqualTo("Rooms and guests");
        for (var name : List.of("idOnPage", "versionOnPage", "createdOnPage", "updatedOnPage")) {
            assertThat(sectionOf(name)).as(name).isEqualTo("Payments");
            assertThat(field(name).isAnnotationPresent(HiddenInEditor.class)).as(name).isTrue();
        }
        for (var name : List.of("comments", "id", "version", "created", "updated")) {
            assertThat(field(name).isAnnotationPresent(HiddenInView.class)).as(name).isTrue();
        }
    }

    @Test
    void theHolderAndTheOtherSystemsHaveRoom() throws Exception {
        assertThat(field("holderFirstName").getAnnotation(Section.class).panelWidth()).isEqualTo(PanelWidth.MEDIUM);
        assertThat(field("otherSystems").getAnnotation(Section.class).panelWidth()).isEqualTo(PanelWidth.WIDE);
        assertThat(Arrays.stream(BookingViewModel.class.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(Section.class))
                .map(f -> f.getAnnotation(Section.class).value()))
                .doesNotContain("Amounts");
    }
}
