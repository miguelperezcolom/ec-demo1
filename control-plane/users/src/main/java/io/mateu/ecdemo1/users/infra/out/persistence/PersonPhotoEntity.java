package io.mateu.ecdemo1.users.infra.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PersonPhotoEntity {

    @Id
    String token;

    @Column(nullable = false)
    String userId;

    @Column(nullable = false)
    String contentType;

    @Column(nullable = false, columnDefinition = "bytea")
    byte[] content;
}
