package io.mateu.ecdemo1.shell.infra.in.ui;

import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.Text;
import io.mateu.uidl.data.VerticalLayout;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.ComponentTreeSupplier;
import io.mateu.uidl.interfaces.HttpRequest;

import java.util.List;

/**
 * Opera's free rooms for the next two weeks, room type by night — what a booking for those dates can
 * still get in Opera. Opened from the home's tile. Read from pms-integration-service, which keeps it
 * read every few minutes; Opera answers how many rooms it would sell, up to a cap.
 */
@Title("Cupo en Opera")
public class CupoOpera implements ComponentTreeSupplier {

    @Override
    public Component component(HttpRequest httpRequest) {
        var read = OperaRooms.read();
        if (read.isEmpty()) {
            return new Text("El cupo de Opera no está disponible ahora: pms-integration no lo ha leído aún o Opera no responde.");
        }
        var calendar = read.get();
        return VerticalLayout.builder().style("width: 100%;").content(List.of(
                new Text("Habitaciones libres en Opera (" + calendar.hotelId() + ") cada noche, por tipo — "
                        + calendar.cap() + "+ es «" + calendar.cap() + " o más». Leído " + OperaRooms.readAt(calendar) + "."),
                OperaRooms.grid(calendar))).build();
    }
}
