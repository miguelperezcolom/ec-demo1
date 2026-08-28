package io.mateu.ecdemo1.iacp.infra.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "mcp")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class McpEntity {

    @Id
    String id;
    @Column(nullable = false)
    String name;
    @Column(nullable = false)
    String url;
    @Column(nullable = false)
    String transport;
    long timeoutSeconds;
    @Column(length = 2048)
    String description;
    boolean enabled;
    LocalDateTime created;
}
