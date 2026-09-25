package io.mateu.ecdemo1.communication.store;

import io.mateu.ecdemo1.integration.model.notification.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Who is told about what. An empty type or hotel means "any": a recipient with neither gets every
 * notification of every hotel — the integration's administrators. Per hotel is how the isolation
 * the rest of the design keeps reaches the people too (HLA R31).
 */
@Entity
@Table(name = "recipient")
@NoArgsConstructor
@Getter
@Setter
public class Recipient {

    @Id
    public String id;
    @Column(nullable = false)
    public String name;
    @Column(nullable = false)
    public String email;
    @Enumerated(EnumType.STRING)
    public NotificationType notificationType;
    public String hotelCode;
    public boolean active;

    public boolean wants(NotificationType type, String hotel) {
        return active && (notificationType == null || notificationType == type)
                && (hotelCode == null || hotelCode.isBlank() || hotelCode.equals(hotel));
    }
}
