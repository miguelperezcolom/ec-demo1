package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.uidl.annotations.FoldoutDetail;
import io.mateu.uidl.annotations.HiddenInEditor;
import io.mateu.uidl.annotations.HiddenInView;
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
    void theAmountsAreBadgesNextToTheStatusNotKpis() throws Exception {
        assertThat(Arrays.stream(BookingViewModel.class.getDeclaredFields()).map(java.lang.reflect.Field::getName))
                .doesNotContain("total", "paid", "pending");
        assertThat(field("pendingBadge").getType()).isEqualTo(io.mateu.uidl.data.Status.class);
        assertThat(io.mateu.uidl.interfaces.SubtitleSupplier.class).isAssignableFrom(BookingViewModel.class);
        var foldout = BookingViewModel.class.getAnnotation(FoldoutDetail.class);
        assertThat(foldout.overview()).containsExactly("Booking");
    }

    @Test
    void whatIsLeftToPayIsAmberAndAPaidBookingGreen() {
        var pending = BookingViewModel.pendingBadgeOf(new java.math.BigDecimal("1431.12"), java.math.BigDecimal.ZERO, "EUR");
        assertThat(pending.type()).isEqualTo(io.mateu.uidl.data.StatusType.WARNING);
        assertThat(pending.message()).isEqualTo("Pendiente 1.431,12 EUR");
        var paid = BookingViewModel.pendingBadgeOf(new java.math.BigDecimal("100"), new java.math.BigDecimal("100.00"), "EUR");
        assertThat(paid.type()).isEqualTo(io.mateu.uidl.data.StatusType.SUCCESS);
        assertThat(paid.message()).isEqualTo("Pagado");
        assertThat(BookingViewModel.amountsOf(new java.math.BigDecimal("1431.12"), java.math.BigDecimal.ZERO, "EUR", 5))
                .isEqualTo("Total 1.431,12 EUR (5 noches) · Pagado 0,00 EUR");
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

    @org.junit.jupiter.api.Test
    void eachPaymentIsACardTitledByTypeAndMethod() {
        var p = new PaymentViewModel("p1", io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.PaymentType.Deposit,
                "VISA", new java.math.BigDecimal("100.00"), java.time.LocalDate.of(2026, 10, 1), "ref-1");
        assertThat(BookingViewModel.paymentTitleOf(p)).isEqualTo("Deposit · VISA");
        var vm = new BookingViewModel(null, null, null, null, null, null, null, null);
        vm.payments = java.util.List.of(p);
        var cards = (io.mateu.uidl.data.StatusList) vm.paymentCards();
        assertThat(cards.items()).hasSize(1);
        assertThat(cards.items().get(0).status()).isEqualTo("100.00");
        assertThat(cards.items().get(0).lines()).containsExactly("01/10/2026", "ref-1");
        vm.payments = java.util.List.of();
        assertThat(vm.paymentCards()).isInstanceOf(io.mateu.uidl.data.Text.class);
    }
}
