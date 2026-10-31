package io.mateu.ecdemo1.users.infra.out.persistence;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@AllArgsConstructor@NoArgsConstructor
@Getter
public class UserEntity {

    @Id
    String id;

    String name;

    String email;

    @Column(columnDefinition = "TEXT")
    String groupsJson;

    @Column(columnDefinition = "TEXT")
    String rolesJson;

    String status;

    /**
     * The employee attributes, as JSON: seven optional codes read and written together, never queried
     * one by one here (the content service filters on them, in its own directory). Added after the
     * table existed — ddl-auto adds the column, and rows from before read as null, i.e. no attributes.
     */
    @Column(columnDefinition = "TEXT")
    String attributesJson;

}
