package io.mateu.ecdemo1.users.infra.out.persistence;


import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@AllArgsConstructor@NoArgsConstructor
@Getter
public class UserGroupEntity {

    @Id
    String id;

    String name;

    String description;

    String status;

}
