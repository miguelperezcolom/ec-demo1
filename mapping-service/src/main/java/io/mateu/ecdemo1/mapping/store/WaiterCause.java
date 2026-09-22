package io.mateu.ecdemo1.mapping.store;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** That a process waits on a cause. */
@Entity
@Table(name = "waiter_cause")
@IdClass(WaiterCause.Key.class)
@NoArgsConstructor
@Getter
public class WaiterCause {

    @Id
    public String processKey;
    @Id
    public String causeKey;

    public WaiterCause(String processKey, String causeKey) {
        this.processKey = processKey;
        this.causeKey = causeKey;
    }

    public record Key(String processKey, String causeKey) implements Serializable {
        public Key() {
            this(null, null);
        }
    }
}
